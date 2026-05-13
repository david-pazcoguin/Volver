package com.wheic.arapp;

import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ux.ArFragment;

/**
 * ArFragment subclass for DemoARActivity.
 * Configures the ARCore session at init time so plane detection starts immediately,
 * without the post-init reconfiguration hack that caused slow surface detection.
 */
public class DemoArFragment extends ArFragment {

    @Override
    public Config getSessionConfiguration(Session session) {
        // Build config from scratch — avoids selectHighResCameraConfig() in the parent which
        // can fail on some devices and cause the whole session to silently not initialize.
        Config config = new Config(session);
        config.setDepthMode(Config.DepthMode.DISABLED);
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        config.setFocusMode(Config.FocusMode.AUTO);
        return config;
    }
}
