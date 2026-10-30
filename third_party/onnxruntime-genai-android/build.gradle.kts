import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.gradle.api.Project

plugins {
    base
    `maven-publish`
}

group = "org.fxboomk.fcitx5.android.thirdparty"
version = "0.17.0"

fun Project.githubPackagesRepositoryUrl(): String {
    val explicit = providers.gradleProperty("githubPackagesRepository").orNull?.trim().orEmpty()
    if (explicit.isNotEmpty()) return explicit
    val localRepository = rootProject.rootDir.resolve(".local-thirdparty-artifacts")
    if (localRepository.isDirectory) return localRepository.toURI().toString()
    val repositoryEnv = System.getenv("GITHUB_REPOSITORY")?.trim().orEmpty()
    if (repositoryEnv.isNotEmpty()) return "https://maven.pkg.github.com/$repositoryEnv"
    val owner = providers.gradleProperty("githubPackagesOwner").orNull?.trim().orEmpty().ifBlank { "boomker" }
    val repo = providers.gradleProperty("githubPackagesRepo").orNull?.trim().orEmpty().ifBlank { rootProject.name }
    return "https://maven.pkg.github.com/$owner/$repo"
}

fun Project.githubPackagesUsername(): String =
    providers.gradleProperty("gpr.user").orNull?.trim().orEmpty()
        .ifBlank { System.getenv("GITHUB_ACTOR").orEmpty().trim() }
        .ifBlank { System.getenv("GH_USERNAME").orEmpty().trim() }
        .ifBlank { System.getenv("GITHUB_USERNAME").orEmpty().trim() }

fun Project.githubPackagesToken(): String =
    providers.gradleProperty("gpr.key").orNull?.trim().orEmpty()
        .ifBlank { System.getenv("GITHUB_TOKEN").orEmpty().trim() }
        .ifBlank { System.getenv("GH_TOKEN").orEmpty().trim() }
        .ifBlank { System.getenv("PACKAGE_TOKEN").orEmpty().trim() }

fun isFileBackedRepository(url: String): Boolean = url.startsWith("file:", ignoreCase = true)

val artifactName = "onnxruntime-genai-android"
val artifactFileName = "$artifactName-$version.aar"
val artifactSha256 = "f8b28a9ce448d97e0d5ab9614eae018a4d994d739de84e9fc5f9ff3b6673dd50"
val artifactUrl =
    "https://github.com/microsoft/onnxruntime-genai/releases/download/v$version/$artifactFileName"
val rawDownloadedArtifact = layout.buildDirectory.file("downloads/raw/$artifactFileName")
val downloadedArtifact = layout.buildDirectory.file("downloads/$artifactFileName")

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun requireExpectedSha256(file: File) {
    val actual = sha256(file)
    check(actual == artifactSha256) {
        "Unexpected SHA-256 for $artifactFileName: expected $artifactSha256, got $actual"
    }
}

fun zipAlignExecutable(): File {
    val sdk = providers.environmentVariable("ANDROID_HOME").orNull?.trim().orEmpty()
        .ifBlank { providers.environmentVariable("ANDROID_SDK_ROOT").orNull?.trim().orEmpty() }
        .ifBlank {
            rootProject.rootDir.resolve("local.properties").takeIf { it.isFile }
                ?.readLines()
                ?.firstOrNull { it.startsWith("sdk.dir=") }
                ?.substringAfter('=')
                ?.replace("\\:", ":")
                ?.trim()
                .orEmpty()
        }
    check(sdk.isNotEmpty()) { "Android SDK path is required to align the ONNX Runtime AAR" }
    val buildToolsDir = File(sdk, "build-tools")
        .listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { directory ->
            runCatching { directory to org.gradle.util.GradleVersion.version(directory.name) }.getOrNull()
        }
        ?.maxByOrNull { it.second }?.first
        ?: error("No Android build-tools installation found under $sdk")
    val executable = File(buildToolsDir, if (System.getProperty("os.name").startsWith("Windows")) "zipalign.exe" else "zipalign")
    check(executable.isFile) { "zipalign not found at ${executable.absolutePath}" }
    return executable
}

val downloadOrtGenAiAar = tasks.register("downloadOrtGenAiAar") {
    outputs.file(downloadedArtifact)
    notCompatibleWithConfigurationCache(
        "This publishing-only task performs an HTTP download from a Gradle script action."
    )
    doLast {
        val target = downloadedArtifact.get().asFile
        val raw = rawDownloadedArtifact.get().asFile
        raw.parentFile.mkdirs()
        if (raw.exists() && raw.length() > 0L) {
            runCatching { requireExpectedSha256(raw) }.onFailure { raw.delete() }
        }
        if (!raw.exists() || raw.length() == 0L) {
            val connection = (URL(artifactUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            try {
                check(connection.responseCode in 200..299) {
                    "Download failed (${connection.responseCode}): $artifactUrl"
                }
                connection.inputStream.use { input ->
                    raw.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (failure: Throwable) {
                raw.delete()
                throw failure
            } finally {
                connection.disconnect()
            }
        }
        requireExpectedSha256(raw)
        target.parentFile.mkdirs()
        val aligned = File.createTempFile("$artifactName-", ".aar", target.parentFile)
        try {
            val result = ProcessBuilder(
                zipAlignExecutable().absolutePath,
                "-f", "-P", "16", "4", raw.absolutePath, aligned.absolutePath
            ).inheritIO().start().waitFor()
            check(result == 0) { "zipalign failed for $artifactFileName (exit code $result)" }
            java.nio.file.Files.move(
                aligned.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            aligned.delete()
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            val repositoryUrl = githubPackagesRepositoryUrl()
            url = uri(repositoryUrl)
            if (!isFileBackedRepository(repositoryUrl)) {
                credentials {
                    username = githubPackagesUsername()
                    password = githubPackagesToken()
                }
            }
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = artifactName
            version = project.version.toString()
            artifact(downloadedArtifact) {
                extension = "aar"
                builtBy(downloadOrtGenAiAar)
            }
            pom {
                name.set("ONNX Runtime GenAI Android")
                description.set("Prebuilt ONNX Runtime GenAI Android artifact mirrored for fcitx5-android builds.")
                url.set("https://github.com/microsoft/onnxruntime-genai")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/microsoft/onnxruntime-genai/blob/main/LICENSE")
                    }
                }
            }
        }
    }
}
