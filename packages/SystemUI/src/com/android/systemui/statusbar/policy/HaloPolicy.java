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
import android.os.BatteryManager;

import com.android.systemui.R;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public class HaloPolicy implements BatteryStateChangeCallback, NetworkSignalChangedCallback{

    private OnClockChangedListener mClockChangedListener;

    private boolean mCharging = false;
    private boolean airPlaneMode;
    private boolean mConnected = true;
    private boolean mWifiConnected;
    private boolean mWifiNotConnected;
    private int mWifiSignalIconId;
    private int mSignalStrengthId;
    private int mBatteryLevel = 0;
    private String mLabel;
    private String mWifiLabel;
    private String signalContentDescription;
    private String dataContentDescription;

    private static Context mContext;
    private ConnectivityManager mCm;

    public HaloPolicy(Context context){
        mContext = context;

        mContext.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        NetworkController controller = new NetworkController(mContext);
        controller.addNetworkSignalChangedCallback(this);

        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public interface OnClockChangedListener {
        public abstract void onChange(String s);
    }

    private final BroadcastReceiver mClockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mClockChangedListener.onChange(getSimpleTime());
        }
    };

    public static String getSimpleTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm");
        String amPm = sdf.format(new Date());
        return amPm.toUpperCase();
    }

    public static String getDayofWeek() {
        SimpleDateFormat dayOfWeek = new SimpleDateFormat("ccc");
        return dayOfWeek.format(new Date()).toUpperCase();
    }

    public static String getDayOfMonth() {
        SimpleDateFormat dayOfMonth = new SimpleDateFormat("dd");
        return dayOfMonth.format(new Date()).toUpperCase();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        mBatteryLevel = level;
        mCharging = pluggedIn;
    }

    private BroadcastReceiver mBatteryReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            mBatteryLevel = intent.getIntExtra("level", 0);
            mCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        }
    };

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public boolean getBatteryStatus() {
        return mCharging;
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        airPlaneMode = enabled;
    }

    public boolean getAirplaneModeStatus(){
        if (!mCm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) && mWifiConnected) return false;
        return airPlaneMode;
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
                                    String wifiSignalContentDescriptionId, String description) {
        mWifiConnected = enabled && (wifiSignalIconId > 0) && (description != null);
        mWifiNotConnected = (wifiSignalIconId > 0) && (description == null);
        mWifiSignalIconId = wifiSignalIconId;
        mWifiLabel = description;
    }

    public boolean getWifiStatus(){
        if (mWifiConnected) return true;
        if (mWifiNotConnected) return false;

        return false;
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled,
                                          int mobileSignalIconId, String mobileSignalContentDescriptionId,
                                          int dataTypeIconId, String dataTypeContentDescriptionId,
                                          String description) {

        mSignalStrengthId = enabled && (mobileSignalIconId > 0)
                ? mobileSignalIconId
                : R.drawable.ic_qs_signal_no_signal;

        dataContentDescription = enabled && (dataTypeContentDescriptionId != null) && mCm.getMobileDataEnabled()
                ? dataTypeContentDescriptionId
                : mContext.getResources().getString(R.string.accessibility_no_data);
        mLabel = enabled
                ? description
                : mContext.getResources().getString(R.string.quick_settings_rssi_emergency_only);
    }

    public String getSignalStatus(){
        if (!mCm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) && mWifiNotConnected) return "";

        if (!mCm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE)) mSignalStrengthId = mWifiSignalIconId;

        switch (mSignalStrengthId) {
            case R.drawable.ic_qs_signal_0:
            case R.drawable.ic_qs_signal_full_0:
            case R.drawable.ic_qs_wifi_0:
                signalContentDescription = mContext.getResources().getString(R.string.halo_signal_bars_none);
                break;
            case R.drawable.ic_qs_signal_1:
            case R.drawable.ic_qs_signal_full_1:
            case R.drawable.ic_qs_wifi_1:
            case R.drawable.ic_qs_wifi_full_1:
                signalContentDescription = mContext.getResources().getString(R.string.halo_signal_bars_1);
                break;
            case R.drawable.ic_qs_signal_2:
            case R.drawable.ic_qs_signal_full_2:
            case R.drawable.ic_qs_wifi_2:
            case R.drawable.ic_qs_wifi_full_2:
                signalContentDescription = mContext.getResources().getString(R.string.halo_signal_bars_2);
                break;
            case R.drawable.ic_qs_signal_3:
            case R.drawable.ic_qs_signal_full_3:
            case R.drawable.ic_qs_wifi_3:
            case R.drawable.ic_qs_wifi_full_3:
                signalContentDescription = mContext.getResources().getString(R.string.halo_signal_bars_3);
                break;
            case R.drawable.ic_qs_signal_4:
            case R.drawable.ic_qs_signal_full_4:
            case R.drawable.ic_qs_wifi_4:
            case R.drawable.ic_qs_wifi_full_4:
                signalContentDescription = mContext.getResources().getString(R.string.halo_signal_bars_4);
                break;
            default:
                signalContentDescription = mContext.getResources().getString(R.string.halo_signal_bars_none);
        }

        return removeTrailingPeriod(signalContentDescription);
    }

    public String getDataStatus() {
        if (airPlaneMode) dataContentDescription = "";
        if (mWifiConnected) dataContentDescription = mContext.getResources().getString(R.string.halo_wifi_on);

        return removeTrailingPeriod(dataContentDescription);
    }

    public String getProvider() {
        if(mLabel.length()>10) mLabel = mLabel.substring(0,9) + "...";
        if (mCm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE)) return removeTrailingPeriod(mLabel);

        if(mWifiLabel != null && mWifiLabel.length()>10) mWifiLabel = mWifiLabel.substring(0,9) + "...";
        if (mWifiNotConnected || mWifiLabel == null) mWifiLabel = mContext.getResources().getString(R.string.halo_wifi_off);
        if (airPlaneMode && mWifiLabel == null) mWifiLabel = "- - -";

        return mWifiLabel;
    }

    public boolean getConnectionStatus() {
        mConnected = true;

        if (mSignalStrengthId == R.drawable.ic_qs_signal_0 || mSignalStrengthId == R.drawable.ic_qs_signal_1 ||
                mSignalStrengthId == R.drawable.ic_qs_signal_2 || mSignalStrengthId == R.drawable.ic_qs_signal_3 ||
                mSignalStrengthId == R.drawable.ic_qs_signal_4) mConnected = false;

        return mConnected;
    }

    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        string = string.trim();
        final int length = string.length();
        if (string.endsWith(".")) {
            string = string.substring(0, length - 1);
        }
        return string;
    }
}