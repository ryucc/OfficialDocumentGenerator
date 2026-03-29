#!/bin/bash

# Run frontend dev server
# Usage: ./scripts/run-local-frontend.sh

set -e

echo "=================================================="
echo "Starting Frontend Dev Server"
echo "=================================================="

cd frontend

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
  echo "Installing dependencies..."
  npm install
fi

echo ""
echo "Frontend will run on: http://localhost:5173"
echo "API endpoint: http://localhost:3001/api/v1"
echo ""
echo "Make sure backend is running:"
echo "  ./scripts/run-local-backend.sh"
echo ""
echo "Press Ctrl+C to stop"
echo ""

npm run dev
