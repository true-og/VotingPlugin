import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    id("java")
    eclipse
    id("com.gradleup.shadow") version "8.3.8"
}

group = "com.bencodez"
version = "6.19.1-SNAPSHOT"
description = "VotingPlugin"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/groups/public") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://nexus.velocitypowered.com/repository/maven-public/") }
    maven { url = uri("https://nexus.bencodez.com/repository/maven-public/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://nexus.scarsz.me/content/groups/public/") }
    maven { url = uri("https://repo.purpurmc.org/snapshots") }
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    compileOnly("net.md-5:bungeecord-api:1.21-R0.3")
    compileOnly("com.vexsoftware:nuvotifier-universal:2.7.2")
    compileOnly("be.maximvdw:mvdwplaceholderapi:3.1.1")
    compileOnly("com.velocitypowered:velocity-api:3.1.2-SNAPSHOT")
    implementation("org.bstats:bstats-velocity:3.1.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.bencodez:advancedcore:3.7.19-SNAPSHOT")
    compileOnly("com.discordsrv:discordsrv:1.30.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "Cp1252"
}

val buildProfileId = (findProperty("buildProfile") ?: System.getenv("BUILD_PROFILE") ?: "default").toString()
val buildNumber = (findProperty("buildNumber") ?: System.getenv("BUILD_NUMBER") ?: "NOTSET").toString()
val timestamp = System.currentTimeMillis().toString()
val apiVersion = "1.19"
val resourceProps = mapOf(
    "name" to project.name,
    "version" to project.version.toString(),
    "apiVersion" to apiVersion,
    "timestamp" to timestamp,
    "build" to mapOf(
        "profile" to mapOf("id" to buildProfileId),
        "number" to buildNumber
    )
)

tasks.processResources {
    filteringCharset = "Cp1252"
    inputs.properties(resourceProps)
    filesMatching(listOf("plugin.yml", "bungee.yml", "votingpluginversion.yml")) {
        expand(resourceProps)
    }
}

tasks.jar { enabled = false }

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("VotingPlugin")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    dependencies { exclude(dependency("com.google.*:.*")) }
    relocate("com.tcoded.folialib", "${project.group}.votingplugin.simpleapi.folialib")
    relocate("com.bencodez.simpleapi", "${project.group}.votingplugin.simpleapi")
    relocate("com.bencodez.advancedcore", "${project.group}.votingplugin.advancedcore")
    relocate("net.pl3x.bukkit.chatapi", "${project.group}.votingplugin")
    relocate("me.mrten.mysqlapi", "${project.group}.votingplugin.mysqlapi")
    relocate("com.zaxxer.hikari", "${project.group}.votingplugin.simpleapi.hikari")
    relocate("org.bstats", "${project.group}.votingplugin.bstats")
    relocate("xyz.upperlevel.spigot", "${project.group}.votingplugin.advancedcore.xyz.upperlevel.spigot")
    manifest { attributes(mapOf("paperweight-mappings-namespace" to "mojang")) }
}

tasks.build { dependsOn(tasks.named("shadowJar")) }

tasks.register<Copy>("copyToTestServer") {
    dependsOn(tasks.named("shadowJar"))
    from(tasks.named("shadowJar"))
    into("${System.getProperty("user.home")}/Documents/Test_Server/plugins")
}

