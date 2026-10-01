package org.fxboomk.fcitx5.android.data.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** Decodes and validates an archive before anything is written to the theme directory. */
internal object ThemeArchive {
    fun read(
        src: InputStream,
        tempDir: File,
        charset: Charset = Charsets.UTF_8
    ): Pair<Theme.Custom, Boolean> {
        val root = tempDir.canonicalFile
        val files = mutableListOf<File>()
        ZipInputStream(src, charset).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val path = entry.name.replace('\\', '/')
                require(!path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path)) {
                    "Absolute ZIP entry"
                }
                val file = File(root, path).canonicalFile
                require(file.path.startsWith(root.path + File.separator)) { "ZIP entry outside archive" }
                if (!entry.isDirectory && !path.startsWith("__MACOSX/") && !file.name.startsWith("._")) {
                    require(file !in files) { "Duplicate ZIP entry" }
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zip.copyTo(it) }
                    files.add(file)
                }
                entry = zip.nextEntry
            }
        }
        val jsonFile = files.singleOrNull { it.extension.equals("json", ignoreCase = true) }
            ?: error("Expected one theme JSON")
        val raw = jsonFile.readText()
        fun decode(json: String): Pair<Theme.Custom, Boolean> {
            val (theme, migrated) = Json.decodeFromString(CustomThemeSerializer.WithMigrationStatus, json)
            val bg = theme.backgroundImage ?: return theme to migrated
            fun image(path: String): File {
                val candidates = listOf(File(path), File(jsonFile.parentFile, path), File(root, path))
                return candidates.map { it.canonicalFile }.firstOrNull { it in files && it != jsonFile }
                    ?: error("Missing theme image: $path")
            }
            return theme.copy(backgroundImage = bg.copy(
                croppedFilePath = image(bg.croppedFilePath).absolutePath,
                srcFilePath = image(bg.srcFilePath).absolutePath
            )) to migrated
        }
        return runCatching { decode(raw) }.getOrElse {
            // Repair paths in the JSON itself, then run the same parser and validation again.
            val obj = Json.parseToJsonElement(raw).jsonObject
            val bg = obj["backgroundImage"] as? JsonObject ?: throw it
            val images = files.filter { file ->
                file != jsonFile && (file.extension.lowercase() in
                    setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "avif", "heic") ||
                    file.name.endsWith("-src"))
            }
            fun repairedPath(key: String, role: String): JsonPrimitive {
                val path = (bg[key] as? JsonPrimitive)?.contentOrNull.orEmpty().replace('\\', '/')
                val name = path.substringAfterLast('/')
                val exact = listOf(File(path), File(jsonFile.parentFile, path), File(root, path))
                    .map { it.canonicalFile }.firstOrNull { it in images }
                if (exact != null) return JsonPrimitive(exact.absolutePath)
                val matches = images.filter { file -> file.name == name }
                val match = if (matches.isNotEmpty()) matches.singleOrNull() else images.singleOrNull { file ->
                    file.nameWithoutExtension.endsWith("-$role", ignoreCase = true)
                }
                return JsonPrimitive(requireNotNull(match) { "Missing or ambiguous $role image" }.absolutePath)
            }
            val repaired = JsonObject(bg + mapOf(
                "croppedFilePath" to repairedPath("croppedFilePath", "cropped"),
                "srcFilePath" to repairedPath("srcFilePath", "src")
            ))
            decode(JsonObject(obj + ("backgroundImage" to repaired)).toString())
        }
    }
}
