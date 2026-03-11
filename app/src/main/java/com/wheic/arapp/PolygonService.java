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

    // ── Network config ─────────────────────────────────────────────
    // Testnet (Amoy) — change to mainnet values before release
    public static final String  RPC_URL  = "https://rpc-amoy.polygon.technology";
    public static final long    CHAIN_ID = 80002L;

    // Mainnet constants (uncomment and swap when deploying to production):
    // public static final String  RPC_URL  = "https://polygon-rpc.com";
    // public static final long    CHAIN_ID = 137L;

    // ── Contract ────────────────────────────────────────────────────
    /** Replace this with your deployed IntramurosNFT contract address. */
    public static final String NFT_CONTRACT_ADDRESS = "0xYOUR_CONTRACT_ADDRESS_HERE";

    /** Gas limit for the claimPassport() call. Adjust after profiling on testnet. */
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(200_000L);

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
        new Thread(() -> {
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
        }).start();
    }
}
