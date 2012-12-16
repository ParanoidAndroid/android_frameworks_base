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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BluetoothController;

public class BluetoothTile extends QuickSettingsTile implements BluetoothStateChangeCallback{

    private boolean enabled;
    private boolean connected;
    private BluetoothAdapter mBluetoothAdapter;

    public BluetoothTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        enabled = mBluetoothAdapter.isEnabled();
        connected = mBluetoothAdapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED;

        onClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                if(enabled){
                    mBluetoothAdapter.disable();
                }else{
                    mBluetoothAdapter.enable();
                }
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    enabled = (state == BluetoothAdapter.STATE_ON);
                }

                if(intent.getAction().equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)){
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                            BluetoothAdapter.STATE_DISCONNECTED);
                    connected = (state == BluetoothAdapter.STATE_CONNECTED);
                }
                applyBluetoothChanges();
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        mIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    }

    void checkBluetoothState() {
        enabled = mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON;
        connected = mBluetoothAdapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED;
    }

    private void applyBluetoothChanges(){
        if(enabled){
            if(connected){
                mDrawable = R.drawable.ic_qs_bluetooth_on;
            }else{
                mDrawable = R.drawable.ic_qs_bluetooth_not_connected;
            }
            mLabel = mContext.getString(R.string.quick_settings_bluetooth_label);
        }else{
            mDrawable = R.drawable.ic_qs_bluetooth_off;
            mLabel = mContext.getString(R.string.quick_settings_bluetooth_off_label);
        }
        updateQuickSettings();
    }

    @Override
    void onPostCreate() {
        BluetoothController controller = new BluetoothController(mContext);
        controller.addStateChangedCallback(this);
        checkBluetoothState();
        applyBluetoothChanges();
        super.onPostCreate();
    }

    @Override
    public void onBluetoothStateChange(boolean on) {
        this.enabled = on;
        applyBluetoothChanges();
    }

}
