package com.wheic.arapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    @Test
    public void buildMissionCompletionData_usesConcreteTimestamp() {
        Timestamp completedAt = Timestamp.now();

        Map<String, Object> payload =
                MissionCompletionHelper.buildMissionCompletionData("casa_manila", completedAt);

        assertEquals(Boolean.TRUE, payload.get(FirebaseConfig.FIELD_COMPLETED));
        assertEquals("casa_manila", payload.get(FirebaseConfig.FIELD_MISSION_ID));
        assertSame(completedAt, payload.get(FirebaseConfig.FIELD_COMPLETED_AT));
    }

    @Test
    public void isAllLandmarksComplete_requiresAllEightMissions() {
        Set<String> completed = new HashSet<>(MissionCompletionHelper.ALLOWED_MISSION_IDS);
        assertTrue(MissionCompletionHelper.isAllLandmarksComplete(completed));

        completed.remove("lpu");
        assertFalse(MissionCompletionHelper.isAllLandmarksComplete(completed));
        assertFalse(MissionCompletionHelper.isAllLandmarksComplete(null));
    }
}
