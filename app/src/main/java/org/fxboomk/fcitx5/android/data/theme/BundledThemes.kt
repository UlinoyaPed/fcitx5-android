package org.fxboomk.fcitx5.android.data.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal object BundledThemes {
    fun install(
        themeDir: File,
        listAssets: (String) -> List<String>,
        openAsset: (String) -> InputStream
    ) {
        File(themeDir, "images").mkdirs()
        val marker = File(themeDir, ".bundled-themes-v1")
        val installed = if (marker.isFile) marker.readLines().toMutableSet() else mutableSetOf()
        listAssets("theme").filter { it.endsWith(".json") }.forEach { name ->
            if (name in installed) return@forEach
            val target = File(themeDir, name)
            // An existing same-name theme belongs to the user. Seed each default only once,
            // so later edits and deletions survive refreshes and application upgrades.
            if (!target.exists()) {
                val raw = openAsset("theme/$name").bufferedReader().use { it.readText() }
                val bg = Json.parseToJsonElement(raw).jsonObject["backgroundImage"] as? JsonObject
                listOf("croppedFilePath", "srcFilePath").forEach image@{ key ->
                    val path = bg?.get(key)?.jsonPrimitive?.content ?: return@image
                    require(path.startsWith("images/") && File(path).name == path.removePrefix("images/"))
                    val image = File(themeDir, path)
                    if (!image.exists()) {
                        writeAtomically(image) { output ->
                            openAsset("theme/$path").use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                writeAtomically(target) { it.write(raw.encodeToByteArray()) }
            }
            installed.add(name)
            writeAtomically(marker) { it.write(installed.joinToString("\n").encodeToByteArray()) }
        }
    }

    private fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        val pending = File.createTempFile(".theme-", ".tmp", target.parentFile)
        try {
            pending.outputStream().use(write)
            check(pending.renameTo(target)) { "Cannot install ${target.name}" }
        } finally {
            pending.delete()
        }
    }
}
