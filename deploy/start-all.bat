@echo off
setlocal

:: ── start-all.bat ─────────────────────────────────────────────────────────────
:: Starts DB + migrations, then opens backend and frontend in separate windows.
::
:: Order:
::   1. PostgreSQL container + Flyway migrations  (this window, waits to finish)
::   2. Spring Boot API                           (new window)
::   3. React UI dev server                       (new window)
:: ──────────────────────────────────────────────────────────────────────────────

set DEPLOY=%~dp0

echo ============================================================
echo  Account Posting Orchestrator — Local Start
echo ============================================================
echo.

echo [Step 1/3] Starting database and running migrations...
call "%DEPLOY%start-db.bat"
if %errorlevel% neq 0 (
    echo ERROR: Database setup failed. Aborting.
    pause
    exit /b 1
)

echo [Step 2/3] Opening backend in a new window...
start "Account Posting API" cmd /k ""%DEPLOY%start-backend.bat""

echo Waiting 15 seconds for the API to start before launching the UI...
timeout /t 15 /nobreak >nul

echo [Step 3/3] Opening frontend in a new window...
start "Account Posting UI" cmd /k ""%DEPLOY%start-frontend.bat""

echo.
echo ============================================================
echo  All services started:
echo    UI:      http://localhost:3000
echo    API:     http://localhost:8080/api
echo    Health:  http://localhost:8080/api/actuator/health
echo    DB:      localhost:5432  (postgres / postgres)
echo ============================================================
echo.
echo Close the API and UI windows to stop those services.
echo To stop the database:  docker stop account_posting_db_local
echo.
pause
