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

$username = trim($_POST['username'] ?? '');

if (empty($username) || strlen($username) > 100 || !preg_match('/^[a-zA-Z0-9_]{3,100}$/', $username)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid username']);
    exit;
}

$total_missions = 5;

$stmt = $conn->prepare("SELECT mission_id FROM mission_completions WHERE username = ?");
$stmt->bind_param("s", $username);
$stmt->execute();
$result = $stmt->get_result();

$completed = [];
while ($row = $result->fetch_assoc()) {
    $completed[] = $row['mission_id'];
}

echo json_encode([
    'status'             => 'success',
    'completed_missions' => $completed,
    'all_complete'       => count($completed) >= $total_missions
]);

$stmt->close();
$conn->close();
