package com.speedywatch.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class ScreenLockButton extends View {
    interface Listener {
        void onLockRequested();

        void onUnlockRequested();

    }

    private static final long UNLOCK_HOLD_MILLIS = 1_200L;
    private static final int ACTIVE = Color.rgb(255, 0, 51);

    private final Listener listener;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF progressBounds = new RectF();
    private final Drawable unlockedIcon;
    private final Drawable lockedIcon;
    private final ValueAnimator holdAnimator;

    private boolean locked;
    private boolean holdCancelled;
    private boolean gestureStartedLocked;
    private float holdProgress;

    ScreenLockButton(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        float density = getResources().getDisplayMetrics().density;

        backgroundPaint.setColor(Color.argb(150, 15, 15, 15));
        backgroundPaint.setStyle(Paint.Style.FILL);

        trackPaint.setColor(Color.argb(105, 255, 255, 255));
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(2f * density);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint.setColor(ACTIVE);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(2.5f * density);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        unlockedIcon = requireIcon(R.drawable.ic_lock_open);
        lockedIcon = requireIcon(R.drawable.ic_lock_closed);

        holdAnimator = ValueAnimator.ofFloat(0f, 1f);
        holdAnimator.setDuration(UNLOCK_HOLD_MILLIS);
        holdAnimator.setInterpolator(new LinearInterpolator());
        holdAnimator.addUpdateListener(animation -> {
            holdProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        holdAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                holdCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!holdCancelled && locked && holdProgress >= 1f) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    listener.onUnlockRequested();
                }
            }
        });

        setFocusable(true);
        setClickable(true);
        updateContentDescription();
    }

    void setLocked(boolean locked) {
        if (this.locked == locked) {
            return;
        }
        cancelUnlockHold();
        this.locked = locked;
        updateContentDescription();
        invalidate();
    }

    boolean isLocked() {
        return locked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float visualRadius = 18f * density;
        float ringRadius = 20f * density;

        canvas.drawCircle(centerX, centerY, visualRadius, backgroundPaint);
        progressBounds.set(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius
        );
        canvas.drawArc(progressBounds, -90f, 360f, false, trackPaint);
        if (locked && holdProgress > 0f) {
            canvas.drawArc(progressBounds, -90f, 360f * holdProgress, false, progressPaint);
        }

        int iconSize = Math.round(18f * density);
        int left = Math.round(centerX - iconSize / 2f);
        int top = Math.round(centerY - iconSize / 2f);
        Drawable icon = locked ? lockedIcon : unlockedIcon;
        icon.setBounds(left, top, left + iconSize, top + iconSize);
        icon.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                gestureStartedLocked = locked;
                if (locked) {
                    startUnlockHold();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!contains(event.getX(), event.getY())) {
                    setPressed(false);
                    cancelUnlockHold();
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean activate = isPressed() && contains(event.getX(), event.getY());
                setPressed(false);
                if (gestureStartedLocked) {
                    cancelUnlockHold();
                } else if (activate) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                cancelUnlockHold();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (!locked) {
            listener.onLockRequested();
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelUnlockHold();
        super.onDetachedFromWindow();
    }

    private void startUnlockHold() {
        if (holdAnimator.isRunning()) {
            holdAnimator.cancel();
        }
        holdCancelled = false;
        holdProgress = 0f;
        holdAnimator.start();
    }

    private void cancelUnlockHold() {
        if (holdAnimator.isRunning()) {
            holdAnimator.cancel();
        }
        holdProgress = 0f;
        invalidate();
    }

    private boolean contains(float x, float y) {
        return x >= 0f && x <= getWidth() && y >= 0f && y <= getHeight();
    }

    private Drawable requireIcon(int resourceId) {
        Drawable drawable = getContext().getDrawable(resourceId);
        if (drawable == null) {
            throw new IllegalStateException("Missing screen lock icon");
        }
        drawable = drawable.mutate();
        drawable.setTint(Color.WHITE);
        return drawable;
    }

    private void updateContentDescription() {
        setContentDescription(locked
                ? "Hold to unlock screen controls"
                : "Lock screen controls");
    }
}
