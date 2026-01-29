import java.net.URI

plugins {
    id("java")
    id("maven-publish")
}

group = "me.crafter.mc"
version = "2.15"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") //papermc
    maven("https://jitpack.io") //vault
    maven("https://maven.enginehub.org/repo/") //world guard
    maven("https://repo.dmulloy2.net/repository/public/" ) //protocol lib
    maven("https://maven.playpro.com") //core protect
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT") {
        isTransitive = false
    }
    compileOnly("com.comphenix.protocol:ProtocolLib:5.4.0-SNAPSHOT") {
        isTransitive = false
    }
    compileOnly("net.coreprotect:coreprotect:22.4") {
        isTransitive = false
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = rootProject.name.lowercase()
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "NyaaCatCILocal"
            //local maven repository
            url = uri("file://${System.getenv("MAVEN_DIR")}")
        }
    }
}

tasks {
    compileJava {
        options.compilerArgs.add("-Xlint:deprecation")
        options.encoding = "UTF-8"
    }

    processResources {
        filesMatching("**/plugin.yml") {
            expand("version" to project.version)
        }
    }


    register("publishToNyaaCatCILocal") {
        dependsOn("publishMavenJavaPublicationToNyaaCatCILocalRepository")
    }
}