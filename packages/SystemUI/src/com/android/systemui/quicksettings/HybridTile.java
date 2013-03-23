package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.ExtendedPropertiesUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class HybridTile extends QuickSettingsTile {

    private String mPackagename;
    private String mSourceDir;

    public HybridTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mDrawable = R.drawable.ic_qs_hybrid;
        mLabel = context.getString(R.string.quick_settings_hybrid_label);
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
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
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
            PackageInfo foregroundAppPackageInfo = pm.getPackageInfo(mPackagename, 0);
            mLabel = foregroundAppPackageInfo.applicationInfo.loadLabel(pm).toString();

            ApplicationInfo appInfo = ExtendedPropertiesUtils.getAppInfoFromPackageName(mPackagename);
            mSourceDir = appInfo.sourceDir;
        } catch(NameNotFoundException Exception) {}

        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {

    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
