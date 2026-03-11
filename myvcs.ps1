$ErrorActionPreference = "Stop"

$src = "src\main\java\myvcs"
$out = "out"

if (-not (Test-Path $out)) {
  New-Item -ItemType Directory -Path $out | Out-Null
}

$sources = Get-ChildItem -Path $src -Filter *.java
if (-not $sources) {
  Write-Error "No source files found under $src"
}

$classes = Get-ChildItem -Path "$out\myvcs" -Filter *.class -ErrorAction SilentlyContinue
if (-not $classes) {
  javac -d $out "$src\*.java"
} else {
  $latestSource = $sources | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  $latestClass = $classes | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($latestSource.LastWriteTime -gt $latestClass.LastWriteTime) {
    javac -d $out "$src\*.java"
  }
}

java -cp $out myvcs.Main @args
