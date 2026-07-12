package dev.gaferneira.notificapp.core.data.local.security

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * One-time migration for installs whose Room database predates DATA-02: converts the plaintext
 * SQLite file in place to an encrypted SQLCipher file via SQLCipher's ATTACH + sqlcipher_export()
 * path, so existing captured notifications survive the upgrade instead of being wiped.
 */
internal object DatabaseRekeyer {

    fun rekeyIfNeeded(context: Context, databaseName: String, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(databaseName)
        if (!dbFile.exists() || !isPlaintextSqlite(dbFile)) return

        Timber.i("Rekeying plaintext database '$databaseName' to SQLCipher-encrypted format")
        val encryptedFile = File(dbFile.parentFile, "$databaseName.rekey-tmp")
        encryptedFile.delete()

        val hexKey = passphrase.toHex()
        val plainDb = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, null)
        try {
            plainDb.execSQL("ATTACH DATABASE '${encryptedFile.path}' AS encrypted KEY \"x'$hexKey'\"")
            plainDb.execSQL("SELECT sqlcipher_export('encrypted')")
            plainDb.execSQL("DETACH DATABASE encrypted")
        } finally {
            plainDb.close()
        }

        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        check(encryptedFile.renameTo(dbFile)) { "Failed to move rekeyed database into place" }
    }

    private fun isPlaintextSqlite(file: File): Boolean {
        if (file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(header) }
        return header.contentEquals(SQLITE_HEADER)
    }

    private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    private val SQLITE_HEADER = byteArrayOf(
        'S'.code.toByte(), 'Q'.code.toByte(), 'L'.code.toByte(), 'i'.code.toByte(),
        't'.code.toByte(), 'e'.code.toByte(), ' '.code.toByte(), 'f'.code.toByte(),
        'o'.code.toByte(), 'r'.code.toByte(), 'm'.code.toByte(), 'a'.code.toByte(),
        't'.code.toByte(), ' '.code.toByte(), '3'.code.toByte(), 0,
    )
}
