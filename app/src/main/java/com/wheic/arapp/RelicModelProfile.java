package com.wheic.arapp;

final class RelicModelProfile {
    static final float BOTTOM_CLEARANCE_M = 0.6f;
    static final float FULL_SIZE_DISTANCE_M = 4.0f;
    static final float MIN_DISTANCE_SCALE = 0.40f;

    private RelicModelProfile() {
    }

    static Transform transformFor(String relicId, float distanceMeters) {
        Profile profile = profileFor(relicId);
        float fullScale = fullScaleFor(profile);
        float distanceScale = distanceScaleFor(distanceMeters);
        float visualScale = fullScale * distanceScale;
        return new Transform(
                visualScale,
                yOffsetForScale(profile, visualScale),
                fullScale,
                distanceScale,
                profile.rawMinY,
                profile.rawMaxDimension,
                profile.targetMaxDimensionM);
    }

    static float fullScaleFor(String relicId) {
        return fullScaleFor(profileFor(relicId));
    }

    static float yOffsetForScale(String relicId, float scale) {
        return yOffsetForScale(profileFor(relicId), scale);
    }

    private static float fullScaleFor(Profile profile) {
        return profile.targetMaxDimensionM / profile.rawMaxDimension;
    }

    private static float yOffsetForScale(Profile profile, float scale) {
        return BOTTOM_CLEARANCE_M - (profile.rawMinY * scale);
    }

    private static float distanceScaleFor(float distanceMeters) {
        if (Float.isNaN(distanceMeters) || Float.isInfinite(distanceMeters)
                || distanceMeters <= FULL_SIZE_DISTANCE_M) {
            return 1.0f;
        }
        float scaleRangeM = 15.0f - FULL_SIZE_DISTANCE_M;
        float t = (distanceMeters - FULL_SIZE_DISTANCE_M) / scaleRangeM;
        t = clamp(t, 0.0f, 1.0f);
        return 1.0f - ((1.0f - MIN_DISTANCE_SCALE) * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Profile profileFor(String relicId) {
        if (relicId == null) {
            return profileFor("intramuros_coin");
        }
        // rawMinY/rawMaxDimension are from the GLB POSITION bounds; target is
        // the intended full-size real-world maximum dimension in metres.
        switch (relicId) {
            case "intramuros_coin":
                return new Profile(-1.0f, 2.0f, 0.38f);
            case "peineta":
                return new Profile(-0.17314158f, 0.29916975f, 0.32f);
            case "salakot_elite":
                return new Profile(-0.2837139f, 1.12600005f, 0.52f);
            case "farol_de_aceite":
                return new Profile(-0.11f, 1.0625551f, 0.68f);
            case "pocket_watch":
                return new Profile(-0.013f, 0.144f, 0.38f);
            default:
                return profileFor("intramuros_coin");
        }
    }

    static final class Transform {
        final float visualScale;
        final float localYOffset;
        final float fullScale;
        final float distanceScale;
        final float rawMinY;
        final float rawMaxDimension;
        final float targetMaxDimensionM;

        Transform(float visualScale, float localYOffset, float fullScale,
                float distanceScale, float rawMinY, float rawMaxDimension,
                float targetMaxDimensionM) {
            this.visualScale = visualScale;
            this.localYOffset = localYOffset;
            this.fullScale = fullScale;
            this.distanceScale = distanceScale;
            this.rawMinY = rawMinY;
            this.rawMaxDimension = rawMaxDimension;
            this.targetMaxDimensionM = targetMaxDimensionM;
        }
    }

    private static final class Profile {
        final float rawMinY;
        final float rawMaxDimension;
        final float targetMaxDimensionM;

        Profile(float rawMinY, float rawMaxDimension, float targetMaxDimensionM) {
            this.rawMinY = rawMinY;
            this.rawMaxDimension = rawMaxDimension;
            this.targetMaxDimensionM = targetMaxDimensionM;
        }
    }
}
