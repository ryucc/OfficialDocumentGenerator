#!/bin/bash

# Run both frontend and backend locally
# Usage: ./scripts/run-local-dev.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=================================================="
echo "Starting Local Development Environment"
echo "=================================================="
echo ""
echo "This will start:"
echo "  - Backend API on http://localhost:3001"
echo "  - Frontend on http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop all servers"
echo ""

# Trap to kill all background processes
trap 'kill $(jobs -p) 2>/dev/null' EXIT

# Start backend in background
echo "Starting backend..."
"${SCRIPT_DIR}/run-local-backend.sh" &
BACKEND_PID=$!

# Wait for backend to start
sleep 3

# Start frontend in background
echo "Starting frontend..."
"${SCRIPT_DIR}/run-local-frontend.sh" &
FRONTEND_PID=$!

echo ""
echo "=================================================="
echo "✓ Both servers started!"
echo "=================================================="
echo ""
echo "Backend:  http://localhost:3001/api/v1/projects"
echo "Frontend: http://localhost:5173"
echo ""
echo "Logs will appear below. Press Ctrl+C to stop all."
echo "=================================================="
echo ""

# Wait for any process to exit
wait
