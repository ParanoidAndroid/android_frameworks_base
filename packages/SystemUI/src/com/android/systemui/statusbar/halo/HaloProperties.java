/*
 * Copyright (C) 2013 ParanoidAndroid.
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

package com.android.systemui.statusbar.halo;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;
import android.util.TypedValue;
import android.provider.Settings;

import com.android.systemui.R;

public class HaloProperties extends FrameLayout {

    public enum Overlay {
        NONE,
        BLACK_X,
        BACK_LEFT,
        BACK_RIGHT,
        DISMISS,
        SILENCE,
        CLEAR_ALL
    }

    private LayoutInflater mInflater;

    protected int mHaloX = 0, mHaloY = 0;
    protected float mHaloContentAlpha = 0;

    private Drawable mHaloDismiss;
    private Drawable mHaloBackL;
    private Drawable mHaloBackR;
    private Drawable mHaloBlackX;
    private Drawable mHaloClearAll;
    private Drawable mHaloSilence;
    private Drawable mHaloCurrentOverlay;

    protected View mHaloBubble;
    protected ImageView mHaloBg, mHaloIcon, mHaloOverlay;

    protected View mHaloContentView, mHaloTickerContent;
    protected TextView mHaloTextViewR, mHaloTextViewL;
    protected RelativeLayout mHaloTickerContainer;

    protected View mHaloNumberView;
    protected TextView mHaloNumber;

    private float mFraction = 1.0f;

    CustomObjectAnimator mHaloOverlayAnimator;

    public HaloProperties(Context context) {
        super(context);

        mHaloDismiss = mContext.getResources().getDrawable(R.drawable.halo_dismiss);
        mHaloBackL = mContext.getResources().getDrawable(R.drawable.halo_back_left);
        mHaloBackR = mContext.getResources().getDrawable(R.drawable.halo_back_right);
        mHaloBlackX = mContext.getResources().getDrawable(R.drawable.halo_black_x);
        mHaloClearAll = mContext.getResources().getDrawable(R.drawable.halo_clear_all);
        mHaloSilence = mContext.getResources().getDrawable(R.drawable.halo_silence);

        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mHaloBubble = mInflater.inflate(R.layout.halo_bubble, null);
        mHaloBg = (ImageView) mHaloBubble.findViewById(R.id.halo_bg);
        mHaloIcon = (ImageView) mHaloBubble.findViewById(R.id.app_icon);
        mHaloOverlay = (ImageView) mHaloBubble.findViewById(R.id.halo_overlay);

        mHaloContentView = mInflater.inflate(R.layout.halo_speech, null);
        mHaloTickerContainer = (RelativeLayout)mHaloContentView.findViewById(R.id.container);
        mHaloTickerContent = mHaloContentView.findViewById(R.id.ticker);
        mHaloTextViewR = (TextView) mHaloTickerContent.findViewById(R.id.bubble_r);
        mHaloTextViewL = (TextView) mHaloTickerContent.findViewById(R.id.bubble_l);
        mHaloTextViewL.setAlpha(0f);
        mHaloTextViewR.setAlpha(0f);

        mHaloNumberView = mInflater.inflate(R.layout.halo_number, null);
        mHaloNumber = (TextView) mHaloNumberView.findViewById(R.id.number);
        mHaloNumber.setAlpha(0f);

        mFraction = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        setHaloSize(mFraction);

        mHaloOverlayAnimator = new CustomObjectAnimator(this);
    }

    public void setHaloSize(float fraction) {

        final int newBubbleSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * fraction);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newBubbleSize, newBubbleSize);
        mHaloBg.setLayoutParams(layoutParams);
        mHaloIcon.setLayoutParams(layoutParams);
        mHaloOverlay.setLayoutParams(layoutParams);

        final int newNumberSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_size) * fraction);
        final int newNumberTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_text_size) * fraction);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(newNumberSize, newNumberSize);
        mHaloNumber.setLayoutParams(layoutParams2);
        mHaloNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, newNumberTextSize);

        final int newSpeechTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_text_size) * fraction);
        mHaloTextViewR.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSpeechTextSize);
        mHaloTextViewL.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSpeechTextSize);

        updateResources();
    }

    public void setHaloX(int value) {
        mHaloX = value;
    }

    public void setHaloY(int value) {
        mHaloY = value;
    }

    public int getHaloX() {
        return mHaloX; 
    }

    public int getHaloY() {
        return mHaloY;
    }

    public void setHaloContentAlpha(float value) {
        mHaloContentAlpha = value;
        mHaloTextViewL.setAlpha(value);
        mHaloTextViewR.setAlpha(value);
    }

    public float getHaloContentAlpha() {
        return mHaloContentAlpha;
    }

    public void setHaloOverlay(Overlay overlay) {
        setHaloOverlay(overlay, mHaloOverlay.getAlpha());
    }

    public void setHaloOverlay(Overlay overlay, float overlayAlpha) {

        Drawable d = null;

        switch(overlay) {
            case BLACK_X:
                d = mHaloBlackX;
                break;
            case BACK_LEFT:
                d = mHaloBackL;
                break;
            case BACK_RIGHT:
                d = mHaloBackR;
                break;
            case DISMISS:
                d = mHaloDismiss;
                break;
            case SILENCE:
                d = mHaloSilence;
                break;
            case CLEAR_ALL:
                d = mHaloClearAll;
                break;
        }

        if (d != mHaloCurrentOverlay) {
            mHaloOverlay.setImageDrawable(d);
            mHaloCurrentOverlay = d;
        }

        mHaloOverlay.setAlpha(overlayAlpha);
        updateResources();
    }

    public void updateResources() {

        final int iconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * mFraction);
        final int newSize = (int)(getWidth() * 0.9f) - iconSize;
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(newSize, LinearLayout.LayoutParams.WRAP_CONTENT);
        mHaloTickerContainer.setLayoutParams(layoutParams);

        mHaloContentView.measure(MeasureSpec.getSize(mHaloContentView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloContentView.getMeasuredHeight()));
        mHaloContentView.layout(0, 0, 0, 0);

        mHaloBubble.measure(MeasureSpec.getSize(mHaloBubble.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloBubble.getMeasuredHeight()));
        mHaloBubble.layout(0, 0, 0, 0);

        mHaloNumberView.measure(MeasureSpec.getSize(mHaloNumberView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloNumberView.getMeasuredHeight()));
        mHaloNumberView.layout(0, 0, 0, 0);
    }
}
