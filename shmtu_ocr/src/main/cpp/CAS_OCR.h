#pragma once
#define NCNN_SUPPORT_VULKAN

#include <opencv2/core.hpp>

namespace CAS_OCR
{
    enum CAS_EXPR_Operator
    {
        CAS_EXPR_Operator_Add = 0,
        CAS_EXPR_Operator_Add_CHS = 1,
        CAS_EXPR_Operator_Sub = 2,
        CAS_EXPR_Operator_Sub_CHS = 3,
        CAS_EXPR_Operator_Mul = 4,
        CAS_EXPR_Operator_Mul_CHS = 5
    };

    enum CAS_EXPR_EQUAL_SYMBOL
    {
        CAS_EXPR_EQUAL_SYMBOL_CHS = 0,
        CAS_EXPR_EQUAL_SYMBOL_SYMBOL = 1
    };

#ifndef __ANDROID__
    bool init_model(std::string dir_path = "", std::string type_name = "fp32");
#endif

    std::tuple<int, std::string, int, int, int, int>
    predict_validate_code(const cv::Mat& image_input, bool use_gpu = true);

#ifndef __ANDROID__
    std::tuple<int, std::string, int, int, int, int>
	predict_validate_code(const std::string& image_path, bool use_gpu = true);
#endif

    void release_model();

    bool is_model_init();

#ifdef NCNN_SUPPORT_VULKAN

    void vulkan_is_share_memory();

    enum VULKAN_DEVICE_TYPE
    {
        VULKAN_DEVICE_TYPE_DISCRETE_GPU = 0,
        VULKAN_DEVICE_TYPE_INTEGRATED_GPU = 1,
        VULKAN_DEVICE_TYPE_VIRTUAL_GPU = 2,
        VULKAN_DEVICE_TYPE_CPU = 3
    };

    class NCNN_Device_Info
    {
    public:
        NCNN_Device_Info() = default;

        NCNN_Device_Info(
                int device_index,
                std::string device_name,
                uint32_t api_version,
                uint32_t device_memory,
                VULKAN_DEVICE_TYPE device_type
        );

        void print_info() const;

        int device_index;
        std::string device_name;
        uint32_t api_version;
        uint32_t device_memory;
        VULKAN_DEVICE_TYPE device_type;
    };

    int get_gpu_count();

    bool is_support_vulkan();

    int get_default_gpu_index();

    NCNN_Device_Info get_gpu_info(int gpu_index = get_default_gpu_index());

    std::vector<NCNN_Device_Info> get_all_gpu_info();

    void print_device_info(const NCNN_Device_Info& device_info);

    void set_model_gpu_support(bool use_gpu);
#endif
}
