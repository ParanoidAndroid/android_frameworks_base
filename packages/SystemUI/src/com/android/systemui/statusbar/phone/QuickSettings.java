/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.statusbar.BaseStatusBar;

import java.util.ArrayList;

public class QuickSettings {
    private static final String TAG = "QuickSettings";

    private Context mContext;
    private ViewGroup mContainerView;
    private QuickSettingsController mQSC;
    private PanelBar mBar;

    boolean mTilesSetUp = false;

    /**
     *  ContentObserver to watch for Quick Settings tiles changes
     * @author dvtonder
     *
     */
    private class TilesChangedObserver extends ContentObserver {
        public TilesChangedObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mContainerView != null) {
                // Refresh the container
                mContainerView.removeAllViews();
                setupQuickSettings();
            }
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.QUICK_SETTINGS_TILES),
                    false, this);

            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.QS_DYNAMIC_ALARM),
                    false, this);

            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.QS_DYNAMIC_BUGREPORT),
                    false, this);

            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.QS_DYNAMIC_IME),
                    false, this);

            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.QS_DYNAMIC_WIFI),
                    false, this);
        }
    }

    public QuickSettings(Context context, QuickSettingsContainerView container,
        BaseStatusBar statusBar) {
        mContext = context;
        mContainerView = container;

        mQSC = new QuickSettingsController(mContext,
                (QuickSettingsContainerView) mContainerView, statusBar);

        // Start observing for quick setting tile changes
        TilesChangedObserver observer = new TilesChangedObserver(new Handler());
        observer.startObserving();
    }

    public void setBar(PanelBar bar) {
        mQSC.setBar(bar);
    }

    public void setImeWindowStatus(boolean visible) {
        mQSC.setImeWindowStatus(visible);
    }

    public void updateResources() {
        mQSC.updateResources();
    }

    public void setupQuickSettings() {
        // Setup the tiles that we are going to be showing (including the temporary ones)
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mQSC.loadTiles();
        mQSC.addQuickSettings(inflater);

        mTilesSetUp = true;
    }
}
