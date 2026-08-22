plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    replacements {
        // Mojang renamed ResourceLocation -> Identifier starting with 1.21.11
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        // mapping namespace rename that landed in 26.1
        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}
