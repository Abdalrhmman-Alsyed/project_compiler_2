@echo off
cd /d "%~dp0"

set "JDK=C:\Program Files\Java\jdk-21\bin"
if defined JAVA_HOME set "JDK=%JAVA_HOME%\bin"
if not exist "%JDK%\java.exe" (
    echo JDK not found at "%JDK%"
    echo Set JAVA_HOME or edit the JDK path in run.bat
    exit /b 1
)

powershell -NoProfile -Command "Get-ChildItem -Recurse src -Filter *.java | Where-Object { $_.FullName -notmatch '\\code generator\\' } | ForEach-Object { $_.FullName } | Set-Content -Encoding ASCII sources.txt"
if errorlevel 1 exit /b 1

"%JDK%\javac.exe" -encoding UTF-8 -d out -cp "libs\antlr-4.13.2-complete.jar" @sources.txt
if errorlevel 1 exit /b 1

"%JDK%\java.exe" -Dfile.encoding=UTF-8 -cp "out;libs\antlr-4.13.2-complete.jar" Main %*
