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

// Fabric API and ModMenu each only support Java 17/21/25 starting at certain Minecraft versions.
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
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

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // https://mvnrepository.com/artifact/org.projectlombok/lombok
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    // https://github.com/TerraformersMC/ModMenu
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")

    // https://modrinth.com/mod/dev-auth-neo
    localRuntime("net.litetex.mcm:dev-auth-neo:1.1.1")
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
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
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

// configure the maven publication
publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            groupId = modGroup
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
    }
}

loom {
    runConfigs.all {
        // Shares one `run/` directory across all versions, so you only log in to dev-auth once.
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
