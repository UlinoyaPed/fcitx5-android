/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File
import java.util.Properties
import javax.inject.Inject

fun ExternalNativeBuildJsonTask.abiModel(): CxxAbiModel {
    val abi = ExternalNativeBuildJsonTask::class.java.declaredFields.find { it.name == "abi" }!!
    abi.isAccessible = true
    return abi.get(this) as CxxAbiModel
}

fun ExternalNativeBuildJsonTask.abiModelOrNull(): CxxAbiModel? {
    val abi = ExternalNativeBuildJsonTask::class.java.declaredFields.find { it.name == "abi" } ?: return null
    abi.isAccessible = true
    return abi.get(this) as? CxxAbiModel
}

fun Project.enableNativeBuildMetadataCapture() {
    val metadataDir = layout.buildDirectory.dir("intermediates/native-build-metadata")
    tasks.withType<ExternalNativeBuildJsonTask>().configureEach {
        doFirst {
            val abi = abiModelOrNull() ?: return@doFirst
            val cmakeExecutable = abi.variant.module.cmake?.cmakeExe?.absolutePath ?: return@doFirst
            val buildFolder = abi.cxxBuildFolder.absolutePath
            val outputDir = metadataDir.get().asFile.also(File::mkdirs)
            val metadataFile = outputDir.resolve("${name}.properties")
            val properties = Properties().apply {
                setProperty("cmakeExecutable", cmakeExecutable)
                setProperty("buildFolder", buildFolder)
            }
            metadataFile.outputStream().use { output ->
                properties.store(output, null)
            }
        }
    }
}

private const val NATIVE_INSTALL_CHAIN = "fcitxNativeInstallChain"

@Suppress("UNCHECKED_CAST")
private fun nativeInstallChain(root: Project): MutableMap<String, Task> {
    val extras = root.extensions.extraProperties
    if (!extras.has(NATIVE_INSTALL_CHAIN)) {
        extras[NATIVE_INSTALL_CHAIN] = mutableMapOf<String, Task>()
    }
    return extras[NATIVE_INSTALL_CHAIN] as MutableMap<String, Task>
}

/**
 * Serializes native install tasks that share one CMake build tree.
 *
 * Gradle may run independent tasks concurrently — the configuration cache enables this
 * even inside a single project — and two concurrent CMake/Ninja runs in one build tree
 * corrupt each other. That shows up during automatic regeneration as
 * `configure_file(): No such file or directory` and aborts the release build, so tasks
 * driving the same source project must never overlap.
 */
fun Task.serializeWithNativeInstallTasks(sourceProjectPath: String) {
    val chain = nativeInstallChain(project.rootProject)
    chain[sourceProjectPath]?.takeIf { it !== this }?.let { mustRunAfter(it) }
    chain[sourceProjectPath] = this
}

abstract class CMakeBuildInstallTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    @get:Optional
    abstract val nativeBuildMetadataDir: Property<File>

    @get:Input
    @get:Optional
    abstract val sourceProjectPath: Property<String>

    @get:Input
    @get:Optional
    abstract val buildTarget: Property<String>

    @get:Input
    @get:Optional
    abstract val installComponent: Property<String>

    @get:Input
    abstract val destDir: Property<File>

    @get:Input
    @get:Optional
    abstract val textReplacements: MapProperty<String, Map<String, String>>

    private fun exec(action: Action<ExecSpec>) {
        execOperations.exec(action).rethrowFailure().assertNormalExitValue()
    }

    private fun resolveNativeBuildMetadata(): Pair<String, File> {
        val metadataDir = nativeBuildMetadataDir.orNull
            ?: error("nativeBuildMetadataDir was not configured for ${path}")
        val metadataFile = metadataDir.listFiles()
            ?.filter { it.isFile && it.extension == "properties" }
            ?.maxByOrNull(File::lastModified)
            ?: error("No native build metadata found for ${path} (source project: ${sourceProjectPath.orNull ?: "unknown"})")
        val properties = Properties().apply {
            metadataFile.inputStream().use(this::load)
        }
        val buildFolder = properties.getProperty("buildFolder")
            ?.let(::File)
            ?: error("buildFolder missing in ${metadataFile.absolutePath}")
        val cmakeExecutable = properties.getProperty("cmakeExecutable")
            ?.takeUnless { it.contains("{configuration-time-placeholder:") }
            ?.takeIf { File(it).isFile }
            ?: resolveCMakeExecutableFromBuildFolder(buildFolder)
        return cmakeExecutable to buildFolder
    }

    private fun resolveCMakeExecutableFromBuildFolder(buildFolder: File): String {
        val cacheFile = buildFolder.resolve("CMakeCache.txt")
        val cmakeExecutable = cacheFile.takeIf(File::isFile)
            ?.useLines { lines ->
                lines.firstNotNullOfOrNull { line ->
                    line.removePrefix("CMAKE_COMMAND:INTERNAL=")
                        .takeIf { it != line && File(it).isFile }
                }
            }
        return cmakeExecutable
            ?: error("Unable to resolve cmakeExecutable from ${cacheFile.absolutePath}")
    }

    @TaskAction
    fun execute() {
        val (cmakeExecutable, buildFolder) = resolveNativeBuildMetadata()
        val buildTarget = this.buildTarget.getOrElse("")
        val installComponent = this.installComponent.getOrElse("")
        val destDir = this.destDir.get()
        if (buildTarget.isNotEmpty()) {
            exec {
                commandLine(
                    cmakeExecutable,
                    "--build", buildFolder,
                    "--target", buildTarget
                )
            }
        }
        if (installComponent.isNotEmpty()) {
            exec {
                environment("DESTDIR", destDir.absolutePath)
                commandLine(
                    cmakeExecutable,
                    "--install", buildFolder,
                    "--component", installComponent
                )
            }
        }
        textReplacements.orNull.orEmpty().forEach { (relativePath, replacements) ->
            val file = destDir.resolve(relativePath)
            if (!file.exists()) return@forEach
            var content = file.readText()
            replacements.forEach { (oldValue, newValue) ->
                content = content.replace(oldValue, newValue)
            }
            file.writeText(content)
        }
    }
}

/**
 * Important: make sure that the task runs after than the native task
 * Since we can't declare the dependency relationship, a weaker running order constraint must be enforced
 */

fun Task.runAfterNativeBuild(project: Project) {
    project.tasks.withType<ExternalNativeBuildTask>().all externalNativeBuild@{
        this@runAfterNativeBuild.mustRunAfter(this@externalNativeBuild)
    }
}
