package com.masroufi.app.receipts

import android.content.Context
import android.net.Uri
import com.masroufi.app.core.newId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipts are copied into app-private storage (filesDir/receipts) so they are never exposed through
 * shared/public storage and are removed with the app. Paths stored in the database are relative names.
 */
@Singleton
class ReceiptStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "${newId()}.jpg"
        val target = File(dir, name)
        val input = context.contentResolver.openInputStream(uri) ?: error("Unreadable receipt")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        if (target.length() > MAX_BYTES) {
            target.delete()
            throw IllegalArgumentException("Receipt too large")
        }
        name
    }

    fun file(name: String): File? = File(dir, name).takeIf { it.exists() && it.parentFile == dir }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        File(dir, name).takeIf { it.parentFile == dir }?.delete()
        Unit
    }

    private companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
    }
}
