/*
 * Copyright (C) 2013 SlimRoms (blk_jack)
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

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.provider.Settings;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;

import com.android.systemui.R;


public class ShortcutsWidget extends LinearLayout {
    static final String TAG = "ShortcutsWidget";

    int viewWidth = 0;
    int viewHeight = 0;
    int tmpMargin = 0;
    int oldMargin = 0;
    int getPxPadding;
    int defaultMargin = 5; //px
    int mDefaultChildSizeDp = 46; //dp
    int mDefaultChildSizePx; //px
    int mDefaultChildWidth; //px

    private boolean mToggle;
    private boolean mColorizeToggle;
    private int mColor;
    private String mTargets;
    private int mQuantity;

    private boolean mOverflow;

    private ContentResolver resolver;
    private Context mContext;
    private Handler mHandler;
    private LayoutInflater mInflater;

    private ShortcutsSettingsObserver mObserver = null;
    private View.OnClickListener mExternalClickListener;
    private View.OnLongClickListener mExternalLongClickListener;
    private ViewTreeObserver observer;

    private final OnGlobalLayoutListener mOnGlobalLayoutListener = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            modifyShortcutLayout();
            ShortcutsWidget.this.getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    };

    public ShortcutsWidget(Context context) {
        super(context);
    }

    public ShortcutsWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler();
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        resolver = mContext.getContentResolver();

        mToggle = Settings.System.getIntForUser(resolver,
                Settings.System.NOTIFICATION_SHORTCUTS_TOGGLE, 0, UserHandle.USER_CURRENT) != 0;
        mTargets = Settings.System.getStringForUser(resolver,
                Settings.System.NOTIFICATION_SHORTCUTS_TARGETS, UserHandle.USER_CURRENT);
        mQuantity = Settings.System.getIntForUser(resolver,
                Settings.System.NOTIFICATION_SHORTCUTS_QUANTITY, 6, UserHandle.USER_CURRENT);
        mColor = Settings.System.getIntForUser(resolver,
                Settings.System.NOTIFICATION_SHORTCUTS_COLOR, 0xFFDFE0E0, UserHandle.USER_CURRENT);
        mColorizeToggle = Settings.System.getIntForUser(resolver,
                Settings.System.NOTIFICATION_SHORTCUTS_COLORIZE_TOGGLE, 1, UserHandle.USER_CURRENT) != 0;

        mDefaultChildSizePx = (int) convertDpToPixel(mDefaultChildSizeDp, mContext);
        mDefaultChildWidth = mDefaultChildSizePx + (defaultMargin * 2); //px
    }

    private class ShortcutsSettingsObserver extends ContentObserver {
        public ShortcutsSettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_SHORTCUTS_TOGGLE), true, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_SHORTCUTS_TARGETS), true, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_SHORTCUTS_QUANTITY), true, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_SHORTCUTS_COLOR), true, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATION_SHORTCUTS_COLORIZE_TOGGLE), true, this, UserHandle.USER_ALL);
        }

        public void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            if (uri.equals(Settings.System.getUriFor(Settings.System.NOTIFICATION_SHORTCUTS_TOGGLE))) {
                mToggle = Settings.System.getIntForUser(resolver,
                        Settings.System.NOTIFICATION_SHORTCUTS_TOGGLE, 0, UserHandle.USER_CURRENT) != 0;
                if (mToggle) {
                    recreateShortcutLayout();
                } else {
                    removeAllViews();
                }
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.NOTIFICATION_SHORTCUTS_TARGETS))) {
                mTargets = Settings.System.getStringForUser(resolver,
                        Settings.System.NOTIFICATION_SHORTCUTS_TARGETS, UserHandle.USER_CURRENT);
                recreateShortcutLayout();
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.NOTIFICATION_SHORTCUTS_QUANTITY))) {
                mQuantity = Settings.System.getIntForUser(resolver,
                        Settings.System.NOTIFICATION_SHORTCUTS_QUANTITY, 6, UserHandle.USER_CURRENT);
                recreateShortcutLayout();
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.NOTIFICATION_SHORTCUTS_COLOR))) {
                mColor = Settings.System.getIntForUser(resolver,
                        Settings.System.NOTIFICATION_SHORTCUTS_COLOR, 0xFFDFE0E0, UserHandle.USER_CURRENT);
                if (mToggle) {
                    recreateShortcutLayout();
                }
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.NOTIFICATION_SHORTCUTS_COLORIZE_TOGGLE))) {
                mColorizeToggle = Settings.System.getIntForUser(resolver,
                        Settings.System.NOTIFICATION_SHORTCUTS_COLORIZE_TOGGLE, 1, UserHandle.USER_CURRENT) != 0;
                recreateShortcutLayout();
            }
        }
    }

    public void modifyShortcutLayout() {
        int mChildCount = getChildCount();
        int tmpWidth = getContWidth();

        // Check if width has changed
        if (mChildCount == 0 || tmpWidth == 0) {
            return;
        } else if (viewWidth != tmpWidth) {
            viewWidth = tmpWidth;
            if (mToggle) {
                if (determineMargins()) {
                    modifyMargins();
                } else {
                    recreateShortcutLayout();
                }
            } else {
                removeAllViews();
            }
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    public void setupShortcuts() {
        destroyShortcuts();

        mObserver = new ShortcutsSettingsObserver(mHandler);
        mObserver.observe();

        recreateShortcutLayout();
    }

    public void destroyShortcuts() {
        try {
            removeAllViews();
        } catch (Exception e) {
        }

        if (mObserver != null) {
            mObserver.unobserve();
        }
    }

    public static float convertDpToPixel(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * (metrics.densityDpi/160f);
        return px;
    }

    public int getContWidth() {
        HorizontalScrollView mShortcutScroll = (HorizontalScrollView) ShortcutsWidget.this.getParent();

        // return mContext.getResources().getDisplayMetrics().widthPixels;
        return mShortcutScroll.getWidth();
    }

    public int getShortcutChildCount() {
        String[] mStoredTargets;

        final String EMPTY_TARGET = "empty";

        int drawnChildren = 0;
        try {
            mStoredTargets = mTargets.split("\\|");
        } catch (NullPointerException e) {
            return drawnChildren;
        }

        for (int i = 0; i < mQuantity; i++) {
            if (i < mStoredTargets.length) {
                String uri = mStoredTargets[i];
                if (!uri.equals(EMPTY_TARGET)) {
                    drawnChildren += 1;
                }
            }
        }
        return drawnChildren;
    }

    public boolean determineMargins() {
        int mScrollViewWidth = getContWidth();
        if (mScrollViewWidth == 0) { return false; }

        int mShortcutChildCount = getShortcutChildCount();
        if (mShortcutChildCount == 0) { return false; }

        // Divide width of container by children
        int mShortcutWidth = mScrollViewWidth / mShortcutChildCount;
        // If this number is less than the minimum child width..
        if (mShortcutWidth < mDefaultChildWidth) {
            mOverflow = true;
            // Uh oh, we have overscroll!
            // Round down to figure out how many can fit.
            int tmpCount = mScrollViewWidth / mDefaultChildWidth;
            // Get padding for each side
            tmpMargin = ((mScrollViewWidth - (tmpCount * mDefaultChildWidth)) / tmpCount) / 2;
        } else {
            mOverflow = false;
            tmpMargin = (mShortcutWidth - mDefaultChildWidth) / 2;
        }
        return true;
    }

    public void modifyMargins() {
        int mChildCount;
        try {
            mChildCount = getChildCount();
        } catch (NullPointerException e) {
            return;
        }
        if (oldMargin == tmpMargin) {
            return;
        }
        for (int j = 0; j < mChildCount; j++) {
            View iv = getChildAt(j);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            lp.setMargins(defaultMargin + tmpMargin, 0, defaultMargin + tmpMargin, getPxPadding);
        }
        oldMargin = tmpMargin;
        invalidate();
        requestLayout();
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    public void recreateShortcutLayout() {
        removeAllViews();
        determineMargins();
        buildShortcuts();
    }

    public void buildShortcuts() {
        String[] mStoredTargets;

        final String ICON_RESOURCE = "icon_resource";
        final String ICON_PACKAGE = "icon_package";
        final String ICON_FILE = "icon_file";
        final String EMPTY_TARGET = "empty";

        final Resources res = mContext.getResources();

        if (mTargets == null) {
            mTargets = "empty|empty|empty|empty|empty|empty|empty|empty|empty|empty|empty|empty|empty|empty|empty|empty";
            Settings.System.putString(mContext.getContentResolver(),
                    Settings.System.NOTIFICATION_SHORTCUTS_TARGETS, mTargets);
            return;
        }

        mStoredTargets = mTargets.split("\\|");
        ArrayList<ImageView> storedNotifications = new ArrayList<ImageView>();
        final PackageManager packMan = mContext.getPackageManager();

        for (int i = 0; i < mQuantity; i++) {
            if (i < mStoredTargets.length) {
                String uri = mStoredTargets[i];
                if (!uri.equals(EMPTY_TARGET)) {
                    try {
                        final Intent in = Intent.parseUri(uri,0);
                        Drawable front = null;
                        boolean colorizeIcon = true;
                        if (in.hasExtra(ICON_FILE)) {
                            String fSource = in.getStringExtra(ICON_FILE);
                            if (fSource != null) {
                                File fPath = new File(fSource);
                                if (fPath.exists()) {
                                    front = new BitmapDrawable(res, BitmapFactory.decodeFile(fSource));
                                }
                            }
                            if (!mColorizeToggle) {
                                colorizeIcon = false;
                            }
                        } else if (in.hasExtra(ICON_RESOURCE)) {
                            String rSource = in.getStringExtra(ICON_RESOURCE);
                            String rPackage = in.getStringExtra(ICON_PACKAGE);
                            if (rSource != null) {
                                if (rPackage != null) {
                                    try {
                                        Context rContext = mContext.createPackageContext(rPackage, 0);
                                        int id = rContext.getResources().getIdentifier(rSource, "drawable", rPackage);
                                        front = rContext.getResources().getDrawable(id);
                                    } catch (NameNotFoundException e) {
                                        e.printStackTrace();
                                    } catch (NotFoundException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    front = res.getDrawable(res.getIdentifier(rSource, "drawable", "android"));
                                }
                            }
                        }
                        if (front == null) {
                            ActivityInfo aInfo = in.resolveActivityInfo(packMan, PackageManager.GET_ACTIVITIES);
                            if (aInfo != null) {
                                front = aInfo.loadIcon(packMan);
                            } else {
                                front = res.getDrawable(android.R.drawable.sym_def_app_icon);
                            }
                            if (!mColorizeToggle) {
                                colorizeIcon = false;
                            }
                        }
                        // Draw ImageView?
                        ImageView iv = new ImageView(mContext);
                        try {
                            if (colorizeIcon) {
                                Bitmap colorBitmap = ((BitmapDrawable) front).getBitmap();
                                Bitmap grayscaleBitmap = toGrayscale(colorBitmap);

                                Paint pp = new Paint();
                                PorterDuffColorFilter frontFilter = new PorterDuffColorFilter(mColor, PorterDuff.Mode.MULTIPLY);
                                pp.setColorFilter(frontFilter);
                                Canvas cc = new Canvas(grayscaleBitmap);
                                cc.drawBitmap(grayscaleBitmap, 0, 0, pp);

                                iv.setImageBitmap(grayscaleBitmap);
                            } else {
                                iv.setImageDrawable(front);
                            }
                        } catch (Exception e) {
                            if (colorizeIcon) {
                                PorterDuffColorFilter frontFilter = new PorterDuffColorFilter(mColor, PorterDuff.Mode.MULTIPLY);
                                front.setColorFilter(frontFilter);
                            }
                            iv.setImageDrawable(front);
                        }

                        getPxPadding = (int) convertDpToPixel(5, mContext);
                        iv.setPadding(getPxPadding, getPxPadding, getPxPadding, getPxPadding);
                        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(mDefaultChildSizePx, mDefaultChildSizePx);
                        int determinedMargin = defaultMargin + tmpMargin;
                        layoutParams.setMargins(determinedMargin, 0, determinedMargin, getPxPadding);

                        iv.setLayoutParams(layoutParams);
                        iv.setBackgroundResource(R.drawable.notification_shortcut_bg);
                        iv.setLongClickable(true);
                        iv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                                } catch (RemoteException e) {
                                }
                                if (mExternalClickListener != null) {
                                    mExternalClickListener.onClick(v);
                                }
                                try {
                                    Intent i = in;
                                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    v.getContext().startActivity(i);
                                } catch (Exception e) {
                                }
                            }
                        });
                        iv.setOnLongClickListener(mShortcutLongClickListener);
                        addView(iv);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    private View.OnLongClickListener mShortcutLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            try {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            } catch (RemoteException e) {
            }
            if (mExternalLongClickListener != null) {
                mExternalLongClickListener.onLongClick(v);
            }
            try {
                Intent i = new Intent("android.settings.slim.notificationshortcuts.NOTIFICATION_SHORTCUTS");
                i.addCategory(Intent.CATEGORY_DEFAULT);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                v.getContext().startActivity(i);
            } catch (Exception e) {
            }
            return true;
        }
    };

    void setGlobalButtonOnClickListener(View.OnClickListener listener) {
        mExternalClickListener = listener;
    }

    void setGlobalButtonOnLongClickListener(View.OnLongClickListener listener) {
        mExternalLongClickListener = listener;
    }
}
