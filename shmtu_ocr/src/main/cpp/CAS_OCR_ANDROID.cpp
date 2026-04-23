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

    extern bool is_init;
    extern bool global_use_gpu;
    extern CAS_MODEL_STATUS model_status;

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

    void set_net_opt(ncnn::Net &net, bool use_gpu) {
        ncnn::Option &opt = net.opt;
        opt.lightmode = true;
        opt.num_threads = 4;
        opt.blob_allocator = &g_blob_pool_allocator;
        opt.workspace_allocator = &g_workspace_pool_allocator;

        opt.use_vulkan_compute = use_gpu;
    }

    bool init_all_model_from_assets(AAssetManager *mgr, bool use_gpu) {
        CAS_OCR::set_net_opt(net_equal_symbol, use_gpu);
        CAS_OCR::set_net_opt(net_operator, use_gpu);
        CAS_OCR::set_net_opt(net_digit, use_gpu);

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

        if (isSuccessful)
        {
            global_use_gpu = use_gpu;
            model_status = global_use_gpu ? CAS_MODEL_STATUS_LOADED_GPU : CAS_MODEL_STATUS_LOADED_CPU;
            is_init = true;
        }

        return isSuccessful;
    }

    bool init_all_model_from_dir(std::string dir_path, bool use_gpu) {
        CAS_OCR::set_net_opt(net_equal_symbol, use_gpu);
        CAS_OCR::set_net_opt(net_operator, use_gpu);
        CAS_OCR::set_net_opt(net_digit, use_gpu);

        bool isSuccessful = true;

        isSuccessful = CAS_OCR::init_model_from_dir(
                net_equal_symbol,
                dir_path,
                "resnet18_equal_symbol_latest",
                "fp16"
        ) && isSuccessful;

        isSuccessful = CAS_OCR::init_model_from_dir(
                net_operator,
                dir_path,
                "resnet18_operator_latest",
                "fp16"
        ) && isSuccessful;

        isSuccessful = CAS_OCR::init_model_from_dir(
                net_digit,
                dir_path,
                "resnet34_digit_latest",
                "fp16"
        ) && isSuccessful;

        if (isSuccessful)
        {
            global_use_gpu = use_gpu;
            model_status = global_use_gpu ? CAS_MODEL_STATUS_LOADED_GPU : CAS_MODEL_STATUS_LOADED_CPU;
            is_init = true;
        }

        return isSuccessful;
    }

    bool init_model_from_dir(
            ncnn::Net &net,
            std::string dir_path,
            std::string name,
            std::string precision
    ) {
        if (!dir_path.empty() && dir_path.back() != '/' && dir_path.back() != '\\') {
            dir_path += "/";
        }

        std::string file_name_param = dir_path + name + "." + precision + ".param";
        std::string file_name_model = dir_path + name + "." + precision + ".bin";

        int ret = net.load_param(file_name_param.c_str());
        if (ret != 0) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    logcat_tag.c_str(),
                    "load_param failed: %s",
                    file_name_param.c_str()
            );
            return false;
        }

        ret = net.load_model(file_name_model.c_str());
        if (ret != 0) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    logcat_tag.c_str(),
                    "load_model failed: %s",
                    file_name_model.c_str()
            );
            return false;
        }

        return true;
    }

    CAS_MODEL_STATUS get_all_model_status()
    {
        return model_status;
    }

    void release_all_model()
    {
        net_equal_symbol.clear();
        net_operator.clear();
        net_digit.clear();
        is_init = false;
        model_status = CAS_MODEL_STATUS_NOT_LOADED;
    }

    bool is_vulkan_supported()
    {
        return ncnn::get_gpu_count() > 0;
    }
}