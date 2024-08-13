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
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
    compileOnly("net.coreprotect:coreprotect:22.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "lockettepro"
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "github-package"
            url = URI(System.getenv("GITHUB_MAVEN_URL") ?: "https://github.com")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}