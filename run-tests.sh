#!/bin/sh
# Compiles everything and runs the 21 tests
rm -rf out
javac -d out $(find src -name "*.java") || { echo "Compile failed. Need JDK 17+ (java -version)"; exit 1; }
java -cp out com.ridebooking.AllTests
