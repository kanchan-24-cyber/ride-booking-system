#!/bin/sh
# Compiles everything and starts the website at http://localhost:8080
rm -rf out
javac -d out $(find src -name "*.java") || { echo "Compile failed. Need JDK 17+ (java -version)"; exit 1; }
echo "Open http://localhost:8080 in your browser. Press Ctrl+C to stop."
java -cp out com.ridebooking.web.WebServer
