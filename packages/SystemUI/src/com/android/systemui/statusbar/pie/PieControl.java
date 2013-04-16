/*
 * Copyright (C) 2013 ParanoidAndroid
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

package com.android.systemui.statusbar.pie;

import android.widget.FrameLayout;
import android.view.View.OnClickListener;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.ColorUtils;
import android.app.SearchManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.view.ViewGroup.LayoutParams;
import android.app.StatusBarManager;
import android.view.KeyEvent;
import android.content.Intent;
import android.app.ActivityOptions;
import android.os.SystemClock;
import android.hardware.input.InputManager;
import android.os.UserHandle;
import android.content.ActivityNotFoundException;
import android.graphics.Path;
import android.widget.ImageView.ScaleType;
import android.graphics.Rect;
import android.graphics.Point;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.MotionEvent;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.recent.RecentsActivity;
import com.android.systemui.recent.RecentsActivity.NavigationCallback;

import java.util.ArrayList;
import java.util.List;
import java.lang.Runnable;

// +----------------------------------------------------------------------------------+
// | CLASS PieControl                                                                 |
// +==================================================================================+
// | Abstracts view construction, click handling and statusbar callbacks              |
// +----------------------------------------------------------------------------------+
public class PieControl extends FrameLayout implements StatusBarPanel, NavigationCallback {

    public static final String BACK_BUTTON = "##back##";
    public static final String HOME_BUTTON = "##home##";
    public static final String MENU_BUTTON = "##menu##";
    public static final String SEARCH_BUTTON = "##search##";
    public static final String RECENT_BUTTON = "##recent##";
    public static final String CLEAR_ALL_BUTTON = "##clear##";   

    private PieItem mBack;
    private PieItem mHome;
    private PieItem mMenu;
    private PieItem mRecent;
    private PieItem mSearch;

    private int mNavigationIconHints;
    private ViewGroup mContentFrame;
    private Rect mContentArea = new Rect();

    protected Context mContext;
    protected Handler mHandler;
    protected Pie mPie;
    protected PieSurface mSurface;

    protected PieItem mCurrentItem;
    protected List<PieItem> mItems;
    protected boolean mMenuButton;
    protected Intent mAssistant;

    protected boolean mShow;
    protected Point mCenter = new Point(0, 0);

    public PieControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        setWillNotDraw(false);
        setDrawingCacheEnabled(false);

        mShow = false;
        setVisibility(View.GONE);

        // Set recents activity navigation bar view
        RecentsActivity.addNavigationCallback(this);

        // Populate PIE
        mAssistant = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, UserHandle.USER_CURRENT);

        mItems = new ArrayList<PieItem>();
        mBack = PieItem.makeItem(this, R.drawable.ic_sysbar_back, BACK_BUTTON, false);
        mHome = PieItem.makeItem(this, R.drawable.ic_sysbar_home, HOME_BUTTON, false);
        mRecent = PieItem.makeItem(this, R.drawable.ic_sysbar_recent, RECENT_BUTTON, false);
        mMenu = PieItem.makeItem(this, R.drawable.ic_sysbar_menu, MENU_BUTTON, true);
        mItems.add(mMenu);

        if (mAssistant != null) {
            mSearch = PieItem.makeItem(this, R.drawable.ic_sysbar_search_side, SEARCH_BUTTON, true);
            mItems.add(mSearch);
        }

        mItems.add(mRecent);
        mItems.add(mHome);
        mItems.add(mBack);
    }

    public void init(Pie pie) {
        mPie = pie;
        mHandler = mPie.mStatusBar.getHandler();
        mSurface = new PieSurface(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mSurface.draw(canvas);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mSurface.touch(event);
    }

    public void setMenu(boolean state) {
        mMenuButton = state;
    }

    public void animateCollapsePanels() {
        //TODO: close panels
        //mPieControl.getPieMenu().getStatusPanel().hidePanels(true);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mContentFrame = (ViewGroup)findViewById(R.id.content_frame);
        setWillNotDraw(false);
        show(false);
    }

    @Override
    public boolean isInContentArea(int x, int y) {
        mContentArea.left = mContentFrame.getLeft() + mContentFrame.getPaddingLeft();
        mContentArea.top = mContentFrame.getTop() + mContentFrame.getPaddingTop();
        mContentArea.right = mContentFrame.getRight() - mContentFrame.getPaddingRight();
        mContentArea.bottom = mContentFrame.getBottom() - mContentFrame.getPaddingBottom();
        return mContentArea.contains(x, y);
    }

    @Override
    public void setNavigationIconHints(int button, int hints, boolean force) {
        mNavigationIconHints = hints;
        if (button == NavigationCallback.NAVBAR_RECENTS_HINT) {
            boolean alt = (0 != (hints & StatusBarManager.NAVIGATION_HINT_RECENT_ALT));
            mRecent.setIcon(alt ? R.drawable.ic_sysbar_recent_clear
                    : R.drawable.ic_sysbar_recent);
            mRecent.mName = alt ? CLEAR_ALL_BUTTON : RECENT_BUTTON;
        }
    }

    @Override
    public int getNavigationIconHints() {
        return mNavigationIconHints;
    }

    public void show(boolean show) {

    }


    public void show(int verticalPos) {

    }
}
