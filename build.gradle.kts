import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.JavaVersion.VERSION_21
import org.gradle.api.JavaVersion.VERSION_25

plugins {
    id("dev.kikugie.loom-back-compat")
    `maven-publish`
}

val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modVersion = (findProperty("mod_version") as String?) ?: (property("mod.version") as String)
val modGroup = property("mod.group") as String
val mcCompat = property("mod.mc_compat") as String

version = "$modVersion+${sc.current.version}"
base.archivesName = modId

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> VERSION_25
    sc.current.parsed >= "1.20.5" -> VERSION_21
    else -> VERSION_17
}

repositories {
    mavenCentral()

    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com/")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // https://mvnrepository.com/artifact/org.projectlombok/lombok
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    // https://github.com/TerraformersMC/ModMenu
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")

    // https://modrinth.com/mod/dev-auth-neo
    localRuntime("net.litetex.mcm:dev-auth-neo:1.2.0")
}

tasks.processResources {
    val props = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to modVersion,
        "minecraft" to mcCompat,
    )
    inputs.properties(props)

    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
}

java {
    withSourcesJar()

    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

tasks.jar {
    inputs.property("projectName", modId)

    from("${rootProject.projectDir}/LICENSE") {
        rename { "${it}_$modId" }
    }
}

loom {
    runConfigs.all {
        runDirectory = rootProject.file("run")
    }

    runs {
        named("client") {
            property("devauth.enabled", "1")
        }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the mod jar for the active version and copies it to build/libs/{minecraft version}/"

    inputs.property("version", sc.current.version)
    // loomx.mod(Sources)Jar resolves to the right task regardless of which Loom major version is active
    from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${sc.current.version}"))
}
