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
 * 3D arrow overlay drawn over the AR camera feed (no sphere).
 * A pure gold tetrahedron arrow rotates in 3D to point at the target bearing.
 * Device pitch/roll tilt the arrow for a gyroscopic feel.
 */
public class ARNavigationOverlay extends View implements SensorEventListener {

    // ── 3D Arrow prism model (8 vertices, 12 faces) ──────────────────
    // Arrow points +Y. Thickness in Z (front = +Z, back = -Z).
    // Width in X. This gives a solid prism you can view from any angle.
    private static final float T = 0.20f; // half-thickness
    private static final float[][] MV = {
        // Front face (z = +T)
        {  0f,     1.00f,   T },  // v0: tip-front
        { -0.42f, -0.30f,   T },  // v1: left-base-front
        {  0f,    -0.08f,   T },  // v2: notch-front
        {  0.42f, -0.30f,   T },  // v3: right-base-front
        // Back face (z = -T)
        {  0f,     1.00f,  -T },  // v4: tip-back
        { -0.42f, -0.30f,  -T },  // v5: left-base-back
        {  0f,    -0.08f,  -T },  // v6: notch-back
        {  0.42f, -0.30f,  -T },  // v7: right-base-back
    };

    // 12 triangular faces (CCW winding → outward normals)
    private static final int[][] MF = {
        // Front face (+Z outward) — bright gold
        {0, 1, 2},  {0, 2, 3},
        // Back face (-Z outward) — dark
        {4, 6, 5},  {4, 7, 6},
        // Left slope (tip→left-base, front→back)
        {0, 4, 5},  {0, 5, 1},
        // Right slope (tip→right-base, front→back)
        {0, 3, 7},  {0, 7, 4},
        // Bottom-left (left-base→notch, front→back)
        {1, 5, 6},  {1, 6, 2},
        // Bottom-right (notch→right-base, front→back)
        {2, 6, 7},  {2, 7, 3},
    };

    // Base colors per face (Lambert shading modulates these)
    private static final int[] FC = {
        0xFFFFD740, 0xFFF0C830,  // front – bright gold
        0xFF7A5000, 0xFF6A4500,  // back  – dark gold
        0xFFC87010, 0xFFC87010,  // left slope – medium gold
        0xFFB06000, 0xFFB06000,  // right slope – medium-dark gold
        0xFFA05000, 0xFFA05000,  // bottom-left – shadow gold
        0xFF9A4800, 0xFF9A4800,  // bottom-right – shadow gold
    };

    // Light direction (unit vector): upper-left, toward viewer.
    private static final float[] LD = normalizeV(new float[]{ -0.35f, 0.65f, 0.70f });

    // ── Sensors ──────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor accelerometer, magnetometer;
    private final float[] rotMat    = new float[9];
    private final float[] gravity   = new float[3];
    private final float[] geomag    = new float[3];
    private final float[] orientBuf = new float[3];
    private boolean hasGravity, hasMagnetic;

    private static final float ROTATION_ALPHA = 0.12f;  // bearing smoothing
    private static final float TILT_ALPHA     = 0.18f;  // pitch/roll smoothing

    // ── State ─────────────────────────────────────────────────────────
    private float   currentAzimuth   = 0f;
    private float   targetBearing    = 0f;
    private float   displayedBearing = 0f;  // smoothed bearing for arrow spin
    private float   distanceMeters   = -1f;
    private boolean hasTarget        = false;
    private boolean hasLocation      = false;
    private String  missionName      = "";
    private float   devicePitch      = 0f;  // raw radians from sensor
    private float   deviceRoll       = 0f;
    private float   smoothPitch      = 0f;  // smoothed
    private float   smoothRoll       = 0f;

    private double targetLat, targetLon, currentLat, currentLon;
    private final float[] bearingBuf = new float[2];

    // ── Paints ────────────────────────────────────────────────────────
    private final Paint facePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  facePath    = new Path();
    private final RectF pillRect    = new RectF();

    // ── Constructors ──────────────────────────────────────────────────
    public ARNavigationOverlay(Context context) { super(context); init(context); }
    public ARNavigationOverlay(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public ARNavigationOverlay(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(context); }

    private void init(Context ctx) {
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor == null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        }

        edgePaint.setColor(Color.parseColor("#FF8C00"));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(1.2f));
        edgePaint.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        distPaint.setColor(Color.parseColor("#f1c40f"));
        distPaint.setTextAlign(Paint.Align.CENTER);

        pillPaint.setColor(Color.parseColor("#CC000000"));
        pillPaint.setStyle(Paint.Style.FILL);

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
        currentLat     = lat;
        currentLon     = lon;
        distanceMeters = distance;
        hasLocation    = true;
        recalc();
        postInvalidate();
    }

    public void startSensors() {
        if (sensorManager == null) return;
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (magnetometer  != null) sensorManager.registerListener(this, magnetometer,  SensorManager.SENSOR_DELAY_GAME);
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
            updateFromMatrix();
            postInvalidate();
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, gravity); hasGravity = true;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, geomag); hasMagnetic = true;
        }
        if (hasGravity && hasMagnetic) {
            if (SensorManager.getRotationMatrix(rotMat, null, gravity, geomag)) {
                updateFromMatrix();
                postInvalidate();
            }
        }
    }

    private void updateFromMatrix() {
        currentAzimuth = bearingFromMatrix(rotMat);
        SensorManager.getOrientation(rotMat, orientBuf);
        devicePitch = orientBuf[1];   // radians
        deviceRoll  = orientBuf[2];   // radians
        // Smooth pitch/roll to reduce 3D jitter
        smoothPitch += TILT_ALPHA * (devicePitch - smoothPitch);
        smoothRoll  += TILT_ALPHA * (deviceRoll  - smoothRoll);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ── Main draw ─────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (!hasTarget) return;

        float w = getWidth();
        float h = getHeight();
        float scale = Math.min(w, h) * 0.18f;  // arrow size

        float cx = w / 2f;
        float cy = h * 0.35f;

        // ── Smooth the displayed bearing (shortest-arc interpolation) ──
        float relAngle = normalize360(targetBearing - currentAzimuth);
        float bDiff    = relAngle - displayedBearing;
        if (bDiff >  180f) bDiff -= 360f;
        if (bDiff < -180f) bDiff += 360f;
        displayedBearing = normalize360(displayedBearing + bDiff * ROTATION_ALPHA);

        // Bearing spin — Rz(-displayedBearing) points arrow at target
        float bearingRad = (float) Math.toRadians(-displayedBearing);
        float[] Rz    = makeRz(bearingRad);

        // Pitch mapping: phone upright (pitch≈-π/2) → Rx≈+70° (see arrow back)
        //                phone at ground (pitch≈0)  → Rx≈-20° (see arrow top)
        float rxAngle = -smoothPitch - 0.35f;
        float ryAngle = smoothRoll * 0.30f;
        float[] Rx    = makeRx(rxAngle);
        float[] Ry    = makeRy(ryAngle);
        float[] gyroR = matMul(Rx, Ry);
        float[] R     = matMul(gyroR, Rz);

        // Transform and render arrow
        draw3DArrow(canvas, cx, cy, scale, R);

        // HUD at bottom
        drawHUD(canvas, w, h);

        // Keep animating while bearing is still settling
        if (Math.abs(bDiff) > 0.3f) postInvalidateOnAnimation();
    }

    /**
     * Renders the 3D arrow using perspective projection, painter's algorithm,
     * and Lambert shading per face.
     */
    private void draw3DArrow(Canvas canvas, float cx, float cy, float scale, float[] R) {
        // Transform vertices
        float[][] tv = new float[MV.length][3];
        for (int i = 0; i < MV.length; i++) tv[i] = applyRot(R, MV[i]);

        // Painter's sort
        final int     nf    = MF.length;
        final float[] avgZ  = new float[nf];
        Integer[]     order = new Integer[nf];
        for (int f = 0; f < nf; f++) {
            order[f] = f;
            int[] vi = MF[f];
            avgZ[f] = (tv[vi[0]][2] + tv[vi[1]][2] + tv[vi[2]][2]) / 3f;
        }
        Arrays.sort(order, (a, b) -> Float.compare(avgZ[a], avgZ[b]));

        // Draw faces back → front
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
    }

    // ── HUD ───────────────────────────────────────────────────────────

    private void drawHUD(Canvas canvas, float w, float h) {
        String distLabel;
        if (!hasLocation || distanceMeters < 0) {
            distLabel = "Locating…";
        } else if (distanceMeters >= 1000) {
            distLabel = String.format(Locale.US, "%.1f km", distanceMeters / 1000f);
        } else {
            distLabel = String.format(Locale.US, "%d m", (int) distanceMeters);
        }

        float diff = displayedBearing;
        if (diff >  180f) diff -= 360f;

        String dirLabel;
        boolean behind   = Math.abs(diff) > 130;
        boolean offRight = !behind && diff >  31f;
        boolean offLeft  = !behind && diff < -31f;

        if (behind)          dirLabel = "↩ TURN AROUND";
        else if (offRight)   dirLabel = "▶▶ TURN RIGHT";
        else if (offLeft)    dirLabel = "◀◀ TURN LEFT";
        else if (diff >  25) dirLabel = "▲▶ BEAR RIGHT";
        else if (diff < -25) dirLabel = "◀▲ BEAR LEFT";
        else                 dirLabel = "▲  AHEAD";

        float pillW  = w * 0.62f;
        float pillH  = dp(68);
        float pillY  = h * 0.87f;
        float radius = dp(16);

        pillRect.set(w / 2f - pillW / 2f, pillY - pillH / 2f,
                     w / 2f + pillW / 2f, pillY + pillH / 2f);
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint);

        textPaint.setTextSize(dp(11));
        canvas.drawText(missionName.toUpperCase(Locale.US), w / 2f, pillY - dp(14), textPaint);

        distPaint.setTextSize(dp(22));
        canvas.drawText(distLabel, w / 2f - dp(30), pillY + dp(16), distPaint);

        distPaint.setTextSize(dp(11));
        canvas.drawText(dirLabel, w / 2f + dp(30), pillY + dp(16), distPaint);
    }

    // ── 3D Math ──────────────────────────────────────────────────────

    private static float normalize360(float deg) {
        deg = deg % 360f;
        if (deg < 0) deg += 360f;
        return deg;
    }

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

    // ── Other helpers ─────────────────────────────────────────────────

    private void recalc() {
        if (!hasTarget || !hasLocation) return;
        Location.distanceBetween(currentLat, currentLon, targetLat, targetLon, bearingBuf);
        targetBearing = bearingBuf[1];
        if (targetBearing < 0) targetBearing += 360f;
    }

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
