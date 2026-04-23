package com.khm.shmtu.cas.ocr.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object ImageUtils {
    suspend fun downloadImageFromURL(url: String): Bitmap {
        return withContext(Dispatchers.IO) {
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            try {
                urlConnection.doInput = true
                urlConnection.connect()

                if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    urlConnection.inputStream.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } else {
                    throw Exception("Failed to download image: HTTP ${urlConnection.responseCode}")
                }
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    fun getBitmapFromAssets(context: Context, fileName: String?): Bitmap? {
        return try {
            context.assets.open(fileName!!).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @Throws(FileNotFoundException::class)
    fun decodeUri(context: Context, selectedImage: Uri?): Bitmap? {
        // Decode image size
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(selectedImage!!), null, o
        )

        // The new size we want to scale to
        val REQUIRED_SIZE = 400

        // Find the correct scale value. It should be the power of 2.
        var width_tmp = o.outWidth
        var height_tmp = o.outHeight
        var scale = 1
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE
                || height_tmp / 2 < REQUIRED_SIZE
            ) {
                break
            }
            width_tmp /= 2
            height_tmp /= 2
            scale *= 2
        }

        // Decode with inSampleSize
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = scale
        return BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(selectedImage),
            null, o2
        )
    }
}
