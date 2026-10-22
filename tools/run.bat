@echo off
rem Compile puis execute l'application capstone (sortie utilisee pour les captures du README).
cd /d "%~dp0.."
call mvn -B -q compile exec:java -Dexec.mainClass=com.example.MainApp
