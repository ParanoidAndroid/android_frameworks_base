/*
 * Copyright (C) 2011 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010 ParanoidAndroid Project
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

package com.android.systemui.statusbar;

import android.app.ActionBar.Tab;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.view.PieItem;
import com.android.systemui.statusbar.view.PieMenu;
import com.android.systemui.statusbar.view.PieMenu.PieView.OnLayoutListener;
import com.android.systemui.statusbar.view.PieStackView;
import com.android.systemui.statusbar.view.PieStackView.OnCurrentListener;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Quick Controls pie menu
 */
public class PieControl implements PieMenu.PieController, OnClickListener {
    public static final String BACK_BUTTON = "##back##";
    public static final String HOME_BUTTON = "##home##";
    public static final String MENU_BUTTON = "##menu##";
    public static final String RECENT_BUTTON = "##recent##";

    protected Context mContext;
    protected PieMenu mPie;
    protected int mItemSize;
    protected TextView mTabsCount;
    private PieItem mBack;
    private PieItem mHome;
    private PieItem mMenu;
    private PieItem mRecent;
    private OnNavButtonPressedListener mListener;
    private PieControlPanel mPanel;

    public PieControl(Context context) {
        mContext = context;
        mItemSize = (int) context.getResources().getDimension(R.dimen.pie_item_size);
    }

    public void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mContext);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            populateMenu();
            mPie.setController(this);
            mPie.setPanel(mPanel);
        }
        container.addView(mPie);
    }

    public void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    public void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    protected void setPanel(PieControlPanel panel) {
        mPanel = panel;
    }

    protected void setClickListener(OnClickListener listener, PieItem... items) {
        for (PieItem item : items) {
            item.getView().setOnClickListener(listener);
        }
    }

    @Override
    public boolean onOpen() {
        return true;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return mPie.onTouchEvent(event);
    }

    protected void populateMenu() {
        mBack = makeItem(R.drawable.ic_sysbar_back, 1, BACK_BUTTON);
        mHome = makeItem(R.drawable.ic_sysbar_home, 1, HOME_BUTTON);
        mRecent = makeItem(R.drawable.ic_sysbar_recent, 1, RECENT_BUTTON);
        mMenu = makeItem(R.drawable.ic_sysbar_menu, 1, MENU_BUTTON);
            
        setClickListener(this, mBack, mHome, mRecent, mMenu);

        mPie.addItem(mMenu);
        mPie.addItem(mRecent);
        mPie.addItem(mHome);
        mPie.addItem(mBack);
    }

    @Override
    public void onClick(View v) {
        String buttonName = null;
        if (v == mBack.getView()) {
            buttonName = BACK_BUTTON;
        } else if (v == mHome.getView()) {
            buttonName = HOME_BUTTON;
        } else if (v == mRecent.getView()) {
            buttonName = RECENT_BUTTON;
        } else if (v == mMenu.getView()) {
            buttonName = MENU_BUTTON;
        }

        if (mListener != null) {
            mListener.onNavButtonPressed(buttonName);
        }
    }

    protected PieItem makeItem(int image, int l, String name) {
        ImageView view = new ImageView(mContext);
        view.setImageResource(image);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return new PieItem(view, mContext, l, name);
    }

    public void show(boolean show) {
        mPie.show(show);
    }

    public void setCenter(int x, int y) {
        mPie.setCenter(x, y);
    }

    public void setOnNavButtonPressedListener(OnNavButtonPressedListener listener) {
        mListener = listener;
    }

    public interface OnNavButtonPressedListener {
        public void onNavButtonPressed(String buttonName);
    }

}
