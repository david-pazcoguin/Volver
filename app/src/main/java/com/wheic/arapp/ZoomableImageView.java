package com.wheic.arapp;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private static final float MAX_SCALE_FACTOR = 5f;

    private final Matrix imageMatrix = new Matrix();
    private final float[] matVals = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private boolean initialized = false;

    public ZoomableImageView(Context c) { super(c); init(c); }
    public ZoomableImageView(Context c, AttributeSet a) { super(c, a); init(c); }
    public ZoomableImageView(Context c, AttributeSet a, int d) { super(c, a, d); init(c); }

    private void init(Context c) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float min = getMinScale();
                float cur = getScale();
                float max = min * MAX_SCALE_FACTOR;
                float factor = d.getScaleFactor();
                float newScale = cur * factor;
                if (newScale < min) factor = min / cur;
                if (newScale > max) factor = max / cur;
                imageMatrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
                constrain();
                setImageMatrix(imageMatrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX, float dY) {
                if (getScale() > getMinScale() * 1.05f) {
                    imageMatrix.postTranslate(-dX, -dY);
                    constrain();
                    setImageMatrix(imageMatrix);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float cur = getScale();
                float min = getMinScale();
                if (cur > min * 1.4f) {
                    float factor = min / cur;
                    imageMatrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
                } else {
                    float factor = (min * 2.5f) / cur;
                    imageMatrix.postScale(factor, factor, e.getX(), e.getY());
                }
                constrain();
                setImageMatrix(imageMatrix);
                return true;
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!initialized && getDrawable() != null) fitImage();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        initialized = false;
        post(this::fitImage);
    }

    private void fitImage() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        int dw = getDrawable().getIntrinsicWidth();
        int dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float scale = Math.min((float) getWidth() / dw, (float) getHeight() / dh);
        float dx = (getWidth() - dw * scale) / 2f;
        float dy = (getHeight() - dh * scale) / 2f;
        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        setImageMatrix(imageMatrix);
        initialized = true;
    }

    private float getScale() {
        imageMatrix.getValues(matVals);
        return matVals[Matrix.MSCALE_X];
    }

    private float getMinScale() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return 1f;
        int dw = getDrawable().getIntrinsicWidth();
        int dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return 1f;
        return Math.min((float) getWidth() / dw, (float) getHeight() / dh);
    }

    private void constrain() {
        if (getDrawable() == null) return;
        imageMatrix.getValues(matVals);
        float scale = matVals[Matrix.MSCALE_X];
        float tx = matVals[Matrix.MTRANS_X];
        float ty = matVals[Matrix.MTRANS_Y];
        int dw = getDrawable().getIntrinsicWidth();
        int dh = getDrawable().getIntrinsicHeight();
        float sw = dw * scale, sh = dh * scale;
        float vw = getWidth(), vh = getHeight();

        float fixX, fixY;
        if (sw <= vw) fixX = (vw - sw) / 2f - tx;
        else if (tx > 0) fixX = -tx;
        else if (tx + sw < vw) fixX = vw - tx - sw;
        else fixX = 0;

        if (sh <= vh) fixY = (vh - sh) / 2f - ty;
        else if (ty > 0) fixY = -ty;
        else if (ty + sh < vh) fixY = vh - ty - sh;
        else fixY = 0;

        imageMatrix.postTranslate(fixX, fixY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        return true;
    }
}
