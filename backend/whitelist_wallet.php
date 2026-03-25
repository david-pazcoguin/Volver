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
 * 2. Create a .env file with OWNER_PRIVATE_KEY, CONTRACT_ADDRESS, RPC_URL.
 *    Keep this secret — never commit it to version control.
 * 3. Set DB credentials via environment variables.
 * ──────────────────────────────────────────────────────────────────
 */

header('Content-Type: application/json');

require_once __DIR__ . '/vendor/autoload.php';

use Web3\Web3;
use Web3\Contract;
use Web3\Utils;

// ── Config from environment variables ────────────────────────────
define('DB_HOST',           getenv('DB_HOST')           ?: 'localhost');
define('DB_NAME',           getenv('DB_NAME')           ?: 'your_database_name');
define('DB_USER',           getenv('DB_USER')           ?: 'your_db_user');
define('DB_PASS',           getenv('DB_PASSWORD')       ?: 'your_db_password');

define('RPC_URL',           getenv('POLYGON_RPC_URL')         ?: 'https://rpc-amoy.polygon.technology');
define('CHAIN_ID',          (int)(getenv('POLYGON_CHAIN_ID')  ?: 80002));
define('CONTRACT_ADDRESS',  getenv('CONTRACT_ADDRESS')        ?: '');
define('OWNER_PRIVATE_KEY', getenv('OWNER_PRIVATE_KEY')       ?: '');
define('TOTAL_MISSIONS',    5);
// ────────────────────────────────────────────────────────────────

$conn = new mysqli(DB_HOST, DB_USER, DB_PASS, DB_NAME);
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Service unavailable']);
    exit;
}

$username       = trim($_POST['username'] ?? '');
$wallet_address = strtolower(trim($_POST['wallet_address'] ?? ''));

// Input validation
if (empty($username) || strlen($username) > 100 || !preg_match('/^[a-zA-Z0-9_]{3,100}$/', $username)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid username']);
    exit;
}

if (!preg_match('/^0x[0-9a-f]{40}$/', $wallet_address)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid wallet address']);
    exit;
}

// Verify all missions are complete (prepared statement)
$stmt = $conn->prepare("SELECT COUNT(*) AS cnt FROM mission_completions WHERE username = ?");
$stmt->bind_param("s", $username);
$stmt->execute();
$result = $stmt->get_result();
$row = $result->fetch_assoc();
$stmt->close();

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
