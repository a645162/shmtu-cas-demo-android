//
// Created by konghaomin on 2024/2/9.
//

#ifndef SHMTU_CAS_OCR_DEMO_HANDLE_IMG_H
#define SHMTU_CAS_OCR_DEMO_HANDLE_IMG_H

#include <opencv2/opencv.hpp>

cv::Mat splitImgByRatio(
        const cv::Mat &image,
        float startRatio = 0.7f,
        float endRatio = 1
);

#endif //SHMTU_CAS_OCR_DEMO_HANDLE_IMG_H
