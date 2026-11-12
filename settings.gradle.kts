pluginManagement {
    repositories {
        maven { url = uri("https://maven.leclowndu93150.dev/releases") }
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    id("dev.prism.settings") version "0.6.8"
}

rootProject.name = "Salts Anti Aliasing"

include(":vulkanmod")
project(":vulkanmod").projectDir = file("versions/vulkanmod")

prism {
    sharedCommon("versions/common")

    for (minecraftVersion in listOf("1.18.2", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5")) {
        version(minecraftVersion) { common(); fabric() }
    }

    for (minecraftVersion in listOf("1.21.9", "1.21.10", "1.21.11")) {
        version(minecraftVersion) { common(); fabric() }
    }

    for (minecraftVersion in listOf("26.1", "26.1.1", "26.1.2")) {
        version(minecraftVersion) { common(); fabric() }
    }

    version("26.2") {
        common()
        fabric()
    }

    version("26.3") {
        common()
        fabric()
    }
}
