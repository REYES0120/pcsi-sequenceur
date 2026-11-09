package pcsi.engine

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Fichier data.json sur le téléphone. Un import n'écrase l'ancien fichier que s'il est
 * entièrement valide : un fichier erroné ne remplace jamais de bonnes données.
 */
class DataStore(private val file: File) {

    /** Null si aucun import n'a encore été fait. */
    fun load(): ImportResult? {
        if (!file.exists()) return null
        return Importer.parse(file.readText(Charsets.UTF_8))
    }

    fun import(text: String): ImportResult {
        val result = Importer.parse(text)
        if (!result.ok) return result
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (ex: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return result
    }
}
