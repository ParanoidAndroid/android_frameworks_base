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

package android.os;

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

import android.os.IBinder;
import android.os.ServiceManager;

public class HybridManager {
    private static IHybridService mService;

    public static PackageManager mPackageManager;

    public static List<PackageInfo> mPackageList;

    private Context mContext;

    static {
        IBinder b = ServiceManager.getService(Context.HYBRID_SERVICE);
        mService = IHybridService.Stub.asInterface(b);
    }

    public HybridManager (Context context, IHybridService service) {
        mContext = context;
       // mService = service;
    }

    public void setContext(Context context) {
        try {
            mService.setPackage(context.getPackageName());
        } catch (RemoteException e) { 
        //properites will be from the last application
        }
    }

    public IHybridService getService() {
            return mService;
    }

    public static void setPackageName(String packageName) {
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

   public static boolean isActive() {
        //mService.isActive;
        return true;
    }

  public static boolean isTablet() { 
         // if (getProp(SYSTEMUI).dpi)
         //    return false;
         //   }
        return true;

    }

   public static boolean isExpanded() {
        // return getProp(packageName).expanded;
        return false;
    }

    public static void setExpanded(boolean expanded) {

    }

  public static int getLayout() {
        return 360;
       /* int layout ;
        try {
          layout = mService.getLayout();
         } catch (RemoteException e ) {
            // Shes dead captain 
        } 
        return layout; */
    }

  public static void setLayout(String layout) {
       // getProp(packageName).layout = layout;
    }

  public static int getDpi() {
        return 320;
       /* int dpi ;
        try {
          dpi = mService.getdpi();
         } catch (RemoteException e ) {
            // Shes dead captain 
        } 
        return dpi; */
    }

  public static void setDpi(String dpi) {
         // getProp(SYSTEMUI).dpi = dpi;
    }

  public static  int getDensity() {
        return 320;
    }

 public static int getScaledDensity() {
        return 320;
    }

 public static boolean getLarge() {
        return false;
    }


    
}
