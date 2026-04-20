package com.wheic.arapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import java.util.Locale;

/**
 * Full-screen AR overlay that draws floating directional arrows over the camera feed.
 * Arrows shift left/right based on the bearing difference between where the camera
 * is pointing and where the target mission is. A HUD at the bottom shows mission
 * name, distance, and turn direction.
 */
public class ARNavigationOverlay extends View implements SensorEventListener {

    private static final float FOV_H        = 62f;  // approximate horizontal camera FOV
    private static final int   ARROW_COUNT  = 4;

    // ── Sensors ──────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor accelerometer, magnetometer;
    private final float[] rotMat  = new float[9];
    private final float[] gravity = new float[3];
    private final float[] geomag  = new float[3];
    private boolean hasGravity, hasMagnetic;

    // ── State ─────────────────────────────────────────────────────────
    private float  currentAzimuth = 0f;
    private float  targetBearing  = 0f;
    private float  distanceMeters = -1f;
    private boolean hasTarget     = false;
    private boolean hasLocation   = false;
    private String  missionName   = "";

    private double targetLat, targetLon, currentLat, currentLon;
    private final float[] bearingBuf = new float[2];

    // ── Animation ─────────────────────────────────────────────────────
    private float animPhase = 0f;

    // ── Paints ────────────────────────────────────────────────────────
    private final Paint arrowFill  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowRim   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  arrowPath  = new Path();
    private final RectF pillRect   = new RectF();

    // ── Constructors ──────────────────────────────────────────────────

    public ARNavigationOverlay(Context context) {
        super(context); init(context);
    }
    public ARNavigationOverlay(Context context, AttributeSet attrs) {
        super(context, attrs); init(context);
    }
    public ARNavigationOverlay(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle); init(context);
    }

    private void init(Context ctx) {
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor == null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        }

        arrowFill.setColor(Color.parseColor("#f1c40f"));
        arrowFill.setStyle(Paint.Style.FILL);

        arrowRim.setColor(Color.parseColor("#e67e22"));
        arrowRim.setStyle(Paint.Style.STROKE);
        arrowRim.setStrokeWidth(dp(2.5f));
        arrowRim.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        distPaint.setColor(Color.parseColor("#f1c40f"));
        distPaint.setTextAlign(Paint.Align.CENTER);

        pillPaint.setColor(Color.parseColor("#CC000000"));
        pillPaint.setStyle(Paint.Style.FILL);

        edgePaint.setColor(Color.parseColor("#f1c40f"));
        edgePaint.setStyle(Paint.Style.FILL);

        try {
            Typeface bold = ResourcesCompat.getFont(ctx, R.font.montserrat_bold);
            if (bold != null) {
                textPaint.setTypeface(bold);
                distPaint.setTypeface(bold);
            }
        } catch (Exception ignored) {}
    }

    // ── Public API ────────────────────────────────────────────────────

    public void setTargetLocation(double lat, double lon, String name) {
        targetLat   = lat;
        targetLon   = lon;
        missionName = name != null ? name : "";
        hasTarget   = true;
        recalc();
        postInvalidate();
    }

    public void updateCurrentLocation(double lat, double lon, float distance) {
        currentLat    = lat;
        currentLon    = lon;
        distanceMeters = distance;
        hasLocation   = true;
        recalc();
        postInvalidate();
    }

    public void startSensors() {
        if (sensorManager == null) return;
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (magnetometer != null)
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stopSensors() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    // ── Sensor callbacks ──────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotMat, event.values);
            currentAzimuth = bearingFromMatrix(rotMat);
            postInvalidate();
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, gravity); hasGravity = true;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, geomag); hasMagnetic = true;
        }
        if (hasGravity && hasMagnetic) {
            if (SensorManager.getRotationMatrix(rotMat, null, gravity, geomag)) {
                currentAzimuth = bearingFromMatrix(rotMat);
                postInvalidate();
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ── Drawing ───────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (!hasTarget) return;

        float w = getWidth();
        float h = getHeight();

        // Bearing difference: how many degrees off-centre is the target
        float diff = targetBearing - currentAzimuth;
        while (diff >  180) diff -= 360;
        while (diff < -180) diff += 360;

        // Advance wave animation
        animPhase = (animPhase + 0.04f) % 1.0f;

        boolean isBehind    = Math.abs(diff) > 130;
        boolean isOffRight  = !isBehind && diff >  (FOV_H / 2f);
        boolean isOffLeft   = !isBehind && diff < -(FOV_H / 2f);
        boolean isInView    = !isBehind && !isOffLeft && !isOffRight;

        if (isInView) {
            // Map diff (-FOV/2 … +FOV/2) → screen X (20% … 80%)
            float cx = w * 0.5f + (diff / (FOV_H / 2f)) * w * 0.30f;
            drawArrowTrail(canvas, cx, h);
        } else if (isOffLeft) {
            drawEdgeArrow(canvas, w, h, false);
        } else if (isOffRight) {
            drawEdgeArrow(canvas, w, h, true);
        } else {
            drawTurnAround(canvas, w, h);
        }

        drawHUD(canvas, w, h, diff, isBehind, isOffLeft, isOffRight);

        postInvalidateOnAnimation();
    }

    private void drawArrowTrail(Canvas canvas, float cx, float h) {
        for (int i = 0; i < ARROW_COUNT; i++) {
            float t = i / (float) ARROW_COUNT; // 0 = closest (bottom), 1 = farthest (top)

            // Wave: each arrow bobs independently
            float wave = (float) Math.sin((animPhase - t * 0.3f) * Math.PI * 2) * dp(7);
            float cy   = h * (0.72f - t * 0.38f) + wave;

            float size  = dp(28) * (1f - t * 0.45f);
            int   alpha = (int) (255 * (1f - t * 0.45f));

            arrowFill.setAlpha(alpha);
            arrowRim.setAlpha(alpha);
            drawChevron(canvas, cx, cy, size, 0);
        }
        arrowFill.setAlpha(255);
        arrowRim.setAlpha(255);
    }

    private void drawEdgeArrow(Canvas canvas, float w, float h, boolean right) {
        float pulse = 1f + 0.15f * (float) Math.sin(animPhase * Math.PI * 2);
        float cx = right ? w * 0.84f : w * 0.16f;
        float cy = h * 0.50f;
        float size = dp(32) * pulse;
        // Rotate 90° left or right
        drawChevron(canvas, cx, cy, size, right ? 90 : -90);
    }

    private void drawTurnAround(Canvas canvas, float w, float h) {
        float pulse = 1f + 0.15f * (float) Math.sin(animPhase * Math.PI * 2);
        drawChevron(canvas, w / 2f, h * 0.50f, dp(36) * pulse, 180);
    }

    /** Draws a single chevron arrow (▲) at cx,cy with given size, rotated by angleDeg. */
    private void drawChevron(Canvas canvas, float cx, float cy, float size, float angleDeg) {
        arrowPath.reset();
        arrowPath.moveTo(0,  -size);
        arrowPath.lineTo(-size * 0.58f,  size * 0.38f);
        arrowPath.lineTo(0,  size * 0.08f);
        arrowPath.lineTo( size * 0.58f,  size * 0.38f);
        arrowPath.close();

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(angleDeg);
        canvas.drawPath(arrowPath, arrowFill);
        canvas.drawPath(arrowPath, arrowRim);
        canvas.restore();
    }

    private void drawHUD(Canvas canvas, float w, float h,
                         float diff, boolean behind, boolean offLeft, boolean offRight) {

        String distLabel;
        if (!hasLocation || distanceMeters < 0) {
            distLabel = "Locating…";
        } else if (distanceMeters >= 1000) {
            distLabel = String.format(Locale.US, "%.1f km", distanceMeters / 1000f);
        } else {
            distLabel = String.format(Locale.US, "%d m", (int) distanceMeters);
        }

        String dirLabel;
        if (behind)             dirLabel = "↩ TURN AROUND";
        else if (offRight)      dirLabel = "▶▶ TURN RIGHT";
        else if (offLeft)       dirLabel = "◀◀ TURN LEFT";
        else if (diff >  25)    dirLabel = "▲▶ BEAR RIGHT";
        else if (diff < -25)    dirLabel = "◀▲ BEAR LEFT";
        else                    dirLabel = "▲  AHEAD";

        float pillW   = w * 0.62f;
        float pillH   = dp(68);
        float pillY   = h * 0.87f;
        float radius  = dp(16);

        pillRect.set(w / 2f - pillW / 2f, pillY - pillH / 2f,
                     w / 2f + pillW / 2f, pillY + pillH / 2f);
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint);

        // Mission name (small white)
        textPaint.setTextSize(dp(11));
        canvas.drawText(missionName.toUpperCase(Locale.US), w / 2f, pillY - dp(14), textPaint);

        // Distance (large gold)
        distPaint.setTextSize(dp(22));
        canvas.drawText(distLabel, w / 2f - dp(30), pillY + dp(16), distPaint);

        // Direction label (small gold)
        distPaint.setTextSize(dp(11));
        canvas.drawText(dirLabel, w / 2f + dp(30), pillY + dp(16), distPaint);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void recalc() {
        if (!hasTarget || !hasLocation) return;
        Location.distanceBetween(currentLat, currentLon, targetLat, targetLon, bearingBuf);
        targetBearing = bearingBuf[1];
        if (targetBearing < 0) targetBearing += 360f;
    }

    /** Bearing of back camera from rotation matrix. Works for any device tilt. */
    private static float bearingFromMatrix(float[] R) {
        float deg = (float) Math.toDegrees(Math.atan2(-R[2], -R[5]));
        if (deg < 0) deg += 360f;
        return deg;
    }

    private static void lowPass(float[] in, float[] out) {
        for (int i = 0; i < in.length; i++) out[i] += 0.25f * (in[i] - out[i]);
    }

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
