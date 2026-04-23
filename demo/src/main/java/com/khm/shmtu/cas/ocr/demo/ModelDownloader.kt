package com.khm.shmtu.cas.ocr.demo

import android.os.Handler
import android.os.Looper
import com.khm.shmtu.cas.ocr.SHMTU_NCNN_Model
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelDownloader {

    interface DownloadProgressListener {
        fun onProgress(fileIndex: Int, totalFiles: Int, currentFileProgress: Int)
        fun onSuccess()
        fun onError(error: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun download(source: SHMTU_NCNN_Model.ModelSource, context: android.content.Context, listener: DownloadProgressListener) {
        Thread {
            val modelDir = SHMTU_NCNN_Model.getModelDir(context)
            val dir = File(modelDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val urls = SHMTU_NCNN_Model.buildModelUrls(source)
            val fallbackUrls = SHMTU_NCNN_Model.buildModelUrls(
                if (source == SHMTU_NCNN_Model.ModelSource.GITEE)
                    SHMTU_NCNN_Model.ModelSource.GITHUB
                else
                    SHMTU_NCNN_Model.ModelSource.GITEE
            )

            val totalFiles = SHMTU_NCNN_Model.MODEL_FILES.size
            var downloadedFiles = 0

            for (i in 0 until totalFiles) {
                val fileName = SHMTU_NCNN_Model.MODEL_FILES[i]
                val file = File(modelDir + fileName)

                if (file.exists()) {
                    downloadedFiles++
                    val overallProgress = ((downloadedFiles * 100) / totalFiles)
                    mainHandler.post {
                        listener.onProgress(downloadedFiles, totalFiles, 100)
                    }
                    continue
                }

                var success = false
                var lastError: String? = null

                for (attempt in 0..1) {
                    val urlStr = if (attempt == 0) urls[i] else fallbackUrls[i]
                    try {
                        mainHandler.post {
                            listener.onProgress(downloadedFiles, totalFiles, 0)
                        }

                        val request = Request.Builder()
                            .url(urlStr)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body ?: throw Exception("Empty response body")
                                val contentLength = body.contentLength()
                                var bytesRead: Long = 0

                                FileOutputStream(file).use { output ->
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(8192)
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            output.write(buffer, 0, read)
                                            bytesRead += read
                                            if (contentLength > 0) {
                                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                                mainHandler.post {
                                                    listener.onProgress(downloadedFiles, totalFiles, progress)
                                                }
                                            }
                                        }
                                    }
                                }
                                success = true
                                break
                            } else {
                                lastError = "HTTP ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        lastError = e.message
                    }
                }

                if (success) {
                    downloadedFiles++
                    mainHandler.post {
                        listener.onProgress(downloadedFiles, totalFiles, 100)
                    }
                } else {
                    mainHandler.post {
                        listener.onError("Download failed: $fileName - $lastError")
                    }
                    return@Thread
                }
            }

            mainHandler.post {
                listener.onSuccess()
            }
        }.start()
    }

    fun release() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}