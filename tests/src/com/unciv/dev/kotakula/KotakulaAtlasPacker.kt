package com.unciv.dev.kotakula

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import com.unciv.logic.kotakula.KotakulaEdition
import java.io.File
import java.util.zip.CRC32

/**
 * Last step of the mod build (KC-22, section 13): packs `Images` of the mod into `game.atlas`/`game.png`
 * with the settings of the desktop ImagePacker, and writes the file index used by [KotakulaEdition] to
 * install the bundled mod into the mods folder (assets inside a jar or an apk cannot be listed).
 *
 *     ./gradlew :tests:kotakulaMod :tests:kotakulaIcons :tests:kotakulaAtlas
 */
object KotakulaAtlasPacker {
    @JvmStatic
    fun main(args: Array<String>) {
        val modDir = File(args.getOrElse(0) { KotakulaModGenerator.MOD_PATH })
        pack(modDir)
        writeIndex(modDir)
        println("Kotakula Multiverse atlas and index written in ${modDir.absolutePath}")
    }

    fun pack(modDir: File) {
        val images = File(modDir, "Images")
        if (!images.isDirectory) return
        for (old in listOf("game.atlas", "game.png", "game2.png", "game3.png")) File(modDir, old).delete()
        val settings = TexturePacker.Settings().apply {
            // Same as desktop ImagePacker.getDefaultSettings: 2048 is the safe maximum for old chipsets
            maxWidth = 2048
            maxHeight = 2048
            combineSubdirectories = true
            pot = true
            fast = true
            paddingX = 8
            paddingY = 8
            duplicatePadding = true
            filterMin = Texture.TextureFilter.MipMapLinearLinear
            filterMag = Texture.TextureFilter.MipMapLinearLinear
        }
        TexturePacker.process(settings, images.path, modDir.path, "game")
        File(modDir, "Atlases.json").writeText("[game]")
    }

    /** Every file the game needs (the Images sources are packed into the atlas and are not installed) */
    fun writeIndex(modDir: File) {
        val root = modDir.canonicalFile
        val lines = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filterNot { it.startsWith("Images/") || it == KotakulaEdition.INDEX_FILE || it == KotakulaEdition.INSTALLED_MARKER }
            .sorted()
            .map { path ->
                val crc = CRC32().apply { update(File(root, path).readBytes()) }.value
                "$path\t${crc.toString(16)}"
            }
            .toList()
        File(root, KotakulaEdition.INDEX_FILE).writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}
