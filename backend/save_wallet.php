<?php
/**
 * save_wallet.php
 * Saves the user's Polygon wallet address to the database.
 *
 * POST params:
 *   username       — the logged-in user's username
 *   wallet_address — a valid 0x... Polygon address
 *
 * Required column (run once):
 *   ALTER TABLE users ADD COLUMN wallet_address VARCHAR(42) DEFAULT NULL;
 *   (Or use a separate wallets table — adjust the query below accordingly.)
 */

header('Content-Type: application/json');

$host = 'localhost';
$db   = 'your_database_name';   // ← change
$user = 'your_db_user';         // ← change
$pass = 'your_db_password';     // ← change

$conn = new mysqli($host, $user, $pass, $db);
if ($conn->connect_error) {
    echo json_encode(['status' => 'error', 'message' => 'DB connection failed']);
    exit;
}

$username       = $conn->real_escape_string($_POST['username']       ?? '');
$wallet_address = strtolower($conn->real_escape_string($_POST['wallet_address'] ?? ''));

// Basic Polygon address validation (0x + 40 hex chars)
if (empty($username) || !preg_match('/^0x[0-9a-f]{40}$/', $wallet_address)) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid username or wallet address']);
    exit;
}

$sql = "UPDATE users SET wallet_address = '$wallet_address' WHERE username = '$username'";

if ($conn->query($sql) && $conn->affected_rows > 0) {
    echo json_encode(['status' => 'success', 'message' => 'Wallet address saved']);
} else {
    // User row not found or no change
    echo json_encode(['status' => 'success', 'message' => 'No update needed']);
}

$conn->close();
