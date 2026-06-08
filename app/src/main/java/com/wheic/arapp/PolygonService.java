package com.wheic.arapp;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

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

    /**
     * Returns the PolygonScan base URL for the current chain.
     * Amoy testnet (80002) → amoy.polygonscan.com
     * Polygon mainnet (137) → polygonscan.com
     */
    public static String getPolygonScanTxUrl(String txHash) {
        String base = (CHAIN_ID == 137)
                ? "https://polygonscan.com/tx/"
                : "https://amoy.polygonscan.com/tx/";
        return base + txHash;
    }

    /**
     * PolygonScan wallet page — token transfers tab, where users see the NFT
     * they received along with any other ERC-20/ERC-721 activity.
     */
    public static String getPolygonScanAddressUrl(String walletAddress) {
        String base = (CHAIN_ID == 137)
                ? "https://polygonscan.com/address/"
                : "https://amoy.polygonscan.com/address/";
        return base + walletAddress + "#tokentxns";
    }

    /**
     * OpenSea profile for the wallet. On mainnet this shows the actual NFT
     * artwork; testnet uses testnets.opensea.io (limited indexing).
     */
    public static String getOpenSeaUrl(String walletAddress) {
        String base = (CHAIN_ID == 137)
                ? "https://opensea.io/"
                : "https://testnets.opensea.io/";
        return base + walletAddress;
    }

    /** Single background thread for blockchain operations. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // ──────────────────────────────────────────────────────────────
    // Callback interface
    // ──────────────────────────────────────────────────────────────

    public interface TxCallback {
        void onSuccess(String txHash);
        void onError(String errorMessage);
    }

    /** Result of a pre-flight eligibility check. */
    public static final class Eligibility {
        public final boolean whitelisted;
        public final boolean alreadyMinted;
        Eligibility(boolean whitelisted, boolean alreadyMinted) {
            this.whitelisted   = whitelisted;
            this.alreadyMinted = alreadyMinted;
        }
    }

    public interface EligibilityCallback {
        void onResult(Eligibility result);
        void onError(String errorMessage);
    }

    /**
     * Read-only pre-flight: checks whether the wallet is whitelisted and
     * whether it has already minted. Saves gas on doomed transactions.
     */
    public static void checkEligibility(String walletAddress, EligibilityCallback cb) {
        EXECUTOR.execute(() -> {
            Web3j web3j = null;
            try {
                web3j = Web3j.build(new HttpService(RPC_URL));
                boolean whitelisted = callBoolView(web3j, "isWhitelisted", walletAddress);
                boolean minted      = callBoolView(web3j, "hasMinted",    walletAddress);
                cb.onResult(new Eligibility(whitelisted, minted));
            } catch (Exception e) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e);
                cb.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            } finally {
                if (web3j != null) web3j.shutdown();
            }
        });
    }

    private static boolean callBoolView(Web3j web3j, String method, String walletAddress)
            throws Exception {
        Function fn = new Function(
                method,
                Collections.singletonList(new Address(walletAddress)),
                Collections.singletonList(new TypeReference<Bool>() {}));
        String encoded = FunctionEncoder.encode(fn);
        EthCall resp = web3j.ethCall(
                Transaction.createEthCallTransaction(walletAddress, NFT_CONTRACT_ADDRESS, encoded),
                DefaultBlockParameterName.LATEST).send();
        if (resp.hasError()) throw new RuntimeException(resp.getError().getMessage());
        List<Type> decoded = FunctionReturnDecoder.decode(resp.getValue(), fn.getOutputParameters());
        if (decoded.isEmpty()) return false;
        return ((Bool) decoded.get(0)).getValue();
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
                web3j = Web3j.build(new HttpService(RPC_URL, new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()));

                Credentials credentials = Credentials.create(privateKey);
                String sender = credentials.getAddress();

                // Nonce
                EthGetTransactionCount txCountResp = web3j
                        .ethGetTransactionCount(sender, DefaultBlockParameterName.LATEST)
                        .send();
                BigInteger nonce = txCountResp.getTransactionCount();

                // Gas price (+20% tip for faster inclusion, capped at 500 gwei)
                BigInteger MAX_GAS_PRICE = BigInteger.valueOf(500_000_000_000L); // 500 gwei
                EthGasPrice gasPriceResp = web3j.ethGasPrice().send();
                BigInteger gasPrice = gasPriceResp.getGasPrice()
                        .multiply(BigInteger.valueOf(12))
                        .divide(BigInteger.TEN);
                if (gasPrice.compareTo(MAX_GAS_PRICE) > 0) gasPrice = MAX_GAS_PRICE;

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
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            } finally {
                if (web3j != null) web3j.shutdown();
            }
        });
    }
}
