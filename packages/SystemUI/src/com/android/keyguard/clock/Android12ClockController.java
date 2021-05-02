/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import static android.app.slice.Slice.HINT_LIST_ITEM;

import android.animation.ValueAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.text.LineBreaker;
import android.graphics.Typeface;
import android.graphics.Paint.Style;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.net.Uri;
import android.text.Html;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.graphics.ColorUtils;
import com.android.internal.util.Converter;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.android.systemui.synth.animation.ResizeAnimation;
import com.android.systemui.synth.transition.Scale;
import com.android.keyguard.KeyguardSliceView.KeyguardSliceTextView;
import com.android.keyguard.KeyguardSliceView.Row;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.SliceViewManager;
import androidx.slice.core.SliceQuery;
import androidx.slice.widget.ListContent;
import androidx.slice.widget.RowContent;
import androidx.slice.widget.SliceContent;
import androidx.slice.widget.SliceLiveData;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.android.systemui.statusbar.phone
        .KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class Android12ClockController implements ClockPlugin {

    private final static String PROPNAME_HEIGHT = "PROPNAME_HEIGHT";
    private final static String PROPNAME_WIDTH = "PROPNAME_WIDTH";

    private final float mTextSizeNormal = 48f;
    private final float mTextSizeBig = 96f;
    private final float mSliceTextSize = 24f;
    private final float mTitleTextSize = 28f;
    private boolean mHasVisibleNotification = false;
    private boolean mClockState = false;
    private boolean mClockAnimating = false;
    private float clockDividY = 6f;

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Root view of clock.
     */
    private ClockLayout mView;
    private ClockLayout mBigClockView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mClock;
    private TextClock mClockCenter;
    private FrameLayout mContainerClock;
    private FrameLayout mContainerClockCenter;
    private ConstraintSet mContainerSet = new ConstraintSet();
    private ConstraintSet mContainerSetBig = new ConstraintSet();

    private Context mContext;
    
    private Row mRow;
    private TextView mTitle;
    private int mIconSize;
    private int mIconSizeWithHeader;
    private Slice mSlice;
    private boolean mHasHeader;
    private final int mRowWithHeaderPadding;
    private final int mRowPadding;
    private float mRowTextSize;
    private float mRowWithHeaderTextSize;
    private final HashMap<View, PendingIntent> mClickActions;
    private Uri mKeyguardSliceUri;

    private int mTextColor;
    private float mDarkAmount = 0;
    private int mRowHeight = 0;

    private Typeface mSliceTypeface;
    private Typeface mClockTypeface;

    private int mClockHeight = 0;
    private int mClockWidth = 0;
    private int mClockCenterHeight = 0;
    private int mClockCenterWidth = 0;

    /**
     * Time and calendars to check the date
     */
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());
    
    private ValueAnimator animClockSize;
    private ValueAnimator animClockCenterSize;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public Android12ClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mContext = mLayoutInflater.getContext();
        mColorExtractor = colorExtractor;
        mClickActions = new HashMap<>();
        mRowPadding = mResources.getDimensionPixelSize(R.dimen.subtitle_clock_padding);
        mRowWithHeaderPadding = mResources.getDimensionPixelSize(R.dimen.header_subtitle_padding);
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.android_12_clock, null);
        mClock = mView.findViewById(R.id.clock);
        mClockCenter = mView.findViewById(R.id.clock_center);
        mContainerClock = mView.findViewById(R.id.clock_container);
        mContainerClockCenter = mView.findViewById(R.id.clock_center_container);
        mClockTypeface = mClock.getTypeface();

        if (mClockHeight == 0) mClockHeight = mContainerClock.getHeight();
        if (mClockWidth == 0) mClockWidth = mContainerClock.getWidth();
        if (mClockCenterHeight == 0) mClockCenterHeight = mContainerClockCenter.getHeight();
        if (mClockCenterWidth == 0) mClockCenterWidth = mContainerClockCenter.getWidth();
        
        mContainerClockCenter.setVisibility(View.VISIBLE);
        mContainerClock.setVisibility(View.VISIBLE);

        mTitle = mView.findViewById(R.id.title);
        mRow = mView.findViewById(R.id.row);
        mIconSize = (int) mContext.getResources().getDimension(R.dimen.widget_icon_size);
        mIconSizeWithHeader = (int) mContext.getResources().getDimension(R.dimen.header_icon_size);
        mRowTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.widget_label_font_size);
        mRowWithHeaderTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.header_row_font_size);
        mTextColor = Utils.getColorAttrDefaultColor(mContext, R.attr.wallpaperTextColor);

        animClockSize = ValueAnimator.ofInt(0, 48);
        animClockSize.setDuration(500);
        animClockSize.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                mClock.setTextSize((float) Converter.dpToPx(mContext, (int) animation.getAnimatedValue()));
                mClock.setAlpha(animation.getAnimatedFraction());
                mClock.setTranslationX(MathUtils.lerp(-100f, 0f, animation.getAnimatedFraction()));
            }
        });

        animClockCenterSize = ValueAnimator.ofInt(0, 120);
        animClockCenterSize.setDuration(500);
        animClockCenterSize.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                mClockCenter.setTextSize((float) Converter.dpToPx(mContext, (int) animation.getAnimatedValue()));
                mClockCenter.setAlpha(animation.getAnimatedFraction());
                mClockCenter.setTranslationY(MathUtils.lerp(-300f, 0f, animation.getAnimatedFraction()));
            }
        });
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mClock = null;
        mClockCenter = null;
    }

    @Override
    public String getName() {
        return "android_12";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_android_12);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = mLayoutInflater.inflate(R.layout.android_12_clock, null);
        TextClock previewClock = mView.findViewById(R.id.clock);

        return mRenderer.createPreview(previewView, width, height);
    }

    @Override
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public View getBigClockView() {
        return null;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return (int) (totalHeight / clockDividY);
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        mClock.setTextColor(color);
    }

    @Override
    public void setTypeface(Typeface tf) {
        mClockTypeface = tf;
        mClockCenter.setTypeface(tf);
    }

    @Override
    public void setDateTypeface(Typeface tf) {
        for (int i = 0; i < mRow.getChildCount(); i++) {
            KeyguardSliceTextView child = (KeyguardSliceTextView) mRow.getChildAt(i);
            child.setTypeface(tf);
        }
        mTitle.setTypeface(tf);
        mSliceTypeface = tf;
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {}

    @Override
    public void setSlice(Slice slice) {
        mSlice = slice;
        if (mSlice == null) {
            mRow.setVisibility(View.GONE);
            mHasHeader = false;
            return;
        }
        mClickActions.clear();

        ListContent lc = new ListContent(mContext, mSlice);
        SliceContent headerContent = lc.getHeader();
        mHasHeader = headerContent != null && !headerContent.getSliceItem().hasHint(HINT_LIST_ITEM);
        List<SliceContent> subItems = new ArrayList<>();
        for (int i = 0; i < lc.getRowItems().size(); i++) {
            SliceContent subItem = lc.getRowItems().get(i);
            String itemUri = subItem.getSliceItem().getSlice().getUri().toString();
            // Filter out the action row
            if (!KeyguardSliceProvider.KEYGUARD_ACTION_URI.equals(itemUri)) {
                subItems.add(subItem);
            }
        }

        final int subItemsCount = subItems.size();
        final int blendedColor = getTextColor();
        final int startIndex = mHasHeader ? 1 : 0; // First item is header; skip it
        mRow.setVisibility(subItemsCount > 0 ? View.VISIBLE : View.GONE);

        if (!mHasHeader) {
            mTitle.setVisibility(View.GONE);
        } else {
            mTitle.setVisibility(View.VISIBLE);

            RowContent header = lc.getHeader();
            SliceItem mainTitle = header.getTitleItem();
            CharSequence title = mainTitle != null ? mainTitle.getText() : null;
            mTitle.setText(title);
            mTitle.setTextSize(mTitleTextSize);
            if (mSliceTypeface != null) mTitle.setTypeface(mSliceTypeface);
            if (header.getPrimaryAction() != null
                    && header.getPrimaryAction().getAction() != null) {
                mClickActions.put(mTitle, header.getPrimaryAction().getAction());
            }
        }

        for (int i = startIndex; i < subItemsCount; i++) {
            RowContent rc = (RowContent) subItems.get(i);
            SliceItem item = rc.getSliceItem();
            final Uri itemTag = item.getSlice().getUri();
            final boolean isWeatherSlice = itemTag.toString().equals(KeyguardSliceProvider.KEYGUARD_WEATHER_URI);
            final boolean isDateSlice = itemTag.toString().equals(KeyguardSliceProvider.KEYGUARD_DATE_URI);
            // Try to reuse the view if already exists in the layout
            KeyguardSliceTextView button = mRow.findViewWithTag(itemTag);
            if (button == null) {
                button = new KeyguardSliceTextView(mContext);
                button.setShouldTintDrawable(!isWeatherSlice);
                button.setTextSize(isDateSlice ? mTitleTextSize : mSliceTextSize);
                button.setTextColor(blendedColor);
                button.setGravity(Gravity.START);
                button.setTag(itemTag);
                final int viewIndex = i - (mHasHeader ? 0 : 0);
                mRow.addView(button, viewIndex);
            } else {
                button.setShouldTintDrawable(!isWeatherSlice);
                button.setTextSize(isDateSlice ? mTitleTextSize : mSliceTextSize);
                button.setGravity(Gravity.START);
            }

            if (mSliceTypeface != null) button.setTypeface(mSliceTypeface);
            // button.setVisibility(isDateSlice ? View.GONE : View.VISIBLE);

            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) button.getLayoutParams();
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.START;
            layoutParams.topMargin = 8;
            layoutParams.bottomMargin = 8;
            button.setLayoutParams(layoutParams);

            PendingIntent pendingIntent = null;
            if (rc.getPrimaryAction() != null) {
                pendingIntent = rc.getPrimaryAction().getAction();
            }
            mClickActions.put(button, pendingIntent);

            final SliceItem titleItem = rc.getTitleItem();
            button.setText(titleItem == null ? null : titleItem.getText());
            button.setContentDescription(rc.getContentDescription());

            Drawable iconDrawable = null;
            SliceItem icon = SliceQuery.find(item.getSlice(),
                    android.app.slice.SliceItem.FORMAT_IMAGE);
            if (icon != null) {
                final int iconSize = Converter.dpToPx(mContext, (((int) mSliceTextSize) - 4));
                iconDrawable = icon.getIcon().loadDrawable(mContext);
                if (iconDrawable != null) {
                    final int width = (int) (iconDrawable.getIntrinsicWidth()
                            / (float) iconDrawable.getIntrinsicHeight() * (iconSize));
                    iconDrawable.setBounds(0, 0, Math.max(width, 1), (iconSize));
                }
            }
            button.setCompoundDrawables(iconDrawable, null, null, null);
        }

        // Removing old views
        for (int i = 0; i < mRow.getChildCount(); i++) {
            View child = mRow.getChildAt(i);
            if (!mClickActions.containsKey(child)) {
                mRow.removeView(child);
                i--;
            }
        }

        mTitle.requestLayout();
        mRow.requestLayout();

        mRowHeight = mRow.getHeight() + (mHasHeader ? mTitle.getHeight() : 0);
        // if (mRow.getChildCount() != 0) mContainerSetBig.setMargin(mClock.getId(), ConstraintSet.TOP, mRowHeight);
    };

    @Override
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        if (hasVisibleNotifications == mHasVisibleNotification) {
            return;
        }
        mHasVisibleNotification = hasVisibleNotifications;
        animate();
    }

    private void animate() {
        if (!mHasVisibleNotification) {
            animClockSize.setCurrentFraction(1f);
            animClockSize.reverse();
            animClockCenterSize.setCurrentFraction(0f);
            animClockCenterSize.start();
            /*
            TransitionManager.beginDelayedTransition(mContainerClock,
            new Fade().setDuration(500));
            mContainerClock.setVisibility(View.GONE);
            TransitionManager.beginDelayedTransition(mContainerClockCenter,
            new Fade().setDuration(500));
            mContainerClockCenter.setVisibility(View.VISIBLE);
            */

        } else {
            animClockSize.setCurrentFraction(0f);
            animClockSize.start();
            animClockCenterSize.setCurrentFraction(1f);
            animClockCenterSize.reverse();
            /*
            TransitionManager.beginDelayedTransition(mContainerClock,
            new Fade().setDuration(500));
            mContainerClock.setVisibility(View.VISIBLE);
            TransitionManager.beginDelayedTransition(mContainerClockCenter,
            new Fade().setDuration(500));
            mContainerClockCenter.setVisibility(View.GONE);
            */
        }
    }

    @Override
    public void onTimeTick() {
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mView.setDarkAmount(darkAmount);
        if (mClockTypeface != null) mClock.setTypeface(Typeface.create(mClockTypeface, (mClockTypeface.getWeight() - ((int) (300f * darkAmount))), mClockTypeface.isItalic()));
        for (int i = 0; i < mRow.getChildCount(); i++) {
            KeyguardSliceTextView child = (KeyguardSliceTextView) mRow.getChildAt(i);
            final boolean isDateSlice = child.getTag().toString().equals(KeyguardSliceProvider.KEYGUARD_DATE_URI);
            child.setTextSize((isDateSlice ? mTitleTextSize : mSliceTextSize) + (8f * darkAmount));
        }
        mTitle.setTextSize(mTitleTextSize + (8f * darkAmount));
        mRow.setDarkAmount(darkAmount);
        mTitle.requestLayout();
        mRow.requestLayout();
        mDarkAmount = darkAmount;
        updateTextColors();
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }

    private void updateTextColors() {
        final int blendedColor = getTextColor();
        mTitle.setTextColor(blendedColor);
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(blendedColor);
            }
        }
    }

    int getTextColor() {
        return ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
    }
}
