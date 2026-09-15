# Lingua Universalis - placeholder item resource generator.
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen-placeholder-item.ps1 -Ids a,b,c
# Generates per id:
#   assets/lingua_universalis/models/item/<id>.json   (item/generated)
#   assets/lingua_universalis/textures/item/<id>.png  (16x16 placeholder)
# Replace the PNG files with real art later (same name).
param(
    [Parameter(Mandatory = $true)]
    [string[]]$Ids
)

Add-Type -AssemblyName System.Drawing -ErrorAction Stop
$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'src\main\resources\assets\lingua_universalis'
$modelDir = Join-Path $assets 'models\item'
$texDir = Join-Path $assets 'textures\item'
New-Item -ItemType Directory -Path $modelDir, $texDir -Force | Out-Null
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

foreach ($id in $Ids) {
    $model = @{
        parent   = "minecraft:item/generated"
        textures = @{ layer0 = "lingua_universalis:item/$id" }
    }
    $modelPath = Join-Path $modelDir "$id.json"
    [System.IO.File]::WriteAllText($modelPath, ($model | ConvertTo-Json -Depth 5), $utf8NoBom)

    $sum = 0
    foreach ($ch in $id.ToCharArray()) { $sum += [int]$ch }
    $hue = $sum % 360
    $r = 150 + ($hue % 80)
    $gr = 140 + (($hue / 3) % 80)
    $b = 160 + (($hue / 7) % 80)
    if ($r -gt 255) { $r = 255 }
    if ($gr -gt 255) { $gr = 255 }
    if ($b -gt 255) { $b = 255 }

    $bmp = New-Object System.Drawing.Bitmap 16, 16
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(255, $r, $gr, $b))
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 40, 30, 20))
    $g.DrawRectangle($pen, 0, 0, 15, 15)
    $g.Dispose()
    $pngPath = Join-Path $texDir "$id.png"
    $bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("generated {0}" -f $id)
}
