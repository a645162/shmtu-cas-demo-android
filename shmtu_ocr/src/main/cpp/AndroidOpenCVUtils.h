//
// Created by konghaomin on 2024/2/11.
//

#ifndef SHMTU_CAS_OCR_DEMO_ANDROID_OPENCV_UTILS_H
#define SHMTU_CAS_OCR_DEMO_ANDROID_OPENCV_UTILS_H

// Android JNI
#include <jni.h>
#include <android/bitmap.h>

// OpenCV
#include <opencv2/core.hpp>

cv::Mat convertBitmapToMat(JNIEnv *env, jobject thiz, jobject bitmap);

#endif //SHMTU_CAS_OCR_DEMO_ANDROID_OPENCV_UTILS_H
