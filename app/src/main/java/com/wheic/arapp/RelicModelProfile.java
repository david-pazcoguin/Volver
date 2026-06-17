package com.wheic.arapp;

final class RelicModelProfile {
    static final float BOTTOM_CLEARANCE_M = 0.7f;
    static final float FULL_SIZE_DISTANCE_M = 4.0f;

    private RelicModelProfile() {
    }

    static Transform transformFor(String relicId) {
        Profile profile = profileFor(relicId);
        float fullScale = fullScaleFor(profile);
        return new Transform(
                fullScale,
                yOffsetForScale(profile, fullScale),
                fullScale,
                1.0f,
                profile.rawMinY,
                profile.rawMaxDimension,
                profile.targetMaxDimensionM);
    }

    private static float fullScaleFor(Profile profile) {
        return profile.targetMaxDimensionM / profile.rawMaxDimension;
    }

    private static float yOffsetForScale(Profile profile, float scale) {
        return BOTTOM_CLEARANCE_M - (profile.rawMinY * scale);
    }

    private static Profile profileFor(String relicId) {
        if (relicId == null) {
            return profileFor("intramuros_coin");
        }
        // rawMinY/rawMaxDimension are from the GLB POSITION bounds; target is
        // the intended full-size real-world maximum dimension in metres.
        switch (relicId) {
            case "intramuros_coin":
                return new Profile(-1.0f, 2.0f, 0.76f);
            case "peineta":
                return new Profile(-0.17314158f, 0.29916975f, 0.64f);
            case "salakot_elite":
                return new Profile(-0.2837139f, 1.12600005f, 1.05f);
            case "farol_de_aceite":
                return new Profile(-0.11f, 1.0625551f, 1.35f);
            case "pocket_watch":
                return new Profile(-0.013f, 0.144f, 0.76f);
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
