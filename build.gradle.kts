import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.1.21-RC"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.paperweight.userdev") version "1.7.2"
    id("io.github.revxrsal.zapper") version "1.0.3"
}

group = "me.erneto"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")

    compileOnly("me.clip:placeholderapi:2.11.6")

    zap("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    zap("com.h2database:h2:2.2.220")
    zap(kotlin("stdlib"))
    zap("org.jetbrains.exposed:exposed-core:0.57.0")
    zap("org.jetbrains.exposed:exposed-dao:0.57.0")
    zap("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    zap("io.github.revxrsal:lamp.common:4.0.0-beta.25")
    zap("io.github.revxrsal:lamp.bukkit:4.0.0-beta.25")
    zap("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.21.0")
    zap("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.21.0")

    implementation("dev.dejvokep:boosted-yaml:1.3.6")
}

zapper {
    libsFolder = "libs"
    repositories { includeProjectRepositories() }
}

val targetJavaVersion = 21

kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        javaParameters = true
    }
}
