/*
 * Copyright (C) 2012 CyanogenMod Project
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

package com.android.systemui.statusbar.quicksettings;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.internal.view.RotationPolicy;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class AutoRotateTile extends QuickSettingsTile {

    private static final String TAG = "AutoRotateButton";

    public AutoRotateTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        new AutoRotationObserver(handler);

        onClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                RotationPolicy.setRotationLock(mContext, getAutoRotation());
            }
        };

        onLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }
        };
    }

    void applyAutoRotationChanges() {
        if(!getAutoRotation()){
            mDrawable = R.drawable.ic_qs_rotation_locked;
            mLabel = mContext.getString(R.string.quick_settings_rotation_locked_label);
        }else{
            mDrawable = R.drawable.ic_qs_auto_rotate;
            mLabel = mContext.getString(R.string.quick_settings_rotation_unlocked_label);
        }
        updateQuickSettings();
    }

    @Override
    void onPostCreate() {
        applyAutoRotationChanges();
        super.onPostCreate();
    }

    private boolean getAutoRotation() {
        return !RotationPolicy.isRotationLocked(mContext);
    }

    private class AutoRotationObserver extends ContentObserver {
        public AutoRotationObserver(Handler handler) {
            super(handler);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            applyAutoRotationChanges();
        }
    }

}
