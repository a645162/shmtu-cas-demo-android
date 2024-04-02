//
// Created by Haomin Kong on 2024/2/11.
//

#include "CAS_OCR_ANDROID.h"

extern std::string logcat_tag;

namespace CAS_OCR {
    static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
    static ncnn::PoolAllocator g_workspace_pool_allocator;

    extern ncnn::Net net_equal_symbol;
    extern ncnn::Net net_operator;
    extern ncnn::Net net_digit;

    bool init_model_from_assets(
            ncnn::Net &net,
            AAssetManager *mgr,
            std::string name,
            std::string precision
    ) {
        const auto file_name_param = name + "." + precision + ".param";
        int ret = net.load_param(mgr, file_name_param.c_str());
        if (ret != 0) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    logcat_tag.c_str(),
                    "load_param failed!"
            );
            return false;
        }

        const auto file_name_model = name + "." + precision + ".bin";
        ret = net.load_model(mgr, file_name_model.c_str());
        if (ret != 0) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    logcat_tag.c_str(),
                    "load_model failed!"
            );
            return false;
        }

        return true;
    }

    void set_net_opt(ncnn::Net &net) {
        ncnn::Option &opt = net.opt;
        opt.lightmode = true;
        opt.num_threads = 4;
        opt.blob_allocator = &g_blob_pool_allocator;
        opt.workspace_allocator = &g_workspace_pool_allocator;

        // use vulkan compute
        if (ncnn::get_gpu_count() != 0)
            opt.use_vulkan_compute = true;

        //    net.opt = opt;
    }

    bool init_all_model_from_assets(AAssetManager *mgr) {
        CAS_OCR::set_net_opt(net_equal_symbol);
        CAS_OCR::set_net_opt(net_operator);
        CAS_OCR::set_net_opt(net_digit);

        bool isSuccessful = true;

        isSuccessful = CAS_OCR::init_model_from_assets(
                net_equal_symbol,
                mgr,
                "resnet18_equal_symbol_latest",
                "fp16"
        ) && isSuccessful;

        isSuccessful = CAS_OCR::init_model_from_assets(
                net_operator,
                mgr,
                "resnet18_operator_latest",
                "fp16"
        ) && isSuccessful;

        isSuccessful = CAS_OCR::init_model_from_assets(
                net_digit,
                mgr,
                "resnet34_digit_latest",
                "fp16"
        ) && isSuccessful;

        return isSuccessful;
    }
}