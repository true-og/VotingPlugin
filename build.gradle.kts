/* This is free and unencumbered software released into the public domain */

import org.gradle.kotlin.dsl.provideDelegate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.gradleup.shadow") version "8.3.6" // Import Shadow plugin.
    eclipse // Import Eclipse plugin.
    kotlin("jvm") version "2.1.21" // Import Kotlin JVM plugin.
    id("io.freefair.lombok") version "8.6"
}

extra["kotlinAttribute"] = Attribute.of("kotlin-tag", Boolean::class.javaObjectType)

val kotlinAttribute: Attribute<Boolean> by rootProject.extra

/* --------------------------- JDK / Kotlin ---------------------------- */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain { // Select Java toolchain.
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

kotlin { jvmToolchain(17) }

/* ----------------------------- Metadata ------------------------------ */
group = "com.bencodez" // Declare bundle identifier.

version = "6.19.1-SNAPSHOT" // Declare plugin version (will be in .jar).

val apiVersion = "1.19" // Declare minecraft server target version.

val buildProfileId = (findProperty("build.profile.id") ?: findProperty("profile") ?: System.getenv("BUILD_PROFILE_ID") ?: "default").toString()
val buildNumber = (findProperty("build.number") ?: System.getenv("BUILD_NUMBER") ?: "NOTSET").toString()
val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to version.toString(),
        "apiVersion" to apiVersion,
        "timestamp" to timestamp,
        "build" to mapOf(
            "profile" to mapOf("id" to buildProfileId),
            "number" to buildNumber
        ),
        "project" to mapOf(
            "version" to version.toString()
        )
    )
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching(listOf("plugin.yml", "bungee.yml", "votingpluginversion.yml")) { expand(props) }
    from("LICENSE") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://nexus.velocitypowered.com/repository/maven-public/") }
    maven { url = uri("https://nexus.bencodez.com/repository/maven-public/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://nexus.scarsz.me/content/groups/public/") }
    maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
    System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let { // TrueOG Bootstrap mavenLocal().
        val dir = file(it)
        if (dir.isDirectory) {
            println("Using SELF_MAVEN_LOCAL_REPO at: $it")
            maven { url = uri("file://${dir.absolutePath}") }
        } else {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
    } ?: logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
}

/* ---------------------- Java project deps ---------------------------- */
dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare Purpur API version to be packaged.
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:2.2.3") // Import MiniPlaceholders API.
    compileOnlyApi(project(":libs:Utilities-OG")) // Import TrueOG Network Utilities-OG Java API (from source).
    compileOnly("net.md-5:bungeecord-api:1.21-R0.3")
    compileOnly("com.velocitypowered:velocity-api:3.1.2-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("be.maximvdw:mvdwplaceholderapi:3.1.1")
    compileOnly("com.vexsoftware:nuvotifier-universal:2.7.2")
    compileOnly("com.discordsrv:discordsrv:1.30.0")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("org.bstats:bstats-velocity:3.1.0")
    implementation("com.bencodez:advancedcore:3.7.19-SNAPSHOT")
}

apply(from = "eclipse.gradle.kts") // Import eclipse classpath support script.

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    exclude("io.github.miniplaceholders.*") // Exclude the MiniPlaceholders package from being shadowed.
    archiveClassifier.set("") // Use empty string instead of null.
    archiveVersion.set("")
    minimize()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    dependencies {
        exclude { it.moduleGroup.startsWith("com.google") }
    }
    relocate("com.tcoded.folialib", "${project.group}.votingplugin.simpleapi.folialib")
    relocate("com.bencodez.simpleapi", "${project.group}.votingplugin.simpleapi")
    relocate("com.bencodez.advancedcore", "${project.group}.votingplugin.advancedcore")
    relocate("net.pl3x.bukkit.chatapi", "${project.group}.votingplugin")
    relocate("me.mrten.mysqlapi", "${project.group}.votingplugin.mysqlapi")
    relocate("com.zaxxer.hikari", "${project.group}.votingplugin.hikari")
    relocate("org.bstats", "${project.group}.votingplugin.bstats")
    relocate("xyz.upperlevel.spigot", "${project.group}.votingplugin.advancedcore.xyz.upperlevel.spigot")
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

tasks.jar { 
    archiveClassifier.set("part") // Applies to root jarfile only.
    archiveVersion.set("")
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

tasks.build { dependsOn(tasks.shadowJar) } // Build depends on shadow.

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters") // Enable reflection for java code.
    options.isFork = true // Run javac in its own process.
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ------------------------------ Eclipse SHIM ------------------------- */

// This can't be put in eclipse.gradle.kts because Gradle is weird.
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
    eclipse.project.name = "${project.name}-${rootProject.name}"
    tasks.withType<Jar>().configureEach { archiveBaseName.set("${project.name}-${rootProject.name}") }
}

/* ------------------------------ Extra Tasks -------------------------- */
tasks.register<Copy>("installToTestServer") {
    dependsOn(tasks.shadowJar)
    from(layout.buildDirectory.file("libs/${rootProject.name}.jar"))
    into(System.getProperty("user.home") + "/Documents/Test_Server/plugins")
}

