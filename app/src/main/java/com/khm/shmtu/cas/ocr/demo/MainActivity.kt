package com.khm.shmtu.cas.ocr.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.khm.shmtu.cas.captcha.Captcha
import com.khm.shmtu.cas.captcha.CaptchaAndroid
import com.khm.shmtu.cas.ocr.SHMTU_NCNN
import com.khm.shmtu.cas.ocr.SHMTU_NCNN_Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val shmtuNcnn = SHMTU_NCNN()

    private var imageView: ImageView? = null
    private var innerBitmap: Bitmap? = null

    private var infoResult: TextView? = null
    private var tvModelStatus: TextView? = null
    private var progressBar: ProgressBar? = null

    private var isDownloading = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            try {
                val selectedImage = result.data?.data
                val bitmap = ImageUtils.decodeUri(this, selectedImage)
                val rgba = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                innerBitmap = Bitmap.createScaledBitmap(rgba, 400, 140, false)
                rgba.recycle()
                imageView?.setImageBitmap(bitmap)
            } catch (e: FileNotFoundException) {
                Log.e("MainActivity", "FileNotFoundException")
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activate_main)

        supportActionBar?.apply {
            title = "验证码识别"
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        initWidget()
    }

    private fun doOcrDemo() {
        if (innerBitmap == null) {
            Toast.makeText(this, "请先选择图片!", Toast.LENGTH_SHORT).show()
            return
        }

        val status = shmtuNcnn.getModelStatus()
        if (status == SHMTU_NCNN.ModelStatus.NOT_LOADED) {
            AlertDialog.Builder(this)
                .setTitle("模型未加载")
                .setMessage("请先加载模型后再进行识别")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val resultObj = shmtuNcnn.predict_validate_code(innerBitmap)
        if (resultObj == null) {
            Toast.makeText(this, "识别失败!", Toast.LENGTH_SHORT).show()
            return
        }
        infoResult?.text = resultObj[1] as String
    }

    private fun initWidget() {
        tvModelStatus = findViewById<View>(R.id.tv_model_status) as TextView
        progressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        updateModelStatusText()

        infoResult = findViewById<View>(R.id.infoResult) as TextView
        imageView = findViewById<View>(R.id.imageView) as ImageView

        findViewById<Button>(R.id.buttonGetFromNet).setOnClickListener {
            val imageURL = "https://cas.shmtu.edu.cn/cas/captcha"
            this.launch {
                try {
                    val bitmap = ImageUtils.downloadImageFromURL(imageURL)
                    innerBitmap = bitmap
                    imageView?.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        findViewById<Button>(R.id.buttonInner1).setOnClickListener {
            val bitmap = ImageUtils.getBitmapFromAssets(this, "test1_20240102160004_server.png")
            innerBitmap = bitmap
            imageView?.setImageBitmap(bitmap)
        }

        findViewById<Button>(R.id.buttonInner2).setOnClickListener {
            val bitmap = ImageUtils.getBitmapFromAssets(this, "test2_20240102160811_server.png")
            innerBitmap = bitmap
            imageView?.setImageBitmap(bitmap)
        }

        findViewById<Button>(R.id.buttonSelectImageFromLocal).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            imagePickerLauncher.launch(intent)
        }

        findViewById<Button>(R.id.buttonDetect).setOnClickListener { doOcrDemo() }

        findViewById<Button>(R.id.button_ocr_server).setOnClickListener { ocrViaRemoteServer() }

        findViewById<Button>(R.id.button_load_model).setOnClickListener { showLoadModelDialog() }

        findViewById<Button>(R.id.button_download_model).setOnClickListener { showDownloadModelDialog() }

        findViewById<Button>(R.id.button_check_status).setOnClickListener {
            updateModelStatusText()
            Toast.makeText(this, "状态: ${shmtuNcnn.getModelStatus()}", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.button_release_model).setOnClickListener {
            if (shmtuNcnn.getModelStatus() == SHMTU_NCNN.ModelStatus.NOT_LOADED) {
                Toast.makeText(this, "模型未加载!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            releaseModel()
        }
    }

private fun showLoadModelDialog() {
        val status = shmtuNcnn.getModelStatus()
        if (status != SHMTU_NCNN.ModelStatus.NOT_LOADED) {
            Toast.makeText(this, "模型已加载: $status", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()

        if (SHMTU_NCNN_Model.isModelBuiltIn(assets)) {
            options.add("从内置资源加载")
        }

        options.add("从本地已下载模型加载")

        val items = options.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择加载方式")
            .setItems(items) { _, which ->
                showDeviceSelectionDialog { useGpu ->
                    val whichOption = options[which]
                    when {
                        whichOption.contains("内置") -> loadModelFromAssets(useGpu)
                        else -> loadModelFromDownloaded(useGpu)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeviceSelectionDialog(onSelected: (Boolean) -> Unit) {
        if (!shmtuNcnn.isVulkanSupported()) {
            onSelected(false)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("选择运行设备")
            .setItems(arrayOf("CPU", "GPU")) { _, which ->
                onSelected(which == 1)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDownloadModelDialog() {
        if (isDownloading) {
            Toast.makeText(this, "正在下载中...", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("从 Gitee 下载 (默认)", "从 GitHub 下载")

        AlertDialog.Builder(this)
            .setTitle("选择下载源")
            .setItems(options) { _, which ->
                val source = if (which == 0) SHMTU_NCNN_Model.ModelSource.GITEE else SHMTU_NCNN_Model.ModelSource.GITHUB
                downloadModel(source)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGpuSelectionDialog(options: Array<String>, onSelected: (Boolean) -> Unit) {
        val useGpu = options.size > 1 && shmtuNcnn.isVulkanSupported()
        onSelected(useGpu)
    }

    private fun updateModelStatusText() {
        val status = shmtuNcnn.getModelStatus()
        val gpuSupport = shmtuNcnn.isVulkanSupported()
        val statusText = when (status) {
            SHMTU_NCNN.ModelStatus.NOT_LOADED -> "未加载"
            SHMTU_NCNN.ModelStatus.LOADED_CPU -> "CPU模式"
            SHMTU_NCNN.ModelStatus.LOADED_GPU -> "GPU模式"
        }
        tvModelStatus?.text = "$statusText | GPU:${if (gpuSupport) "支持" else "不支持"}"
        infoResult?.text = ""
    }

    private fun loadModelFromAssets(useGpu: Boolean) {
        if (!SHMTU_NCNN_Model.isModelBuiltIn(assets)) {
            Toast.makeText(this, "内置模型不存在!", Toast.LENGTH_SHORT).show()
            return
        }
        if (useGpu && !shmtuNcnn.isVulkanSupported()) {
            Toast.makeText(this, "GPU不支持!", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在加载内置模型...", Toast.LENGTH_SHORT).show()

        SHMTU_NCNN_Model.loadModelFromAssetsAsync(shmtuNcnn, assets, useGpu, object : SHMTU_NCNN_Model.LoadCallback {
            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "内置模型加载成功", Toast.LENGTH_SHORT).show()
                    updateModelStatusText()
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "加载失败: $error", Toast.LENGTH_LONG).show()
                    updateModelStatusText()
                }
            }
        })
    }

    private fun loadModelFromDownloaded(useGpu: Boolean) {
        if (!SHMTU_NCNN_Model.isModelDownloaded(this)) {
            Toast.makeText(this, "本地未下载模型，请先下载!", Toast.LENGTH_SHORT).show()
            return
        }
        if (useGpu && !shmtuNcnn.isVulkanSupported()) {
            Toast.makeText(this, "GPU不支持!", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在从本地加载模型...", Toast.LENGTH_SHORT).show()

        SHMTU_NCNN_Model.loadModelFromDirAsync(shmtuNcnn, this, useGpu, object : SHMTU_NCNN_Model.LoadCallback {
            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "本地模型加载成功", Toast.LENGTH_SHORT).show()
                    updateModelStatusText()
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "加载失败: $error", Toast.LENGTH_LONG).show()
                    updateModelStatusText()
                }
            }
        })
    }

    private fun downloadModel(source: SHMTU_NCNN_Model.ModelSource) {
        val sourceName = if (source == SHMTU_NCNN_Model.ModelSource.GITEE) "Gitee" else "GitHub"
        Toast.makeText(this, "正在从${sourceName}下载模型...", Toast.LENGTH_SHORT).show()
        isDownloading = true
        progressBar?.visibility = View.VISIBLE

        SHMTU_NCNN_Model.downloadModelAsync(this, source, true, object : SHMTU_NCNN_Model.DownloadProgressListener {
            override fun onProgress(progress: Int) {
                runOnUiThread {
                    progressBar?.progress = progress
                }
            }

            override fun onSuccess(modelDir: File?) {
                runOnUiThread {
                    isDownloading = false
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "模型下载成功", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    isDownloading = false
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "下载失败: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun releaseModel() {
        shmtuNcnn.releaseModel()
        updateModelStatusText()
        Toast.makeText(this, "模型已释放", Toast.LENGTH_SHORT).show()
    }

    private fun ocrViaRemoteServer() {
        if (innerBitmap == null) {
            Toast.makeText(this, "请先选择图片!", Toast.LENGTH_SHORT).show()
            return
        }

        val ip = findViewById<EditText>(R.id.editText_Ip).text.trim().toString()
        val port = findViewById<EditText>(R.id.editText_Port).text.trim().toString()

        if (ip.isBlank()) {
            Toast.makeText(this, "无效的服务器地址!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Captcha.validatePort(port)) {
            Toast.makeText(this, "无效的端口!", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            val imageData = CaptchaAndroid.AndroidBitmapToByteArray(innerBitmap!!)
            val result = Captcha.ocrByRemoteTcpServerAutoRetry(ip, port.toInt(), imageData)

            runOnUiThread {
                if (result.isBlank()) {
                    Toast.makeText(this, "远程OCR失败!", Toast.LENGTH_SHORT).show()
                } else {
                    infoResult?.text = result
                }
            }
        }.start()
    }
}