project(shmtu_cas_ncnn)

cmake_minimum_required(VERSION 3.10)

set(LIB_3RD_PARTY_DIR ${CMAKE_SOURCE_DIR}/3rdparty)

set(OpenCV_DIR ${LIB_3RD_PARTY_DIR}/opencv-mobile-4.9.0-android/sdk/native/jni)
set(ncnn_DIR ${LIB_3RD_PARTY_DIR}/ncnn-20240102-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)

find_package(OpenCV REQUIRED)
find_package(ncnn REQUIRED)

#add_compile_options(-fexceptions)

add_library(
        shmtu_cas_ncnn SHARED

        ncnn_jni.cpp

        CAS_OCR.cpp
        CAS_OCR.h

        CAS_OCR_ANDROID.cpp
        CAS_OCR_ANDROID.h

        AndroidOpenCVUtils.cpp
        AndroidOpenCVUtils.h
)

target_link_libraries(shmtu_cas_ncnn ncnn)
target_link_libraries(shmtu_cas_ncnn ${OpenCV_LIBS} jnigraphics)
