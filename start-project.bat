@echo off
title Online Fuel Delivery Management System - Launcher
echo ======================================================================
echo  Starting Online Fuel Delivery Management System...
echo ======================================================================

:: 1. Verify MySQL Service
echo [1/3] Checking MySQL Service...
sc query MySQL80 | find "RUNNING" >nul
if %errorlevel% neq 0 (
    echo Starting MySQL80 service...
    net start MySQL80
) else (
    echo MySQL is already running.
)

:: 2. Start Apache Tomcat
echo [2/3] Starting Apache Tomcat 10.1...
cd /d "D:\apache-tomcat-10.1.52\bin"
start "Apache Tomcat" catalina.bat run

:: 3. Launch Public Cloudflare HTTPS Tunnel
echo [3/3] Starting Public Cloudflare HTTPS Tunnel...
cd /d "%~dp0"
if exist cloudflared.exe (
    start "Cloudflare Public Tunnel" cloudflared.exe tunnel --url http://localhost:8080
)

echo.
echo ======================================================================
echo  PROJECT IS RUNNING!
echo  Local URL:   http://localhost:8080/Online_Fuel_Delivery_Management_System/Home_Page.html
echo  Admin Login: admin@fuelexpress.in / admin123
echo  User Login:  rahul@example.com / pass123
echo ======================================================================
pause
