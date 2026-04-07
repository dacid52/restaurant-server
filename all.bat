@echo off
chcp 65001 >nul
setlocal

rem Portable Maven folder
set "MAVEN_DIR=%~dp0.maven\apache-maven-3.9.6"
set "MVN_CMD=mvn"
set "SPRING_PROFILE=local"
if "%JWT_SECRET%"=="" (
  for /f %%I in ('powershell -NoProfile -Command "([guid]::NewGuid().ToString(\"N\") + [guid]::NewGuid().ToString(\"N\"))"') do set "JWT_SECRET=%%I"
)
if "%INTERNAL_SERVICE_TOKEN%"=="" (
  for /f %%I in ('powershell -NoProfile -Command "[guid]::NewGuid().ToString(\"N\")"') do set "INTERNAL_SERVICE_TOKEN=%%I"
)
set PORTS=3000 3002 3003 3004 3005 3006 3007 3008 3011

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 goto start_apps

echo "mvn" not found. Preparing portable Maven...
if exist "%MAVEN_DIR%" goto set_mvn

echo Downloading Apache Maven 3.9.6...
powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile 'maven.zip'"

echo Extracting Maven...
powershell -Command "Expand-Archive -Path 'maven.zip' -DestinationPath '%~dp0.maven' -Force"

del maven.zip

:set_mvn
set "MVN_CMD=%MAVEN_DIR%\bin\mvn.cmd"
echo Portable Maven is ready.

:start_apps
echo =======================================================
echo     START RESTAURANT MICROSERVICES
echo =======================================================
echo JWT secret is generated for this run.
echo Internal service token is generated for this run.
echo MySQL local: Laragon ^| user=root ^| password=empty
echo Cleaning old listeners on project ports...
for %%P in (%PORTS%) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr /r /c:":%%P .*LISTENING"') do (
    taskkill /PID %%I /F >nul 2>nul
  )
)
timeout /t 2 /nobreak >nul

echo 1. Starting API Gateway (Port 3000)...
start "API Gateway (3000)" cmd /k "cd /d "%~dp0api-gateway" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

timeout /t 3 /nobreak >nul

echo 2. Starting User Service (Port 3005)...
start "User Service (3005)" cmd /k "cd /d "%~dp0user-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 3. Starting Menu Service (Port 3002)...
start "Menu Service (3002)" cmd /k "cd /d "%~dp0menu-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 4. Starting Order Service (Port 3003)...
start "Order Service (3003)" cmd /k "cd /d "%~dp0order-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 5. Starting Kitchen Service (Port 3004)...
start "Kitchen Service (3004)" cmd /k "cd /d "%~dp0kitchen-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 6. Starting Inventory Service (Port 3006)...
start "Inventory Service (3006)" cmd /k "cd /d "%~dp0inventory-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 7. Starting Image Service (Port 3007)...
start "Image Service (3007)" cmd /k "cd /d "%~dp0image-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 8. Starting Payment Service (Port 3008)...
start "Payment Service (3008)" cmd /k "cd /d "%~dp0payment-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo 9. Starting Table Service + Customer Web (Port 3011)...
start "Table Service (3011)" cmd /k "cd /d "%~dp0table-service" && %MVN_CMD% -Dspring-boot.run.profiles=%SPRING_PROFILE% spring-boot:run"

echo =======================================================
echo Done. 9 independent Command Prompt windows were opened.
echo NOTE: Build Fe-Customer truoc neu chua co static files:
echo   build-customer-fe.bat
echo Customer web: http://localhost:3011
echo =======================================================
