@echo off
chcp 65001 >nul
rem Запуск приложения: находит JDK 21 и запускает Spring Boot через Maven Wrapper.
cd /d "%~dp0"

if not exist ".env" (
    echo Нет файла .env. Сначала выполните: powershell -ExecutionPolicy Bypass -File db\setup.ps1
    pause
    exit /b 1
)

rem Настройки Maven Wrapper (если папки .mvn нет - создаём).
if not exist ".mvn\wrapper\maven-wrapper.properties" (
    mkdir ".mvn\wrapper" 2>nul
    (
        echo wrapperVersion=3.3.2
        echo distributionType=only-script
        echo distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip
    ) > ".mvn\wrapper\maven-wrapper.properties"
)

rem Проекту нужна Java 21+. Если JAVA_HOME не указывает на неё, ищем Temurin 21.
set "JDK21="
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JDK21=%%~fD"
if defined JDK21 set "JAVA_HOME=%JDK21%"
if not defined JAVA_HOME (
    echo Не найдена Java 21. Установите: winget install --id EclipseAdoptium.Temurin.21.JDK -e
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Java: %JAVA_HOME%

call "%~dp0mvnw.cmd" spring-boot:run
echo.
echo Приложение остановлено.
pause
