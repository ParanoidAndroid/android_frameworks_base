/*
 * Copyright (C) 2013 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.hybrid;

import android.content.Context;
import com.android.os.Handler;
import com.android.internal.hybrid.IHybirdService;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

public class HybridService extends IHybirdService.Stub {

    private static final String TAG = "HybridService";

    public HybridService (Context context) {
    super();
    mContext = context;
    Log.i(TAG, "Spawned hybrid thread")
    }

}
