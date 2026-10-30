package pl.fleet.monitor.ui.map

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import pl.fleet.monitor.data.now

/**
 * Kafelki mapy zapisane na dysku telefonu (katalog pamięci podręcznej systemu).
 * Znana okolica wyświetla się od razu, bez ponownego pobierania. Całość jest czyszczona,
 * gdy przekroczy [MAX_BYTES] albo ma więcej niż [MAX_AGE_DAYS] dni (mapy OSM się zmieniają).
 */
internal class TileCache(root: String) {
    private val dir = Path(root, "osm-tiles")
    private val created = Path(dir, "created")

    private fun path(zoom: Int, x: Int, y: Int) = Path(dir, zoom.toString(), x.toString(), "$y.png")

    fun read(zoom: Int, x: Int, y: Int): ByteArray? = runCatching {
        val p = path(zoom, x, y)
        if (SystemFileSystem.metadataOrNull(p)?.isRegularFile == true) {
            SystemFileSystem.source(p).buffered().use { it.readByteArray() }
        } else {
            null
        }
    }.getOrNull()

    fun write(zoom: Int, x: Int, y: Int, bytes: ByteArray) {
        runCatching {
            val p = path(zoom, x, y)
            p.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(p).buffered().use { it.write(bytes) }
        }
    }

    /** Usuwa całą pamięć podręczną, gdy jest za duża lub za stara (wywoływane raz przy starcie). */
    fun prune() {
        runCatching {
            val nowSec = now().epochSeconds
            if (!SystemFileSystem.exists(dir)) SystemFileSystem.createDirectories(dir)
            val createdAt = runCatching {
                SystemFileSystem.source(created).buffered().use { it.readString().trim().toLong() }
            }.getOrNull()
            val tooOld = createdAt == null || nowSec - createdAt > MAX_AGE_DAYS * 86_400L
            if (tooOld || sizeOf(dir) > MAX_BYTES) {
                deleteRecursively(dir)
                SystemFileSystem.createDirectories(dir)
                SystemFileSystem.sink(created).buffered().use { it.writeString(nowSec.toString()) }
            }
        }
    }

    private fun sizeOf(p: Path): Long {
        val meta = SystemFileSystem.metadataOrNull(p) ?: return 0
        return if (meta.isDirectory) SystemFileSystem.list(p).sumOf { sizeOf(it) } else meta.size
    }

    private fun deleteRecursively(p: Path) {
        val meta = SystemFileSystem.metadataOrNull(p) ?: return
        if (meta.isDirectory) SystemFileSystem.list(p).forEach { deleteRecursively(it) }
        SystemFileSystem.delete(p, mustExist = false)
    }

    private companion object {
        const val MAX_BYTES = 150L * 1024 * 1024
        const val MAX_AGE_DAYS = 30
    }
}
