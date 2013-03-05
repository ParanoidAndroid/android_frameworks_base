package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class DesktopModeTile extends QuickSettingsTile {

    public DesktopModeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.EXPANDED_DESKTOP_STATE, !getExpandedDesktopState() ? 1 : 0);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.EXPANDED_DESKTOP_STATE)
                , this);
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if(getExpandedDesktopState()){
            mDrawable = R.drawable.ic_qs_expanded_desktop;
            mLabel = mContext.getString(R.string.quick_settings_desktop_mode_default);
        }else{
            mDrawable = R.drawable.ic_qs_expanded_desktop_off;
            mLabel = mContext.getString(R.string.quick_settings_desktop_mode_full);
        }
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    private boolean getExpandedDesktopState() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1;
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
