@echo off
setlocal

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto runMaven

echo Maven not found, downloading...
mkdir "%MAVEN_HOME%" 2>nul

set "URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
set "TMPFILE=%TEMP%\maven-download.zip"

curl -fsSL "%URL%" -o "%TMPFILE%"
if errorlevel 1 (
    set "URL=https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
    curl -fsSL "%URL%" -o "%TMPFILE%"
)

powershell -Command "Expand-Archive -Path '%TMPFILE%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force"
del "%TMPFILE%" 2>nul

echo Maven 3.9.9 installed to %MAVEN_HOME%

:runMaven
"%MVN_CMD%" %*
