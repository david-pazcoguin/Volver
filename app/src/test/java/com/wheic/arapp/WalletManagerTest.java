package com.wheic.arapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WalletManagerTest {

    @Test
    public void validAddress_acceptsChecksum() {
        assertTrue(WalletManager.isValidAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0"));
    }

    @Test
    public void validAddress_acceptsAllLowercase() {
        assertTrue(WalletManager.isValidAddress("0x742d35cc6634c0532925a3b844bc9e7595f0beb0"));
    }

    @Test
    public void validAddress_rejectsNull() {
        assertFalse(WalletManager.isValidAddress(null));
    }

    @Test
    public void validAddress_rejectsEmpty() {
        assertFalse(WalletManager.isValidAddress(""));
    }

    @Test
    public void validAddress_rejectsWrongPrefix() {
        assertFalse(WalletManager.isValidAddress("1x742d35cc6634c0532925a3b844bc9e7595f0beb0"));
    }

    @Test
    public void validAddress_rejectsShortAddress() {
        assertFalse(WalletManager.isValidAddress("0x742d35cc"));
    }

    @Test
    public void validAddress_rejectsNonHex() {
        assertFalse(WalletManager.isValidAddress("0xZZZd35cc6634c0532925a3b844bc9e7595f0beb0"));
    }

    @Test
    public void validAddress_rejectsTrailingWhitespace() {
        assertFalse(WalletManager.isValidAddress("0x742d35cc6634c0532925a3b844bc9e7595f0beb0 "));
    }
}
