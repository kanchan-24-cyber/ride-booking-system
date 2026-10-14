@echo off
REM Compiles everything and runs the 21 tests
if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 (
  echo Compile failed. Check that JDK 17 or newer is installed: java -version
  pause
  exit /b 1
)
java -cp out com.ridebooking.AllTests
pause
