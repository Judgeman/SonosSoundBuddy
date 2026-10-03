package de.paul.sonoscontrol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Eigene Bilder für Kategorien und Musik-Einträge. Das gewählte Foto wird verkleinert in den
 * App-Speicher kopiert — so bleibt es erhalten, auch wenn es in der Galerie
 * gelöscht wird, und die App braucht keine Speicher-Berechtigung.
 */
class CustomImageStore(context: Context) {

    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "custom_images")
    /** Ordner einer früheren Version, nur noch zum Aufräumen. */
    private val legacyDirectory = File(context.filesDir, "category_images")

    /** Kopiert das Bild hinter [uri] und gibt den Pfad der Kopie zurück. [name] landet im Dateinamen. */
    suspend fun import(uri: Uri, name: String): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Das Bild konnte nicht gelesen werden.")

        // Grob mit inSampleSize verkleinern, damit große Fotos keinen Speicher sprengen
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIZE_PX) sampleSize *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IOException("Das Bild konnte nicht gelesen werden.")

        val bitmap = scaleDown(rotateUpright(decoded, uri))
        directory.mkdirs()
        // Neuer Dateiname bei jedem Import, damit Coil kein altes Bild aus dem Cache zeigt
        val file = File(directory, "$name-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }

    /** Löscht ein früher importiertes Bild; andere Bild-Arten werden ignoriert. */
    suspend fun delete(imageKey: String?) {
        val image = CustomImage.fromKey(imageKey) as? CustomImage.File ?: return
        withContext(Dispatchers.IO) {
            val file = File(image.path)
            // Nur Dateien aus dem eigenen Ordner anfassen
            val parent = file.parentFile?.canonicalPath
            if (parent == directory.canonicalPath || parent == legacyDirectory.canonicalPath) file.delete()
        }
    }

    /** Fotos der Kamera liegen oft quer und tragen die Drehung nur in den EXIF-Daten. */
    private fun rotateUpright(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: return bitmap
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_SIZE_PX) return bitmap
        val factor = MAX_SIZE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        private const val MAX_SIZE_PX = 768
    }
}
