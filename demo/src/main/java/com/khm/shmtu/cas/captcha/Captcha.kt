package com.khm.shmtu.cas.captcha

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.URL

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.SocketTimeoutException

class Captcha {

    companion object {

        fun validateIPAddress(ip: String): Boolean {
            val ipAddressPattern = Regex(
                "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
            )
            return ipAddressPattern.matches(ip)
        }

        fun validatePort(port: String): Boolean {
            val integerPort = port.toIntOrNull()
            return integerPort != null && validatePort(integerPort)
        }

        fun validatePort(port: Int): Boolean {
            return port in 0..65535
        }

        fun getImageDataFromUrl(
            imageUrl: String = "https://cas.shmtu.edu.cn/cas/captcha"
        ): ByteArray {
            val url = URL(imageUrl)
            val inputStream = BufferedInputStream(url.openStream())
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            return outputStream.toByteArray()
        }

        fun getImageDataFromUrlUsingGet(
            cookie: String? = null
        ): Pair<ByteArray?, String>? {
            val imageUrl = "https://cas.shmtu.edu.cn/cas/captcha"

            val client = OkHttpClient.Builder()
                .build()

            val requestBuilder = Request.Builder()
                .url(imageUrl)
                .get()

            if (!cookie.isNullOrBlank()) {
                requestBuilder.addHeader("Cookie", cookie)
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    println("请求失败，状态码：${response.code}")
                    return null
                }

                // JSESSIONID是在获取验证码的过程中设置到浏览器的Cookie中的
                // 如果不存在更新JSESSIONID操作则直接返回原本传入的Cookie
                // 如果没有传入Cookie，一般服务器会Set-Cookie返回一个新的JSESSIONID
                // 因此一般不会出现Cookie为空的情况
                val returnCookie =
                    response.headers["Set-Cookie"] ?: (cookie ?: "")

                return Pair(response.body?.bytes(), returnCookie)
            } catch (e: IOException) {
                println("请求失败：${e.message}")
                return null
            }
        }

        fun ocrByRemoteTcpServer(
            host: String, port: Int,
            imageData: ByteArray
        ): String {
            Socket(host, port).use { socket ->
                // 设置超时时间为 5 秒
                // socket.setSoTimeout(5000)

                val outputStream = socket.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)

                // 发送图像数据
                dataOutputStream.write(imageData)
                dataOutputStream.flush()

                // 发送特殊标记，表示图像数据发送完毕
                val endMarker = "<END>".toByteArray(Charsets.UTF_8)
                outputStream.write(endMarker)
                outputStream.flush()

                try {
                    val inputStream = socket.getInputStream()
                    val response = inputStream.readBytes().toString(Charsets.UTF_8)
                    return response
                } catch (e: SocketTimeoutException) {
                    // 超时，返回空字符串
                    println("SocketTimeoutException")
                    return ""
                }
            }
        }

        fun ocrByRemoteTcpServerAutoRetry(
            host: String, port: Int,
            imageData: ByteArray,
            retryTimes: Int = 3
        ): String {
            var result: String = ""

            for (i in 1..retryTimes) {

                println("第${i}次尝试远程识别验证码")
                println("ip: ${host}, port: $port")
                try {
                    result = ocrByRemoteTcpServer(host, port, imageData)
                } catch (e: Exception) {
                    println("第${i + 1}次尝试远程识别验证码失败")
                    println("错误信息：${e.message}")
                    continue
                }

                if (result.isNotEmpty()) {
                    break
                }
            }

            return result
        }

        fun getExprResultByExprString(expr: String): String {
            val index = expr.indexOf("=")
            if (index != -1) {
                val result = expr.substring(index + 1).trim()
                return result
            }
            return ""
        }

        fun testLocalTcpServerOcr() {
            println("识别验证码 Test")
            val resultCaptcha =
                getImageDataFromUrlUsingGet()

            if (resultCaptcha == null) {
                println("获取验证码失败")
                return
            }

            val imageData = resultCaptcha.first
            println(resultCaptcha.second)

            if (imageData == null) {
                println("获取验证码失败")
                return
            }

            val validateCode =
                ocrByRemoteTcpServerAutoRetry(
                    "127.0.0.1", 21601,
                    imageData
                )

            val exprResult =
                getExprResultByExprString(validateCode)
            println(validateCode)
            println(exprResult)
        }

        fun testLocalTcpServerOcrMultiThread(times: Int = 10) {
            val threads = List(times) {
                Thread {
                    testLocalTcpServerOcr()
                }
            }

            threads.forEach { it.start() } // 启动所有线程

            // 等待所有线程执行完毕
            threads.forEach { it.join() }

            println("All threads have finished execution.")
        }

    }

}
