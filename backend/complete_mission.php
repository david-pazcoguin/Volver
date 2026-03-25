<?php
/**
 * complete_mission.php
 * Marks a single mission as complete for a user.
 *
 * POST params:
 *   username   — the logged-in user's username
 *   mission_id — one of: fort_santiago, baluarte_san_diego, casa_manila,
 *                         museo_intramuros, centro_turismo
 *
 * Required table (run once):
 *   CREATE TABLE mission_completions (
 *     id          INT AUTO_INCREMENT PRIMARY KEY,
 *     username    VARCHAR(100) NOT NULL,
 *     mission_id  VARCHAR(50)  NOT NULL,
 *     completed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 *     UNIQUE KEY unique_completion (username, mission_id)
 *   );
 */

header('Content-Type: application/json');

$host   = getenv('DB_HOST')     ?: 'localhost';
$db     = getenv('DB_NAME')     ?: 'your_database_name';
$user   = getenv('DB_USER')     ?: 'your_db_user';
$pass   = getenv('DB_PASSWORD') ?: 'your_db_password';

$conn = new mysqli($host, $user, $pass, $db);
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Service unavailable']);
    exit;
}

$username   = trim($_POST['username']   ?? '');
$mission_id = trim($_POST['mission_id'] ?? '');

// Input validation
if (empty($username) || strlen($username) > 100 || !preg_match('/^[a-zA-Z0-9_]{3,100}$/', $username)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid username']);
    exit;
}

$valid_missions = [
    'fort_santiago', 'baluarte_san_diego', 'casa_manila',
    'museo_intramuros', 'centro_turismo'
];

if (!in_array($mission_id, $valid_missions, true)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid mission ID']);
    exit;
}

// Prepared statement prevents SQL injection
$stmt = $conn->prepare("INSERT IGNORE INTO mission_completions (username, mission_id) VALUES (?, ?)");
$stmt->bind_param("ss", $username, $mission_id);

if ($stmt->execute()) {
    echo json_encode(['status' => 'success', 'message' => 'Mission recorded']);
} else {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Failed to record mission']);
}

$stmt->close();
$conn->close();
