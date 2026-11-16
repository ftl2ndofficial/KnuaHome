plugins {
    java
}

group = "com.ftl2nd"
version = "1.0.0"
description = "KnuaHome - Multi-version Paper & Folia Home Plugin (1.21 - 26.3) with NMS Core"

java {
    toolchain {
        // Compile with Java 25 while targeting Java 21 bytecode (--release 21)
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.viaversion.com")
}

val gradleUserHome = gradle.gradleUserHomeDir
val nmsSpecs = listOf(
    "nmsV1_21" to "1.21",
    "nmsV1_21_6" to "1.21.6",
    "nmsV1_21_9" to "1.21.9",
    "nmsV1_21_11" to "1.21.11",
    "nmsV26_2" to "26.2",
    "nmsV26_3" to "26.3"
)

fun resolvePaperCacheDir(ver: String): File {
    val legacy = File(gradleUserHome, "caches/cdialog-paper-$ver")
    if (File(legacy, "versions/$ver/paper-$ver.jar").exists()) {
        return legacy
    }
    return File(gradleUserHome, "caches/knuahome-paper-$ver")
}

fun findPaperArchive(ver: String): File? {
    val customProp = providers.gradleProperty("paperArchiveDir").orNull
    val customEnv = System.getenv("PAPER_ARCHIVE_DIR")
    val candidates = listOfNotNull(
        customProp?.let { file(it) },
        customEnv?.let { file(it) },
        file("paper-jars"),
        File("D:/PaperMC Archive")
    ).filter { it.isDirectory }

    for (dir in candidates) {
        val exact = File(dir, "paper-$ver.jar")
        if (exact.isFile) {
            return exact
        }
        val matched = dir.listFiles { f ->
            f.isFile && f.name.startsWith("paper-$ver-") && f.name.endsWith(".jar")
        }?.firstOrNull()
        if (matched != null) {
            return matched
        }
    }
    return null
}

fun ensurePaperExtracted(ver: String) {
    val dir = resolvePaperCacheDir(ver)
    val serverJar = File(dir, "versions/$ver/paper-$ver.jar")
    if (serverJar.exists()) {
        return
    }
    val archiveFile = findPaperArchive(ver) ?: error(
        "Paper server jar for Minecraft $ver not found! " +
            "Place paper-$ver-*.jar inside '${file("paper-jars").absolutePath}' " +
            "or pass -PpaperArchiveDir=/path/to/paper-jars (download from https://papermc.io/downloads/paper)."
    )
    dir.mkdirs()
    val java25Launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }.get().executablePath.asFile.absolutePath

    val process = ProcessBuilder(
        java25Launcher,
        "-Dpaperclip.patchonly=true",
        "-jar",
        archiveFile.absolutePath
    )
        .directory(dir)
        .inheritIO()
        .start()
    val exitCode = process.waitFor()
    require(exitCode == 0 && serverJar.exists()) {
        "Failed to extract Paperclip $ver from ${archiveFile.name} (exitCode=$exitCode)"
    }
}

nmsSpecs.forEach { (_, ver) -> ensurePaperExtracted(ver) }

val defaultPaperDir = resolvePaperCacheDir("1.21.11")

dependencies {
    compileOnly(fileTree(defaultPaperDir) { include("**/*.jar") })
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.viaversion:viaversion-api:5.2.1")

    testImplementation(fileTree(defaultPaperDir) { include("**/*.jar") })
    testImplementation("net.luckperms:api:5.4")
    testImplementation("me.clip:placeholderapi:2.11.6")
    testImplementation("com.viaversion:viaversion-api:5.2.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val nmsSourceSets = nmsSpecs.map { (name, ver) ->
    val ss = sourceSets.create(name) {
        java.srcDir("src/$name/java")
        compileClasspath = sourceSets.main.get().output + fileTree(resolvePaperCacheDir(ver)) { include("**/*.jar") }
    }
    tasks.named(ss.compileJavaTaskName) {
        dependsOn(tasks.compileJava)
    }
    ss
}

tasks.named<Jar>("jar") {
    from("LICENSE")
    nmsSourceSets.forEach { ss ->
        from(ss.output)
    }
}

dependencies {
    nmsSourceSets.forEach { ss ->
        testImplementation(ss.output)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Emit Java 21 (class version 65.0) bytecode for 1.21 - 26.3 compatibility
    options.release.set(21)
}

tasks.withType<ProcessResources>().configureEach {
    filteringCharset = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    val projPath = projectDir.absolutePath
    val hasNonAscii = projPath.any { it.code > 127 }
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    if (isWindows && hasNonAscii) {
        val junctionDir = File(gradleUserHome, "caches/knuahome-ws-junction")
        if (!junctionDir.exists()) {
            junctionDir.parentFile.mkdirs()
            ProcessBuilder("cmd", "/c", "mklink", "/J", junctionDir.absolutePath, projPath)
                .start()
                .waitFor()
        }
        workingDir = junctionDir
        val junctionPath = junctionDir.absolutePath
        classpath = files(classpath.elements.map { elements ->
            elements.map { loc ->
                val f = loc.asFile
                if (f.absolutePath.startsWith(projPath, ignoreCase = true)) {
                    File(junctionPath + f.absolutePath.substring(projPath.length))
                } else {
                    f
                }
            }
        })
        testClassesDirs = files(testClassesDirs.elements.map { elements ->
            elements.map { loc ->
                val f = loc.asFile
                if (f.absolutePath.startsWith(projPath, ignoreCase = true)) {
                    File(junctionPath + f.absolutePath.substring(projPath.length))
                } else {
                    f
                }
            }
        })
    }
}
