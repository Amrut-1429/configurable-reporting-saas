@echo off
echo Starting NexusBI Platform...
echo.

echo Starting Spring Boot Backend (Port 8081)...
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set PATH=%JAVA_HOME%\bin;C:\Users\Amrut\Downloads\apache-maven-3.9.9\bin;%PATH%
start "NexusBI Backend" cmd /c "cd backend && mvn spring-boot:run"

echo Starting React Frontend...
start "NexusBI Frontend" cmd /c "cd frontend && npm run dev"

echo.
echo Both servers are starting in separate windows.
echo The frontend will be available at http://localhost:5173 (or 5174 if 5173 is in use).
echo Close those windows to stop the servers.
pause
