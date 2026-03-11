@echo off
setlocal
if not exist out mkdir out
for /f "delims=" %%F in ('dir /b /s src\main\java\myvcs\*.java') do set SRC_FOUND=1
if not defined SRC_FOUND (
  echo No source files found under src\main\java\myvcs
  exit /b 1
)
for /f "delims=" %%F in ('dir /b /s src\main\java\myvcs\*.java') do set NEWER=1
for /f "delims=" %%F in ('dir /b /s out\myvcs\*.class 2^>nul') do set HAS_CLASS=1
if not defined HAS_CLASS (
  javac -d out src\main\java\myvcs\*.java || exit /b 1
) else (
  for %%S in (src\main\java\myvcs\*.java) do (
    for %%C in (out\myvcs\*.class) do (
      if %%~tS GTR %%~tC (
        javac -d out src\main\java\myvcs\*.java || exit /b 1
        goto run
      )
    )
  )
)
:run
java -cp out myvcs.Main %*
