/*
 * Copyright (C) 2012 ParanoidAndroid Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncStatusObserver;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class SyncTile extends QuickSettingsTile {

    private SyncStatusObserver mSyncObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            updateTileState();
            updateQuickSettings();
        }
    };

    public SyncTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateTileState();

        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentResolver.setMasterSyncAutomatically(!isMasterSyncEnabled());
            }
        };
        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SYNC_SETTINGS);
                return true;
            }
        };
        ContentResolver.addStatusChangeListener(ContentResolver
                .SYNC_OBSERVER_TYPE_SETTINGS, mSyncObserver);
    }

    private void updateTileState() {
        if(isMasterSyncEnabled()) {
            mDrawable = R.drawable.ic_qs_sync_on;
            mLabel = mContext.getString(R.string.quick_settings_sync_label);
        } else {
            mDrawable = R.drawable.ic_qs_sync_off;
            mLabel = mContext.getString(R.string.quick_settings_sync_off_label);
        }
    }

    private boolean isMasterSyncEnabled() {
        return ContentResolver.getMasterSyncAutomatically();
    }
}
