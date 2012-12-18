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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class NfcTile extends QuickSettingsTile {

    private NfcAdapter mAdapter;

    public NfcTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateTileState();

        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeNfcState();
            }
        };
        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Nfc adapter needs a custom strategy because NFC service is started
                // after tiles.
                mAdapter = NfcAdapter.getDefaultAdapter(context);
                updateTileState();
                updateQuickSettings();
            }
        };
        mIntentFilter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
    }

    private void updateTileState() {
        boolean enabled = isNfcEnabled();
        if(enabled) {
            mDrawable = R.drawable.ic_qs_nfc_on;
            mLabel = mContext.getString(R.string.quick_settings_nfc_label);
        } else {
            mDrawable = R.drawable.ic_qs_nfc_off;
            mLabel = mContext.getString(R.string.quick_settings_nfc_off_label);
        }
    }

    private void changeNfcState() {
        if(mAdapter == null) {
            mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        }

        AsyncTask.execute(new Runnable() {
            public void run() {
                if (isNfcEnabled()) {
                    mAdapter.disable();
                } else {
                    mAdapter.enable();
                }
            }
        });
    }

    private boolean isNfcEnabled() {
        if (mAdapter == null) {
            return false;
        }
        return mAdapter.isEnabled();
    }
}
