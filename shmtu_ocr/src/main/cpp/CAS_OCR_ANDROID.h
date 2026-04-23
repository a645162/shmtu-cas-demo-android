//
// Created by konghaomin on 2024/2/11.
//

#ifndef SHMTU_CAS_OCR_DEMO_CAS_OCR_ANDROID_H
#define SHMTU_CAS_OCR_DEMO_CAS_OCR_ANDROID_H

#include "CAS_OCR.h"

#include "AndroidOpenCVUtils.h"

// Android
#include <android/asset_manager.h>

// NCNN
#include <net.h>

namespace CAS_OCR {

    bool init_model_from_assets(
            ncnn::Net &net,
            AAssetManager *mgr,
            std::string name,
            std::string precision
    );

    void set_net_opt(ncnn::Net &net, bool use_gpu);

    bool init_all_model_from_assets(AAssetManager *mgr, bool use_gpu);

    bool init_all_model_from_dir(std::string dir_path, bool use_gpu);

    bool init_model_from_dir(ncnn::Net &net, std::string dir_path, std::string name, std::string precision);

    CAS_MODEL_STATUS get_all_model_status();

    void release_all_model();

    bool is_vulkan_supported();

}

#endif //SHMTU_CAS_OCR_DEMO_CAS_OCR_ANDROID_H
