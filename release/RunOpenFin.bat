@ECHO OFF

SETLOCAL ENABLEDELAYEDEXPANSION
SETLOCAL ENABLEEXTENSIONS


:loop
 IF "%1"=="" GOTO cont
 SET opt=%1
 IF "%opt%" == "--config" (
    SET startupURL=%2
 )

 SHIFT & GOTO loop

:cont

echo %startupURL%

SET openfinLocation=%LocalAppData%\OpenFin

cd %openfinLocation%
start OpenFinRVM.exe --config=%startupURL%

ENDLOCAL