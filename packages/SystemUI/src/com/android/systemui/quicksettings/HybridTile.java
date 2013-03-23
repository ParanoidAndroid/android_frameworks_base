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

    private static final String PARANOID_PREFERENCES_PKG = "com.paranoid.preferences"
    private static final String STOCK_COLORS = "NULL|NULL|NULL|NULL|NULL";

    private String mPackagename;
    private String mSourceDir;
    private String mStatus;
    private String mColor = STOCK_COLORS;

    public HybridTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mLabel = context.getString(R.string.quick_settings_hybrid_label);
        mTileLayout = R.layout.quick_settings_tile_hybrid;

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent("android.intent.action.MAIN");
                intent.putExtra("package", mPackagename);
                intent.putExtra("appname", mLabel);
                intent.putExtra("filename", mSourceDir);
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setComponent(new ComponentName("com.paranoid.preferences", 
                        "com.paranoid.preferences.hybrid.ViewPagerActivity"));
                mContext.startActivity(intent);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = mContext.getPackageManager()
                        .getLaunchIntentForPackage(PARANOID_PREFERENCES_PKG);
                mContext.startActivity(intent);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.FOREGROUND_APP), this);
    }

    @Override
    public void updateResources() {
        mPackagename = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.FOREGROUND_APP);

        PackageManager pm = mContext.getPackageManager();

        try {
            PackageInfo foregroundAppPackageInfo = pm.
                    getPackageInfo(mPackagename, 0);
            mLabel = foregroundAppPackageInfo.applicationInfo.
                    loadLabel(pm).toString();

            ExtendedPropertiesUtils.refreshProperties();
            ApplicationInfo appInfo = ExtendedPropertiesUtils.
                    getAppInfoFromPackageName(mPackagename);
            mSourceDir = appInfo.sourceDir;

            mStatus = String.valueOf(ExtendedPropertiesUtils.getActualProperty(mPackagename +
                    ExtendedPropertiesUtils.PARANOID_DPI_SUFFIX)) + " DPI / " +
                    String.valueOf(ExtendedPropertiesUtils.getActualProperty(mPackagename +
                    ExtendedPropertiesUtils.PARANOID_LAYOUT_SUFFIX)) + "p";

            mColor = ExtendedPropertiesUtils.getProperty(mPackagename +
                    ExtendedPropertiesUtils.PARANOID_COLORS_SUFFIX, STOCK_COLORS);

        } catch(NameNotFoundException Exception) {}

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
        super.onPostCreate();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
