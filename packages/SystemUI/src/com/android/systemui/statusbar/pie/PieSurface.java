/*
 * Copyright (C) 2013 ParanoidAndroid
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

package com.android.systemui.statusbar.pie;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

// +----------------------------------------------------------------------------------+
// | CLASS PieSurface                                                                 |
// +----------------------------------------------------------------------------------+
public class PieSurface {

    // Linear
    private static int ANIMATOR_DEC_SPEED15 = 1;
    private static int ANIMATOR_ACC_SPEED15 = 2;

    // Cascade
    private static int ANIMATOR_ACC_INC_1 = ANIMATOR_ACC_SPEED15 + 1;
    private static int ANIMATOR_ACC_INC_15 = ANIMATOR_ACC_INC_1 + 15;

    // Special purpose
    private static int ANIMATOR_BATTERY_METER = ANIMATOR_ACC_INC_15 + 1;
    private static int ANIMATOR_SNAP_GROW = ANIMATOR_ACC_INC_15 + 2;
    private static int ANIMATOR_END = ANIMATOR_SNAP_GROW;

    private static final int COLOR_ALPHA_MASK = 0xaa000000;
    private static final int COLOR_OPAQUE_MASK = 0xff000000;
    private static final int COLOR_SNAP_BACKGROUND = 0xffffffff;
    private static final int COLOR_PIE_BACKGROUND = 0xaa000000;
    private static final int COLOR_PIE_BUTTON = 0xb2ffffff;
    private static final int COLOR_PIE_SELECT = 0xaaffffff;
    private static final int COLOR_PIE_OUTLINES = 0x55ffffff;
    private static final int COLOR_CHEVRON_LEFT = 0x0999cc;
    private static final int COLOR_CHEVRON_RIGHT = 0x53d5e5;
    private static final int COLOR_BATTERY_JUICE = 0x33b5e5;
    private static final int COLOR_BATTERY_JUICE_LOW = 0xffbb33;
    private static final int COLOR_BATTERY_JUICE_CRITICAL = 0xff4444;
    private static final int COLOR_BATTERY_BACKGROUND = 0xffffff;
    private static final int COLOR_STATUS = 0xffffff;
    private static final int BASE_SPEED = 1000;
    private static final int EMPTY_ANGLE_BASE = 0;
    private static final int CHEVRON_FRAGMENTS = 16;
    private static final float SIZE_BASE = 1f;
    
    private Pie mPie;
    private PieControl mControl;
    private PieStatusPanel mPanel;
    private PiePolicy mPolicy;

    private Context mContext;
    private Resources mResources;    
    private Vibrator mVibrator;

    private int mOverallSpeed = BASE_SPEED;
    private int mPanelDegree;
    private int mPanelOrientation;
    private int mInnerPieRadius;
    private int mOuterPieRadius;
    private int mPieGap;
    private int mInnerChevronRadius;
    private int mOuterChevronRadius;
    private int mInnerChevronRightRadius;
    private int mOuterChevronRightRadius;
    private int mInnerBatteryRadius;
    private int mOuterBatteryRadius;
    private int mStatusRadius;
    private int mNotificationsRadius;
    private int mEmptyAngle = EMPTY_ANGLE_BASE;

    private Point mCenter = new Point(0, 0);
    private float mCenterDistance = 0;

    private Path mStatusPath = new Path();
    private Path[] mChevronPathLeft  = new Path[CHEVRON_FRAGMENTS+1];
    private Path mChevronPathRight;
    private Path mBatteryPathBackground;
    private Path mBatteryPathJuice;

    private Paint mPieBackground = new Paint(COLOR_PIE_BACKGROUND);
    private Paint mPieSelected = new Paint(COLOR_PIE_SELECT);
    private Paint mPieOutlines = new Paint(COLOR_PIE_OUTLINES);
    private Paint mChevronBackgroundLeft = new Paint(COLOR_CHEVRON_LEFT);
    private Paint mChevronBackgroundRight = new Paint(COLOR_CHEVRON_RIGHT);
    private Paint mBatteryJuice = new Paint(COLOR_BATTERY_JUICE);
    private Paint mBatteryBackground = new Paint(COLOR_BATTERY_BACKGROUND);
    private Paint mSnapBackground = new Paint(COLOR_SNAP_BACKGROUND);

    private Paint mClockPaint;
    private Paint mAmPmPaint;
    private Paint mStatusPaint;
    private Paint mNotificationPaint;

    private String mClockText;
    private String mClockTextAmPm;
    private float mClockTextAmPmSize;
    private float mClockTextTotalOffset = 0;
    private float[] mClockTextOffsets = new float[20];
    private float mClockTextRotation;
    private float mClockOffset;
    private float mAmPmOffset;
    private float mStatusOffset;

    private int mNotificationCount;
    private float mNotificationsRowSize;
    private int mNotificationIconSize;
    private int mNotificationTextSize;
    private String[] mNotificationText;
    private Bitmap[] mNotificationIcon;
    private Path[] mNotificationPath;

    private float mStartBattery;
    private float mEndBattery;
    private int mBatteryLevel;

    private class SnapPoint {
        public SnapPoint(int snapX, int snapY, int snapRadius, int snapAlpha, int snapGravity) {
            x = snapX;
            y = snapY;
            radius = snapRadius;
            alpha = snapAlpha;
            gravity = snapGravity;
            active = false;
        }

        public int x;
        public int y;
        public int radius;
        public int alpha;
        public int gravity;
        public boolean active;
    }

    private SnapPoint[] mSnapPoint = new SnapPoint[3];
    int mSnapRadius;
    int mSnapThickness;

    // Flags
    private int mStatusMode;
    private float mPieSize = SIZE_BASE;
    private boolean mOpen;
    private boolean mNavbarZero;
    private boolean mUseMenuAlways;
    private boolean mUseSearch;
    private boolean mHapticFeedback;

    // Animations
    private int mGlowOffsetLeft = 150;
    private int mGlowOffsetRight = 150;

    public PieSurface(PieControl control) {
        mControl = control;
        mPie = mControl.mPie;
        mContext = mControl.mContext;
        mPanel = mPie.mPanel;
    }

    public void draw(Canvas canvas) {
        if (mOpen) {
            int state;

            // Draw background
            if (mStatusMode != -1 && !mNavbarZero) {
                canvas.drawARGB((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xcc), 0, 0, 0);
            }

            // Snap points
            if (mCenterDistance > mOuterChevronRadius) {
                for (int i = 0; i < 3; i++) {
                    SnapPoint snap = mSnapPoint[i];
                    mSnapBackground.setAlpha((int)(snap.alpha + (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction * 80 : 0)));

                    canvas.drawCircle (snap.x, snap.y, (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction *
                            Math.max(getWidth(), getHeight()) * 1.5f : 0), mSnapBackground);

                    float snapDistanceX = snap.x-mX;
                    float snapDistanceY = snap.y-mY;
                    float snapDistance = (float)Math.sqrt(Math.pow(snapDistanceX, 2) + Math.pow(snapDistanceY, 2));
                    float snapTouch = snapDistance < mSnapRadius * 7 ? 200 - (snapDistance * (200 - snap.alpha) / (mSnapRadius * 7)) : snap.alpha;

                    mSnapBackground.setAlpha((int)(snapTouch));
                    int len = (int)(snap.radius * 1.3f + (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction * 500 : 0));
                    int thick = (int)(len * 0.2f);

                    Path plus = new Path();
                    plus.addRect(snap.x - len / 2, snap.y - thick / 2, snap.x + len / 2, snap.y + thick / 2, Path.Direction.CW);
                    plus.addRect(snap.x - thick / 2, snap.y - len / 2, snap.x + thick / 2, snap.y + len / 2, Path.Direction.CW);
                    canvas.drawPath(plus, mSnapBackground);
                }
            }

            // Draw base menu
            for (PieItem item : mItems) {
                if (!canItemDisplay(item)) continue;
                drawItem(canvas, item);
            }

            // Paint status report only if settings allow
            if (mStatusMode != -1 && !mNavbarZero) {

                // Draw chevron rings
                mChevronBackgroundLeft.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * mGlowOffsetLeft / 2 * (mPanelOrientation == Gravity.TOP ? 0.2 : 1)));
                mChevronBackgroundRight.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * mGlowOffsetRight * (mPanelOrientation == Gravity.TOP ? 0.2 : 1)));

                if (mStatusPanel.getCurrentViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL) {
                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    for (int i=0; i < CHEVRON_FRAGMENTS + 1; i++) {
                        canvas.drawPath(mChevronPathLeft[i], mChevronBackgroundLeft);
                    }
                    canvas.restoreToCount(state);
                }

                if (mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                    state = canvas.save();
                    canvas.rotate(180 + (1-mAnimators[ANIMATOR_BATTERY_METER].fraction) * 90, mCenter.x, mCenter.y);
                    canvas.drawPath(mChevronPathRight, mChevronBackgroundRight);
                    canvas.restoreToCount(state);
                }

                // Better not show inverted junk for top pies
                if (mPanelOrientation != Gravity.TOP) {

                    // Draw Battery
                    mBatteryBackground.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0x22));
                    mBatteryJuice.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0x88));

                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    canvas.drawPath(mBatteryPathBackground, mBatteryBackground);
                    canvas.restoreToCount(state);

                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    canvas.drawPath(mBatteryPathJuice, mBatteryJuice);
                    canvas.restoreToCount(state);

                    // Draw clock && AM/PM
                    state = canvas.save();
                    canvas.rotate(mClockTextRotation - (1-mAnimators[ANIMATOR_DEC_SPEED15].fraction) * 90, mCenter.x, mCenter.y);

                    mClockPaint.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xcc));
                    float lastPos = 0;
                    for(int i = 0; i < mClockText.length(); i++) {
                        canvas.drawTextOnPath("" + mClockText.charAt(i), mStatusPath, lastPos, mClockOffset, mClockPaint);
                        lastPos += mClockTextOffsets[i];
                    }

                    mAmPmPaint.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xaa));
                    canvas.drawTextOnPath(mClockTextAmPm, mStatusPath, lastPos - mClockTextAmPmSize, mAmPmOffset, mAmPmPaint);
                    canvas.restoreToCount(state);

                    // Device status information and date
                    mStatusPaint.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0xaa));
                    
                    state = canvas.save();
                    canvas.rotate(mPanel.getDegree() + 180 + (1-mAnimators[ANIMATOR_DEC_SPEED15].fraction) * 90, mCenter.x, mCenter.y);
                    if (mPolicy.supportsTelephony()) {
                        canvas.drawTextOnPath(mPolicy.getNetworkProvider(), mStatusPath, 0, mStatusOffset * 4, mStatusPaint);
                    }
                    canvas.drawTextOnPath(mPolicy.getSimpleDate(), mStatusPath, 0, mStatusOffset * 3, mStatusPaint);
                    canvas.drawTextOnPath(mPanel.getBar().getNotificationData().size() + " " + mContext.getString(R.string.status_bar_latest_events_title).toUpperCase(), mStatusPath, 0, mStatusOffset * 2, mStatusPaint);
                    canvas.drawTextOnPath(mContext.getString(R.string.quick_settings_wifi_label).toUpperCase() + ": " + mPolicy.getWifiSsid(), mStatusPath, 0, mStatusOffset * 1, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getBatteryLevelReadable(), mStatusPath, 0, mStatusOffset * 0, mStatusPaint);
                    canvas.restoreToCount(state);

                    state = canvas.save();
                    canvas.rotate(mPanel.getDegree() + 180, mCenter.x, mCenter.y);

                    // Notifications
                    if (mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {

                        for (int i = 0; i < mNotificationCount && i < 10; i++) {
                            mNotificationPaint.setAlpha((int)(mAnimators[ANIMATOR_ACC_INC_1 + i].fraction * mGlowOffsetRight));

                            canvas.drawTextOnPath(mNotificationText[i], mNotificationPath[i], 0, 0, mNotificationPaint);

                            int IconState = canvas.save();
                            int posX = (int)(mCenter.x + mNotificationsRadius + i * mNotificationsRowSize);
                            int posY = (int)(mCenter.y - mNotificationIconSize * 1.4f);
                            int iconCenter = mNotificationIconSize / 2;

                            canvas.rotate(90, posX + iconCenter, posY + iconCenter);
                            canvas.drawBitmap(mNotificationIcon[i], null, new Rect(posX, posY, posX +
                                    mNotificationIconSize,posY + mNotificationIconSize), mNotificationPaint);
                            canvas.restoreToCount(IconState);
                        }
                    }
                    canvas.restoreToCount(state);
                }
            }
        }
    }
    
    public boolean touch(MotionEvent event) {
        if (evt.getPointerCount() > 1) return true;
        mX = evt.getRawX();
        mY = evt.getRawY();
        float distanceX = mCenter.x-mX;
        float distanceY = mCenter.y-mY;
        mCenterDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));
        float shadeTreshold = mOuterChevronRadius; 
        
        int action = evt.getActionMasked();
        if (MotionEvent.ACTION_DOWN == action) {
            // Open panel
            animateIn();
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                PieItem item = mCurrentItem;

                // Activate any panels?
                mStatusPanel.hidePanels(true);
                switch(mStatusPanel.getFlipViewState()) {
                    case PieStatusPanel.NOTIFICATIONS_PANEL:
                        mStatusPanel.setCurrentViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
                        mStatusPanel.showNotificationsPanel();
                        break;
                    case PieStatusPanel.QUICK_SETTINGS_PANEL:
                        mStatusPanel.setCurrentViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
                        mStatusPanel.showTilesPanel();
                    break;
                }
      
                // Check for click actions
                if (item != null && item.getView() != null && mCenterDistance < shadeTreshold) {
                    if(mHapticFeedback) mVibrator.vibrate(2);
                    item.getView().performClick();
                }
            }

            // Say good bye
            deselect();
            animateOut();
            return true;
        } else if (MotionEvent.ACTION_MOVE == action) {

            boolean snapActive = false;
            for (int i = 0; i < 3; i++) {
                SnapPoint snap = mSnapPoint[i];                
                float snapDistanceX = snap.x-mX;
                float snapDistanceY = snap.y-mY;
                float snapDistance = (float)Math.sqrt(Math.pow(snapDistanceX, 2) + Math.pow(snapDistanceY, 2));

                if (snapDistance < mSnapRadius) {
                    snap.alpha = 60;
                    if (!snap.active) {
                        mAnimators[ANIMATOR_SNAP_GROW].cancel();
                        mAnimators[ANIMATOR_SNAP_GROW].animator.start();
                        if(mHapticFeedback) mVibrator.vibrate(2);
                    }
                    snap.active = true;
                    snapActive = true;
                    mStatusPanel.setFlipViewState(-1);
                    mGlowOffsetLeft = 150;
                    mGlowOffsetRight = 150;
                } else {
                    if (snap.active) {
                        mAnimators[ANIMATOR_SNAP_GROW].cancel();
                    }
                    snap.alpha = 30;
                    snap.active = false;
                }
            }

            // Trigger the shades?
            if (mCenterDistance > shadeTreshold) {
                int state = -1;
                switch (mPanelOrientation) {
                    case Gravity.BOTTOM:
                        state = distanceX > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.TOP:
                        state = distanceX > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.LEFT:
                        state = distanceY > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.RIGHT:
                        state = distanceY < 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                }

                if (!mNavbarZero) {
                    if (state == PieStatusPanel.QUICK_SETTINGS_PANEL && 
                            mStatusPanel.getFlipViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL
                            && mStatusPanel.getCurrentViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL) {
                        mGlowOffsetRight = mPanelOrientation != Gravity.TOP ? 150 : 255;;
                        mGlowOffsetLeft = mPanelOrientation != Gravity.TOP ? 255 : 150;
                        mStatusPanel.setFlipViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
                        if (mHapticFeedback && !snapActive) mVibrator.vibrate(2);
                    } else if (state == PieStatusPanel.NOTIFICATIONS_PANEL && 
                            mStatusPanel.getFlipViewState() != PieStatusPanel.NOTIFICATIONS_PANEL
                            && mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                        mGlowOffsetRight = mPanelOrientation != Gravity.TOP ? 255 : 150;
                        mGlowOffsetLeft = mPanelOrientation != Gravity.TOP ? 150 : 255;
                        mStatusPanel.setFlipViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
                        if (mHapticFeedback && !snapActive) mVibrator.vibrate(2);
                    }
                }
                deselect();
            }

            // Take back shade trigger if user decides to abandon his gesture
            if (mCenterDistance < shadeTreshold) {
                mStatusPanel.setFlipViewState(-1);
                mGlowOffsetLeft = 150;
                mGlowOffsetRight = 150;

                // Check for onEnter separately or'll face constant deselect
                PieItem item = findItem(getPolar(mX, mY));
                if (item != null) {
                    if (mCenterDistance < shadeTreshold && mCenterDistance > (mInnerPieRadius * 0.75f)) {
                        onEnter(item);
                    } else {
                        deselect();
                    }
                }
            }
            invalidate();
        }
        // always re-dispatch event
        return false;
    }

    private class SnapPoint {
        public SnapPoint(int snapX, int snapY, int snapRadius, int snapAlpha, int snapGravity) {
            x = snapX;
            y = snapY;
            radius = snapRadius;
            alpha = snapAlpha;
            gravity = snapGravity;
            active = false;
        }

        public int x;
        public int y;
        public int radius;
        public int alpha;
        public int gravity;
        public boolean active;
    }

    private class CustomValueAnimator {

        public CustomValueAnimator(int animateIndex) {
            index = animateIndex;
            manual = false;
            animateIn = true;
            animator = ValueAnimator.ofInt(0, 1);
            animator.addUpdateListener(new CustomAnimatorUpdateListener(index));
            fraction = 0;
        }

        public void start() {
            if (!manual) {
                animator.setDuration(duration);
                animator.start();
            }
        }

        public void reverse(int milliSeconds) {
            if (!manual) {
                animator.setDuration(milliSeconds);
                animator.reverse();
            }
        }

        public void cancel() {
            animator.cancel();
            fraction = 0;
        }

        public int index;
        public int duration;
        public boolean manual;
        public boolean animateIn;
        public float fraction;
        public ValueAnimator animator;
    }

    private class CustomAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        private int mIndex;
        CustomAnimatorUpdateListener(int index) {
            mIndex = index;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {            
            mAnimators[mIndex].fraction = animation.getAnimatedFraction();

            // Special purpose animators go here
            if (mIndex == ANIMATOR_BATTERY_METER) {
                mBatteryPathJuice = makeSlice(mStartBattery, mStartBattery + (float)animation.getAnimatedFraction() *
                        (mBatteryLevel * (mEndBattery-mStartBattery) / 100), mInnerBatteryRadius, mOuterBatteryRadius, mCenter);
            }
            invalidate();
        }
    }

    private void cancelAnimation() {
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i].cancel();
        }
    }

    private void animateIn() {
        // Cancel & start all animations
        cancelAnimation();
        invalidate();
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i].animateIn = true;
            mAnimators[i].start();
        }
    }

    public void animateOut() {
        mPanel.show(false);
        cancelAnimation();
    }
}
