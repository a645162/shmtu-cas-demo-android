# 上海海事大学 统一认证平台 验证码 OCR Demo

## 使用到的开源库

- Tencent NCNN
- OpenCV

### NCNN

利用NCNN进行验证码识别的代码请详见VC++中的实现(CAS_OCR.cpp)。

具体路径为:VS解决方案目录\VC\NCNN_Digit\CAS_OCR.cpp

### OpenCV

为了缩小应用的体积，使用了轻量化编译的[opencv-mobile](https://github.com/nihui/opencv-mobile)库。
