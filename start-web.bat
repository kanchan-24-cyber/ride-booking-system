@echo off
REM Compiles everything and starts the website at http://localhost:8080
if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 (
  echo Compile failed. Check that JDK 17 or newer is installed: java -version
  pause
  exit /b 1
)
echo.
echo Open http://localhost:8080 in your browser. Press Ctrl+C to stop.
java -cp out com.ridebooking.web.WebServer
