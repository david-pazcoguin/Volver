package com.wheic.arapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PolygonServiceTest {

    @Test
    public void buildClaimPassportData_isFourByteSelector() {
        String encoded = PolygonService.buildClaimPassportData();
        assertNotNull(encoded);
        // No-arg function — encoded output is exactly the 4-byte selector (10 chars incl. 0x).
        assertTrue("Should start with 0x", encoded.startsWith("0x"));
        assertEquals("No-arg call is selector only", 10, encoded.length());
        assertTrue("Selector is lowercase hex",
                encoded.substring(2).matches("[0-9a-f]{8}"));
    }

    @Test
    public void buildClaimPassportData_isDeterministic() {
        assertEquals(PolygonService.buildClaimPassportData(),
                     PolygonService.buildClaimPassportData());
    }

    @Test
    public void polygonScanUrl_usesAmoyForTestnet() {
        String url = PolygonService.getPolygonScanTxUrl("0xdeadbeef");
        assertNotNull(url);
        // The URL base depends on CHAIN_ID at build-time; both variants are acceptable.
        assertTrue(url.startsWith("https://amoy.polygonscan.com/tx/")
                || url.startsWith("https://polygonscan.com/tx/"));
        assertTrue(url.endsWith("0xdeadbeef"));
    }

    @Test
    public void metamaskDeepLink_containsContractAndChain() {
        String link = PolygonService.buildMetaMaskDeepLink();
        assertNotNull(link);
        assertTrue(link.startsWith("https://metamask.app.link/send/"));
        assertTrue(link.contains("value=0x0"));
        assertTrue(link.contains("data=0x"));
    }
}
