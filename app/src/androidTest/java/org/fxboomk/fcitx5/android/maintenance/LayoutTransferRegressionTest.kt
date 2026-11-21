/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance

import android.content.Intent
import android.net.Uri
import android.view.inspector.WindowInspector
import android.widget.Button
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.TextKeyboardLayoutProfileManagerActivity
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.data.LayoutDataManager
import org.fxboomk.fcitx5.android.ui.main.settings.behavior.share.LayoutProfileArchive
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LayoutTransferRegressionTest {
    @Test fun activityExportsJsonAndZipAndRejectsFileCreatedAfterConfirmationPreview() {
        val s=MaintenanceImeSession()
        val names=listOf("maintenance-a-${UUID.randomUUID()}", "maintenance-b-${UUID.randomUUID()}")
        val files=names.map { checkNotNull(UserConfigFiles.textKeyboardLayoutJson(it)) }
        val jsonFile=File(s.context.cacheDir,"${names[0]}.json")
        val zipFile=File(s.context.cacheDir,"${names[0]}.zip")
        val activity=s.instrumentation.startActivitySync(Intent(s.context, TextKeyboardLayoutProfileManagerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TextKeyboardLayoutProfileManagerActivity
        fun invoke(name: String,vararg args: Any) = s.main {
            activity.javaClass.declaredMethods.single { it.name==name }.apply { isAccessible=true }.invoke(activity,*args)
        }
        try {
            val json=LayoutDataManager(s.context).apply { loadFromFile(null) }.exportCurrentJsonString()
            assertTrue(json.isNotBlank())
            files.forEach { it.parentFile!!.mkdirs(); it.writeText(json) }
            invoke("writeProfileJsonTo",Uri.fromFile(jsonFile),names[0])
            s.await("JSON export complete") { jsonFile.isFile && jsonFile.readText()==json }
            invoke("writeProfilesZipTo",Uri.fromFile(zipFile),names)
            s.await("ZIP export complete") { runCatching { zipFile.inputStream().use { LayoutProfileArchive.readZip(it) }.size==2 }.getOrDefault(false) }
            val archived = zipFile.inputStream().use(LayoutProfileArchive::readZip)
            assertEquals(names.map { "$it.json" }.toSet(), archived.map { it.name }.toSet())
            assertTrue("Each exported entry retains its JSON", archived.all { it.json == json })
            files.forEach { assertTrue(it.delete()) }
            invoke("importFromZip",Uri.fromFile(zipFile))
            var positive: Button?=null
            s.await("real import confirmation dialog") {
                positive=WindowInspector.getGlobalWindowViews().asSequence().flatMap { it.descendants() }.filterIsInstance<Button>().firstOrNull { it.id==android.R.id.button1 && it.isShown }
                positive!=null
            }
            files[0].writeText("another writer created this file after preview")
            s.main { positive!!.performClick() }
            s.await("non-conflicting imported layout fully written") { files[1].isFile && files[1].readText()==json }
            assertEquals("another writer created this file after preview",files[0].readText())
        } finally {
            s.main { activity.finish() }; files.forEach { it.delete() }; jsonFile.delete(); zipFile.delete()
        }
    }
}
