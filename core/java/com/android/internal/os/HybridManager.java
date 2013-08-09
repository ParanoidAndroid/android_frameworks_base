package com.android.internal.os;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Math;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.util.Log;
import android.content.Context;


import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import android.os.IHybridService;
import 	android.os.RemoteException;

public class HybridManager {
    private final IHybridService mService;
    Context mContext;
    public static PackageManager mPackageManager;    
    public static Display mDisplay;
    public static List<PackageInfo> mPackageList;

    public HybridManager (Context context, IHybridService service) {
        mContext = context;
        mService = service;
    }


    public void setContext(Context context) {
        try {
        mService.setPackage(context.getPackageName());
        } catch (RemoteException e) { 

        }
    }

/**
     * Returns an {@link ApplicationInfo}, with the given path.
     *
     * @param  path  the apk path
     * @return application info
     */
    public ApplicationInfo getAppInfoFromPath(String path) {
            for(int i=0; mPackageList != null &&
                    i < mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null &&
                        p.applicationInfo.sourceDir.equals(path)) {
                    return p.applicationInfo;
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
    public ApplicationInfo getAppInfoFromPackageName(
            String packageName) {
            for(int i=0; mPackageList != null &&
                    i<mPackageList.size(); i++) {
                PackageInfo p = mPackageList.get(i);
                if (p.applicationInfo != null && p.applicationInfo
                        .packageName.equals(packageName)) {
                    return p.applicationInfo;
                }
            }
        return null;
    }

    public void testWrite() {
        try {
        mService.testWrite();
         } catch (RemoteException e ) {

        }
    }
    
    /**
     * Returns an {@link ApplicationInfo}, with the given PID.
     *
     * @param  pid  the application PID
     * @return application info
     */
    public ApplicationInfo getAppInfoFromPid(int pid) {
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
        return null;
    }
}
