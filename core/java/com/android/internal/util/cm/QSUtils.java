package com.android.internal.util.cm;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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

    	private static int[] Colors = new int[] {
        	android.R.color.holo_blue_dark,
        	android.R.color.holo_green_dark,
        	android.R.color.holo_orange_dark,
	        android.R.color.holo_purple,
    	    android.R.color.holo_red_dark
    	};	

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

		public static void setBackgroundStyle(Context ctx, View v) {
        String tileBg = Settings.System.getString(ctx.getContentResolver(),
                Settings.System.QUICK_SETTINGS_BACKGROUND_STYLE);
        final String packName = "com.android.systemui";
        if (tileBg != null) {
            if (!tileBg.equals("random")) {
                try {
                    v.setBackgroundColor(Integer.parseInt(tileBg));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            } else if (tileBg.equals("random")) {
                Random generator = new Random();
                int color = ctx.getResources().getColor(Colors[generator.nextInt(Colors.length)]);
                v.setBackgroundColor(color);
            }
        } else {
            try {
                PackageManager manager = ctx.getPackageManager();
                Resources mSystemUiResources = manager.getResourcesForApplication(packName);
                int resID = mSystemUiResources.getIdentifier("qs_tile_background", "drawable", packName);
                Drawable d = mSystemUiResources.getDrawable(resID);
                v.setBackground(d);
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static int getTileTextColor(Context ctx) {
        int tileTextColor = Settings.System.getInt(ctx.getContentResolver(),
                Settings.System.QUICK_SETTINGS_TEXT_COLOR, 0xFFFFFFFF);
        return tileTextColor;
    }


}
