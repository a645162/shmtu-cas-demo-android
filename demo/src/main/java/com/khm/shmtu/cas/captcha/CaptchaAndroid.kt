package com.khm.shmtu.cas.captcha

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class CaptchaAndroid {

    companion object {

        fun AndroidBitmapToByteArray(bitmap: Bitmap): ByteArray {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            return baos.toByteArray()
        }

    }

}