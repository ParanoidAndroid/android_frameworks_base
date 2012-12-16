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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class VibrationModeTile extends QuickSettingsTile {

    private AudioManager mAudioManager;
    public final static String VIBRATION_STATE_CHANGED = "com.android.systemui.statusbar.quicksettings.VIBRATION_STATE_CHANGED";

    public VibrationModeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        onClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
                    //Vibrate -> Silent
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT){
                    //Silent -> Vibrate
                    vibrator.vibrate(300);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    Intent i = new Intent(VIBRATION_STATE_CHANGED);
                    mContext.sendBroadcast(i);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
                    if(mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON){
                        //Sound + Vibrate -> Sound
                        mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    }else{
                        //Sound -> Sound + Vibrate
                        vibrator.vibrate(300);
                        Intent i = new Intent(VIBRATION_STATE_CHANGED);
                        mContext.sendBroadcast(i);
                        mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    }
                    applyVibrationChanges();
                }
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                applyVibrationChanges();
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mIntentFilter.addAction(VIBRATION_STATE_CHANGED);
    }

    private void applyVibrationChanges(){
        int vibrateSetting = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && vibrateSetting == AudioManager.VIBRATE_SETTING_ON){
            //Sound + vibrate
            mDrawable = R.drawable.ic_qs_vibrate_on;
            mLabel = mContext.getString(R.string.quick_settings_vibration_on);
        }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
            //Vibrate
            mDrawable = R.drawable.ic_qs_vibrate_on;
            mLabel = mContext.getString(R.string.quick_settings_vibration_on);
        }else{
            //No vibration
            mDrawable = R.drawable.ic_qs_vibrate_off;
            mLabel = mContext.getString(R.string.quick_settings_vibration_off);
        }
        updateQuickSettings();
    }

}
