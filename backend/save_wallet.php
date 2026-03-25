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

$host = getenv('DB_HOST')     ?: 'localhost';
$db   = getenv('DB_NAME')     ?: 'your_database_name';
$user = getenv('DB_USER')     ?: 'your_db_user';
$pass = getenv('DB_PASSWORD') ?: 'your_db_password';

$conn = new mysqli($host, $user, $pass, $db);
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

// Prepared statement prevents SQL injection
$stmt = $conn->prepare("UPDATE users SET wallet_address = ? WHERE username = ?");
$stmt->bind_param("ss", $wallet_address, $username);

if ($stmt->execute() && $stmt->affected_rows > 0) {
    echo json_encode(['status' => 'success', 'message' => 'Wallet address saved']);
} else {
    echo json_encode(['status' => 'success', 'message' => 'No update needed']);
}

$stmt->close();
$conn->close();
