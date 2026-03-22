package com.wheic.arapp;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles all Polygon blockchain interactions.
 *
 * ─── SETUP STEPS ───────────────────────────────────────────────────────────
 * 1. Deploy IntramurosNFT.sol to Polygon Amoy testnet (or mainnet).
 * 2. Replace NFT_CONTRACT_ADDRESS with the deployed address.
 * 3. Switch RPC_URL / CHAIN_ID to mainnet when ready for production.
 * ───────────────────────────────────────────────────────────────────────────
 */
public class PolygonService {

    // ── Network config (injected from build.gradle / gradle.properties) ──
    public static final String  RPC_URL  = BuildConfig.POLYGON_RPC_URL;
    public static final long    CHAIN_ID = BuildConfig.POLYGON_CHAIN_ID;

    // ── Contract ────────────────────────────────────────────────────
    public static final String NFT_CONTRACT_ADDRESS = BuildConfig.NFT_CONTRACT_ADDRESS;

    /** Gas limit for the claimPassport() call. Adjust after profiling on testnet. */
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(200_000L);

    /** Single background thread for blockchain operations. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // ──────────────────────────────────────────────────────────────
    // Callback interface
    // ──────────────────────────────────────────────────────────────

    public interface TxCallback {
        void onSuccess(String txHash);
        void onError(String errorMessage);
    }

    // ──────────────────────────────────────────────────────────────
    // Transaction data builders
    // ──────────────────────────────────────────────────────────────

    /**
     * ABI-encodes the claimPassport() call.
     * The user calls this on the contract; the contract checks the whitelist.
     */
    public static String buildClaimPassportData() {
        Function function = new Function(
                "claimPassport",
                Collections.emptyList(),
                Collections.emptyList()
        );
        return FunctionEncoder.encode(function);
    }

    /**
     * Returns a MetaMask mobile deep-link that pre-fills the mint transaction.
     * When the user taps this link on their phone, MetaMask opens and asks them
     * to confirm the transaction (they pay gas).
     */
    public static String buildMetaMaskDeepLink() {
        String data = buildClaimPassportData();
        return "https://metamask.app.link/send/"
                + NFT_CONTRACT_ADDRESS + "@" + CHAIN_ID
                + "?value=0x0&data=" + data;
    }

    // ──────────────────────────────────────────────────────────────
    // Embedded wallet minting
    // ──────────────────────────────────────────────────────────────

    /**
     * Signs and broadcasts the claimPassport() transaction using the embedded wallet.
     * Must be called from a background thread (network I/O).
     */
    public static void mintWithEmbeddedWallet(String privateKey, TxCallback callback) {
        EXECUTOR.execute(() -> {
            Web3j web3j = null;
            try {
                web3j = Web3j.build(new HttpService(RPC_URL));

                Credentials credentials = Credentials.create(privateKey);
                String sender = credentials.getAddress();

                // Nonce
                EthGetTransactionCount txCountResp = web3j
                        .ethGetTransactionCount(sender, DefaultBlockParameterName.LATEST)
                        .send();
                BigInteger nonce = txCountResp.getTransactionCount();

                // Gas price (+20% tip for faster inclusion)
                EthGasPrice gasPriceResp = web3j.ethGasPrice().send();
                BigInteger gasPrice = gasPriceResp.getGasPrice()
                        .multiply(BigInteger.valueOf(12))
                        .divide(BigInteger.TEN);

                // Build, sign, broadcast
                String encodedData = buildClaimPassportData();
                RawTransaction rawTx = RawTransaction.createTransaction(
                        nonce, gasPrice, GAS_LIMIT,
                        NFT_CONTRACT_ADDRESS, BigInteger.ZERO, encodedData);

                byte[] signedMsg = TransactionEncoder.signMessage(rawTx, CHAIN_ID, credentials);
                String hexTx    = Numeric.toHexString(signedMsg);

                EthSendTransaction response = web3j.ethSendRawTransaction(hexTx).send();

                if (response.hasError()) {
                    callback.onError(response.getError().getMessage());
                } else {
                    callback.onSuccess(response.getTransactionHash());
                }

            } catch (Exception e) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            } finally {
                if (web3j != null) web3j.shutdown();
            }
        });
    }
}
