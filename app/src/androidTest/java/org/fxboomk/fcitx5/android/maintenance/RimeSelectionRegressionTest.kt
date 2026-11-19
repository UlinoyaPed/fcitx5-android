/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import android.content.Context
import android.content.Intent
import android.widget.CheckBox
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fxboomk.fcitx5.android.core.CapabilityFlags
import org.fxboomk.fcitx5.android.core.RawConfig
import org.fxboomk.fcitx5.android.daemon.FcitxDaemon
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import org.fxboomk.fcitx5.android.ui.main.MainActivity
import org.fxboomk.fcitx5.android.ui.main.settings.SettingsRoute
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.manager.RimeSchemaResolver
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RimeSelectionRegressionTest {
    private data class Row(val selected: Boolean,val id: String,val label: String)
    @Test fun menuKeepsHiddenEnabledSchemasAndOrderOnlySaveDoesNotDeploy() = runBlocking {
        val s=MaintenanceImeSession()
        val name="maintenance-rime-menu"
        val connection=FcitxDaemon.connect(name)
        val visibility=s.context.getSharedPreferences("rime-schema-selector",Context.MODE_PRIVATE)
        val oldVisibility=visibility.getString("visible-order",null)
        val custom=File(UserConfigFiles.rimeDataDir(),"default.custom.yaml")
        val order=File(UserConfigFiles.configDir(),"conf/rime-schema-selector.conf")
        val backups=listOf(custom,order).associateWith { if(it.isFile)it.readBytes() else null }
        var activity: MainActivity?=null
        var original: List<Row> = emptyList()
        var enabled: Array<String> = emptyArray()
        var active=""
        fun encode(rows: List<Row>)=rows.joinToString("\n") { "${if(it.selected)1 else 0}\t${it.id}\t${it.label}" }
        fun cfg(rows: List<Row>,changed: Boolean)=RawConfig(arrayOf(RawConfig("Items",encode(rows)),RawConfig("SelectionChanged",if(changed)"1" else "0")))
        fun launch(): MainActivity = (s.instrumentation.startActivitySync(Intent(s.context,MainActivity::class.java)
            .setAction(Intent.ACTION_RUN).putExtra(MainActivity.EXTRA_SETTINGS_ROUTE,SettingsRoute.MultiSelect("Rime","rime","schema-selector","Items",1))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity).also { activity=it }
        try {
            withTimeout(180000) { connection.runOnReady {
                enabled=enabledIme().map { it.uniqueName }.toTypedArray(); active=currentIme().uniqueName
                assertTrue(availableIme().any { it.uniqueName=="rime" })
                setEnabledIme((enabled.toList()+"rime").distinct().toTypedArray())
                activate(12400,s.context.packageName); setCapFlags(CapabilityFlags.DefaultFlags); focus(true); activateIme("rime")
                val raw=getAddonSubConfig("rime","schema-selector"); val tree=raw.findByName("cfg")?:raw
                original=tree["Items"].value.orEmpty().lineSequence().filter { it.isNotBlank() }.map {
                    val parts=it.split('\t',limit=3); Row(parts[0]=="1",parts[1],parts.getOrElse(2){parts[1]})
                }.toList()
            } }
            assertTrue(original.size>=2); assertTrue(original.any { it.selected })
            visibility.edit().remove("visible-order").commit()
            var menu=launch()
            val selected=original.filter { it.selected }
            s.await("initial menu shows enabled bundled schemas",120000) {
                val boxes=menu.window.decorView.descendants().filterIsInstance<CheckBox>().toList()
                selected.all { row-> boxes.any { it.contentDescription?.toString()==row.label && it.isChecked } }
            }
            s.main { menu.finish() }; s.instrumentation.waitForIdleSync()
            val edited=original.firstOrNull { !it.selected }?:selected.first()
            val retained=selected.filter { it.id!=edited.id }.map { it.id }.toSet()
            assertTrue("fixture must retain another enabled schema",retained.isNotEmpty())
            visibility.edit().putString("visible-order",JSONArray(listOf(edited.id)).toString()).commit()
            menu=launch()
            var box: CheckBox?=null
            s.await("explicit visible schema row") { box=menu.window.decorView.descendants().filterIsInstance<CheckBox>().firstOrNull { it.contentDescription?.toString()==edited.label }; box!=null }
            s.main { box!!.performClick(); menu.finish() }
            val expected=retained+if(edited.selected)emptySet() else setOf(edited.id)
            s.await("selection deploy keeps hidden enabled schemas",180000) { RimeSchemaResolver.readSelectedIds().toSet()==expected }
            assertEquals(JSONArray(listOf(edited.id)).toString(),visibility.getString("visible-order",null))
            val before=if(custom.isFile)custom.readBytes() else null; val modified=custom.lastModified()
            val reversed=original.reversed().map { it.copy(selected=it.id in expected) }
            withTimeout(180000) { connection.runOnReady { setAddonSubConfig("rime","schema-selector-order",cfg(reversed,false)) } }
            assertArrayEquals(before,if(custom.isFile)custom.readBytes() else null); assertEquals(modified,custom.lastModified())
            val savedOrder=order.readLines().filter { it.matches(Regex("\\d+=.*")) }.map { it.substringAfter('=') }
            assertEquals(reversed.map { it.id },savedOrder)
        } finally {
            s.main { activity?.finish() }
            withTimeout(180000) { connection.runOnReady {
                if(original.isNotEmpty()) setAddonSubConfig("rime","schema-selector",cfg(original,true))
                backups.forEach { (file,bytes)->if(bytes==null)file.delete() else { file.parentFile!!.mkdirs(); file.writeBytes(bytes) } }
                if(enabled.isNotEmpty())setEnabledIme(enabled); if(active.isNotBlank())activateIme(active)
                focus(false); deactivate(12400)
            } }
            visibility.edit().apply { if(oldVisibility==null)remove("visible-order") else putString("visible-order",oldVisibility) }.commit()
            FcitxDaemon.disconnect(name)
        }
    }
}
