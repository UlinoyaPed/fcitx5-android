/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fxboomk.fcitx5.android.data.pinyin

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.fxboomk.fcitx5.android.input.config.UserConfigFiles
import java.io.File

object CustomShuangpinSchemeStore {
    const val UNIQUE_NAME = "shuangpin-custom"

    /**
     * 自定义双拼输入法条目配置内容。
     *
     * 与安装包内置的 `usr/share/fcitx5/inputmethod/shuangpin-custom.conf` 保持一致，
     * 供用户改用自定义 id 时写入用户级条目目录（条目名取自文件名）。
     */
    private const val ENTRY_CONF = """[InputMethod]
Name=Custom Shuangpin
Icon=fcitx-shuangpin
Label=双
LangCode=zh_CN
Addon=pinyin
Configurable=True
"""

    private data class CachedScheme(val stamp: Long, val scheme: CustomShuangpinScheme)

    @Volatile
    private var cachedScheme: CachedScheme? = null

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): CustomShuangpinScheme {
        val jsonFile = UserConfigFiles.customShuangpinSchemeJson()
        if (jsonFile.isFile) {
            val stamp = jsonFile.lastModified()
            cachedScheme?.takeIf { it.stamp == stamp }?.let { return it.scheme }
            readJson(jsonFile)?.let {
                val scheme = sanitize(it)
                cachedScheme = CachedScheme(stamp, scheme)
                return scheme
            }
        }

        val spDatFile = UserConfigFiles.customShuangpinSpDat()
        if (spDatFile.isFile) {
            return sanitize(
                runCatching {
                    CustomShuangpinSchemeCodec.parseSpDat(spDatFile.readText())
                }.getOrDefault(CustomShuangpinSchemeCodec.defaultScheme())
            )
        }

        return CustomShuangpinSchemeCodec.defaultScheme()
    }

    fun displayName(): String = load().name.ifBlank { CustomShuangpinSchemeCodec.DEFAULT_NAME }

    /** 当前方案使用的输入法 uniqueName（由方案 id 派生）。 */
    fun currentUniqueName(): String = uniqueNameFor(load().id)

    /** 由方案 id（`shuangpin-` 之后的后缀）得到输入法 uniqueName。 */
    fun uniqueNameFor(id: String): String =
        ShuangpinLayoutKeys.uniqueNameForSuffix(id.trim().lowercase())

    /** 判断某个输入法 uniqueName 是否就是当前的自定义双拼方案。 */
    fun isCustomScheme(uniqueName: String): Boolean = uniqueName == currentUniqueName()

    /** 自定义方案改用其他 id 后隐藏内置占位条目；其他用户条目继续可用。 */
    fun isStaleCustomEntry(uniqueName: String): Boolean =
        uniqueName == UNIQUE_NAME && !isCustomScheme(uniqueName)

    /**
     * Materialize the default scheme on first start, and migrate an existing
     * hand-written `sp.dat` from the old "自定义" option into the structured
     * JSON representation. The JSON file is the source of truth afterwards.
     */
    fun ensureInitialized() {
        val jsonFile = UserConfigFiles.customShuangpinSchemeJson()
        val spDatFile = UserConfigFiles.customShuangpinSpDat()

        if (jsonFile.isFile) {
            var scheme = (readJson(jsonFile)
                ?: runCatching {
                    CustomShuangpinSchemeCodec.parseSpDat(spDatFile.readText())
                }.getOrNull()
                ?: CustomShuangpinSchemeCodec.defaultScheme()).let(::sanitize)
            // Older app versions stored no syllable overrides in JSON, but the
            // existing sp.dat may already contain a [音节] section. Preserve
            // those entries when upgrading.
            if (scheme.syllables.isEmpty() && spDatFile.isFile) {
                val spScheme = runCatching {
                    CustomShuangpinSchemeCodec.parseSpDat(spDatFile.readText())
                }.getOrNull()
                if (spScheme != null && spScheme.syllables.isNotEmpty()) {
                    scheme = scheme.copy(syllables = spScheme.syllables)
                }
            }
            writeScheme(jsonFile, spDatFile, scheme)
            cachedScheme = null
            return
        }

        if (spDatFile.isFile) {
            // Preserve a hand-written sp.dat (including syllable overrides)
            // until the user edits and saves the scheme in the app.
            val scheme = sanitize(
                runCatching {
                    CustomShuangpinSchemeCodec.parseSpDat(spDatFile.readText())
                }.getOrDefault(CustomShuangpinSchemeCodec.defaultScheme())
            )
            writeJson(jsonFile, scheme)
            cachedScheme = null
            return
        }

        writeScheme(jsonFile, spDatFile, CustomShuangpinSchemeCodec.defaultScheme())
        cachedScheme = null
    }

    fun save(scheme: CustomShuangpinScheme): Result<Unit> = runCatching {
        require(CustomShuangpinSchemeCodec.isValidName(scheme.name)) {
            "Invalid custom shuangpin scheme name"
        }
        require(CustomShuangpinSchemeCodec.isValidZeroInitial(scheme.zeroInitial)) {
            "Invalid custom shuangpin zero-initial marker"
        }
        require(scheme.initials.values.all(CustomShuangpinSchemeCodec::isValidKey)) {
            "Invalid custom shuangpin initial key"
        }
        require(scheme.finals.values.all(CustomShuangpinSchemeCodec::isValidKey)) {
            "Invalid custom shuangpin final key"
        }
        require(scheme.syllables.all { (pinyin, keys) ->
            CustomShuangpinSchemeCodec.isValidSyllable(pinyin) &&
                keys.isNotEmpty() &&
                keys.all(CustomShuangpinSchemeCodec::isValidSyllableKeys)
        }) {
            "Invalid custom shuangpin syllable override"
        }
        require(ShuangpinLayoutKeys.isValidCustomSuffix(scheme.id)) {
            "Invalid custom shuangpin scheme id"
        }

        val jsonFile = UserConfigFiles.customShuangpinSchemeJson()
        val spDatFile = UserConfigFiles.customShuangpinSpDat()
        cachedScheme = null
        writeScheme(jsonFile, spDatFile, scheme)
    }

    /**
     * 重命名自定义方案，并迁移全部关联数据。
     *
     * 迁移内容包括：方案名称与 id（JSON、sp.dat）、用户级输入法条目配置
     * （`<data>/inputmethod/shuangpin-<id>.conf`）、该输入法的专属配置
     * （`config/pinyin/shuangpin-<id>.conf`），以及所有键盘布局文件中
     * `shuangpin-<旧 id>` → `shuangpin-<新 id>` 的顶层键——布局高度覆写保存在
     * 键内的 `__meta__` 中，因此随键一并迁移。
     */
    fun rename(newName: String, newId: String): Result<Unit> = runCatching {
        val name = newName.trim()
        require(CustomShuangpinSchemeCodec.isValidName(name)) {
            "Invalid custom shuangpin scheme name"
        }
        val id = newId.trim().lowercase()
        require(ShuangpinLayoutKeys.isValidCustomSuffix(id)) {
            "Invalid custom shuangpin scheme id"
        }

        val previous = load()
        if (previous.name == name && previous.id == id) return@runCatching

        val oldUniqueName = uniqueNameFor(previous.id)
        val newUniqueName = uniqueNameFor(id)
        val layouts = UserConfigFiles.listTextKeyboardLayoutProfiles()
            .mapNotNull(UserConfigFiles::textKeyboardLayoutJson).filter(File::isFile)
        if (oldUniqueName != newUniqueName) {
            require(!UserConfigFiles.pinyinImConfig(newUniqueName).exists()) { "Target input-method configuration already exists" }
            require(entryConfFile(newUniqueName)?.exists() != true) { "Target input-method entry already exists" }
            layouts.forEach { file ->
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                require(oldUniqueName !in root || newUniqueName !in root) { "Target layout already exists" }
            }
        }
        val files = (layouts + listOf(
            UserConfigFiles.customShuangpinSchemeJson(), UserConfigFiles.customShuangpinSpDat(),
            UserConfigFiles.pinyinImConfig(oldUniqueName), UserConfigFiles.pinyinImConfig(newUniqueName)
        ) + listOfNotNull(entryConfFile(oldUniqueName), entryConfFile(newUniqueName))).distinct()
        val originals = files.associateWith { file ->
            require(!file.isDirectory) { "Configuration path is a directory" }
            if (file.isFile) file.readBytes() else null
        }
        try {
            if (oldUniqueName != newUniqueName) {
                val oldEntry = entryConfFile(oldUniqueName)
                val newEntry = entryConfFile(newUniqueName)
                if (oldEntry?.isFile == true && newEntry != null) writeBytes(newEntry, oldEntry.readBytes())
            }
            save(previous.copy(name = name, id = id)).getOrThrow()
            if (oldUniqueName != newUniqueName) {
                renameImConfig(oldUniqueName, newUniqueName)
                renameLayoutKeys(oldUniqueName, newUniqueName)
                entryConfFile(oldUniqueName)?.takeIf(File::isFile)?.let {
                    check(it.delete()) { "Could not remove renamed input-method entry" }
                }
            }
        } catch (failure: Exception) {
            originals.forEach { (file, bytes) ->
                runCatching {
                    if (bytes != null) writeBytes(file, bytes)
                    else check(!file.exists() || file.delete()) { "Could not restore configuration" }
                }.exceptionOrNull()?.let(failure::addSuppressed)
            }
            cachedScheme = null
            throw failure
        }
    }

    private fun writeScheme(
        jsonFile: File,
        spDatFile: File,
        scheme: CustomShuangpinScheme
    ) {
        jsonFile.parentFile?.mkdirs()
        spDatFile.parentFile?.mkdirs()

        writeJson(jsonFile, scheme)
        writeBytes(spDatFile, CustomShuangpinSchemeCodec.toSpDat(scheme).toByteArray(Charsets.UTF_8))
        syncEntryConf(scheme)
    }

    private fun writeJson(jsonFile: File, scheme: CustomShuangpinScheme) {
        jsonFile.parentFile?.mkdirs()
        writeBytes(jsonFile, json.encodeToString(scheme).toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.writeBytes(bytes)
            temp.delete()
        }
    }

    /** 用户级输入法条目配置：`<data>/inputmethod/<uniqueName>.conf`。 */
    private fun entryConfFile(uniqueName: String): File? =
        UserConfigFiles.inputMethodConfDir()?.let { File(it, "$uniqueName.conf") }

    /**
     * 同步用户级输入法条目配置。
     *
     * 默认后缀 `custom` 直接使用安装包内置的
     * `usr/share/fcitx5/inputmethod/shuangpin-custom.conf`，因此只在自定义后缀时
     * 写入用户级配置，保留其他自定义双拼条目配置及用户对条目内容的修改。
     */
    private fun syncEntryConf(scheme: CustomShuangpinScheme) {
        val uniqueName = uniqueNameFor(scheme.id)
        if (scheme.id != ShuangpinLayoutKeys.DEFAULT_CUSTOM_SUFFIX) {
            entryConfFile(uniqueName)?.let { confFile ->
                confFile.parentFile?.mkdirs()
                if (!confFile.exists()) writeBytes(confFile, ENTRY_CONF.toByteArray(Charsets.UTF_8))
            }
        }
        // Other shuangpin-* entries may be user-maintained input methods.
        // Only an explicit rename may remove the source entry; never sweep this directory.
    }

    private fun renameImConfig(oldUniqueName: String, newUniqueName: String) {
        val oldFile = UserConfigFiles.pinyinImConfig(oldUniqueName)
        if (!oldFile.isFile) return
        val newFile = UserConfigFiles.pinyinImConfig(newUniqueName)
        newFile.parentFile?.mkdirs()
        if (!oldFile.renameTo(newFile)) {
            newFile.writeBytes(oldFile.readBytes())
            oldFile.delete()
        }
    }

    private fun renameLayoutKeys(oldUniqueName: String, newUniqueName: String) {
        UserConfigFiles.listTextKeyboardLayoutProfiles().forEach { profile ->
            val file = UserConfigFiles.textKeyboardLayoutJson(profile) ?: return@forEach
            renameLayoutKey(file, oldUniqueName, newUniqueName)
        }
    }

    /** 把布局文件中的顶层键 `oldKey` 重命名为 `newKey`，其余内容原样保留。 */
    private fun renameLayoutKey(file: File, oldKey: String, newKey: String) {
        if (!file.isFile) return
        val root = runCatching {
            Json.parseToJsonElement(file.readText()).jsonObject
        }.getOrNull() ?: return
        if (oldKey !in root || newKey in root) return
        val renamed = JsonObject(
            root.map { (key, value) -> if (key == oldKey) newKey to value else key to value }.toMap()
        )
        writeBytes(file, json.encodeToString(renamed).toByteArray(Charsets.UTF_8))
    }

    private fun sanitize(scheme: CustomShuangpinScheme): CustomShuangpinScheme {
        val name = scheme.name.trim().takeIf(CustomShuangpinSchemeCodec::isValidName)
            ?: CustomShuangpinSchemeCodec.DEFAULT_NAME
        val id = scheme.id.trim().lowercase()
            .takeIf(ShuangpinLayoutKeys::isValidCustomSuffix)
            ?: ShuangpinLayoutKeys.DEFAULT_CUSTOM_SUFFIX
        val zeroInitial = scheme.zeroInitial.trim().lowercase()
            .takeIf(CustomShuangpinSchemeCodec::isValidZeroInitial) ?: "o"
        val syllables = scheme.syllables
            .mapKeys { it.key.trim().lowercase() }
            .mapValues { (_, keys) ->
                keys.map { it.trim().lowercase() }
                    .filter(CustomShuangpinSchemeCodec::isValidSyllableKeys)
                    .distinct()
            }
            .filterKeys(CustomShuangpinSchemeCodec::isValidSyllable)
            .filterValues { it.isNotEmpty() }
        return scheme.copy(name = name, zeroInitial = zeroInitial, syllables = syllables, id = id)
    }

    private fun readJson(file: File): CustomShuangpinScheme? = runCatching {
        json.decodeFromString<CustomShuangpinScheme>(file.readText())
    }.getOrNull()
}
