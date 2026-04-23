package com.khm.shmtu.cas.ocr.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.khm.shmtu.cas.captcha.Captcha
import com.khm.shmtu.cas.captcha.CaptchaAndroid
import com.khm.shmtu.cas.ocr.SHMTU_NCNN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.FileNotFoundException


class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val shmtuNcnn = SHMTU_NCNN()

    private var imageView: ImageView? = null
    private var innerBitmap: Bitmap? = null

    private var infoResult: TextView? = null

    @SuppressLint("SourceLockedOrientationActivity")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activate_main)

        supportActionBar?.apply {
            title = "验证码识别"
        }

        // 设置屏幕方向为竖屏模式
        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        initWidget()
    }

    private fun doOcrDemo() {
        if (innerBitmap == null) return
        val resultObj =
            shmtuNcnn.predict_validate_code(innerBitmap)
        if (resultObj == null) {
            Toast.makeText(
                this@MainActivity,
                "detect failed!",
                Toast.LENGTH_SHORT
            )
                .show()
            return
        }
        infoResult!!.text = resultObj[1] as String
    }

    private fun initWidget() {
        updateModelStatusText()

        infoResult = findViewById<View>(R.id.infoResult) as TextView
        imageView = findViewById<View>(R.id.imageView) as ImageView

        val buttonGetFromNet = findViewById<View>(R.id.buttonGetFromNet) as Button
        buttonGetFromNet.setOnClickListener {
            val imageURL = "https://cas.shmtu.edu.cn/cas/captcha"
            this.launch {
                try {
                    val bitmap = ImageUtils.downloadImageFromURL(imageURL)
                    innerBitmap = bitmap
                    imageView!!.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    // 处理异常，例如显示错误消息
                    e.printStackTrace()
                }
            }
        }

        (findViewById<View>(R.id.buttonInner1) as Button).setOnClickListener {
            val bitmap = ImageUtils.getBitmapFromAssets(
                this@MainActivity,
                "test1_20240102160004_server.png"
            )
            innerBitmap = bitmap
            imageView!!.setImageBitmap(bitmap)
        }
        (findViewById<View>(R.id.buttonInner2) as Button).setOnClickListener {
            val bitmap = ImageUtils.getBitmapFromAssets(
                this@MainActivity,
                "test2_20240102160811_server.png"
            )
            innerBitmap = bitmap
            imageView!!.setImageBitmap(bitmap)
        }

        val buttonImage = findViewById<View>(R.id.buttonSelectImageFromLocal) as Button
        buttonImage.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK)
            i.setType("image/*")
            startActivityForResult(i, REQUEST_CODE_SELECT_IMAGE)
        }

        val buttonDetect = findViewById<View>(R.id.buttonDetect) as Button
        buttonDetect.setOnClickListener { doOcrDemo() }

        val buttonOcrViaRemoteServer = findViewById<Button>(R.id.button_ocr_server)
        buttonOcrViaRemoteServer.setOnClickListener {
            ocrViaRemoteServer()
        }

        val buttonLoadModel = findViewById<Button>(R.id.button_load_model)
        buttonLoadModel.setOnClickListener {
            showLoadModelDialog()
        }

        val buttonCheckStatus = findViewById<Button>(R.id.button_check_status)
        buttonCheckStatus.setOnClickListener {
            updateModelStatusText()
            Toast.makeText(this, "Model Status: ${shmtuNcnn.getModelStatus()}", Toast.LENGTH_SHORT).show()
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
        val gpuText = if (gpuSupport) "支持" else "不支持"
        infoResult?.text = "状态: $status | GPU: $gpuText"
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
            val deviceText = if (useGpu) "GPU" else "CPU"
            Toast.makeText(this, "模型已加载到$deviceText", Toast.LENGTH_SHORT).show()
        }
    }

    private fun releaseModel() {
        shmtuNcnn.releaseModel()
        updateModelStatusText()
        Toast.makeText(this, "Model released", Toast.LENGTH_SHORT).show()
    }

    private fun ocrViaRemoteServer() {
        if (innerBitmap == null) {
            Toast.makeText(
                this@MainActivity,
                "Please import an image first!",
                Toast.LENGTH_SHORT
            )
                .show()
            return
        }

        val editTextIp = findViewById<EditText>(R.id.editText_Ip)
        val editTextPort = findViewById<EditText>(R.id.editText_Port)

        val ip =
            editTextIp.text.trim().toString()
        val port =
            editTextPort.text.trim().toString()

        if (ip.isBlank()) {
            Toast.makeText(
                this@MainActivity,
                "Invalid host!",
                Toast.LENGTH_SHORT
            )
                .show()
            return
        }

        if (!Captcha.validatePort(port)) {
            Toast.makeText(
                this@MainActivity,
                "Invalid port number!",
                Toast.LENGTH_SHORT
            )
                .show()
            return
        }

        Thread {
            val imageData =
                CaptchaAndroid.AndroidBitmapToByteArray(innerBitmap!!)

            val result =
                Captcha.ocrByRemoteTcpServerAutoRetry(
                    ip,
                    port.toInt(),
                    imageData
                )

            if (result.isBlank()) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "OCR via Remote Server failed!",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            } else {
                infoResult!!.text = result
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            // Get the image from data
            try {
                if (requestCode == REQUEST_CODE_SELECT_IMAGE) {
                    val selectedImage = data?.data
                    val bitmap = ImageUtils.decodeUri(this, selectedImage)
                    val rgba = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)

                    // resize to 400x140
                    innerBitmap =
                        Bitmap.createScaledBitmap(rgba, 400, 140, false)
                    rgba.recycle()
                    imageView!!.setImageBitmap(bitmap)
                }
            } catch (e: FileNotFoundException) {
                Log.e("MainActivity", "FileNotFoundException")
                return
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_SELECT_IMAGE = 1
    }
}
