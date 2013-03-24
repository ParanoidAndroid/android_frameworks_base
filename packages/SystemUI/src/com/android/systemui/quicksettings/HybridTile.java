package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.ExtendedPropertiesUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

import java.math.BigInteger;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class HybridTile extends QuickSettingsTile {

    private static final String PARANOID_PREFERENCES_PKG = "com.paranoid.preferences";
    private static final String STOCK_COLORS = "NULL|NULL|NULL|NULL|NULL";
    
    private String mDefaultLabel;
    private String mPackageName;
    private String mSourceDir;
    private String mStatus;
    private String mColor = STOCK_COLORS;

    private PackageManager mPm;

    public HybridTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mDefaultLabel = context.getString(R.string.quick_settings_hybrid_label);
        mLabel = mDefaultLabel;
        mTileLayout = R.layout.quick_settings_tile_hybrid;
        mPm = context.getPackageManager();

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mLabel.equals(mDefaultLabel)) {
                    Intent intent = new Intent("android.intent.action.MAIN");
                    intent.putExtra("package", mPackageName);
                    intent.putExtra("appname", mLabel);
                    intent.putExtra("filename", mSourceDir);
                    intent.putExtra("apply", "autoLaunch");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setComponent(new ComponentName(PARANOID_PREFERENCES_PKG, 
                            PARANOID_PREFERENCES_PKG + ".hybrid.ViewPagerActivity"));
                    mContext.startActivity(intent);
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    Intent launchIntent = mPm.getLaunchIntentForPackage(
                            PARANOID_PREFERENCES_PKG); 
                    mContext.startActivity(launchIntent);
                } catch(NullPointerException e) { }
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.FOREGROUND_APP), this);
    }

    @Override
    public void updateResources() {
        mPackageName = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.FOREGROUND_APP);
        try {
            PackageInfo foregroundAppPackageInfo = mPm.
                    getPackageInfo(mPackageName, 0);
            mLabel = foregroundAppPackageInfo.applicationInfo.
                    loadLabel(mPm).toString();

            ExtendedPropertiesUtils.refreshProperties();
            ApplicationInfo appInfo = ExtendedPropertiesUtils.
                    getAppInfoFromPackageName(mPackageName);
            mSourceDir = appInfo.sourceDir;

            mStatus = String.valueOf(ExtendedPropertiesUtils.getActualProperty(mPackageName +
                    ExtendedPropertiesUtils.PARANOID_DPI_SUFFIX)) + " DPI / " +
                    String.valueOf(ExtendedPropertiesUtils.getActualProperty(mPackageName +
                    ExtendedPropertiesUtils.PARANOID_LAYOUT_SUFFIX)) + "P";

            mColor = ExtendedPropertiesUtils.getProperty(mPackageName +
                    ExtendedPropertiesUtils.PARANOID_COLORS_SUFFIX, STOCK_COLORS);

        } catch(Exception e) {
            mLabel = mDefaultLabel; // No app found with package name
        }

        updateQuickSettings();
    }


    @Override
    void updateQuickSettings() {

        TextView status = (TextView) mTile.findViewById(R.id.hybrid_status);
        status.setText(mStatus);

        TextView app = (TextView) mTile.findViewById(R.id.hybrid_app);
        app.setText(mLabel);

        // Color changes

        View[] swatches = new View[5];
        swatches[0] = mTile.findViewById(R.id.hybrid_swatch1);
        swatches[1] = mTile.findViewById(R.id.hybrid_swatch2);
        swatches[2] = mTile.findViewById(R.id.hybrid_swatch3);
        swatches[3] = mTile.findViewById(R.id.hybrid_swatch4);
        swatches[4] = mTile.findViewById(R.id.hybrid_swatch5);

        String[] colors = mColor.split(ExtendedPropertiesUtils.PARANOID_STRING_DELIMITER);
        if (colors.length == ExtendedPropertiesUtils.PARANOID_COLORS_COUNT) {
            for(int colorIndex = 0; colorIndex < ExtendedPropertiesUtils.PARANOID_COLORS_COUNT; colorIndex++) {
                swatches[colorIndex].setBackgroundDrawable(mContext.getResources().getDrawable(
                        R.drawable.color_picker).mutate());                    
                swatches[colorIndex].getBackground().setColorFilter(colors[colorIndex]
                        .toUpperCase().equals("NULL") ? ExtendedPropertiesUtils.PARANOID_COLORCODES_DEFAULTS[
                colorIndex] : new BigInteger(colors[colorIndex], 16).intValue(),
                        PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    @Override
    void onPostCreate() {
        updateResources();
        super.onPostCreate();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
