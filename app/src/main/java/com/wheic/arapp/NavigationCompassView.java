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
 * Compass overlay that shows an arrow pointing toward a target GPS location.
 * Uses accelerometer + magnetometer to determine device heading, then rotates
 * the arrow to show the relative direction to the target (left, right, ahead, behind).
 */
public class NavigationCompassView extends View implements SensorEventListener {

    private static final float SENSOR_ALPHA = 0.25f;
    private static final float ROTATION_ALPHA = 0.1f;

    // ── Paints ──────────────────────────────────────────────────────
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint directionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint northDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final RectF distBgRect = new RectF();

    // ── Sensors ─────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationVals = new float[3];
    private boolean hasGravity, hasMagnetic;

    // ── State ───────────────────────────────────────────────────────
    private double targetLat, targetLon;
    private double currentLat, currentLon;
    private float currentAzimuth;
    private float targetBearing;
    private float displayedRotation;
    private float distanceMeters = -1f;
    private boolean hasTarget, hasLocation;

    private final float[] bearingResults = new float[2];

    // ── Constructors ────────────────────────────────────────────────

    public NavigationCompassView(Context context) {
        super(context);
        init(context);
    }

    public NavigationCompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NavigationCompassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        bgPaint.setColor(Color.parseColor("#CC1a1a2e"));

        ringPaint.setColor(Color.parseColor("#55FFFFFF"));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dpToPx(1.5f));

        arrowPaint.setColor(Color.parseColor("#f1c40f"));
        arrowPaint.setStyle(Paint.Style.FILL);

        arrowOutline.setColor(Color.parseColor("#e67e22"));
        arrowOutline.setStyle(Paint.Style.STROKE);
        arrowOutline.setStrokeWidth(dpToPx(1.5f));
        arrowOutline.setStrokeJoin(Paint.Join.ROUND);

        directionPaint.setColor(Color.WHITE);
        directionPaint.setTextAlign(Paint.Align.CENTER);

        distTextPaint.setColor(Color.WHITE);
        distTextPaint.setTextAlign(Paint.Align.CENTER);

        distBgPaint.setColor(Color.parseColor("#AA000000"));
        distBgPaint.setStyle(Paint.Style.FILL);

        northDotPaint.setColor(Color.parseColor("#e74c3c"));
        northDotPaint.setStyle(Paint.Style.FILL);

        try {
            Typeface bold = ResourcesCompat.getFont(context, R.font.montserrat_bold);
            if (bold != null) {
                directionPaint.setTypeface(bold);
                distTextPaint.setTypeface(bold);
            }
        } catch (Exception ignored) { }
    }

    // ── Public API ──────────────────────────────────────────────────

    public void setTargetLocation(double lat, double lon) {
        targetLat = lat;
        targetLon = lon;
        hasTarget = true;
        recalcBearing();
        postInvalidate();
    }

    public void updateCurrentLocation(double lat, double lon, float distance) {
        currentLat = lat;
        currentLon = lon;
        distanceMeters = distance;
        hasLocation = true;
        recalcBearing();
        postInvalidate();
    }

    public void startSensors() {
        if (sensorManager == null) return;
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (magnetometer != null)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void stopSensors() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    // ── Sensor callbacks ────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, gravity);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, geomagnetic);
            hasMagnetic = true;
        }

        if (hasGravity && hasMagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                SensorManager.getOrientation(rotationMatrix, orientationVals);
                currentAzimuth = (float) Math.toDegrees(orientationVals[0]);
                if (currentAzimuth < 0) currentAzimuth += 360f;
                postInvalidate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ── Drawing ─────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (!hasTarget) return;

        float w = getWidth();
        float compassR = w * 0.42f;
        float cx = w / 2f;
        float cy = compassR + dpToPx(4);

        // Smooth rotation toward target direction
        float relativeAngle = normalize(targetBearing - currentAzimuth);
        float diff = normalize(relativeAngle - displayedRotation);
        if (diff > 180f) diff -= 360f;
        displayedRotation = normalize(displayedRotation + diff * ROTATION_ALPHA);

        // Background circle + ring
        canvas.drawCircle(cx, cy, compassR, bgPaint);
        canvas.drawCircle(cx, cy, compassR, ringPaint);

        // North indicator (small red dot, rotated by -azimuth)
        float northAngle = normalize(-currentAzimuth);
        float northR = compassR * 0.82f;
        float nx = cx + (float) Math.sin(Math.toRadians(northAngle)) * northR;
        float ny = cy - (float) Math.cos(Math.toRadians(northAngle)) * northR;
        canvas.drawCircle(nx, ny, dpToPx(3.5f), northDotPaint);

        // Arrow pointing toward target
        canvas.save();
        canvas.rotate(displayedRotation, cx, cy);

        float aLen = compassR * 0.58f;
        float aW = compassR * 0.36f;
        arrowPath.reset();
        arrowPath.moveTo(cx, cy - aLen);
        arrowPath.lineTo(cx - aW / 2f, cy + aLen * 0.25f);
        arrowPath.lineTo(cx, cy + aLen * 0.05f);
        arrowPath.lineTo(cx + aW / 2f, cy + aLen * 0.25f);
        arrowPath.close();

        canvas.drawPath(arrowPath, arrowPaint);
        canvas.drawPath(arrowPath, arrowOutline);
        canvas.restore();

        // Direction label
        String dir = directionLabel(displayedRotation);
        directionPaint.setTextSize(compassR * 0.30f);
        float dirY = cy + compassR + directionPaint.getTextSize() + dpToPx(8);
        canvas.drawText(dir, cx, dirY, directionPaint);

        // Distance badge
        if (distanceMeters >= 0 && hasLocation) {
            String dist = formatDist(distanceMeters);
            distTextPaint.setTextSize(compassR * 0.24f);
            float distY = dirY + distTextPaint.getTextSize() + dpToPx(6);
            float tw = distTextPaint.measureText(dist);
            float px = dpToPx(12);
            float py = dpToPx(5);
            distBgRect.set(cx - tw / 2 - px, distY - distTextPaint.getTextSize() - py,
                    cx + tw / 2 + px, distY + py);
            canvas.drawRoundRect(distBgRect, dpToPx(10), dpToPx(10), distBgPaint);
            canvas.drawText(dist, cx, distY, distTextPaint);
        }

        // Keep animating if rotation hasn't settled
        if (Math.abs(diff) > 0.3f) {
            postInvalidateOnAnimation();
        }
    }

    // ── Utilities ───────────────────────────────────────────────────

    private void recalcBearing() {
        if (!hasTarget || !hasLocation) return;
        Location.distanceBetween(currentLat, currentLon, targetLat, targetLon, bearingResults);
        targetBearing = bearingResults[1];
        if (targetBearing < 0) targetBearing += 360f;
    }

    private static String directionLabel(float rotation) {
        float a = normalize(rotation);
        if (a <= 20f || a >= 340f)  return "AHEAD";
        if (a < 70f)               return "SLIGHT RIGHT";
        if (a <= 110f)             return "RIGHT";
        if (a < 160f)              return "BEHIND RIGHT";
        if (a <= 200f)             return "BEHIND YOU";
        if (a < 250f)              return "BEHIND LEFT";
        if (a <= 290f)             return "LEFT";
        return "SLIGHT LEFT";
    }

    private static String formatDist(float meters) {
        if (meters >= 1000f) {
            return String.format(Locale.US, "%.1f km", meters / 1000f);
        }
        return String.format(Locale.US, "%d m", (int) meters);
    }

    private static float normalize(float deg) {
        deg = deg % 360f;
        if (deg < 0) deg += 360f;
        return deg;
    }

    private static void lowPass(float[] input, float[] output) {
        for (int i = 0; i < input.length; i++) {
            output[i] += SENSOR_ALPHA * (input[i] - output[i]);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
