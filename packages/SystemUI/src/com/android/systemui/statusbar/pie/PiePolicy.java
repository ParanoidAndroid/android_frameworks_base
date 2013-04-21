/*
 * Copyright (C) 2013 ParanoidAndroid Project
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

import android.provider.Settings;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.os.Handler;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.ColorUtils;
import android.view.WindowManager;
import android.app.Notification;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.TimeInterpolator;
import android.os.Vibrator;
import android.content.Context;

import com.android.systemui.R;
import com.android.internal.statusbar.StatusBarNotification;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.policy.NotificationRowLayout;

// +----------------------------------------------------------------------------------+
// | CLASS PiePolicy                                                                  |
// +==================================================================================+
// | This class takes care of all parameters and variables that somehow define or     |
// | shape PIE. It will observe, change, animate and shift them when needed.          |
// | PieSurface will later reflect these values visually.
// +----------------------------------------------------------------------------------+
public class PiePolicy {

    protected static final int GRAVITY[] = {Gravity.LEFT, Gravity.TOP, Gravity.RIGHT, Gravity.BOTTOM};  

    private Pie mPie;
    private PieControl mControl;
    private PieUtils mUtils; 

    protected boolean mPieAllowed;
    protected boolean mExpanded;
    protected boolean mUseMenuAlways;
    protected boolean mUseSearch;

    protected int mGravity;
    protected int mDegree;
    protected int mStatusMode;

    protected float mPieSize;
    protected float mTriggerSize;
    protected float mPieGap;
    protected float mInnerPieRadius;
    protected float mOuterPieRadius;



    public PiePolicy(Pie pie) {
        mPie = pie;
        mControl = mPie.mControl;
        mUtils = new PieUtils(mContext);

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();


        // Get all dimensions
        update();
    }

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }
    }

    private void update() {
        mExpanded = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1;

        mNavbarZero = Integer.parseInt(ExtendedPropertiesUtils.getProperty(
                "com.android.systemui.navbar.dpi", "100")) == 0;

        mPieAllowed = mExpanded || mNavbarZero;

        mGravity = GRAVITY[Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_GRAVITY, 3)];

        switch(mGravity) {
            case Gravity.LEFT:
                mPanelDegree = 180;
                break;
            case Gravity.TOP:
                mPanelDegree = -90;
                break;
            case Gravity.BOTTOM:
                mPanelDegree = 90;
                break;
            default:
                mPanelDegree = 0;
                break;
        }

/*
        mUseMenuAlways = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_MENU, 1) == 1;

        mUseSearch = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_SEARCH, 1) == 1;

        mStatusMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_MODE, 2);

        mTriggerSize = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PIE_TRIGGER, 1f);

        mPieSize = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PIE_SIZE, 0.9f);

        mPieGap = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_GAP, 3);

        mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;

        // Snap
        mSnapRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_snap_radius) * mPieSize);
        mSnapThickness = (int)(mResources.getDimensionPixelSize(R.dimen.pie_snap_thickness) * mPieSize);

        Point outSize = new Point(0,0);
        WindowManager windowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(outSize);
        int mWidth = outSize.x;
        int mHeight = outSize.y;

        int snapIndex = 0;
        if (mPanelOrientation != Gravity.LEFT)
            mSnapPoint[snapIndex++] = new SnapPoint(0 + mSnapThickness / 2, mHeight / 2, mSnapRadius, 0x22, Gravity.LEFT);
        if (mPanelOrientation != Gravity.TOP)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth / 2, mSnapThickness / 2, mSnapRadius, 0x22, Gravity.TOP);
        if (mPanelOrientation != Gravity.RIGHT)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth - mSnapThickness / 2, mHeight / 2, mSnapRadius, 0x22, Gravity.RIGHT);
        if (mPanelOrientation != Gravity.BOTTOM)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth / 2, mHeight - mSnapThickness / 2, mSnapRadius, 0x22, Gravity.BOTTOM);

        // Create Pie
        mEmptyAngle = (int)(EMPTY_ANGLE_BASE * mPieSize);
        mInnerPieRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_radius_start) * mPieSize);
        mOuterPieRadius = (int)(mInnerPieRadius + mResources.getDimensionPixelSize(R.dimen.pie_radius_increment) * mPieSize);

        // Calculate chevrons: 0 - 82 & -4 - 90
        mInnerChevronRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_chevron_start) * mPieSize);
        mOuterChevronRadius = (int)(mInnerChevronRadius + mResources.getDimensionPixelSize(R.dimen.pie_chevron_increment) * mPieSize);
        mInnerChevronRightRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_chevron_start_right) * mPieSize);
        mOuterChevronRightRadius = (int)(mInnerChevronRightRadius + mResources.getDimensionPixelSize(R.dimen.pie_chevron_increment_right) * mPieSize);

        // Create slices
        float fragmentSize = 90 / CHEVRON_FRAGMENTS;
        for (int i=0; i < CHEVRON_FRAGMENTS + 1; i++) {
            mChevronPathLeft[i] = makeSlice(mPanelDegree + (i * fragmentSize), mPanelDegree + (i * fragmentSize) + fragmentSize / 2,
                    mInnerChevronRadius, mOuterChevronRadius, mCenter);
        }

        mChevronPathRight = makeSlice(mPanelDegree + (mPanelOrientation != Gravity.TOP ? -5 : 3), mPanelDegree + 90, mInnerChevronRightRadius,
                mOuterChevronRightRadius, mCenter);

        // Calculate text circle
        mStatusRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_status_start) * mPieSize);
        mStatusPath.reset();
        mStatusPath.addCircle(mCenter.x, mCenter.y, mStatusRadius, Path.Direction.CW);

        mClockPaint.setTextSize(mResources.getDimensionPixelSize(R.dimen.pie_clock_size) * mPieSize);
        mClockOffset = mResources.getDimensionPixelSize(R.dimen.pie_clock_offset) * mPieSize;
        mAmPmPaint.setTextSize(mResources.getDimensionPixelSize(R.dimen.pie_ampm_size) * mPieSize);
        mAmPmOffset = mResources.getDimensionPixelSize(R.dimen.pie_ampm_offset) * mPieSize;

        mStatusPaint.setTextSize((int)(mResources.getDimensionPixelSize(R.dimen.pie_status_size) * mPieSize));
        mStatusOffset = mResources.getDimensionPixelSize(R.dimen.pie_status_offset) * mPieSize;
        mNotificationTextSize = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notification_size) * mPieSize);
        mNotificationPaint.setTextSize(mNotificationTextSize);

        // Battery
        mInnerBatteryRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_battery_start) * mPieSize);
        mOuterBatteryRadius = (int)(mInnerBatteryRadius + mResources.getDimensionPixelSize(R.dimen.pie_battery_increment) * mPieSize);

        mBatteryBackground.setColor(COLOR_BATTERY_BACKGROUND);
        mBatteryLevel = mPolicy.getBatteryLevel();
        if(mBatteryLevel <= PiePolicy.LOW_BATTERY_LEVEL
                && mBatteryLevel > PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(COLOR_BATTERY_JUICE_LOW);
        } else if(mBatteryLevel <= PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(COLOR_BATTERY_JUICE_CRITICAL);
        } else {
            mBatteryJuice.setColor(COLOR_BATTERY_JUICE);
        }

        mStartBattery = mPanel.getDegree() + mEmptyAngle + mPieGap;
        mEndBattery = mPanel.getDegree() + (mPieGap <= 2 ? 88 : 90 - mPieGap);
        mBatteryPathBackground = makeSlice(mStartBattery, mEndBattery, mInnerBatteryRadius, mOuterBatteryRadius, mCenter);
        mBatteryPathJuice = makeSlice(mStartBattery, mStartBattery, mInnerBatteryRadius, mOuterBatteryRadius, mCenter);

        // Colors
        ColorUtils.ColorSettingInfo buttonColorInfo = ColorUtils.getColorSettingInfo(mContext,
                Settings.System.NAV_BUTTON_COLOR);

        mNotificationPaint.setColor(COLOR_STATUS);
        mSnapBackground.setColor(COLOR_SNAP_BACKGROUND);
        mStatusPaint.setColor(COLOR_STATUS);

        if (ColorUtils.getPerAppColorState(mContext)) {
            ColorUtils.ColorSettingInfo colorInfo;
            colorInfo = ColorUtils.getColorSettingInfo(mContext, Settings.System.NAV_BAR_COLOR);
            mPieBackground.setColor(ColorUtils.extractRGB(colorInfo.lastColor) | COLOR_ALPHA_MASK);

            colorInfo = ColorUtils.getColorSettingInfo(mContext, Settings.System.NAV_GLOW_COLOR);
            mPieSelected.setColor(ColorUtils.extractRGB(colorInfo.lastColor) | COLOR_ALPHA_MASK);

            colorInfo = ColorUtils.getColorSettingInfo(mContext, Settings.System.STATUS_ICON_COLOR);
            mClockPaint.setColor(colorInfo.lastColor);
            mAmPmPaint.setColor(colorInfo.lastColor);
            mClockPaint.setColor(colorInfo.lastColor);

            mChevronBackgroundLeft.setColor(ColorUtils.extractRGB(buttonColorInfo.lastColor) | COLOR_OPAQUE_MASK);
            mChevronBackgroundRight.setColor(ColorUtils.extractRGB(buttonColorInfo.lastColor) | COLOR_OPAQUE_MASK);
            mPieOutlines.setColor(ColorUtils.extractRGB(buttonColorInfo.lastColor) | COLOR_ALPHA_MASK);
            mBatteryJuice.setColorFilter(buttonColorInfo.isLastColorNull ? null :
                    new PorterDuffColorFilter(ColorUtils.extractRGB(buttonColorInfo.lastColor) | COLOR_OPAQUE_MASK, Mode.SRC_ATOP));

            buttonColorInfo = ColorUtils.getColorSettingInfo(mContext, Settings.System.NAV_BUTTON_COLOR);
            for (PieItem item : mItems) {
                item.setColor(buttonColorInfo.isLastColorNull ? COLOR_PIE_BUTTON : buttonColorInfo.lastColor);
            }
        } else {
            mPieBackground.setColor(COLOR_PIE_BACKGROUND);
            mPieSelected.setColor(COLOR_PIE_SELECT);
            mPieOutlines.setColor(COLOR_PIE_OUTLINES);
            mClockPaint.setColor(COLOR_STATUS);
            mAmPmPaint.setColor(COLOR_STATUS);
            mChevronBackgroundLeft.setColor(COLOR_CHEVRON_LEFT);
            mChevronBackgroundRight.setColor(COLOR_CHEVRON_RIGHT);
            mBatteryJuice.setColorFilter(null);
        }

        // Notifications
        mNotificationCount = 0;
        mNotificationsRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notifications_start) * mPieSize);
        mNotificationIconSize = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notification_icon_size) * mPieSize);
        mNotificationsRowSize = mResources.getDimensionPixelSize(R.dimen.pie_notification_row_size) * mPieSize;

        if (mPanel.getBar() != null) {
            getNotifications();
        }

        // Measure clock
        measureClock(mPolicy.getSimpleTime());

        // Determine animationspeed
        mOverallSpeed = BASE_SPEED / 4;
        int mInitialSpeed = BASE_SPEED * (mStatusMode == -1 ? 0 : mStatusMode) / 2;

        // Create animators
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i] = new CustomValueAnimator(i);
        }

        // Linear animators
        mAnimators[ANIMATOR_DEC_SPEED15].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_DEC_SPEED15].animator.setInterpolator(new DecelerateInterpolator());
        mAnimators[ANIMATOR_DEC_SPEED15].animator.setStartDelay((int)(mInitialSpeed * 1.5));

        mAnimators[ANIMATOR_ACC_SPEED15].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_ACC_SPEED15].animator.setInterpolator(new AccelerateInterpolator());
        mAnimators[ANIMATOR_ACC_SPEED15].animator.setStartDelay((int)(mInitialSpeed * 1.5));

        // Cascade accelerators
        int count = 0;
        for(int i = ANIMATOR_ACC_INC_1; i < ANIMATOR_ACC_INC_15 + 1; i++) {
            mAnimators[i].duration = 150;
            mAnimators[i].animator.setInterpolator(new DecelerateInterpolator());
            mAnimators[i].animator.setStartDelay((int)(mInitialSpeed * 1.5f + (++count * 75) ));
        }

        // Special purpose
        mAnimators[ANIMATOR_BATTERY_METER].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_BATTERY_METER].animator.setInterpolator(new DecelerateInterpolator());
        mAnimators[ANIMATOR_BATTERY_METER].animator.setStartDelay((int)(mInitialSpeed * 1.5));

        mAnimators[ANIMATOR_SNAP_GROW].manual = true;
        mAnimators[ANIMATOR_SNAP_GROW].animator.setDuration(1000);
        mAnimators[ANIMATOR_SNAP_GROW].animator.setInterpolator(new AccelerateInterpolator());
        mAnimators[ANIMATOR_SNAP_GROW].animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) {
                if (mAnimators[ANIMATOR_SNAP_GROW].fraction == 1) {
                    for (int i = 0; i < 3; i++) {
                        SnapPoint snap = mSnapPoint[i];
                        if (snap.active) {
                            if(mHapticFeedback) mVibrator.vibrate(2);
                            mStatusPanel.hidePanels(true);
                            deselect();
                            animateOut();
                            mPanel.reorient(snap.gravity, true);
                        }
                    }
                }
            }});
*/

        // Re-do Pie
        mPie.update();
    }

    private void measureClock(String text) {
/*
        mClockText = text;

        mClockTextAmPm = mPolicy.getAmPm();
        mClockTextAmPmSize = mAmPmPaint.measureText(mClockTextAmPm);
        mClockTextTotalOffset = 0;

        for( int i = 0; i < mClockText.length(); i++ ) {
            char character = mClockText.charAt(i);
            float measure = mClockPaint.measureText("" + character); 
            mClockTextOffsets[i] = measure * (character == '1' || character == ':' ? 0.5f : 0.8f);
            mClockTextTotalOffset += measure * (character == '1' || character == ':' ? 0.6f : 0.9f);
        }

        mClockTextRotation = mPanel.getDegree() + (180 - (mClockTextTotalOffset * 360 /
                (2f * (mStatusRadius+Math.abs(mClockOffset)) * (float)Math.PI))) - 2;*/
    }

    private void getNotifications() {/*
        NotificationData notifData = mPanel.getBar().getNotificationData();
        if (notifData != null) {

            mNotificationText = new String[notifData.size()];
            mNotificationIcon = new Bitmap[notifData.size()];
            mNotificationPath = new Path[notifData.size()];

            for (int i = 0; i < notifData.size(); i++ ) {
                NotificationData.Entry entry = notifData.get(i);
                StatusBarNotification statusNotif = entry.notification;
                if (statusNotif == null) continue;
                Notification notif = statusNotif.notification;
                if (notif == null) continue;
                CharSequence tickerText = notif.tickerText;
                if (tickerText == null) continue;

                if (entry.icon != null) {
                    StatusBarIconView iconView = entry.icon;
                    StatusBarIcon icon = iconView.getStatusBarIcon();
                    Drawable drawable = entry.icon.getIcon(mContext, icon);
                    if (!(drawable instanceof BitmapDrawable)) continue;
                    
                    mNotificationIcon[mNotificationCount] = ((BitmapDrawable)drawable).getBitmap();

                    String text = tickerText.toString();
                    if (text.length() > 100) text = text.substring(0, 100) + "..";
                    mNotificationText[mNotificationCount] = text;

                    Path notifictionPath = new Path();
                    notifictionPath.addCircle(mCenter.x, mCenter.y, mNotificationsRadius +
                            (mNotificationsRowSize * mNotificationCount) + (mNotificationsRowSize-mNotificationTextSize),
                            Path.Direction.CW);
                    mNotificationPath[mNotificationCount] = notifictionPath;

                    mNotificationCount++;
                }
            }
        }*/
    }
}
