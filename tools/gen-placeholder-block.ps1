# Lingua Universalis - placeholder BLOCK resource generator.
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen-placeholder-block.ps1 -Ids a,b,c
# Generates per id:
#   blockstates/<id>.json, models/block/<id>.json (cube_all),
#   textures/block/<id>.png (16x16), models/item/<id>.json (parent: block/<id>)
param(
    [Parameter(Mandatory = $true)]
    [string[]]$Ids
)

Add-Type -AssemblyName System.Drawing -ErrorAction Stop
$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'src\main\resources\assets\lingua_universalis'
$bsDir = Join-Path $assets 'blockstates'
$blockModelDir = Join-Path $assets 'models\block'
$itemModelDir = Join-Path $assets 'models\item'
$texDir = Join-Path $assets 'textures\block'
foreach ($d in @($bsDir, $blockModelDir, $itemModelDir, $texDir)) {
    New-Item -ItemType Directory -Path $d -Force | Out-Null
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

foreach ($id in $Ids) {
    $blockstate = @{ variants = @{ "" = @{ model = "lingua_universalis:block/$id" } } }
    $blockModel = @{
        parent   = "minecraft:block/cube_all"
        textures = @{ all = "lingua_universalis:block/$id" }
    }
    $itemModel = @{ parent = "lingua_universalis:block/$id" }

    [System.IO.File]::WriteAllText((Join-Path $bsDir "$id.json"),
        ($blockstate | ConvertTo-Json -Depth 6), $utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $blockModelDir "$id.json"),
        ($blockModel | ConvertTo-Json -Depth 4), $utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $itemModelDir "$id.json"),
        ($itemModel | ConvertTo-Json -Depth 4), $utf8NoBom)

    $sum = 0
    foreach ($ch in $id.ToCharArray()) { $sum += [int]$ch }
    $hue = $sum % 360
    $r = 120 + ($hue % 90)
    $gr = 110 + (($hue / 2) % 90)
    $b = 130 + (($hue / 5) % 90)
    if ($r -gt 255) { $r = 255 }
    if ($gr -gt 255) { $gr = 255 }
    if ($b -gt 255) { $b = 255 }

    $bmp = New-Object System.Drawing.Bitmap 16, 16
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(255, $r, $gr, $b))
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 30, 30, 30))
    $g.DrawRectangle($pen, 0, 0, 15, 15)
    $g.DrawLine($pen, 0, 0, 15, 15)
    $g.DrawLine($pen, 15, 0, 0, 15)
    $g.Dispose()
    $pngPath = Join-Path $texDir "$id.png"
    $bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("generated block {0}" -f $id)
}
