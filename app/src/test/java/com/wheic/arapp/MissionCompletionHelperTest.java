package com.wheic.arapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MissionCompletionHelperTest {

    @Test
    public void allowlist_containsAllMissionLocations() {
        assertEquals(8, MissionCompletionHelper.ALLOWED_MISSION_IDS.size());
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("fort_santiago"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("baluarte_san_diego"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("casa_manila"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("museo_intramuros"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("centro_turismo"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("san_agustin_church"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("manila_cathedral"));
        assertTrue(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("lpu"));
    }

    @Test
    public void allowlist_rejectsUnknownIds() {
        assertFalse(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("fort-santiago"));
        assertFalse(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("FORT_SANTIAGO"));
        assertFalse(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains(""));
        assertFalse(MissionCompletionHelper.ALLOWED_MISSION_IDS.contains("unknown"));
    }
}
