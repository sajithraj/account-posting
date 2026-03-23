@echo off
setlocal

:: ── start-db.bat ──────────────────────────────────────────────────────────────
:: Starts a local PostgreSQL container and runs Flyway migrations (dev profile).
:: Run this once before starting the backend.
:: ──────────────────────────────────────────────────────────────────────────────

set ROOT=%~dp0..
set CONTAINER=account_posting_db_local
set DB=account_posting_db
set USER=postgres
set PASS=postgres
set PORT=5432

echo [1/3] Checking for existing container...
docker inspect %CONTAINER% >nul 2>&1
if %errorlevel%==0 (
    echo      Container already exists. Starting if stopped...
    docker start %CONTAINER% >nul 2>&1
) else (
    echo [1/3] Starting PostgreSQL container...
    docker run -d ^
        --name %CONTAINER% ^
        -e POSTGRES_DB=%DB% ^
        -e POSTGRES_USER=%USER% ^
        -e POSTGRES_PASSWORD=%PASS% ^
        -p %PORT%:5432 ^
        postgres:16-alpine
)

echo [2/3] Waiting for PostgreSQL to be ready...
:wait_loop
docker exec %CONTAINER% pg_isready -U %USER% -d %DB% >nul 2>&1
if %errorlevel% neq 0 (
    timeout /t 2 /nobreak >nul
    goto wait_loop
)
echo      PostgreSQL is ready.

echo [3/3] Running Flyway migrations (dev)...
cd /d "%ROOT%\db"
call mvn flyway:migrate -Pdev -q
if %errorlevel% neq 0 (
    echo ERROR: Flyway migration failed.
    exit /b 1
)
echo      Migrations applied successfully.

echo.
echo Database is ready on localhost:%PORT%
echo   DB:   %DB%
echo   User: %USER%  Pass: %PASS%
echo.
