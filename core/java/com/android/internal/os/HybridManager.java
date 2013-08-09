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

package com.android.internal.os;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.util.Log;

import android.os.IHybridService;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.List;

public class HybridManager {
    private final IHybridService mService;

    public static PackageManager mPackageManager;

    public static List<PackageInfo> mPackageList;

    private Context mContext;

    public HybridManager (Context context, IHybridService service) {
        mContext = context;
        mService = service;
    }

    public void setContext(Context context) {
        try {
            mService.setPackage(context.getPackageName());
        } catch (RemoteException e) { 
        //properites will be from the last application
        }
    }

    public void setPackageName(String packageName) {
        try {
            mService.setPackage(packageName);
        } catch (RemoteException e) { 
        //properites will be from the last application
        }
    }

    public void testWrite() {
        try {
        mService.testWrite();
         } catch (RemoteException e ) {

        }
    }
    
}
