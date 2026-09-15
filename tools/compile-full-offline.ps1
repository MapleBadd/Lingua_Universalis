# Lingua Universalis - FULL offline compile check for ALL mod sources.
#
# Two modes:
#   [real]  首选：对 **真实 NeoForge 26.2.0.77 补丁面 + 真实补丁版 Minecraft** 编译
#           （classpath = build/moddev/artifacts/minecraft-patched-*.jar + neoforge-*-universal.jar
#             + 运行库 + 真实 GeckoLib jar）。与 `gradlew build` 的编译阶段几乎等价，
#            不需要任何桩，能查出所有 NeoForge 事件/补丁 API 的真实签名问题。
#   [stub]  回退：仅有 vanilla client.jar 时，用 tools/neoforge-stubs + tools/vanilla-patch-stubs 做语法/类型校验。
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/compile-full-offline.ps1
#   powershell -ExecutionPolicy Bypass -File tools/compile-full-offline.ps1 -Mode stub
# Environment overrides:
#   $env:LU_PATCHED_JAR  -> 真实补丁版 MC jar（默认自动探测 build\moddev\artifacts\minecraft-patched-*-merged.jar）
#   $env:LU_NEOFORGE_JAR -> 真实 NeoForge universal jar（默认自动探测 .gradle 缓存）
#   $env:LU_MC_JAR       -> vanilla client jar（stub 模式 / 回退用，默认 %TEMP%\mc262-client.jar）
#   $env:LU_LIBS         -> 运行库目录（默认 %TEMP%\mclibs）
#   $env:LU_GECKOLIB_JAR -> GeckoLib NeoForge jar（默认 .gradle 缓存 / %TEMP%\geckolib-26.2-5.5.5.jar / <root>\libs）
param([ValidateSet('auto', 'real', 'stub')][string]$Mode = 'auto')
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

function Find-First($patterns) {
    foreach ($p in $patterns) {
        $hit = Get-ChildItem -Path $p -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

# ---- real (patched MC + real NeoForge) detection -------------------------------
# 首选：build\lu-compile-classpath.txt —— 由 tools\dump-classpath.gradle 从 Gradle 导出的
# **与真实构建完全一致**的 classpath（补丁版 MC + NeoForge + 全部运行库 + GeckoLib）。
# 生成方式：
#   gradlew.bat --offline -q --no-configuration-cache --init-script tools/dump-classpath.gradle luDumpCp |
#       Set-Content build\lu-compile-classpath.txt
$cpFile = $env:LU_CP_FILE
if (-not $cpFile) { $cpFile = Join-Path $root 'build\lu-compile-classpath.txt' }
$realCp = $null
if (Test-Path $cpFile) {
    $raw = (Get-Content -Raw $cpFile).Trim()
    $entries = $raw -split ';' | Where-Object { $_ }
    if ($entries.Count -gt 0 -and -not ($entries | Where-Object { -not (Test-Path $_) })) {
        $realCp = ($entries -join ';')
    }
}

$patchedJar = $env:LU_PATCHED_JAR
if (-not $patchedJar) {
    $patchedJar = Find-First @(
        (Join-Path $root 'build\moddev\artifacts\minecraft-patched-*-merged.jar'),
        (Join-Path $root 'build\moddev\artifacts\minecraft-patched-*.jar')
    )
}
$neoforgeJar = $env:LU_NEOFORGE_JAR
if (-not $neoforgeJar) {
    $neoforgeJar = Find-First @(
        (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\*\*\neoforge-*-universal.jar')
    )
}

$libsDir = $env:LU_LIBS
if (-not $libsDir) { $libsDir = Join-Path $env:TEMP 'mclibs' }

# ---- GeckoLib ------------------------------------------------------------------
$geckoJar = $env:LU_GECKOLIB_JAR
if (-not $geckoJar) {
    $geckoJar = Find-First @(
        (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\maven.modrinth\geckolib\*\*\geckolib-*.jar'),
        (Join-Path $root 'libs\geckolib*.jar'),
        (Join-Path $env:TEMP 'geckolib-26.2-5.5.5.jar')
    )
}
if ($geckoJar -and -not (Test-Path $geckoJar)) { $geckoJar = $null }
if (-not $geckoJar) {
    Write-Output 'WARNING: GeckoLib jar not found - animation sources will not compile.'
}

# ---- javac ---------------------------------------------------------------------
$javac = 'javac.exe'
foreach ($cand in @($env:JAVA_HOME_25, "$env:LOCALAPPDATA\LinguaUnivJdk25\jdk-25.0.4.1")) {
    if ($cand -and (Test-Path (Join-Path $cand 'bin\javac.exe'))) {
        $javac = Join-Path $cand 'bin\javac.exe'
        break
    }
}

$sources = @()
Get-ChildItem -Path (Join-Path $root 'src\main\java') -Recurse -Filter *.java |
    ForEach-Object { $sources += $_.FullName }

$useReal = ($Mode -ne 'stub') -and (($realCp) -or ($patchedJar -and $neoforgeJar -and (Test-Path $libsDir)))

if ($useReal) {
    if ($realCp) {
        $cp = $realCp
    } else {
        $cp = "$patchedJar;$neoforgeJar;$libsDir\*"
        if ($geckoJar) { $cp = "$cp;$geckoJar" }
    }
    $out = Join-Path $root 'build\full-offline-classes'
    if (Test-Path $out) { Remove-Item $out -Recurse -Force }
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    Write-Output 'MODE: real (真实补丁版 Minecraft + 真实 NeoForge API, no stubs)'
    Write-Output ("  classpath  : {0}" -f $(if ($realCp) { "Gradle 导出 ($cpFile)" } else { "自动探测" }))
    & $javac -encoding UTF-8 -nowarn -cp $cp -d $out $sources
    if ($LASTEXITCODE -ne 0) {
        Write-Output 'FULL OFFLINE COMPILE FAILED'
        exit $LASTEXITCODE
    }
    Write-Output 'FULL OFFLINE COMPILE OK (real NeoForge API)'
    exit 0
}

if ($Mode -eq 'real') {
    throw "real mode requested but prerequisites are missing (patched=$patchedJar neoforge=$neoforgeJar libs=$libsDir)"
}

# ---- stub fallback -------------------------------------------------------------
$clientJar = $env:LU_MC_JAR
if (-not $clientJar) { $clientJar = Join-Path $env:TEMP 'mc262-client.jar' }
if (-not (Test-Path $clientJar)) { throw "client jar not found: $clientJar" }
if (-not (Test-Path $libsDir)) { throw "libs dir not found: $libsDir" }

foreach ($stubRoot in @('neoforge-stubs', 'vanilla-patch-stubs')) {
    Get-ChildItem -Path (Join-Path $root ("tools\{0}\src" -f $stubRoot)) -Recurse -Filter *.java -ErrorAction SilentlyContinue |
        ForEach-Object { $sources += $_.FullName }
}

$out = Join-Path $root 'build\full-offline-classes'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null

$cp = "$clientJar;$libsDir\*"
if ($geckoJar) { $cp = "$cp;$geckoJar" }
Write-Output 'MODE: stub (vanilla client jar + NeoForge source-level stubs)'
& $javac -encoding UTF-8 -nowarn -cp $cp -d $out $sources
if ($LASTEXITCODE -ne 0) {
    Write-Output 'FULL OFFLINE COMPILE FAILED'
    exit $LASTEXITCODE
}
Write-Output 'FULL OFFLINE COMPILE OK (stub mode)'
