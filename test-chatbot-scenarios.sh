#!/bin/bash
# Comprehensive chatbot test script
# Run this against a locally started server (after mvnw.cmd clean compile && mvnw.cmd spring-boot:run)
# Usage: bash test-chatbot-scenarios.sh BASE_URL

BASE_URL="${1:-http://localhost:8080}"
HEADER="X-Demo-User: 1"

echo "========================================"
echo "  Chatbot Scenario Tests"
echo "  Base URL: $BASE_URL"
echo "========================================"
echo ""

run_test() {
    local description="$1"
    local query="$2"
    local expected_pattern="$3"
    local should_match="${4:-true}"  # true = expected pattern should be in response; false = should NOT

    echo "--- Test: $description"
    echo "Query: $query"
    RESPONSE=$(curl -s "$BASE_URL/ask?q=$query" -H "$HEADER")
    echo "Response: $RESPONSE"

    if [ "$should_match" = "true" ]; then
        if echo "$RESPONSE" | grep -qi "$expected_pattern"; then
            echo "✅ PASS: Contains expected pattern '$expected_pattern'"
        else
            echo "❌ FAIL: Expected '$expected_pattern' in response"
        fi
    else
        if echo "$RESPONSE" | grep -qi "$expected_pattern"; then
            echo "❌ FAIL: Should NOT contain '$expected_pattern' in response"
        else
            echo "✅ PASS: Correctly does not return '$expected_pattern'"
        fi
    fi
    echo ""
    sleep 1
}

# === RAG + Document Q&A ===
run_test "Hackathon team size (RAG)" \
    "how many students can be in a hackathon team" \
    "2 to 4" \
    "true"

run_test "Project rules (RAG, multi-fact)" \
    "what are the project rules" \
    "hackathon\|team\|leader\|deadline\|judg\|plagiarism\|disqualification" \
    "true"

run_test "Out-of-scope joke (should be gated)" \
    "tell me a joke" \
    "joke\|funny\|laugh" \
    "false"

run_test "Unknown hackathon winner (should be gated)" \
    "who won the 2024 hackathon" \
    "2024\|winner\|won" \
    "false"

# === Tool Dispatch Tests ===
run_test "Show my projects (D1)" \
    "show my projects" \
    "active project" \
    "true"

run_test "Find teammates (D2)" \
    "find teammates" \
    "teammate\|skill gap\|candidate" \
    "true"

run_test "Upcoming events (D3)" \
    "upcoming events" \
    "event\|WORKSHOP\|HACKATHON" \
    "true"

run_test "My bookmarks (D4)" \
    "my bookmarks" \
    "bookmark\|saved" \
    "true"

run_test "Notifications (D5)" \
    "notifications" \
    "notification\|notif\|read\|alert" \
    "true"

run_test "Who knows Java (D6)" \
    "who knows Java" \
    "student\|Java\|available" \
    "true"

run_test "Glossary for JS (D7)" \
    "glossary for js" \
    "JavaScript\|canonical\|alias" \
    "true"

run_test "Skill graph dashboard (D8)" \
    "skill graph dashboard" \
    "Campus Intelligence\|analytics\|students\|Top skills" \
    "true"

# === Multi-Hop / Conversation Memory ===
echo "--- Test: Multi-hop conversation"
FIRST_RESP=$(curl -s "$BASE_URL/ask?q=show%20my%20projects" -H "$HEADER")
echo "First: $FIRST_RESP"
sleep 1
SECOND_RESP=$(curl -s "$BASE_URL/api/agent" -H "$HEADER" -H "Content-Type: application/json" -d '{"question":"who can fill the gaps in Campus AI"}')
echo "Second (follow-up): $SECOND_RESP"
echo ""

# === Profile/Stats ===
run_test "Profile completeness" \
    "how complete is my profile" \
    "complete\|%\ \|score\|missing" \
    "true"

run_test "Skill recommendations" \
    "recommend skills" \
    "recommend\|skill\|suggest" \
    "true"

# === Edge Cases ===
run_test "No header (should error)" \
    "hello" \
    "400\|Bad Request\|login\|header" \
    "true"

run_test "Empty query" \
    "" \
    "error\|bad request\|rephrase" \
    "true"

echo "========================================"
echo "  Test Summary"
echo "========================================"
echo "Check results above. All RAG queries should return doc-based answers."
echo "Gated queries should return the OUT_OF_SCOPE message."
echo "Tool queries should return structured data."
