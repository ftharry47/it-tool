@ECHO OFF
SETLOCAL
CALL "%~dp0tools\node\npm.cmd" %*
EXIT /B %ERRORLEVEL%

