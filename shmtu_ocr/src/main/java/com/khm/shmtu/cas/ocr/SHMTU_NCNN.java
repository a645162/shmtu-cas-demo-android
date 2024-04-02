// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.khm.shmtu.cas.ocr;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import java.util.ArrayList;

public class SHMTU_NCNN {
    private native boolean Init(AssetManager mgr);

    private native ArrayList<String> Detect(Bitmap bitmap, boolean use_gpu);

    static {
        System.loadLibrary("shmtu_cas_ncnn");
    }

    private static boolean isInit = false;

    public boolean InitModel(AssetManager mgr) {
        if (!isInit) {
            isInit = Init(mgr);
            return isInit;
        } else {
            return true;
        }
    }

    public Object[] predict_validate_code(Bitmap bitmap, boolean use_gpu) {
        if (!isInit) {
            return null;
        }
        ArrayList<String> result = Detect(bitmap, use_gpu);
        if (result == null || result.size() != 6) {
            return null;
        } else {
            Object[] tuples_result = new Object[]{0, "", 0, 0, 0, 0};

            tuples_result[0] = Integer.parseInt(result.get(0));

            tuples_result[1] = result.get(1);

            tuples_result[2] = Integer.parseInt(result.get(2));
            tuples_result[3] = Integer.parseInt(result.get(3));
            tuples_result[4] = Integer.parseInt(result.get(4));
            tuples_result[5] = Integer.parseInt(result.get(5));

            return tuples_result;
        }

    }
}
