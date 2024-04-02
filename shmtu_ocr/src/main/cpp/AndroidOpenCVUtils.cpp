//
// Created by konghaomin on 2024/2/11.
//

#include "AndroidOpenCVUtils.h"

// OpenCV
#include <opencv2/imgproc.hpp>

cv::Mat convertBitmapToMat(JNIEnv *env, jobject thiz, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels;

    // 获取 Bitmap 信息
    AndroidBitmap_getInfo(env, bitmap, &info);

    // 锁定 Bitmap 并获取像素数据
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // 将 Bitmap 转换为 OpenCV Mat 对象
    cv::Mat image(info.height, info.width, CV_8UC4, pixels);

    // 在这里可以对图像进行处理，如转换颜色空间等

    // 解锁 Bitmap
    AndroidBitmap_unlockPixels(env, bitmap);

    return image;
}
