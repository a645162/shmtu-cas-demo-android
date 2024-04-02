//
// Created by konghaomin on 2024/2/11.
// https://blog.csdn.net/matt45m/article/details/121853230

#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
cv::Mat cv_template;

void BitmapToMat2(JNIEnv *env, jobject& bitmap, cv::Mat& mat, jboolean needUnPremultiplyAlpha)
{
    AndroidBitmapInfo info;
    void *pixels = 0;
    cv::Mat &dst = mat;

    try {
        CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
        CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                  info.format == ANDROID_BITMAP_FORMAT_RGB_565);
        CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
        CV_Assert(pixels);
        dst.create(info.height, info.width, CV_8UC4);
        if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if (needUnPremultiplyAlpha) cvtColor(tmp, dst, cv::COLOR_mRGBA2RGBA);
            else tmp.copyTo(dst);
        } else {
//             info.format == ANDROID_BITMAP_FORMAT_RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            cvtColor(tmp, dst, cv::COLOR_BGR5652RGBA);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch (const cv::Exception &e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("org/opencv/core/CvException");
        if (!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nBitmapToMat}");
        return;
    }
}

void BitmapToMat(JNIEnv *env, jobject& bitmap, cv::Mat& mat) {
    BitmapToMat2(env, bitmap, mat, false);
}

void MatToBitmap2
        (JNIEnv *env, cv::Mat& mat, jobject& bitmap, jboolean needPremultiplyAlpha)
{
    AndroidBitmapInfo info;
    void *pixels = 0;
    cv::Mat &src = mat;

    try {
        CV_Assert(AndroidBitmap_getInfo(env, bitmap, &info) >= 0);
        CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                  info.format == ANDROID_BITMAP_FORMAT_RGB_565);
        CV_Assert(src.dims == 2 && info.height == (uint32_t) src.rows &&
                  info.width == (uint32_t) src.cols);
        CV_Assert(src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4);
        CV_Assert(AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0);
        CV_Assert(pixels);
        if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
        {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if (src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, cv::COLOR_GRAY2RGBA);
            }
            else if (src.type() == CV_8UC3)
            {
                cvtColor(src, tmp, cv::COLOR_RGB2RGBA);
            }
            else if (src.type() == CV_8UC4)
            {
                if (needPremultiplyAlpha)
                {
                    cvtColor(src, tmp, cv::COLOR_RGBA2mRGBA);
                }
                else{
                    src.copyTo(tmp);
                }
            }
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if (src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, cv::COLOR_GRAY2BGR565);
            }
            else if (src.type() == CV_8UC3)
            {
                cvtColor(src, tmp, cv::COLOR_RGB2BGR565);
            }
            else if (src.type() == CV_8UC4)
            {
                cvtColor(src, tmp, cv::COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }catch (const cv::Exception &e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("org/opencv/core/CvException");
        if (!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nMatToBitmap}");
        return;
    }
}

jobject generateBitmap(JNIEnv *env, uint32_t width, uint32_t height)
{

    jclass bitmapCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapFunction = env->GetStaticMethodID(bitmapCls,
                                                            "createBitmap",
                                                            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jstring configName = env->NewStringUTF("ARGB_8888");
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOfBitmapConfigFunction = env->GetStaticMethodID(
            bitmapConfigClass, "valueOf",
            "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");

    jobject bitmapConfig = env->CallStaticObjectMethod(bitmapConfigClass,
                                                       valueOfBitmapConfigFunction, configName);

    jobject newBitmap = env->CallStaticObjectMethod(bitmapCls,createBitmapFunction,
                                                    width,
                                                    height, bitmapConfig);
    return newBitmap;
}


void MatToBitmap(JNIEnv *env, cv::Mat& mat, jobject& bitmap)
{
    MatToBitmap2(env, mat, bitmap, false);
}


extern "C" JNIEXPORT jobject JNICALL
Java_com_dashu_dashuaiip_DaShuAPI_grayModel(JNIEnv *env,jobject, jobject image)
{

    cv::Mat cv_src,cv_gray;
    //bitmap转化成mat
    BitmapToMat(env,image,cv_src);
    cv::cvtColor(cv_src,cv_gray,cv::COLOR_BGRA2GRAY);

    MatToBitmap(env,cv_gray,image);
    return image;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_dashu_dashuaiip_DaShuAPI_readAssetsImage(JNIEnv *env,jobject)
{
    jobject dst = generateBitmap(env,cv_template.cols,cv_template.rows);
    MatToBitmap(env,cv_template,dst);
    return dst;
}

extern "C"
JNIEXPORT int JNICALL
Java_com_dashu_dashuaiip_DaShuAPI_sendTemplate(JNIEnv *env, jobject instance, jintArray pix_, jint w, jint h)
{
jint *pix = env->GetIntArrayElements(pix_, NULL);
if (pix == NULL)
{
return -1;
}

//将c++图片转成Opencv图片
cv::Mat cv_temp(h, w, CV_8UC4, (unsigned char *) pix);

if(cv_temp.empty())
{
return -2;
}

cv::cvtColor(cv_temp,cv_template,cv::COLOR_BGRA2BGR);

return 0;
}

