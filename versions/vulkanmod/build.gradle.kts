plugins { `java-library` }

group = rootProject.group
version = rootProject.version

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

repositories { mavenCentral() }
val testLwjgl = providers.gradleProperty("vulkanmodTestLwjgl").getOrElse("3.3.1")

dependencies {
    compileOnly(project(":common"))
    compileOnly("org.joml:joml:1.10.5")
    compileOnly("org.lwjgl:lwjgl:3.3.1")
    compileOnly("org.lwjgl:lwjgl-vulkan:3.3.1")
    compileOnly("org.lwjgl:lwjgl-vma:3.3.1")
    compileOnly("org.lwjgl:lwjgl-shaderc:3.3.1")
    testImplementation(project(":common"))
    testImplementation("org.joml:joml:1.10.5")
    testImplementation("org.lwjgl:lwjgl:$testLwjgl")
    testImplementation("org.lwjgl:lwjgl-vulkan:$testLwjgl")
    testImplementation("org.lwjgl:lwjgl-vma:$testLwjgl")
    testImplementation("org.lwjgl:lwjgl-shaderc:$testLwjgl")
    testRuntimeOnly("org.lwjgl:lwjgl:$testLwjgl:natives-windows")
    testRuntimeOnly("org.lwjgl:lwjgl-shaderc:$testLwjgl:natives-windows")
    testRuntimeOnly("org.lwjgl:lwjgl-vma:$testLwjgl:natives-windows")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

// Share algorithm sources with the validated modern ports; compile them through our own
// Vulkan pipeline implementation rather than any version's Minecraft post-chain parser.
sourceSets.main {
    resources.srcDir("../26.2/common/src/main/resources")
}
tasks.test {
    useJUnitPlatform()
    // New GPU drivers enumerate more extensions than LWJGL 3.3.1's 64 KiB default can hold.
    systemProperty("org.lwjgl.system.stackSize", "512")
    jvmArgs("-Xss4m", "--enable-native-access=ALL-UNNAMED")
    systemProperty("salts.gpuValidation", providers.gradleProperty("gpuValidation").getOrElse("false"))
}
