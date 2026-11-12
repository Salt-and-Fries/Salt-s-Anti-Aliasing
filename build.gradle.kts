plugins {
    base
    id("dev.prism")
}

group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("mod_version").get()

prism {
    metadata {
        modId = "salts_anti_aliasing"
        name = "Salt's Anti Aliasing"
        description = "Client-side anti-aliasing, sharpening, and upscale pipeline foundation for Minecraft."
        license = "All-Rights-Reserved"
        author("Salt and Fries")
        archivesName = "${providers.gradleProperty("archives_base_name").get()}-mc{mc}"
        includeFile("LICENSE.txt", "{name}_salts-anti-aliasing")
    }

    maven("TerraformersMC", "https://maven.terraformersmc.com/releases/")
    maven("CaffeineMC", "https://maven.caffeinemc.net/releases")
    modrinthMaven()

    // Config, render vocabulary, and pipeline planning compile without Minecraft or a loader.
    sharedCommon {
        javaVersion = 17
    }

    for ((minecraftVersion, mappingsVersion, apiVersion, modMenuVersion, vulkanModId) in listOf(
            listOf("1.18.2", "1.18.2-20220404.173914", "0.76.0+1.18.2", "3.2.5", "JOtvsq3o"),
            listOf("1.19.2", "1.19.2-20220805.130853", "0.77.0+1.19.2", "4.1.2", "W6KPVWT3"),
            listOf("1.19.3", "1.19.3-20221207.122022", "0.75.1+1.19.3", "5.1.0", "mslg9G1S"),
            listOf("1.19.4", "1.19.4-20230314.122934", "0.87.2+1.19.4", "6.3.1", "rGsZuGn8"),
            listOf("1.20", "1.20-20230608.053357", "0.83.0+1.20", "7.0.1", "ZhvilXaM"),
            listOf("1.20.1", "1.20.1-20230612.114412", "0.92.12+1.20.1", "7.2.2", "ZhvilXaM"),
            listOf("1.20.2", "1.20.2-20241215.201144", "0.91.6+1.20.2", "8.0.1", "nC4iQVVW"),
            listOf("1.20.3", "1.20.3-20241215.201103", "0.91.1+1.20.3", "9.0.0", "hZjQjfpD"),
            listOf("1.20.4", "1.20.4-20240627.114801", "0.97.3+1.20.4", "9.2.0", "hZjQjfpD"),
            listOf("1.20.5", "1.20.5-20241215.200814", "0.97.8+1.20.5", "10.0.0", "bPsylH8I"),
            listOf("1.20.6", "1.20.6-20240627.102356", "0.100.8+1.20.6", "10.0.0", "bPsylH8I"),
            listOf("1.21", "1.21-20240613.152323", "0.102.0+1.21", "11.0.5", "2eqroDKD"),
            listOf("1.21.1", "1.21.1-20240808.144430", "0.116.18+1.21.1", "11.0.5", "2eqroDKD"),
            listOf("1.21.2", "1.21.2-20241022.151510", "0.106.1+1.21.2", "12.0.1", "mr9uxFGT"),
            listOf("1.21.3", "1.21.3-20241023.131943", "0.114.1+1.21.3", "12.0.1", "mr9uxFGT"),
            listOf("1.21.4", "1.21.4-20241203.161809", "0.119.4+1.21.4", "13.0.4", "RAtVo03C"),
            listOf("1.21.5", "1.21.5-20250325.162830", "0.128.2+1.21.5", "14.0.2", "7pYvUnJk"))) {
      version(minecraftVersion) {
        javaVersion = if (minecraftVersion in listOf("1.18.2", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4")) 17 else 21
        neoFormVersion = mappingsVersion
        fabric {
            loaderVersion = providers.gradleProperty("loader_version").get()
            fabricApi(apiVersion)
            dependencies { modCompileOnly("com.terraformersmc:modmenu:$modMenuVersion") }
            rawProject {
                extensions.extraProperties["minecraft_version"] = minecraftVersion
                extensions.extraProperties["vulkanmod_adapter"] = when (minecraftVersion) {
                    "1.20.1" -> "mc1_20"
                    "1.20.4" -> "mc1_20_3"
                    "1.20.6" -> "mc1_20_5"
                    "1.21.1" -> "mc1_21"
                    "1.21.3" -> "mc1_21_2"
                    else -> "mc${minecraftVersion.replace('.', '_')}"
                }
                extensions.extraProperties["vulkanmod_legacy_validation"] = minecraftVersion in listOf("1.18.2", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3")
                extensions.extraProperties["vulkanmod_version_id"] = vulkanModId
                extensions.extraProperties["vulkanmod_lwjgl_version"] = if (vulkanModId in listOf("rGsZuGn8", "JOtvsq3o", "W6KPVWT3", "mslg9G1S")) "3.3.1" else if (vulkanModId in listOf("hZjQjfpD", "ZhvilXaM", "nC4iQVVW")) "3.3.2" else "3.3.3"
                apply(mapOf("from" to rootProject.file("gradle/fabric-vulkanmod.gradle")))
            }
        }
      }
    }

    for ((minecraftVersion, mappingsVersion, apiVersion) in listOf(
            Triple("1.21.9", "1.21.9-20250930.151910", "0.134.1+1.21.9"),
            Triple("1.21.10", "1.21.10-20251010.172816", "0.138.4+1.21.10"),
            Triple("1.21.11", "1.21.11-20251209.172050", "0.141.6+1.21.11"))) {
      version(minecraftVersion) {
        javaVersion = 21
        neoFormVersion = mappingsVersion
        fabric {
            loaderVersion = providers.gradleProperty("loader_version").get()
            fabricApi(apiVersion)
            dependencies { modCompileOnly("com.terraformersmc:modmenu:${if (minecraftVersion == "1.21.11") "17.0.1" else "16.0.1"}") }
            rawProject {
                extensions.extraProperties["minecraft_version"] = minecraftVersion
                extensions.extraProperties["vulkanmod_adapter"] = if (minecraftVersion == "1.21.11") "mc1_21_11" else "mc1_21_9"
                extensions.extraProperties["vulkanmod_version_id"] = if (minecraftVersion == "1.21.11") "hWKRTXbQ" else "jkDMvvWU"
                extensions.extraProperties["vulkanmod_lwjgl_version"] = "3.3.3"
                apply(mapOf("from" to rootProject.file("gradle/fabric-vulkanmod.gradle")))
            }
        }
      }
    }

    for ((minecraftVersion, fabricApiVersion) in mapOf(
            "26.1" to "0.155.3+26.1.2",
            "26.1.1" to "0.155.3+26.1.2",
            "26.1.2" to "0.155.3+26.1.2")) {
      version(minecraftVersion) {
        javaVersion = 25
        neoFormVersion = "$minecraftVersion-1"
        fabric {
            loaderVersion = providers.gradleProperty("loader_version").get()
            fabricApi(fabricApiVersion)
            dependencies {
                compileOnly("com.terraformersmc:modmenu:18.0.2")
            }
            rawProject {
                extensions.extraProperties["minecraft_version"] = minecraftVersion
                extensions.extraProperties["vulkanmod_adapter"] = "mc26_1"
                extensions.extraProperties["vulkanmod_version_id"] = "CEmnv55N"
                extensions.extraProperties["vulkanmod_lwjgl_version"] = "3.4.1"
                apply(mapOf("from" to rootProject.file("gradle/fabric-vulkanmod.gradle")))
            }
        }
      }
    }

    version("26.3") {
        javaVersion = 25
        neoFormVersion = "26.3-1"

        fabric {
            loaderVersion = providers.gradleProperty("loader_version").get()
            fabricApi(providers.gradleProperty("fabric_version").get())
            dependencies {
                compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")
                compileOnly("net.caffeinemc:sodium-fabric-api:${providers.gradleProperty("sodium_api_version").get()}")
            }
            rawLoom {
                splitEnvironmentSourceSets()
            }
            rawProject {
                // Keep the native packaging, tests, and development profiles with this target.
                apply(mapOf("from" to rootProject.file("gradle/fabric.gradle")))
            }
        }
    }

    version("26.2") {
        javaVersion = 25
        neoFormVersion = "26.2-2"

        fabric {
            loaderVersion = providers.gradleProperty("loader_version").get()
            fabricApi(providers.gradleProperty("fabric_version_26_2").get())
            dependencies {
                compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version_26_2").get()}")
                compileOnly("net.caffeinemc:sodium-fabric-api:${providers.gradleProperty("sodium_api_version_26_2").get()}")
            }
            rawLoom {
                splitEnvironmentSourceSets()
            }
            rawProject {
                extensions.extraProperties["minecraft_version"] = "26.2"
                extensions.extraProperties["sodium_api_version"] = providers.gradleProperty("sodium_api_version_26_2").get()
                for (property in listOf("sodium_stable_runtime", "sodium_beta_runtime", "sodium_stable_sha256", "sodium_beta_sha256")) {
                    extensions.extraProperties[property] = providers.gradleProperty("${property}_26_2").get()
                }
                apply(mapOf("from" to rootProject.file("gradle/fabric.gradle")))
            }
        }
    }
}

val vulkanModTargets = (groovy.json.JsonSlurper().parse(
    file("versions/vulkanmod/compatibility/official-targets.json")) as List<*>)
    .map { (it as Map<*, *>)["minecraft"].toString() }
val fabricTargets = vulkanModTargets + listOf("26.2", "26.3")

// Collect only the main distributable task outputs. Sync removes stale release jars.
val collectJars = tasks.register<Sync>("collectJars") {
    for (minecraftVersion in fabricTargets) {
        val targetPath = ":$minecraftVersion:fabric"
        val jarTask = if (minecraftVersion.startsWith("26.")) "jar" else "remapJar"
        dependsOn("$targetPath:verifyPublicJar")
        from(provider {
            project(targetPath).tasks.named<org.gradle.api.tasks.bundling.AbstractArchiveTask>(jarTask)
                .get().archiveFile.get().asFile
        })
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    into(layout.buildDirectory.dir("libs"))
    doFirst {
        val expected = rootDir.canonicalFile.toPath().resolve("build/libs").normalize()
        check(destinationDir.canonicalFile.toPath() == expected) {
            "Release collection must stay inside the workspace build/libs directory"
        }
    }
}

tasks.named("assemble") { dependsOn(collectJars) }
tasks.named("check") {
    dependsOn(":common:check", ":vulkanmod:check", ":26.2:common:check", ":26.2:fabric:check", ":26.3:common:check", ":26.3:fabric:check")
    dependsOn(vulkanModTargets.map { ":$it:fabric:verifyPublicJar" })
}

// Short launch commands continue to select the primary 26.3 development target.
tasks.register("runClient") { dependsOn(":26.3:fabric:runClient") }
tasks.register("runDatagen") { dependsOn(":26.3:fabric:runDatagen") }
