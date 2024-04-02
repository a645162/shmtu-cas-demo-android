// ReSharper disable CppClangTidyClangDiagnosticInvalidUtf8

#ifndef _WINDOWS
#if defined(_WIN32) || defined (_M_ARM) || defined (_M_ARM64)
#define _WINDOWS  // NOLINT(clang-diagnostic-reserved-macro-identifier)
#endif
#endif

#ifdef _WINDOWS
#pragma comment(linker, "/ignore:4099")
#pragma comment(linker, "/ignore:4793")
#endif

#include "CAS_OCR.h"

#include <iostream>
#include <cstdio>

// OpenCV
#include <opencv2/imgproc.hpp>
#ifndef __ANDROID__
#include <opencv2/imgcodecs.hpp>
#endif

// NCNN
#include <net.h>

#ifdef NCNN_SUPPORT_VULKAN
#include <gpu.h>
#endif // NCNN_SUPPORT_VULKAN

// 分割图像
// Python Version:src/utils/pic/spilt_img.py
static auto split_img_by_ratio(
        const cv::Mat& image,
        float start_ratio = 0.7f,
        float end_ratio = 1.0f
) -> cv::Mat
{
    // 获取图像的宽度和高度
    const int height = image.rows;
    const int width = image.cols;

    // 计算水平方向上的裁剪范围
    if (start_ratio > end_ratio)
    {
        std::swap(start_ratio, end_ratio);
    }

    const int horizontal_start = static_cast<int>(
            static_cast<float>(width) * start_ratio
    );
    int horizontal_end = static_cast<int>(
            static_cast<float>(width) * end_ratio
    );
    if (end_ratio >= 1)
    {
        horizontal_end = width;
    }

    cv::Mat horizontal_part = image(
            cv::Rect(
                    horizontal_start, 0,
                    horizontal_end - horizontal_start,
                    height
            )
    ).clone();

    return horizontal_part;
}

namespace CAS_OCR
{
    bool is_init = false;
    bool global_use_gpu = false;

    ncnn::Net net_equal_symbol;
    ncnn::Net net_operator;
    ncnn::Net net_digit;

    constexpr float mean_values[3] =
            {123.675f, 116.28f, 103.53f};
    constexpr float norm_values[3] = {
            (1.0f / 58.395f),
            (1.0f / 57.12f),
            (1.0f / 57.375f)
    };

    // Python Version:src/config/config.py
    constexpr float equal_symbol_key_start = 0.7f;
    constexpr float equal_symbol_key_end = 1.0f;
    constexpr float key_point_symbol[3] = {0.25f, 0.58f, 0.75f};
    constexpr float key_point_chs[3] = {0.15f, 0.33f, 0.46f};
    constexpr int config_thresh = 200;

    bool path_check_windows_style(const std::string& dir_path)
    {
        bool windows_style = false;

        for (const char& c : dir_path)
        {
            if (c == '\\')
            {
                windows_style = true;
                break;
            }
        }

        return windows_style;
    }

    void path_ensure_slash(std::string& dir_path)
    {
        if (
                !dir_path.empty() &&
                dir_path.back() != '/' &&
                dir_path.back() != '\\'
                )
        {
            // 如果结尾不是斜杠或反斜杠，则添加斜杠
            dir_path +=
                    path_check_windows_style(dir_path) ? "\\" : "/";
        }
    }

    bool init_model_for_net(
            ncnn::Net& net,
            std::string dir_path,
            const std::string& name
    )
    {
        path_ensure_slash(dir_path);

        printf(
                "Loading model:%s\n",
                name.c_str()
        );

        const auto ret_param =
                net.load_param(
                        (dir_path + name + ".param").c_str()
                );
        const auto ret_model =
                net.load_model(
                        (dir_path + name + ".bin").c_str()
                );

        return ret_param == 0 && ret_model == 0;
    }

    bool already_set_opt = false;
    bool vulkan_share_memory = false;
    bool set_opt_before_init = false;


    // 加载模型
    bool init_model(std::string dir_path, std::string type_name)
    {
        if (is_init)
        {
            return true;
        }

        if (dir_path.empty())
        {
            const auto dir_path_default = "../../checkpoint/";
            dir_path = dir_path_default;
        }

#ifdef _DEBUG
        printf(
			"mean:[%.3f,%.3f,%.3f]\n",
			mean_values[0],
			mean_values[1],
			mean_values[2]
		);
		printf(
			"norm:[%.3f,%.3f,%.3f]\n",
			norm_values[0],
			norm_values[1],
			norm_values[2]
		);
#endif

        if (type_name.empty())
        {
            type_name = "fp32";
        }

        const std::string device_type_str =
                global_use_gpu ? "GPU" : "CPU";

        printf("Checkpoint Directory:%s\n", dir_path.c_str());
        printf("Target Device:%s\n", device_type_str.c_str());

        if (global_use_gpu)
        {
            const auto device_info =
                    get_gpu_info(get_default_gpu_index());

            printf("\tDevice Name:%s\n", device_info.device_name.c_str());
            printf("\tDevice Memory:%d MB\n", device_info.device_memory);
        }

        bool isSuccess = true;

        // 等号识别
        isSuccess = isSuccess && init_model_for_net(
                net_equal_symbol,
                dir_path,
                "resnet18_equal_symbol_latest." + type_name
        );

        // 运算符识别
        isSuccess = isSuccess && init_model_for_net(
                net_operator,
                dir_path,
                "resnet18_operator_latest." + type_name
        );

        // 数字识别
        isSuccess = isSuccess && init_model_for_net(
                net_digit,
                dir_path,
                "resnet34_digit_latest." + type_name
        );

        if (isSuccess)
        {
            set_opt_before_init = already_set_opt;

            is_init = true;
        }
        else
        {
            std::cerr << "Failed to load model." << '\n';
        }

        return is_init;
    }

    // 使用模型进行预测
    int predict_by_model(
            const ncnn::Net& net,
            const cv::Mat& input_image,
            const bool use_gpu
    )
    {
        cv::Mat image = input_image.clone();

        // 调整图像大小为模型的输入尺寸（假设为224x224）
        cv::resize(image, image, cv::Size(224, 224));

        // Check channel
        if (image.channels() != 3)
        {
            std::cerr << "Image channel is not 3." << '\n';
            return -1;
        }

        // 将图像数据转换为ncnn的Mat对象
        ncnn::Mat in =
                ncnn::Mat::from_pixels(image.data, ncnn::Mat::PIXEL_BGR, image.cols, image.rows);

        // 归一化，模型的输入需要进行归一化
        in.substract_mean_normalize(mean_values, norm_values);

        // 输入到网络中进行推理
        ncnn::Extractor ex = net.create_extractor();

#ifdef NCNN_SUPPORT_VULKAN
        // Set GPU
        if (vulkan_share_memory && set_opt_before_init)
        {
            // 安卓平台GPU与CPU共享内存，因此可以通过这样来设置是用什么
            // 其他平台模型已经加载到显卡中了，这个Function并不会生效
            ex.set_vulkan_compute(use_gpu && global_use_gpu);
        }
        // ex.set_vulkan_compute() is no-op, please set net.opt.use_vulkan_compute=true/false before net.load_param()
        // If you want to disable vulkan for only some layer, see https://github.com/Tencent/ncnn/wiki/layer-feat-mask
#endif

        ex.input("input", in);
        ncnn::Mat out;
        ex.extract("output", out);
        const int output_count = out.w;

        // printf("out.w:%d\n", output_count);

        int max_index = 0;
        for (int j = 0; j < output_count; ++j)
        {
            if (out[j] > out[max_index])
            {
                max_index = j;
            }
        }

        return max_index;
    }

    int get_operator_type_by_int(const int type)
    {
        switch (type)
        {
            case 0:
            case 1:
                return CAS_EXPR_Operator_Add;
            case 2:
            case 3:
                return CAS_EXPR_Operator_Sub;
            case 4:
            case 5:
                return CAS_EXPR_Operator_Mul;
            default:
                return CAS_EXPR_Operator_Add;
        }
    }

    std::string get_operator_str_by_int(const int type)
    {
        switch (type)
        {
            case 0:
            case 1:
                return "+";
            case 2:
            case 3:
                return "-";
            case 4:
            case 5:
                return "*";
            default:
                return "";
        }
    }

    int calculate_operator(
            const int left, const int right,
            int operator_type
    )
    {
        operator_type = get_operator_type_by_int(operator_type);
        switch (operator_type)
        {
            case CAS_EXPR_Operator_Add:
                return left + right;
            case CAS_EXPR_Operator_Sub:
                return left - right;
            case CAS_EXPR_Operator_Mul:
                return left * right;
            default:
                return 0;
        }
    }

    // Python Version:src/classify/predict/predict_file.py
    std::tuple<int, std::string, int, int, int, int>
    predict_validate_code(
            const cv::Mat& image_input,
            bool use_gpu
    )
    {
        cv::Mat image_gray;
        cv::cvtColor(image_input, image_gray, cv::COLOR_BGR2GRAY);
        cv::threshold(image_gray, image_gray, config_thresh, 255, cv::THRESH_BINARY);

        cv::Mat image(image_gray.size(), CV_8UC3);
        cv::merge(std::vector<cv::Mat>{image_gray, image_gray, image_gray}, image);

        // 分割图像
        const auto image_equal_symbol =
                split_img_by_ratio(image, equal_symbol_key_start, equal_symbol_key_end);

        const auto predicted_equal_symbol =
                predict_by_model(
                        net_equal_symbol,
                        image_equal_symbol,
                        use_gpu
                );
        // printf("predicted_equal_symbol:%d\n", predicted_equal_symbol);

        const float* key_point;
        if (predicted_equal_symbol == CAS_EXPR_EQUAL_SYMBOL_CHS)
        {
            // CHS
            key_point = key_point_chs;
        }
        else
        {
            // Symbol
            key_point = key_point_symbol;
        }

        // Split image
        const auto image_digit_1 =
                split_img_by_ratio(
                        image,
                        0,
                        *(key_point + 0)
                );

        const auto img_operator =
                split_img_by_ratio(
                        image,
                        *(key_point + 0),
                        *(key_point + 1)
                );

        const auto image_digit_2 =
                split_img_by_ratio(
                        image,
                        *(key_point + 1),
                        *(key_point + 2)
                );

        // Predict
        const auto predicted_operator =
                predict_by_model(
                        net_operator,
                        img_operator,
                        use_gpu
                );
        // printf("predicted_operator:%d\n", predicted_operator);

        const auto predicted_digit_1 =
                predict_by_model(
                        net_digit,
                        image_digit_1,
                        use_gpu
                );

        const auto predicted_digit_2 =
                predict_by_model(
                        net_digit,
                        image_digit_2,
                        use_gpu
                );

        const int result = calculate_operator(
                predicted_digit_1,
                predicted_digit_2,
                predicted_operator
        );

        const std::string expr =
                std::to_string(predicted_digit_1)
                + " "
                + get_operator_str_by_int(predicted_operator)
                + " "
                + std::to_string(predicted_digit_2)
                + " = "
                + std::to_string(result);

        return std::make_tuple(
                result,
                expr,
                predicted_equal_symbol,
                predicted_operator,
                predicted_digit_1,
                predicted_digit_2
        );
    }

#ifndef __ANDROID__
    std::tuple<int, std::string, int, int, int, int>
	predict_validate_code(
		const std::string& image_path,
		bool use_gpu
	)
	{
		const cv::Mat image = cv::imread(image_path);
		if (image.empty())
		{
			std::cerr << "Failed to read image." << std::endl;
			return std::make_tuple(0, "", 0, 0, 0, 0);
		}
		return predict_validate_code(image, use_gpu);
	}
#endif

    void release_model()
    {
        net_equal_symbol.clear();
        net_operator.clear();
        net_digit.clear();
        is_init = false;
    }

    bool is_model_init()
    {
        return is_init;
    }

#ifdef NCNN_SUPPORT_VULKAN

    void vulkan_is_share_memory()
    {
        vulkan_share_memory = true;
    }

    // Must before net.load_param
    void set_model_opt_gpu(ncnn::Net& net, const bool use_gpu)
    {
        ncnn::Option& opt = net.opt;

        global_use_gpu = use_gpu && get_gpu_count() > 0;
        opt.use_vulkan_compute = global_use_gpu;
    }

    void set_model_gpu_support(bool use_gpu)
    {
        if (is_init)
        {
            return;
        }

        already_set_opt = true;

        if (use_gpu && get_gpu_count() == 0)
        {
            std::cerr << "Try to use GPU, but No GPU device."
                      << std::endl;
            use_gpu = false;
        }

#ifdef __ANDROID__
        // 在 Android 平台上编译
        vulkan_is_share_memory();
#endif

        set_model_opt_gpu(net_equal_symbol, use_gpu);
        set_model_opt_gpu(net_operator, use_gpu);
        set_model_opt_gpu(net_digit, use_gpu);
    }

    NCNN_Device_Info::NCNN_Device_Info(
            int device_index,
            std::string device_name,
            uint32_t api_version,
            const uint32_t device_memory,
            const VULKAN_DEVICE_TYPE device_type
    ) :
            device_index(device_index),
            device_name(std::move(device_name)),
            api_version(api_version),
            device_memory(device_memory),
            device_type(device_type)
    {
    }

    void NCNN_Device_Info::print_info() const
    {
        print_device_info(*this);
    }

    int get_gpu_count()
    {
        return ncnn::get_gpu_count();
    }

    bool is_support_vulkan()
    {
        return get_gpu_count() > 0;
    }

    int get_default_gpu_index()
    {
        return ncnn::get_default_gpu_index();
    }

    NCNN_Device_Info get_gpu_info(int gpu_index)
    {
        const ncnn::GpuInfo& gpu_info = ncnn::get_gpu_device(gpu_index)->info;

        return {
                gpu_index,
                gpu_info.device_name(),
                gpu_info.api_version(),
                gpu_info.max_shared_memory_size(),
                static_cast<VULKAN_DEVICE_TYPE>(gpu_info.type())
        };
    }

    std::vector<NCNN_Device_Info> get_all_gpu_info()
    {
        const int gpu_count = get_gpu_count();

        std::vector<NCNN_Device_Info> device_info_list;
        device_info_list.reserve(gpu_count);

        for (int i = 0; i < gpu_count; ++i)
        {
            device_info_list.push_back(get_gpu_info(i));
        }

        return device_info_list;
    }

    void print_device_info(const NCNN_Device_Info& device_info)
    {
        std::cout << "Device "
                  << device_info.device_index << ":" << "\n";
        std::cout << "  Device Name: "
                  << device_info.device_name << "\n";
        std::cout << "  Vulkan API Version: "
                  << device_info.api_version << "\n";
        std::cout << "  Device Memory: "
                  << device_info.device_memory << " MB" << "\n";
    }
#endif
}
