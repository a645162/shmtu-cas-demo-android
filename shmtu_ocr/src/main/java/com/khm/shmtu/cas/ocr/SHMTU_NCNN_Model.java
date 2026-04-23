package com.khm.shmtu.cas.ocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SHMTU_NCNN_Model {
    public enum ModelSource {
        GITEE,
        GITHUB
    }

    public interface DownloadProgressListener {
        void onProgress(int progress);
        void onSuccess(File modelDir);
        void onError(String error);
    }

    public interface LoadCallback {
        void onSuccess();
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    public static void downloadModelAsync(Context context, ModelSource source, DownloadProgressListener listener) {
        downloadModelAsync(context, source, false, listener);
    }

    public static void downloadModelAsync(Context context, ModelSource source, boolean tryFallback, DownloadProgressListener listener) {
        executor.execute(() -> {
            try {
                String modelDir = getModelDir(context);
                File dir = new File(modelDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String[] urls = buildModelUrls(source);
                int totalFiles = MODEL_FILES.length;
                int downloaded = 0;

                for (int i = 0; i < totalFiles; i++) {
                    String fileName = MODEL_FILES[i];
                    String urlStr = urls[i];
                    File file = new File(modelDir + fileName);

                    if (file.exists()) {
                        downloaded++;
                        final int progress = (int) ((downloaded * 100.0) / totalFiles);
                        mainHandler.post(() -> listener.onProgress(progress));
                        continue;
                    }

                    boolean downloadSuccess = false;
                    String lastError = null;

                    for (int attempt = 0; attempt < (tryFallback ? 2 : 1); attempt++) {
                        try {
                            URL url = new URL(urlStr);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(30000);
                            conn.setReadTimeout(30000);
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                            try (InputStream in = conn.getInputStream();
                                 FileOutputStream out = new FileOutputStream(file)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, len);
                                }
                            }
                            conn.disconnect();
                            downloadSuccess = true;
                            break;
                        } catch (Exception e) {
                            lastError = e.getMessage();
                            if (attempt == 0 && tryFallback) {
                                ModelSource fallbackSource = (source == ModelSource.GITEE) ? ModelSource.GITHUB : ModelSource.GITEE;
                                urlStr = buildModelUrls(fallbackSource)[i];
                            }
                        }
                    }

                    if (!downloadSuccess) {
                        final String error = "Failed to download " + fileName + ": " + lastError;
                        mainHandler.post(() -> listener.onError(error));
                        return;
                    }

                    downloaded++;
                    final int progress = (int) ((downloaded * 100.0) / totalFiles);
                    mainHandler.post(() -> listener.onProgress(progress));
                }

                mainHandler.post(() -> listener.onSuccess(dir));
            } catch (Exception e) {
                mainHandler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }

    public static boolean isModelDownloaded(Context context) {
        String modelDir = getModelDir(context);
        for (String fileName : MODEL_FILES) {
            File file = new File(modelDir + fileName);
            if (!file.exists()) {
                return false;
            }
        }
        return true;
    }

    public static void loadModelFromAssetsAsync(SHMTU_NCNN ncnn, AssetManager assetManager, boolean useGpu, LoadCallback callback) {
        executor.execute(() -> {
            try {
                boolean success = ncnn.InitModel(assetManager, useGpu);
                if (success) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError("Failed to load model from assets"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void loadModelFromDirAsync(SHMTU_NCNN ncnn, Context context, boolean useGpu, LoadCallback callback) {
        executor.execute(() -> {
            try {
                String modelDir = getModelDir(context);
                boolean success = ncnn.InitModelFromDir(modelDir, useGpu);
                if (success) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError("Failed to load model from " + modelDir));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void loadModelAsync(SHMTU_NCNN ncnn, Context context, ModelSource source, boolean useGpu, LoadCallback callback) {
        loadModelAsync(ncnn, context, source, true, useGpu, callback);
    }

    public static void loadModelAsync(SHMTU_NCNN ncnn, Context context, ModelSource source, boolean tryFallback, boolean useGpu, LoadCallback callback) {
        if (isModelDownloaded(context)) {
            loadModelFromDirAsync(ncnn, context, useGpu, callback);
        } else {
            downloadModelAsync(context, source, tryFallback, new DownloadProgressListener() {
                @Override
                public void onProgress(int progress) {}

                @Override
                public void onSuccess(File modelDir) {
                    loadModelFromDirAsync(ncnn, context, useGpu, callback);
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> callback.onError(error));
                }
            });
        }
    }
}