@echo off
setlocal

:: ── start-frontend.bat ────────────────────────────────────────────────────────
:: Starts the React dev server.
:: Vite proxies /api to http://localhost:8080 — no CORS config needed.
:: UI will be available at http://localhost:3000
:: ──────────────────────────────────────────────────────────────────────────────

set ROOT=%~dp0..

echo Starting React UI (Vite dev server)...
echo UI will be available at http://localhost:3000
echo Press Ctrl+C to stop.
echo.

cd /d "%ROOT%\ui"

:: Install dependencies if node_modules is missing
if not exist node_modules (
    echo node_modules not found. Running npm install...
    call npm install
)

call npm run dev
