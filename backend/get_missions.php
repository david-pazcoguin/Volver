<?php
/**
 * get_missions.php
 * Returns the user's completed mission IDs and whether all 5 are done.
 *
 * POST params:
 *   username — the logged-in user's username
 *
 * Response:
 *   {
 *     "status": "success",
 *     "completed_missions": ["fort_santiago", "casa_manila"],
 *     "all_complete": false
 *   }
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

$username = $conn->real_escape_string($_POST['username'] ?? '');

if (empty($username)) {
    echo json_encode(['status' => 'error', 'message' => 'Username required']);
    exit;
}

$total_missions = 5;
$result = $conn->query(
    "SELECT mission_id FROM mission_completions WHERE username = '$username'"
);

$completed = [];
while ($row = $result->fetch_assoc()) {
    $completed[] = $row['mission_id'];
}

echo json_encode([
    'status'             => 'success',
    'completed_missions' => $completed,
    'all_complete'       => count($completed) >= $total_missions
]);

$conn->close();
