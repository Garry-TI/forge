@echo off
setlocal

set "SCRIPT_PATH=%~dp0scripts\generate_card_animation.py"
if not exist "%SCRIPT_PATH%" set "SCRIPT_PATH=%~dp0..\..\..\scripts\generate_card_animation.py"
if not exist "%SCRIPT_PATH%" set "SCRIPT_PATH=D:\projects\forge\scripts\generate_card_animation.py"

if "%~1"=="" goto :show_usage
if "%~1"=="-h" goto :show_usage
if "%~1"=="--help" goto :show_usage
if "%~1"=="/?" goto :show_usage

python "%SCRIPT_PATH%" %*
exit /b %ERRORLEVEL%

:show_usage
echo ====================================================================
echo   Forge MTG Animated Card Generator
echo ====================================================================
echo.
echo Usage:
echo   generate-card-animation.cmd ^<SET^> "^<CARD_NAME^>" "^<VIDEO_PATH^>" [^<CARD_NUMBER^>]
echo   generate-card-animation.cmd ^<SET^> "^<CARD_NAME^>" ^<CARD_NUMBER^> "^<VIDEO_PATH^>"
echo.
echo Examples:
echo   generate-card-animation.cmd AFR "Improvised Weaponry" "clip.mp4"
echo   generate-card-animation.cmd AFR "Acererak the Archlich" "clip.mp4" 372
echo   generate-card-animation.cmd AFR "Acererak the Archlich" 372 "clip.mp4"
echo   generate-card-animation.cmd FDN "Burst Lightning" "burst.mp4"
echo.
echo Options:
echo   --number 372     Card / collector number [for cards with multiple arts]
echo   --fps 24         Frame rate [default: 24]
echo   --quality 88     JPEG quality 1-100 [default: 88]
echo   --x 35 --y 70    Art window position [default: x=35, y=70, w=420, h=314]
echo.
echo ====================================================================
pause
exit /b 1
