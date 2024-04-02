// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <jni.h>
#include <iostream>

#include <string>
#include <vector>

#include <opencv2/opencv.hpp>

// ncnn
#include <net.h>
#include <benchmark.h>

#include "CAS_OCR_ANDROID.h"

//#include "res.id.h"

std::string logcat_tag = "SHMTU_CAS_OCR_NCNN";

// Python Version:src/config/config.py
const float mean_values[3] = {123.675f, 116.28f, 103.53f};
const float norm_values[3] = {1 / 58.395f, 1 / 57.12f, 1 / 57.375f};

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    __android_log_print(
            ANDROID_LOG_DEBUG,
            logcat_tag.c_str(),
            "SHMTU_CAS_OCR_JNI_OnLoad"
    );

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    __android_log_print(
            ANDROID_LOG_DEBUG,
            logcat_tag.c_str(),
            "SHMTU_CAS_OCR_JNI_OnUnload"
    );

    ncnn::destroy_gpu_instance();
}

// public native boolean Init(AssetManager mgr);
JNIEXPORT jboolean JNICALL
Java_com_khm_shmtu_cas_ocr_SHMTU_1NCNN_Init(JNIEnv *env, jobject thiz, jobject assetManager) {
    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);

    const auto isSuccessful = CAS_OCR::init_all_model_from_assets(mgr);

    if (isSuccessful) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

// public native String Detect(Bitmap bitmap, boolean use_gpu);
JNIEXPORT jobject JNICALL
Java_com_khm_shmtu_cas_ocr_SHMTU_1NCNN_Detect(
        JNIEnv *env, jobject thiz,
        jobject bitmap, jboolean use_gpu
) {
    bool use_gpu_cpp_bool = use_gpu == JNI_TRUE;

    if (use_gpu_cpp_bool && ncnn::get_gpu_count() == 0) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                logcat_tag.c_str(),
                "No vulkan capable GPU!"
        );
        use_gpu_cpp_bool = false;
    }

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return NULL;

    const auto image_input = convertBitmapToMat(env, thiz, bitmap);

    const auto result =
            CAS_OCR::predict_validate_code(
                    image_input,
                    use_gpu_cpp_bool
            );

    std::vector<std::string> return_tuples = {
            std::to_string(std::get<0>(result)),
            std::get<1>(result),
            std::to_string(std::get<2>(result)),
            std::to_string(std::get<3>(result)),
            std::to_string(std::get<4>(result)),
            std::to_string(std::get<5>(result)),
    };

    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject stringList = env->NewObject(arrayListClass, arrayListConstructor);

    // 添加一些字符串到列表中

    for (const auto &str: return_tuples) {
        jstring jStr = env->NewStringUTF(str.c_str());
        env->CallBooleanMethod(stringList, arrayListAdd, jStr);
        env->DeleteLocalRef(jStr);
    }
    return stringList;
}

}
