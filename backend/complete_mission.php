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

$host   = 'localhost';
$db     = 'your_database_name';   // ← change
$user   = 'your_db_user';         // ← change
$pass   = 'your_db_password';     // ← change

$conn = new mysqli($host, $user, $pass, $db);
if ($conn->connect_error) {
    echo json_encode(['status' => 'error', 'message' => 'DB connection failed']);
    exit;
}

$username   = $conn->real_escape_string($_POST['username']   ?? '');
$mission_id = $conn->real_escape_string($_POST['mission_id'] ?? '');

$valid_missions = [
    'fort_santiago', 'baluarte_san_diego', 'casa_manila',
    'museo_intramuros', 'centro_turismo'
];

if (empty($username) || !in_array($mission_id, $valid_missions)) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid parameters']);
    exit;
}

// INSERT IGNORE skips duplicate completions gracefully
$sql = "INSERT IGNORE INTO mission_completions (username, mission_id) VALUES ('$username', '$mission_id')";

if ($conn->query($sql)) {
    echo json_encode(['status' => 'success', 'message' => 'Mission recorded']);
} else {
    echo json_encode(['status' => 'error', 'message' => $conn->error]);
}

$conn->close();
