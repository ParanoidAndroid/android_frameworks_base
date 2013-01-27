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

package android.hybrid;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.CompatibilityInfo;
import android.util.Log;
import android.view.Display;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ExtendedPropertiesUtils {

    private static final String TAG = "ExtendedPropertiesUtils";

    public static ActivityThread mMainThread;
    public static Context mContext;
    public static PackageManager mPackageManager;    
    public static Display mDisplay;
    public static List<PackageInfo> mPackageList;

    /**
     * Whether if context is already set for current hook
     */
    public static boolean isInitialized() {
        return mContext != null;
    }

    /**
     * Returns an {@link ApplicationInfo}, with the given path.
     *
     * @param  path  the apk path
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPath(String path) {
        if(isInitialized()) {
            for(int i=0; mPackageList != null &&
                    i < mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null &&
                        p.applicationInfo.sourceDir.equals(path)) {
                    return p.applicationInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns an {@link ApplicationInfo}, with the given package name.
     *
     * @param  packageName  the application package name
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPackageName(
            String packageName) {
        if(isInitialized()) {
            for(int i=0; mPackageList != null &&
                    i<mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null && p.applicationInfo
                        .packageName.equals(packageName)) {
                    return p.applicationInfo;
                }
            }
        }
        return null;
    }
    
    /**
     * Returns an {@link ApplicationInfo}, with the given PID.
     *
     * @param  pid  the application PID
     * @return application info
     */
    public static ApplicationInfo getAppInfoFromPid(int pid) {
        if (isInitialized()) {
            List mProcessList = ((ActivityManager) mContext
                    .getSystemService(Context.ACTIVITY_SERVICE))
                    .getRunningAppProcesses();
            Iterator mProcessListIt = mProcessList.iterator();
            while(mProcessListIt.hasNext()) {
                ActivityManager.RunningAppProcessInfo mAppInfo = 
                        (ActivityManager.RunningAppProcessInfo)mProcessListIt.next();
                if(mAppInfo.pid == pid) {
                    return getAppInfoFromPackageName(mAppInfo.processName);
                }
            }
        }
        return null;
    }
}
