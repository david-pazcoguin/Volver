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

import java.util.Arrays;
import java.util.Locale;

/**
 * Compass overlay that renders a 3D gold arrow pointing toward a target GPS location.
 * Bearing drives Rz spin; device pitch/roll drive Rx/Ry gyro tilt in 3D.
 * No sphere — just the arrow floating in space with Lambert shading + perspective.
 */
public class NavigationCompassView extends View implements SensorEventListener {

    private static final float SENSOR_ALPHA   = 0.25f;
    private static final float ROTATION_ALPHA = 0.10f;

    // ── Arrow 3D model (model space: +Y = up, +Z = toward viewer, +X = right) ──
    // Arrow tip points toward +Y; tip vertex pops forward along +Z.
    private static final float[][] MV = {
        {  0f,     1.00f,   0.40f },  // v0: tip   (pops toward viewer)
        { -0.38f, -0.28f,   0.10f },  // v1: left base
        {  0f,    -0.08f,   0.10f },  // v2: notch  (center base)
        {  0.38f, -0.28f,   0.10f },  // v3: right base
        {  0f,     0.22f,  -0.40f },  // v4: back ridge
    };

    // Triangular faces — CCW winding gives outward normals for Lambert lighting.
    private static final int[][] MF = {
        {1, 0, 4},  // left face  → outward normal ≈ (−X, +Y, −Z)
        {4, 0, 3},  // right face → outward normal ≈ (+X, +Y, −Z)
        {0, 1, 2},  // front-lower-left  → outward normal ≈ (+Z dominant)
        {0, 2, 3},  // front-lower-right → outward normal ≈ (+Z dominant)
    };

    // Per-face gold base colors (multiplied by Lambert intensity at draw time).
    private static final int[] FC = {
        Color.parseColor("#C87010"),  // left  – shadow gold
        Color.parseColor("#B06000"),  // right – dark gold
        Color.parseColor("#FFD740"),  // front-lower-left  – bright gold
        Color.parseColor("#F0B820"),  // front-lower-right – mid gold
    };

    // Light direction (unit vector): upper-left, toward viewer.
    private static final float[] LD = normalizeV(new float[]{ -0.35f, 0.65f, 0.70f });

    // ── Paints ──────────────────────────────────────────────────────
    private final Paint facePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint directionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distTextPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distBgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  facePath       = new Path();
    private final RectF distBgRect     = new RectF();

    // ── Sensors ─────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] gravity       = new float[3];
    private final float[] geomagnetic   = new float[3];
    private boolean hasGravity, hasMagnetic;
    private final float[] rotationMatrix  = new float[9];
    private final float[] orientationVals = new float[3];

    // ── State ───────────────────────────────────────────────────────
    private double  targetLat, targetLon;
    private double  currentLat, currentLon;
    private float   currentAzimuth;
    private float   targetBearing;
    private float   displayedRotation;
    private float   distanceMeters = -1f;
    private boolean hasTarget, hasLocation;
    private float   devicePitch, deviceRoll;  // radians, from orientation sensor

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
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor == null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        }

        edgePaint.setColor(Color.parseColor("#FF8C00"));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dpToPx(1.2f));
        edgePaint.setStrokeJoin(Paint.Join.ROUND);

        directionPaint.setColor(Color.WHITE);
        directionPaint.setTextAlign(Paint.Align.CENTER);

        distTextPaint.setColor(Color.WHITE);
        distTextPaint.setTextAlign(Paint.Align.CENTER);

        distBgPaint.setColor(Color.parseColor("#AA000000"));
        distBgPaint.setStyle(Paint.Style.FILL);

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

    // ── Sensor callbacks ────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();

        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            currentAzimuth = bearingFromMatrix(rotationMatrix);
            SensorManager.getOrientation(rotationMatrix, orientationVals);
            devicePitch = orientationVals[1];
            deviceRoll  = orientationVals[2];
            postInvalidate();

        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, gravity);
            hasGravity = true;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, geomagnetic);
            hasMagnetic = true;
        }

        if (hasGravity && hasMagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                currentAzimuth = bearingFromMatrix(rotationMatrix);
                SensorManager.getOrientation(rotationMatrix, orientationVals);
                devicePitch = orientationVals[1];
                deviceRoll  = orientationVals[2];
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

        final float w     = getWidth();
        final float scale = w * 0.38f;          // arrow radius in pixels
        final float cx    = w / 2f;
        final float cy    = scale * 1.05f + dpToPx(4);

        // ── Smooth bearing interpolation ─────────────────────────────
        float relativeAngle = normalize(targetBearing - currentAzimuth);
        float diff = normalize(relativeAngle - displayedRotation);
        if (diff > 180f) diff -= 360f;
        displayedRotation = normalize(displayedRotation + diff * ROTATION_ALPHA);

        // ── Build 3D rotation matrix ─────────────────────────────────
        // Rz(-bearing) points the arrow at the target around the Z axis.
        // Rx(pitch) * Ry(roll) tilts the whole arrow with device gyro motion.
        float bearingRad = (float) Math.toRadians(-displayedRotation);
        float[] Rz    = makeRz(bearingRad);
        float[] Rx    = makeRx(devicePitch * 0.50f);
        float[] Ry    = makeRy(deviceRoll  * 0.50f);
        float[] gyroR = matMul(Rx, Ry);
        float[] R     = matMul(gyroR, Rz);   // gyro applied after bearing spin

        // ── Transform all vertices ───────────────────────────────────
        float[][] tv = new float[MV.length][3];
        for (int i = 0; i < MV.length; i++) tv[i] = applyRot(R, MV[i]);

        // ── Painter's sort — back faces first ────────────────────────
        final int     nf    = MF.length;
        final float[] avgZ  = new float[nf];
        Integer[]     order = new Integer[nf];
        for (int f = 0; f < nf; f++) {
            order[f] = f;
            int[] vi = MF[f];
            avgZ[f] = (tv[vi[0]][2] + tv[vi[1]][2] + tv[vi[2]][2]) / 3f;
        }
        Arrays.sort(order, (a, b) -> Float.compare(avgZ[a], avgZ[b]));

        // ── Draw faces back → front ───────────────────────────────────
        for (int fi : order) {
            int[]   vi    = MF[fi];
            float[] n     = faceNormal(tv[vi[0]], tv[vi[1]], tv[vi[2]]);
            float   intns = Math.max(0.15f, dotV(n, LD));

            float[] p0 = perspProj(tv[vi[0]], cx, cy, scale);
            float[] p1 = perspProj(tv[vi[1]], cx, cy, scale);
            float[] p2 = perspProj(tv[vi[2]], cx, cy, scale);

            facePaint.setColor(applyLighting(FC[fi], intns));
            facePaint.setStyle(Paint.Style.FILL);

            facePath.reset();
            facePath.moveTo(p0[0], p0[1]);
            facePath.lineTo(p1[0], p1[1]);
            facePath.lineTo(p2[0], p2[1]);
            facePath.close();

            canvas.drawPath(facePath, facePaint);
            canvas.drawPath(facePath, edgePaint);
        }

        // ── Direction label ───────────────────────────────────────────
        String dir = directionLabel(displayedRotation);
        directionPaint.setTextSize(scale * 0.32f);
        float dirY = cy + scale * 1.2f + dpToPx(8);
        canvas.drawText(dir, cx, dirY, directionPaint);

        // ── Distance badge ────────────────────────────────────────────
        if (distanceMeters >= 0 && hasLocation) {
            String dist = formatDist(distanceMeters);
            distTextPaint.setTextSize(scale * 0.26f);
            float distY = dirY + distTextPaint.getTextSize() + dpToPx(6);
            float tw = distTextPaint.measureText(dist);
            float px = dpToPx(12), py = dpToPx(5);
            distBgRect.set(cx - tw / 2 - px, distY - distTextPaint.getTextSize() - py,
                           cx + tw / 2 + px, distY + py);
            canvas.drawRoundRect(distBgRect, dpToPx(10), dpToPx(10), distBgPaint);
            canvas.drawText(dist, cx, distY, distTextPaint);
        }

        if (Math.abs(diff) > 0.3f) postInvalidateOnAnimation();
    }

    // ── 3D Math ─────────────────────────────────────────────────────

    /** Rotation around Z (CCW positive). Row-major float[9]. */
    private static float[] makeRz(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{ c,-s,0,  s,c,0,  0,0,1 };
    }

    /** Rotation around X (CCW positive). */
    private static float[] makeRx(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{ 1,0,0,  0,c,-s,  0,s,c };
    }

    /** Rotation around Y (CCW positive). */
    private static float[] makeRy(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{ c,0,s,  0,1,0,  -s,0,c };
    }

    /** 3×3 row-major matrix multiply C = A × B. */
    private static float[] matMul(float[] A, float[] B) {
        float[] C = new float[9];
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                for (int k = 0; k < 3; k++)
                    C[r*3+c] += A[r*3+k] * B[k*3+c];
        return C;
    }

    /** Apply 3×3 rotation matrix to a 3D point. */
    private static float[] applyRot(float[] R, float[] v) {
        return new float[]{
            R[0]*v[0] + R[1]*v[1] + R[2]*v[2],
            R[3]*v[0] + R[4]*v[1] + R[5]*v[2],
            R[6]*v[0] + R[7]*v[1] + R[8]*v[2],
        };
    }

    /**
     * Perspective project a 3D point to screen coords.
     * Camera is at z = −d; larger Z (closer) → larger projected size.
     */
    private static float[] perspProj(float[] v, float cx, float cy, float scale) {
        final float d = 2.5f;
        float w = d / Math.max(0.5f, d - v[2]);   // perspective factor
        return new float[]{
            cx +  v[0] * scale * w,
            cy -  v[1] * scale * w   // screen Y is flipped
        };
    }

    /** Outward face normal via cross product (CCW winding assumed). */
    private static float[] faceNormal(float[] v0, float[] v1, float[] v2) {
        float[] e1 = { v1[0]-v0[0], v1[1]-v0[1], v1[2]-v0[2] };
        float[] e2 = { v2[0]-v0[0], v2[1]-v0[1], v2[2]-v0[2] };
        return normalizeV(new float[]{
            e1[1]*e2[2] - e1[2]*e2[1],
            e1[2]*e2[0] - e1[0]*e2[2],
            e1[0]*e2[1] - e1[1]*e2[0]
        });
    }

    private static float[] normalizeV(float[] v) {
        float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len < 1e-6f) return v.clone();
        return new float[]{ v[0]/len, v[1]/len, v[2]/len };
    }

    private static float dotV(float[] a, float[] b) {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    /** Scale each RGB channel of a color by a lighting intensity (0..1). */
    private static int applyLighting(int color, float intensity) {
        int r = Math.min(255, (int)(Color.red(color)   * intensity));
        int g = Math.min(255, (int)(Color.green(color) * intensity));
        int b = Math.min(255, (int)(Color.blue(color)  * intensity));
        return Color.rgb(r, g, b);
    }

    // ── Utilities ───────────────────────────────────────────────────

    private static float bearingFromMatrix(float[] R) {
        float deg = (float) Math.toDegrees(Math.atan2(-R[2], -R[5]));
        if (deg < 0) deg += 360f;
        return deg;
    }

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
        if (meters >= 1000f) return String.format(Locale.US, "%.1f km", meters / 1000f);
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
