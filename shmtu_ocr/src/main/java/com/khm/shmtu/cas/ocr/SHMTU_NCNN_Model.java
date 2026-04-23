package com.khm.shmtu.cas.ocr;

public class SHMTU_NCNN_Model {
    final public String URL_MODEL_PREFIX
            = "https://gitee.com/a645162/shmtu-cas-ocr-model/releases/download/v1.0-NCNN/";

    final public String FILE_NAME_MODEL_EQUAL_SYMBOL_BIN
            = "resnet18_equal_symbol_latest.fp16.bin";
    final public String FILE_NAME_MODEL_EQUAL_SYMBOL_PARAM
            = "resnet18_equal_symbol_latest.fp16.param";
    final public String FILE_NAME_MODEL_OPERATOR_BIN
            = "resnet18_operator_latest.fp16.bin";
    final public String FILE_NAME_MODEL_OPERATOR_PARAM
            = "resnet18_operator_latest.fp16.param";
    final public String FILE_NAME_MODEL_DIGIT_BIN
            = "resnet34_digit_latest.fp16.bin";
    final public String FILE_NAME_MODEL_DIGIT_PARAM
            = "resnet34_digit_latest.fp16.param";

    final public String URL_MODEL_EQUAL_SYMBOL_BIN
            = URL_MODEL_PREFIX + FILE_NAME_MODEL_EQUAL_SYMBOL_BIN;
    final public String URL_MODEL_EQUAL_SYMBOL_PARAM
            = URL_MODEL_PREFIX + FILE_NAME_MODEL_EQUAL_SYMBOL_PARAM;
    final public String URL_MODEL_OPERATOR_BIN
            = URL_MODEL_PREFIX + FILE_NAME_MODEL_OPERATOR_BIN;
    final public String URL_MODEL_OPERATOR_PARAM
            = URL_MODEL_PREFIX + FILE_NAME_MODEL_OPERATOR_PARAM;
    final public String URL_MODEL_DIGIT_BIN
            = URL_MODEL_PREFIX + FILE_NAME_MODEL_DIGIT_BIN;
    final public String URL_MODEL_DIGIT_PARAM
            = URL_MODEL_PREFIX + FILE_NAME_MODEL_DIGIT_PARAM;
}
