# Lingua Universalis - offline compile check against minimal API stubs.
# Usage: powershell -ExecutionPolicy Bypass -File tools/compile-mc-with-stubs.ps1
# Compiles the CORE + REGISTRY packages (pure logic + the item/block/tab registry layer)
# together with tools/mc-stubs/src stubs, validating those classes without the real
# Minecraft/NeoForge jars. Entity/client/main classes need the real toolchain and are
# verified by a real `gradlew build` instead. Output goes to build/mc-stub-classes.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$javac = 'javac.exe'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
}

$sources = @()
foreach ($pkg in @('core', 'registry')) {
    $dir = Join-Path $root ('src\main\java\com\linguauniversalis\' + $pkg)
    if (Test-Path $dir) {
        # LUEntities depends on the real entity stack; it is checked by `gradlew build`.
        Get-ChildItem -Path $dir -Recurse -Filter *.java |
            Where-Object { $_.Name -ne 'LUEntities.java' } |
            ForEach-Object { $sources += $_.FullName }
    }
}
$stubDir = Join-Path $root 'tools\mc-stubs\src'
Get-ChildItem -Path $stubDir -Recurse -Filter *.java | ForEach-Object { $sources += $_.FullName }

$out = Join-Path $root 'build\mc-stub-classes'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null

& $javac -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) {
    Write-Output "MC-LAYER STUB COMPILE FAILED (exit $LASTEXITCODE)"
    exit $LASTEXITCODE
}
Write-Output 'MC-LAYER STUB COMPILE OK'
