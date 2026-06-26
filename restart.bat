@echo off
echo [ProjVault] Stopping old process...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8090"') do taskkill /F /PID %%a 2>nul
timeout /t 2 /nobreak >nul
echo [ProjVault] Compiling and starting...
cd /d D:\Java-p\ProjVault
call mvnw.cmd spring-boot:run
