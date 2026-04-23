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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.khm.shmtu.cas.captcha.Captcha
import com.khm.shmtu.cas.captcha.CaptchaAndroid
import com.khm.shmtu.cas.ocr.SHMTU_NCNN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val shmtuNcnn = SHMTU_NCNN()

    private var imageView: ImageView? = null
    private var innerBitmap: Bitmap? = null

    private var infoResult: TextView? = null
    private var tvModelStatus: TextView? = null

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

        val options = if (shmtuNcnn.isVulkanSupported()) {
            arrayOf("CPU", "GPU")
        } else {
            arrayOf("CPU (GPU不支持)")
        }

        AlertDialog.Builder(this)
            .setTitle("加载模型")
            .setItems(options) { _, which ->
                val useGpu = which == 1 && shmtuNcnn.isVulkanSupported()
                loadModel(useGpu)
            }
            .setNegativeButton("取消", null)
            .show()
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

    private fun loadModel(useGpu: Boolean) {
        if (useGpu && !shmtuNcnn.isVulkanSupported()) {
            Toast.makeText(this, "GPU不支持!", Toast.LENGTH_SHORT).show()
            return
        }
        val ret = shmtuNcnn.InitModel(assets, useGpu)
        if (!ret) {
            Toast.makeText(this, "模型加载失败!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "模型已加载到${if (useGpu) "GPU" else "CPU"}", Toast.LENGTH_SHORT).show()
        }
        updateModelStatusText()
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