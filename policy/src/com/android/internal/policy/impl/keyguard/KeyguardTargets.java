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
package com.android.internal.policy.impl.keyguard;

import android.database.ContentObserver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.R;

public class KeyguardTargets extends LinearLayout {

    private static final int INNER_PADDING = 20;

    private KeyguardSecurityCallback mCallback;
    private PackageManager mPackageManager;
    private Context mContext;

    public KeyguardTargets(Context context) {
        this(context, null);
    }

    public KeyguardTargets(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mContext.getContentResolver().registerContentObserver(
            Settings.System.getUriFor(Settings.System.LOCKSCREEN_TARGETS), false, new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    updateTargets();
                }});
        mPackageManager = mContext.getPackageManager();
        updateTargets();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }

    private void updateTargets() {
        removeAllViews();
        String apps = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS);
        if(apps == null || apps.isEmpty()) return;
        final String[] targets = apps.split("\\|");
        for(int j = 0; j < targets.length; j++) {
            final String packageName = targets[j];
            ImageView i = new ImageView(mContext);
            // Target will be around 60% of a normal icon
            int dimens = Math.round(mContext.getResources()
                    .getDimensionPixelSize(R.dimen.app_icon_size) / 1.5f);
            LinearLayout.LayoutParams vp = 
                    new LinearLayout.LayoutParams(dimens, dimens);
            i.setLayoutParams(vp);
            Drawable img = null;
            try {
                img = mPackageManager.getApplicationIcon(packageName);
                i.setImageDrawable(img);
                i.setBackground(mContext.getResources().getDrawable(
                        R.drawable.list_selector_holo_dark));
                i.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            mContext.startActivity(mPackageManager
                                    .getLaunchIntentForPackage(packageName));
                            if(mCallback != null) mCallback.dismiss(false);
                        } catch(NullPointerException e) {
                            // No intent found for our package name?
                        }
                    }
                });
                addView(i);
                if(j+1 < targets.length) addSeparator();
            } catch(NameNotFoundException e) {
                // If no icon found, we skip target for avoiding blank
                // ImageViews
            }
        }
    }

    private void addSeparator() {
        View v = new View(mContext);
        LinearLayout.LayoutParams vp = 
                new LinearLayout.LayoutParams(INNER_PADDING, 0);
        v.setLayoutParams(vp);
        addView(v);
    }
}
