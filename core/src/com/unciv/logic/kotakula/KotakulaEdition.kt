package com.unciv.logic.kotakula

import com.badlogic.gdx.Gdx
import com.unciv.logic.files.UncivFiles
import com.unciv.utils.Log

/**
 * KC-22: branding of the build and installation of the bundled Kotakula Multiverse mod.
 *
 * The mod ships inside the game assets (`KotakulaMod/Kotakula Multiverse`, built by the `kotakula*` gradle tasks of
 * the tests module). On every start the bundled copy is compared with the one in the mods folder by the index stamp,
 * and (re)installed when it is missing or outdated, so the player never installs it by hand.
 */
object KotakulaEdition {
    const val EDITION_NAME = "Unciv: Kotakula Edition"
    const val MOD_NAME = "Kotakula Multiverse"
    const val MOD_VERSION = "1.3.1"
    const val BUNDLED_MOD_PATH = "KotakulaMod/$MOD_NAME"
    /** List of the bundled files with their CRC32, written by the atlas packer */
    const val INDEX_FILE = "KotakulaFiles.txt"
    /** Written into the installed copy: [MOD_VERSION] and the hash of the index it was installed from */
    const val INSTALLED_MARKER = "KotakulaInstalled.txt"

    /** @return true when the mod was (re)installed */
    fun installBundledMod(files: UncivFiles): Boolean {
        return try {
            val index = Gdx.files.internal("$BUNDLED_MOD_PATH/$INDEX_FILE")
            if (!index.exists()) return false
            val indexText = index.readString(Charsets.UTF_8.name())
            val stamp = "$MOD_VERSION ${indexText.hashCode().toUInt().toString(16)}"
            val target = files.getModFolder(MOD_NAME)
            val marker = target.child(INSTALLED_MARKER)
            if (marker.exists() && marker.readString(Charsets.UTF_8.name()).trim() == stamp) return false

            // Files of an older version could be left over, the mod folder belongs to the edition entirely
            if (target.exists()) target.deleteDirectory()
            for (line in indexText.lineSequence()) {
                val path = line.substringBefore('\t').trim()
                if (path.isEmpty()) continue
                Gdx.files.internal("$BUNDLED_MOD_PATH/$path").copyTo(target.child(path))
            }
            marker.writeString(stamp, false, Charsets.UTF_8.name())
            Log.debug("Kotakula Multiverse %s installed into %s", MOD_VERSION, target.path())
            true
        } catch (ex: Exception) {
            Log.error("Could not install the bundled Kotakula Multiverse mod", ex)
            false
        }
    }

    /** Version of the mod in the mods folder, for the About screen */
    fun installedModVersion(files: UncivFiles): String? {
        val marker = files.getModFolder(MOD_NAME).child(INSTALLED_MARKER)
        if (!marker.exists()) return null
        return marker.readString(Charsets.UTF_8.name()).trim().substringBefore(' ')
    }
}
