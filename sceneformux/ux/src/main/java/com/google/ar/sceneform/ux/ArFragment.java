/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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
package com.google.ar.sceneform.ux;

import android.util.Log;
import android.util.Size;
import android.widget.Toast;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Session;

import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import java.util.Collections;
import java.util.Set;

/**
 * Implements AR Required ArFragment. Does not require additional permissions and uses the default
 * configuration for ARCore.
 */
public class ArFragment extends BaseArFragment {
  private static final String TAG = "StandardArFragment";

  @Override
  public boolean isArRequired() {
    return true;
  }

  @Override
  public String[] getAdditionalPermissions() {
    return new String[0];
  }

  @Override
  protected void handleSessionException(UnavailableException sessionException) {

    String message;
    if (sessionException instanceof UnavailableArcoreNotInstalledException) {
      message = "Please install ARCore";
    } else if (sessionException instanceof UnavailableApkTooOldException) {
      message = "Please update ARCore";
    } else if (sessionException instanceof UnavailableSdkTooOldException) {
      message = "Please update this app";
    } else if (sessionException instanceof UnavailableDeviceNotCompatibleException) {
      message = "This device does not support AR";
    } else {
      message = "Failed to create AR session";
    }
    Log.e(TAG, "Error: " + message, sessionException);
    Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show();
  }

  @Override
  protected Config getSessionConfiguration(Session session) {
    // Select highest CPU image resolution for the direct SAMPLER_2D upload path.
    // Must be called before session.configure().
    selectHighResCameraConfig(session);

    Config config = new Config(session);
    // Disable heavy ML features that cause frame drops on mid-range devices.
    config.setDepthMode(Config.DepthMode.DISABLED);
    config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
    // Disable plane detection entirely — it causes GPU/CPU spikes after ~3s
    // that trigger camera feed flickering on Mali GPUs.
    // The plane *renderer* was already disabled; this stops the detection too.
    config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
    // Lock autofocus to prevent focus hunting frame drops during AR tracking
    config.setFocusMode(Config.FocusMode.AUTO);
    return config;
  }

  /**
   * Selects the camera config closest to 1280×720 CPU image resolution.
   * 1920×1080 is too expensive for per-frame CPU YUV→ARGB conversion;
   * 1280×720 gives ~2× the pixels of 640×480 at half the cost of 1080p.
   */
  private void selectHighResCameraConfig(Session session) {
    try {
      CameraConfigFilter filter = new CameraConfigFilter(session);
      java.util.List<CameraConfig> configs = session.getSupportedCameraConfigs(filter);
      if (configs.isEmpty()) return;

      // Target ~921,600 pixels (1280×720). Pick config closest to this.
      final int TARGET_PIXELS = 1280 * 720;
      CameraConfig best = configs.get(0);
      int bestDiff = Integer.MAX_VALUE;
      for (CameraConfig cfg : configs) {
        Size cur = cfg.getImageSize();
        int pixels = cur.getWidth() * cur.getHeight();
        int diff = Math.abs(pixels - TARGET_PIXELS);
        if (diff < bestDiff) {
          bestDiff = diff;
          best = cfg;
        }
      }

      session.setCameraConfig(best);
      Size imgSize = best.getImageSize();
      Size texSize = best.getTextureSize();
      Log.e(TAG, "Camera config: CPU=" + imgSize + " GPU=" + texSize
          + " (from " + configs.size() + " configs)");
    } catch (Exception e) {
      Log.e(TAG, "Failed to set high-res camera config", e);
    }
  }

  
  @Override
  protected Set<Session.Feature> getSessionFeatures() {
    return Collections.emptySet();
  }
}
