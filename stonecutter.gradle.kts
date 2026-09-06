plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    replacements {
        // ResourceLocation -> Identifier (starting with 1.21.11)
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        // mapping namespace rename that landed in 26.1
        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }

        regex(current.parsed < "26.1") {
            replace("""\bGuiGraphicsExtractor\b""", "GuiGraphics", """\bGuiGraphics\b""", "GuiGraphicsExtractor")
            replace("""(?<=public void )extractRenderState\b""", "render", """(?<=public void )render\b""", "extractRenderState")
            replace("""(?<=super\.)extractRenderState\b""", "render", """(?<=super\.)render\b""", "extractRenderState")
            replace("""(?<=public void )extractBackground\b""", "renderBackground", """(?<=public void )renderBackground\b""", "extractBackground")
            replace("""(?<=super\.)extractBackground\b""", "renderBackground", """(?<=super\.)renderBackground\b""", "extractBackground")
        }

        string(current.parsed < "26.1") {
            replace("graphics.text(", "graphics.drawString(")
        }

        string(current.parsed < "26.2") {
            replace("Minecraft.getInstance().gui.toastManager()", "Minecraft.getInstance().getToastManager()")
        }
    }
}
