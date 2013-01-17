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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

import com.android.systemui.R;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PiePolicy {

    private static Context mContext;
    private static int mBatteryLevel = 0;

    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            mBatteryLevel = intent.getIntExtra("level", 0);
        }
    };

    public PiePolicy(Context context) {
        mContext = context;
        mContext.registerReceiver(mBatteryInfoReceiver, 
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public static String getWifiSsid() {
        String ssid = mContext.getString(R.string.quick_settings_wifi_not_connected);
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) mContext
                    .getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            return NetworkController.huntForSsid(wifiManager, connectionInfo);
        }
        return ssid.toUpperCase();
    }

    public static String getNetworkProvider() {
        String operatorName = mContext.getString(R.string.quick_settings_wifi_no_network);
        TelephonyManager telephonyManager = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        operatorName = telephonyManager.getNetworkOperatorName();
        if(operatorName == null) {
            operatorName = telephonyManager.getSimOperatorName();
        }
        return operatorName.toUpperCase();
    }

    public static String getSimpleDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("ccc, FF MMM yyyy");
        String date = sdf.format(new Date());
        return date.toUpperCase();
    }

    public static int getBatteryLevel() {
        return mBatteryLevel;
    }

    public static String getBatteryLevelReadable() {
        return mContext.getString(R.string.accessibility_battery_level,
                mBatteryLevel).toUpperCase();
    }
}
