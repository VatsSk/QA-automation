@echo off
setlocal enabledelayedexpansion

set "input_file=D:\Testing engineer\testing-automation\src\main\resources\static\Testforge.html"
set "temp_file=D:\Testing engineer\testing-automation\src\main\resources\static\temp_testforge.html"

set "search=        <button class="btn btn-ghost btn-xs" onclick="viewRun('${id}')">View</button>"
set "replace=        <button class="btn btn-ghost btn-xs" onclick="viewRun('${id}')">View</button>^

        <button class="btn btn-ghost btn-xs" onclick="reRunTest('${id}')">▶ Re-run</button>"

for /f "delims=" %%a in (%input_file%) do (
    set "line=%%a"
    if "!line!"=="%search%" (
        echo %replace%>>%temp_file%
    ) else (
        echo !line!>>%temp_file%
    )
)

move /Y %temp_file% %input_file%
echo Replacement completed.
