@echo off
setlocal

:: ── start-backend.bat ─────────────────────────────────────────────────────────
:: Starts the Spring Boot API locally using the dev profile.
:: PostgreSQL must already be running (run start-db.bat first).
:: API will be available at http://localhost:8080/api
:: ──────────────────────────────────────────────────────────────────────────────

set ROOT=%~dp0..

echo Starting Spring Boot API (profile: dev)...
echo API will be available at http://localhost:8080/api
echo Press Ctrl+C to stop.
echo.

cd /d "%ROOT%\account-posting"
call mvn spring-boot:run -Dspring-boot.run.profiles=dev
