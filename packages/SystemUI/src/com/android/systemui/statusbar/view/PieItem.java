/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.statusbar.view;

import android.database.ContentObserver;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.os.Handler;
import android.provider.Settings;
import android.util.ColorUtils;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.statusbar.view.PieMenu.PieView;

import java.util.ArrayList;
import java.util.List;

/**
 * Pie menu item
 */
public class PieItem {

    private Context mContext;
    private View mView;
    private PieView mPieView;
    private int level;
    private float start;
    private float sweep;
    private float animate;
    private int inner;
    private int outer;
    private boolean mSelected;
    private boolean mEnabled;
    private List<PieItem> mItems;
    private String mName;
    private Path mPath;
    private boolean mIsLesser;

    private ColorUtils.ColorSettingInfo mLastButtonColor;

    public PieItem(View view, Context context, int level, String name, boolean lesser) {
        mContext = context;
        mView = view;
        this.level = level;
        mEnabled = true;
        setAnimationAngle(getAnimationAngle());
        setAlpha(getAlpha());
        mName = name;
        mIsLesser = lesser;

        if(mContext != null) {
            if (ColorUtils.getPerAppColorState(mContext)) {
                mLastButtonColor = ColorUtils.getColorSettingInfo(mContext, Settings.System.NAV_BUTTON_COLOR);
                setColor();

                mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAV_BUTTON_COLOR), false, new ContentObserver(new Handler()) {
                        @Override
                        public void onChange(boolean selfChange) {
                            setColor();
                        }});
            }
        }
    }

    public void setLesser(boolean lesser) {
        mIsLesser = lesser;
    }

    public boolean isLesser() {
        return mIsLesser;
    }

    public void setPath(Path path) {
        mPath = path;
    }

    public Path getPath() {
        return mPath;
    }

    public boolean hasItems() {
        return mItems != null;
    }

    public List<PieItem> getItems() {
        return mItems;
    }

    public void addItem(PieItem item) {
        if (mItems == null) {
            mItems = new ArrayList<PieItem>();
        }
        mItems.add(item);
    }

    public String getName() {
        return  mName;
    }

    public void setAlpha(float alpha) {
        if (mView != null) {
            mView.setAlpha(alpha);
        }
    }

    public float getAlpha() {
        if (mView != null) {
            return mView.getAlpha();
        }
        return 1;
    }

    public void setAnimationAngle(float a) {
        animate = a;
    }

    public float getAnimationAngle() {
        return animate;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void setSelected(boolean s) {
        mSelected = s;
        if (mView != null) {
            mView.setSelected(s);
        }
    }

    public boolean isSelected() {
        return mSelected;
    }

    public int getLevel() {
        return level;
    }

    public void setGeometry(float st, float sw, int inside, int outside) {
        start = st;
        sweep = sw;
        inner = inside;
        outer = outside;
    }

    public float getStart() {
        return start;
    }

    public float getStartAngle() {
        return start + animate;
    }

    public float getSweep() {
        return sweep;
    }

    public int getInnerRadius() {
        return inner;
    }

    public int getOuterRadius() {
        return outer;
    }

    public boolean isPieView() {
        return (mPieView != null);
    }

    public View getView() {
        return mView;
    }

    public void setPieView(PieView sym) {
        mPieView = sym;
    }

    public PieView getPieView() {
        if (mEnabled) {
            return mPieView;
        }
        return null;
    }

    private void setColor() {
        ImageView imageView = ((ImageView) mView);
        Drawable drawable = imageView.getDrawable();
        ColorUtils.ColorSettingInfo colorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_BUTTON_COLOR);
        if (!colorInfo.lastColorString.equals(mLastButtonColor.lastColorString)) {
            if (colorInfo.isLastColorNull) {
                drawable.clearColorFilter();
            } else {
                drawable.setColorFilter(ColorUtils.extractRGB(colorInfo.lastColor)
                        | 0xFF000000, Mode.SRC_ATOP);
            }
            mLastButtonColor = colorInfo;
        }
        imageView.setImageDrawable(drawable);
    }
}
