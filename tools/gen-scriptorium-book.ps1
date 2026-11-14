# Generate merged scriptorium assets (desk + first draft book) -- derived files, art sources untouched.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-scriptorium-book.ps1
#       [-BookScale 1.0] [-ExtraBookRotX 0]
#
# Outputs (src/main/resources/assets/lingua_universalis/):
#   geckolib/models/scriptorium_book.geo.json             desk + book bones (book under BookLocator -> book_root)
#   geckolib/animations/scriptorium_book.animation.json   ONLY "empty" (hides book bones when the desk is empty)
#   textures/block/scriptorium_book.png                   256x128 combined atlas (desk left / book right)
#
# The book on the desk is STATIC: it keeps the artist's model rest pose (no open/display/flip animations).
param(
    [double]$BookScale = 1.0,
    [double]$ExtraBookRotX = 0
)
$ErrorActionPreference = 'Stop'
$base = Split-Path -Parent $PSScriptRoot
$res = Join-Path $base 'src\main\resources\assets\lingua_universalis'
$utf8 = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Drawing

$desk = [System.IO.File]::ReadAllText((Join-Path $res 'geckolib\models\scriptorium.geo.json'), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$book = [System.IO.File]::ReadAllText((Join-Path $res 'geckolib\models\first_draft.geo.json'), [System.Text.Encoding]::UTF8) | ConvertFrom-Json

$locatorBone = $desk.'minecraft:geometry'[0].bones | Where-Object { $_.name -eq 'BookLocator' }
$bookRootBone = $book.'minecraft:geometry'[0].bones | Where-Object { $_.name -eq 'all' }
$LocatorPivot = @([double]($locatorBone.pivot[0]), [double]($locatorBone.pivot[1]), [double]($locatorBone.pivot[2]))
$RootPivot = @([double]($bookRootBone.pivot[0]), [double]($bookRootBone.pivot[1]), [double]($bookRootBone.pivot[2]))

function R4($v) { return [math]::Round([double]$v, 6) }
function MapPos($a) {
    if ($null -eq $a) { return $null }
    $arr = @($a)
    if ($arr.Count -lt 3) { return $arr }
    $out = @()
    $pairs = @(@([double]($arr[0]), [double]($RootPivot[0]), [double]($LocatorPivot[0])),
               @([double]($arr[1]), [double]($RootPivot[1]), [double]($LocatorPivot[1])),
               @([double]($arr[2]), [double]($RootPivot[2]), [double]($LocatorPivot[2])))
    foreach ($pair in $pairs) {
        $v = [double]($pair[0])
        $shifted = ($v - [double]($pair[1])) + [double]($pair[2])
        $scaled = [double]($pair[2]) + ($shifted - [double]($pair[2])) * [double]$BookScale
        $out += (R4 $scaled)
    }
    return $out
}
function ShiftUv($uvObj) {
    $out = [ordered]@{}
    foreach ($face in $uvObj.PSObject.Properties) {
        $f = [ordered]@{ uv = @(([double]$face.Value.uv[0] + 128), [double]$face.Value.uv[1]) }
        if ($face.Value.uv_size) { $f.uv_size = $face.Value.uv_size }
        $out[$face.Name] = [pscustomobject]$f
    }
    return [pscustomobject]$out
}

# ---------------- combined texture ----------------
$deskImg = [System.Drawing.Bitmap]::FromFile((Join-Path $res 'textures\block\scriptorium.png'))
$bookImg = [System.Drawing.Bitmap]::FromFile((Join-Path $res 'textures\item\first_draft.png'))
$combined = New-Object System.Drawing.Bitmap(256, 128, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($combined)
$g.Clear([System.Drawing.Color]::Transparent)
$g.DrawImage($deskImg, 0, 0, 128, 128)
$g.DrawImage($bookImg, 128, 0, 128, 128)
$g.Dispose()
$combined.Save((Join-Path $res 'textures\block\scriptorium_book.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$deskImg.Dispose(); $bookImg.Dispose(); $combined.Dispose()

# ---------------- merged model ----------------
$bones = New-Object System.Collections.ArrayList
[void]$bones.Add(($desk.'minecraft:geometry'[0].bones | Where-Object { $_.name -eq 'all' }))
[void]$bones.Add(($desk.'minecraft:geometry'[0].bones | Where-Object { $_.name -eq 'BookLocator' }))
[void]$bones.Add([pscustomobject]@{
        name     = 'book_root'
        parent   = 'BookLocator'
        pivot    = @((R4 $LocatorPivot[0]), (R4 $LocatorPivot[1]), (R4 $LocatorPivot[2]))
        rotation = @((R4 $ExtraBookRotX), 0, 0)
    })
foreach ($boneDef in $book.'minecraft:geometry'[0].bones) {
    $name = if ($boneDef.name -eq 'all') { 'book_all' } else { $boneDef.name }
    $parent = if ($boneDef.name -eq 'all') { 'book_root' } elseif ($boneDef.parent -eq 'all') { 'book_all' } else { $boneDef.parent }
    $node = [ordered]@{ name = $name; parent = $parent; pivot = (MapPos $boneDef.pivot) }
    if ($boneDef.rotation) { $node.rotation = @((R4 $boneDef.rotation[0]), (R4 $boneDef.rotation[1]), (R4 $boneDef.rotation[2])) }
    if ($boneDef.cubes) {
        $cubes = @()
        foreach ($c in $boneDef.cubes) {
            $size = @((R4 ([double]($c.size[0]) * [double]$BookScale)), (R4 ([double]($c.size[1]) * [double]$BookScale)), (R4 ([double]($c.size[2]) * [double]$BookScale)))
            $nc = [ordered]@{ origin = (MapPos $c.origin); size = $size; uv = (ShiftUv $c.uv) }
            # keep the remaining fields untouched (inflate etc. drive the fine details)
            foreach ($extra in $c.PSObject.Properties) {
                if ($extra.Name -in @('origin', 'size', 'uv', 'pivot')) { continue }
                $nc[$extra.Name] = $extra.Value
            }
            if ($c.pivot) { $nc['pivot'] = (MapPos $c.pivot) }
            $cubes += [pscustomobject]$nc
        }
        $node.cubes = $cubes
    }
    [void]$bones.Add([pscustomobject]$node)
}
$deskDesc = $desk.'minecraft:geometry'[0].description
$bookDesc = $book.'minecraft:geometry'[0].description
$desc = [ordered]@{
    identifier            = 'geometry.scriptorium_book'
    texture_width         = 256
    texture_height        = 128
    visible_bounds_width  = [Math]::Max([double]$deskDesc.visible_bounds_width, [double]$bookDesc.visible_bounds_width)
    visible_bounds_height = [Math]::Max([double]$deskDesc.visible_bounds_height, [double]$bookDesc.visible_bounds_height)
    visible_bounds_offset = @(0, 0.75, 0)
}
$merged = [ordered]@{ format_version = '1.12.0'; 'minecraft:geometry' = @([ordered]@{ description = $desc; bones = $bones }) }
[System.IO.File]::WriteAllText((Join-Path $res 'geckolib\models\scriptorium_book.geo.json'),
    ($merged | ConvertTo-Json -Depth 14 -Compress), $utf8)

# ---------------- animations: "empty" (hide book) + "show" (explicitly restore book scale) ----------------
# Why two: STOP does NOT reset bone transforms that a previous animation (empty: scale 0) left behind,
# so a book placed on the desk stayed invisible. "show" pins the scale back to 1 in one frame.
$animDoc = [ordered]@{
    format_version = '1.8.0'
    animations     = [pscustomobject]([ordered]@{
            empty = [pscustomobject]([ordered]@{
                    loop             = $true
                    animation_length = 0.0417
                    bones            = [pscustomobject]([ordered]@{
                            book_all = [pscustomobject]@{ scale = [pscustomobject]@{ vector = @(0, 0, 0) } }
                        })
                })
            show  = [pscustomobject]([ordered]@{
                    loop             = $true
                    animation_length = 0.0417
                    bones            = [pscustomobject]([ordered]@{
                            book_all = [pscustomobject]@{ scale = [pscustomobject]@{ vector = @(1, 1, 1) } }
                        })
                })
        })
}
[System.IO.File]::WriteAllText((Join-Path $res 'geckolib\animations\scriptorium_book.animation.json'),
    ($animDoc | ConvertTo-Json -Depth 10), $utf8)

Write-Host ("merged assets generated: bookScale={0} extraRotX={1} (only the 'empty' animation)" -f $BookScale, $ExtraBookRotX)
