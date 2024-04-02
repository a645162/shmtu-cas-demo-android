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

    void set_net_opt(ncnn::Net &net);

    bool init_all_model_from_assets(AAssetManager *mgr);

}

#endif //SHMTU_CAS_OCR_DEMO_CAS_OCR_ANDROID_H
