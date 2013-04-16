/*
 * Copyright (C) 2010 The Paranoid Android Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.os.Handler;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.ExpandHelper;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.policy.NotificationRowLayout;

import java.util.ArrayList;
import java.util.List;

public class PieStatusPanel extends LinearLayout {

    public static final int NOTIFICATIONS_PANEL = 0;
    public static final int QUICK_SETTINGS_PANEL = 1;

    private ExpandHelper mExpandHelper;

    private Context mContext;
    private Pie mPie;

    private QuickSettingsContainerView mQuickSettings;
    private NotificationRowLayout mNotificationPanel;
    private ViewGroup[] mPanelParents = new ViewGroup[2];

    private View mContentHeader;
    private ScrollView mScrollView;
    private View mClearButton;
    private View mContentFrame;

    private Handler mHandler = new Handler();
    private NotificationData mNotificationData;
    private Runnable mPostCollapseCleanup = null;

    protected int mCurrentViewState = -1;
    protected int mFlipViewState = -1;

    public PieStatusPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PieStatusPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    public void init(Pie pie) {

        mPie = pie;

        // Set up panels and parents
        mNotificationPanel = mPie.mStatusBar.getNotificationRowLayout();
        mNotificationPanel.setTag(NOTIFICATIONS_PANEL);
        mQuickSettings = mPie.mStatusBar.getQuickSettingsPanel();
        mQuickSettings.setTag(QUICK_SETTINGS_PANEL);
        mPanelParents[NOTIFICATIONS_PANEL] = (ViewGroup) mNotificationPanel.getParent();
        mPanelParents[QUICK_SETTINGS_PANEL] = (ViewGroup) mQuickSettings.getParent();

        // Set up expand-helper
        int minHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_max_height);
        mExpandHelper = new ExpandHelper(mContext, mNotificationPanel, minHeight, maxHeight);
        mExpandHelper.setEventSource(this);
        mExpandHelper.setScrollView(findViewById(R.id.content_scroll));

        // Controls and touch listeners
        mContentHeader = (View) mPie.mPanel.findViewById(R.id.content_header);
        mContentFrame = (View) mPie.mPanel.findViewById(R.id.content_frame);
        mScrollView = (ScrollView) mPie.mPanel.findViewById(R.id.content_scroll);
        mScrollView.setOnTouchListener(new ViewOnTouchListener());
        mContentFrame.setOnTouchListener(new ViewOnTouchListener());
        mClearButton = (ImageView) mPie.mPanel.findViewById(R.id.clear_all_button);
        mClearButton.setOnClickListener(mClearButtonListener);

        // Hide panel
        mPie.mPanel.setVisibility(View.GONE);
    }

    public void hidePanels(boolean reset) {
        if (mCurrentViewState == NOTIFICATIONS_PANEL) {
            hidePanel(mNotificationPanel);
        } else if (mCurrentViewState == QUICK_SETTINGS_PANEL) {
            hidePanel(mQuickSettings);
        }
        if (reset) mCurrentViewState = -1;
    }

    public void swapPanels() {
        hidePanels(false);
        if (mCurrentViewState == NOTIFICATIONS_PANEL) {
            mCurrentViewState = QUICK_SETTINGS_PANEL;
            showPanel(mQuickSettings);
        } else if (mCurrentViewState == QUICK_SETTINGS_PANEL) {
            mCurrentViewState = NOTIFICATIONS_PANEL;
            showPanel(mNotificationPanel);
        }
    }

    private ViewGroup getPanelParent(View panel) {
        return mPanelParents[ (((Integer)panel.getTag()).intValue() == NOTIFICATIONS_PANEL ?
                NOTIFICATIONS_PANEL : QUICK_SETTINGS_PANEL)];
    }

    public void showTilesPanel() {
        showPanel(mQuickSettings);
        ShowClearAll(true);
    }

    public void showNotificationsPanel() {
        showPanel(mNotificationPanel);
        ShowClearAll(false);
    }

    public void hideTilesPanel() {
        hidePanel(mQuickSettings);
    }

    public void hideNotificationsPanel() {
        hidePanel(mNotificationPanel);
    }

    private void showPanel(View panel) {
        mContentFrame.setBackgroundColor(0);
        ValueAnimator alphAnimation  = ValueAnimator.ofInt(0, 1);
        alphAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mScrollView.setX(-(int)((1-animation.getAnimatedFraction()) * mPie.mControl.getWidth() * 1.5));
                mContentFrame.setBackgroundColor((int)(animation.getAnimatedFraction() * 0xEE) << 24);
                mPie.mControl.invalidate();
            }
        });
        alphAnimation.setDuration(600);
        alphAnimation.setInterpolator(new DecelerateInterpolator());
        alphAnimation.start();

        AlphaAnimation alphaUp = new AlphaAnimation(0, 1);
        alphaUp.setFillAfter(true);
        alphaUp.setDuration(1000);
        mContentHeader.startAnimation(alphaUp);

        ViewGroup parent = getPanelParent(panel);
        parent.removeAllViews();
        mScrollView.removeAllViews();
        mScrollView.addView(panel);
        updateContainer(true);
    }

    private void hidePanel(View panel) {
        ViewGroup parent = getPanelParent(panel);
        mScrollView.removeAllViews();
        parent.removeAllViews();
        parent.addView(panel, panel.getLayoutParams());
        updateContainer(false);
    }

    private void updateContainer(boolean visible) {
        mPie.mPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        updatePanelConfiguration();
    }

    public void updatePanelConfiguration() {
        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.pie_panel_padding);
        mScrollView.setPadding(padding,0,padding,0);
        mContentHeader.setPadding(padding,0,padding,0);
    }

    private void ShowClearAll(boolean show){
        mClearButton.setAlpha(show ? 0.0f : 1.0f);
        mClearButton.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        MotionEvent cancellation = MotionEvent.obtain(ev);
        cancellation.setAction(MotionEvent.ACTION_CANCEL);

        boolean intercept = mExpandHelper.onInterceptTouchEvent(ev) ||
                super.onInterceptTouchEvent(ev);
        if (intercept) {
            mNotificationPanel.onInterceptTouchEvent(cancellation);
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = mExpandHelper.onTouchEvent(ev) ||
                super.onTouchEvent(ev);
        return handled;
    }

    class ViewOnTouchListener implements OnTouchListener {
        final int SCROLLING_DISTANCE_TRIGGER = 100;
            float scrollX;
            float scrollY;
            boolean hasScrolled;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        scrollX = event.getX();
                        scrollY = event.getY();
                        hasScrolled = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float distanceY = Math.abs(event.getY() - scrollY);
                        float distanceX = Math.abs(event.getX() - scrollX);
                        if(distanceY > SCROLLING_DISTANCE_TRIGGER ||
                            distanceX > SCROLLING_DISTANCE_TRIGGER) {
                            hasScrolled = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if(!hasScrolled) {
                            hidePanels(true);
                        }
                        break;
                }
                return false;
            }                  
    }

    private View.OnClickListener mClearButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            synchronized (mNotificationData) {
                // animate-swipe all dismissable notifications, then animate the shade closed
                int numChildren = mNotificationPanel.getChildCount();

                int scrollTop = mScrollView.getScrollY();
                int scrollBottom = scrollTop + mScrollView.getHeight();
                final ArrayList<View> snapshot = new ArrayList<View>(numChildren);
                for (int i=0; i<numChildren; i++) {
                    final View child = mNotificationPanel.getChildAt(i);
                    if (mNotificationPanel.canChildBeDismissed(child) && child.getBottom() > scrollTop &&
                            child.getTop() < scrollBottom) {
                        snapshot.add(child);
                    }
                }
                if (snapshot.isEmpty()) {
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Decrease the delay for every row we animate to give the sense of
                        // accelerating the swipes
                        final int ROW_DELAY_DECREMENT = 10;
                        int currentDelay = 140;
                        int totalDelay = 0;

                        // Set the shade-animating state to avoid doing other work during
                        // all of these animations. In particular, avoid layout and
                        // redrawing when collapsing the shade.
                        mNotificationPanel.setViewRemoval(false);

                        mPostCollapseCleanup = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mNotificationPanel.setViewRemoval(true);
                                    mPie.mStatusBar.getService().onClearAllNotifications();
                                } catch (Exception ex) { }
                            }
                        };

                        View sampleView = snapshot.get(0);
                        int width = sampleView.getWidth();
                        final int velocity = width * 8; // 1000/8 = 125 ms duration
                        for (final View _v : snapshot) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mNotificationPanel.dismissRowAnimated(_v, velocity);
                                }
                            }, totalDelay);
                            currentDelay = Math.max(50, currentDelay - ROW_DELAY_DECREMENT);
                            totalDelay += currentDelay;
                        }
                        // Delay the collapse animation until after all swipe animations have
                        // finished. Provide some buffer because there may be some extra delay
                        // before actually starting each swipe animation. Ideally, we'd
                        // synchronize the end of those animations with the start of the collaps
                        // exactly.
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mPostCollapseCleanup.run();
                                hidePanels(true);
                            }
                        }, totalDelay + 225);
                    }
                }).start();
            }
        }
    };
}

