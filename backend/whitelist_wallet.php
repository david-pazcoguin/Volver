<?php
/**
 * whitelist_wallet.php
 * Called by the Android app after the user saves their wallet.
 * Verifies all 5 missions are complete, then calls whitelistAddress()
 * on the IntramurosNFT smart contract.
 *
 * POST params:
 *   username       — logged-in user
 *   wallet_address — their Polygon address
 *
 * ─── SETUP ────────────────────────────────────────────────────────
 * 1. Install web3.php:  composer require sc0vu/web3.php
 * 2. Set OWNER_PRIVATE_KEY to the contract deployer's private key.
 *    Keep this secret — never commit it to version control.
 * 3. Set CONTRACT_ADDRESS to the deployed IntramurosNFT address.
 * 4. Set RPC_URL to the Polygon Amoy or mainnet RPC endpoint.
 * ──────────────────────────────────────────────────────────────────
 */

header('Content-Type: application/json');

require_once __DIR__ . '/vendor/autoload.php';

use Web3\Web3;
use Web3\Contract;
use Web3\Utils;

// ── Config (move to a .env file in production) ──────────────────
define('DB_HOST',           'localhost');
define('DB_NAME',           'your_database_name');   // ← change
define('DB_USER',           'your_db_user');          // ← change
define('DB_PASS',           'your_db_password');      // ← change

define('RPC_URL',           'https://rpc-amoy.polygon.technology');
define('CHAIN_ID',          80002);
define('CONTRACT_ADDRESS',  '0xYOUR_CONTRACT_ADDRESS_HERE');    // ← change
define('OWNER_PRIVATE_KEY', '0xYOUR_OWNER_PRIVATE_KEY_HERE');  // ← change — KEEP SECRET
define('TOTAL_MISSIONS',    5);
// ────────────────────────────────────────────────────────────────

$conn = new mysqli(DB_HOST, DB_USER, DB_PASS, DB_NAME);
if ($conn->connect_error) {
    echo json_encode(['status' => 'error', 'message' => 'DB connection failed']);
    exit;
}

$username       = $conn->real_escape_string($_POST['username']       ?? '');
$wallet_address = strtolower($conn->real_escape_string($_POST['wallet_address'] ?? ''));

if (empty($username) || !preg_match('/^0x[0-9a-f]{40}$/', $wallet_address)) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid parameters']);
    exit;
}

// Verify all missions are complete
$result = $conn->query(
    "SELECT COUNT(*) AS cnt FROM mission_completions WHERE username = '$username'"
);
$row = $result->fetch_assoc();
if ((int)$row['cnt'] < TOTAL_MISSIONS) {
    echo json_encode(['status' => 'error', 'message' => 'Not all missions completed yet']);
    exit;
}

$conn->close();

// ── Call whitelistAddress(wallet) on the contract ────────────────
// ABI fragment for the whitelist function
$abi = json_encode([[
    'name'    => 'whitelistAddress',
    'type'    => 'function',
    'inputs'  => [['name' => 'user', 'type' => 'address']],
    'outputs' => [],
    'stateMutability' => 'nonpayable',
]]);

$web3    = new Web3(RPC_URL);
$eth     = $web3->eth;
$contract = new Contract(RPC_URL, $abi);

// Get nonce for the owner account
$ownerAddress = Utils::privateKeyToAddress(OWNER_PRIVATE_KEY);
$nonce = null;
$eth->getTransactionCount($ownerAddress, 'latest', function ($err, $count) use (&$nonce) {
    if (!$err) $nonce = $count->toString();
});

if ($nonce === null) {
    echo json_encode(['status' => 'error', 'message' => 'Could not fetch nonce']);
    exit;
}

// Encode function call data
$data = $contract->at(CONTRACT_ADDRESS)
                  ->getData('whitelistAddress', $wallet_address);

// Build and sign transaction (uses phpseclib via web3.php)
$txParams = [
    'nonce'    => '0x' . dechex((int)$nonce),
    'to'       => CONTRACT_ADDRESS,
    'value'    => '0x0',
    'data'     => '0x' . $data,
    'gasPrice' => '0x' . dechex(30000000000), // 30 gwei — adjust for current network
    'gas'      => '0x' . dechex(100000),
    'chainId'  => CHAIN_ID,
];

$signedTx = null;
$eth->accounts->signTransaction($txParams, OWNER_PRIVATE_KEY,
    function ($err, $tx) use (&$signedTx) {
        if (!$err) $signedTx = $tx->raw;
    });

if (!$signedTx) {
    echo json_encode(['status' => 'error', 'message' => 'Transaction signing failed']);
    exit;
}

$txHash = null;
$eth->sendRawTransaction($signedTx, function ($err, $hash) use (&$txHash) {
    if (!$err) $txHash = $hash;
});

if ($txHash) {
    echo json_encode([
        'status'  => 'success',
        'message' => 'Wallet whitelisted on-chain',
        'tx_hash' => $txHash
    ]);
} else {
    echo json_encode(['status' => 'error', 'message' => 'Blockchain transaction failed']);
}
