package com.android.internal.util.cm;

import java.io.File;
import java.io.IOException;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;

import com.android.internal.telephony.PhoneConstants;

public class QSUtils {

        public static boolean deviceSupportsUsbTether(Context ctx) {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            return (cm.getTetherableUsbRegexs().length != 0);
        }

        public static boolean deviceSupportsWifiDisplay(Context ctx) {
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            return (dm.getWifiDisplayStatus().getFeatureState() != WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE);
        }

        public static boolean deviceSupportsMobileData(Context ctx) {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
        }

        public static boolean deviceSupportsBluetooth() {
            return (BluetoothAdapter.getDefaultAdapter() != null);
        }

        public static boolean deviceSupportsNfc(Context ctx) {
            return NfcAdapter.getDefaultAdapter(ctx) != null;
        }

        public static boolean deviceSupportsLte(Context ctx) {
            final TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            return (tm.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE) || tm.getLteOnGsmMode() != 0;
        }

    public static int getTileTextColor(Context ctx) {
        int tileTextColor = Settings.System.getInt(ctx.getContentResolver(),
                Settings.System.QUICK_SETTINGS_TEXT_COLOR, 0xFFFFFFFF);
        return tileTextColor;
    }


}