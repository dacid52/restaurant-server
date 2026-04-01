@echo off
setlocal

rem Thu muc chua Maven portable (neu chua cai)
set "MAVEN_DIR=%~dp0.maven\apache-maven-3.9.6"
set "MVN_CMD=mvn"

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 goto start_apps

echo "mvn" khong duoc tim thay tren may. Tien hanh tai va cai dat Maven portable tu dong...
if exist "%MAVEN_DIR%" goto set_mvn

echo Dang tai Apache Maven 3.9.6...
powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile 'maven.zip'"

echo Dang giai nen Maven...
powershell -Command "Expand-Archive -Path 'maven.zip' -DestinationPath '%~dp0.maven' -Force"

del maven.zip

:set_mvn
set "MVN_CMD=%MAVEN_DIR%\bin\mvn.cmd"
echo Da thiet lap xong Maven portable!

:start_apps
echo =======================================================
echo     KHOI DONG HE THONG RESTAURANT MICROSERVICES
echo =======================================================

echo 1. Dang khoi dong API Gateway (Port 3000)...
start "API Gateway (3000)" cmd /k "cd api-gateway && %MVN_CMD% spring-boot:run"

timeout /t 3 /nobreak >nul

echo 2. Dang khoi dong User Service (Port 3005)...
start "User Service (3005)" cmd /k "cd user-service && %MVN_CMD% spring-boot:run"

echo 3. Dang khoi dong Menu Service (Port 3002)...
start "Menu Service (3002)" cmd /k "cd menu-service && %MVN_CMD% spring-boot:run"

echo 4. Dang khoi dong Order Service (Port 3003)...
start "Order Service (3003)" cmd /k "cd order-service && %MVN_CMD% spring-boot:run"

echo 5. Dang khoi dong Kitchen Service (Port 3004)...
start "Kitchen Service (3004)" cmd /k "cd kitchen-service && %MVN_CMD% spring-boot:run"

echo 6. Dang khoi dong Inventory Service (Port 3006)...
start "Inventory Service (3006)" cmd /k "cd inventory-service && %MVN_CMD% spring-boot:run"

echo 7. Dang khoi dong Image Service (Port 3007)...
start "Image Service (3007)" cmd /k "cd image-service && %MVN_CMD% spring-boot:run"

echo 8. Dang khoi dong Payment Service (Port 3008)...
start "Payment Service (3008)" cmd /k "cd payment-service && %MVN_CMD% spring-boot:run"

echo 9. Dang khoi dong Table Service (Port 3011)...
start "Table Service (3011)" cmd /k "cd table-service && %MVN_CMD% spring-boot:run"

echo =======================================================
echo Hoan tat! 9 cua so Command Prompt doc lap da duoc bat.
echo =======================================================
