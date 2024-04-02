// HandleImg.cpp
// Created by Haomin Kong on 2024/2/9.
//

#include "HandleImg.h"

// 分割图像
// Python Version:src/utils/pic/spilt_img.py
cv::Mat splitImgByRatio(
        const cv::Mat &image,
        float startRatio,
        float endRatio
) {
    // 获取图像的宽度和高度
    const int height = image.rows;
    const int width = image.cols;

    // 计算水平方向上的裁剪范围
    if (startRatio > endRatio) {
        std::swap(startRatio, endRatio);
    }

    int horizontalStart = static_cast<int>(
            static_cast<float>(width) * startRatio
    );
    int horizontalEnd = static_cast<int>(
            static_cast<float>(width) * endRatio
    );
    if (endRatio >= 1) {
        horizontalEnd = width;
    }

    cv::Mat horizontalPart = image(
            cv::Rect(
                    horizontalStart, 0,
                    horizontalEnd - horizontalStart,
                    height
            )
    ).clone();

    return horizontalPart;
}
