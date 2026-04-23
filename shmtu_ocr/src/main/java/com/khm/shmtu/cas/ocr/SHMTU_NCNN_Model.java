package com.khm.shmtu.cas.ocr;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;

public class SHMTU_NCNN_Model {
    public enum ModelSource {
        GITEE,
        GITHUB
    }

    public interface LoadCallback {
        void onSuccess();
        void onError(String error);
    }

    public static final String URL_MODEL_PREFIX_GITEE
            = "https://gitee.com/a645162/shmtu-cas-ocr-model/releases/download/v1.0-NCNN/";
    public static final String URL_MODEL_PREFIX_GITHUB
            = "https://github.com/a645162/shmtu-cas-ocr-model/releases/download/v1.0-NCNN/";

    public static final String FILE_NAME_MODEL_EQUAL_SYMBOL_BIN
            = "resnet18_equal_symbol_latest.fp16.bin";
    public static final String FILE_NAME_MODEL_EQUAL_SYMBOL_PARAM
            = "resnet18_equal_symbol_latest.fp16.param";
    public static final String FILE_NAME_MODEL_OPERATOR_BIN
            = "resnet18_operator_latest.fp16.bin";
    public static final String FILE_NAME_MODEL_OPERATOR_PARAM
            = "resnet18_operator_latest.fp16.param";
    public static final String FILE_NAME_MODEL_DIGIT_BIN
            = "resnet34_digit_latest.fp16.bin";
    public static final String FILE_NAME_MODEL_DIGIT_PARAM
            = "resnet34_digit_latest.fp16.param";

    public static final String[] MODEL_FILES = {
            FILE_NAME_MODEL_EQUAL_SYMBOL_BIN,
            FILE_NAME_MODEL_EQUAL_SYMBOL_PARAM,
            FILE_NAME_MODEL_OPERATOR_BIN,
            FILE_NAME_MODEL_OPERATOR_PARAM,
            FILE_NAME_MODEL_DIGIT_BIN,
            FILE_NAME_MODEL_DIGIT_PARAM
    };

    public static String getModelDir(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/ncnn_model/";
    }

    public static String getModelFilePath(Context context, String fileName) {
        return getModelDir(context) + fileName;
    }

    public static String[] buildModelUrls(ModelSource source) {
        String prefix = (source == ModelSource.GITHUB) ? URL_MODEL_PREFIX_GITHUB : URL_MODEL_PREFIX_GITEE;
        String[] urls = new String[MODEL_FILES.length];
        for (int i = 0; i < MODEL_FILES.length; i++) {
            urls[i] = prefix + MODEL_FILES[i];
        }
        return urls;
    }

    public static boolean isModelBuiltIn(AssetManager assetManager) {
        try {
            String[] files = assetManager.list("");
            if (files == null) return false;
            for (String fileName : MODEL_FILES) {
                boolean found = false;
                for (String asset : files) {
                    if (asset.equals(fileName)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isModelDownloaded(Context context) {
        String modelDir = getModelDir(context);
        for (String fileName : MODEL_FILES) {
            File file = new File(modelDir + fileName);
            if (!file.exists() || file.length() == 0) {
                return false;
            }
        }
        return true;
    }

    public static String getDownloadedModelInfo(Context context) {
        String modelDir = getModelDir(context);
        StringBuilder info = new StringBuilder();
        for (String fileName : MODEL_FILES) {
            File file = new File(modelDir + fileName);
            long size = file.exists() ? file.length() : 0;
            info.append(fileName).append(": ").append(size).append(" bytes\n");
        }
        return info.toString();
    }

    public static void loadModelFromAssetsAsync(SHMTU_NCNN ncnn, AssetManager assetManager, boolean useGpu, LoadCallback callback) {
        new Thread(() -> {
            try {
                boolean success = ncnn.InitModel(assetManager, useGpu);
                if (success) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to load model from assets");
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void loadModelFromDirAsync(SHMTU_NCNN ncnn, Context context, boolean useGpu, LoadCallback callback) {
        new Thread(() -> {
            try {
                String modelDir = getModelDir(context);

                for (String fileName : MODEL_FILES) {
                    File file = new File(modelDir + fileName);
                    if (!file.exists() || file.length() == 0) {
                        callback.onError("Model file missing or empty: " + fileName);
                        return;
                    }
                }

                boolean success = ncnn.InitModelFromDir(modelDir, useGpu);
                if (success) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to load model from " + modelDir);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}