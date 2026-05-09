plugins {
    id("fabric-loom") version "1.7.4"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.nucleoid.xyz/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://maven.impactdev.net/repository/production/")
}

fun DependencyHandlerScope.includeMod(dep: String) {
    include(modImplementation(dep)!!)
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    includeMod("eu.pb4:sgui:${project.property("sgui_version")}")

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    include("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // SQLite JDBC bundled
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    include("org.xerial:sqlite-jdbc:3.45.3.0")

    compileOnly("net.luckperms:api:${project.property("luckperms_version")}")

    modCompileOnly("com.cobblemon:fabric:1.7.3+1.21.1")

    // Kotlin stdlib: requerido para interop con Cobblemon (Kotlin) desde Java
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
        expand(props)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}
