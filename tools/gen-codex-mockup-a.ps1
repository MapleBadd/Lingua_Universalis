# Draw the codex GUI wireframe mockup (sketch A) for the design doc.
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-codex-mockup-a.ps1
# Output: docs-mockups/codex-overview.png
$ErrorActionPreference = 'Stop'
$base = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $base 'docs-mockups'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Add-Type -AssemblyName System.Drawing

$W = 1000
$H = 620

function MakeFont([double]$size, [string]$style = 'Regular') {
    return New-Object System.Drawing.Font('Microsoft YaHei', $size, [System.Drawing.FontStyle]::$style, [System.Drawing.GraphicsUnit]::Pixel)
}
function Fill-Round($g, $rect, $color, $radius = 6) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
    $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
    $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $b = New-Object System.Drawing.SolidBrush $color
    $g.FillPath($b, $path)
    $b.Dispose(); $path.Dispose()
}
function Stroke-Round($g, $rect, $color, $width = 1, $radius = 6) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
    $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
    $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $p = New-Object System.Drawing.Pen $color, $width
    $g.DrawPath($p, $path)
    $p.Dispose(); $path.Dispose()
}
function Text($g, [string]$s, $font, $color, [double]$x, [double]$y) {
    $b = New-Object System.Drawing.SolidBrush $color
    $g.DrawString($s, $font, $b, [float]$x, [float]$y)
    $b.Dispose()
}

$paper   = [System.Drawing.Color]::FromArgb(255, 236, 227, 206)
$paper2  = [System.Drawing.Color]::FromArgb(255, 224, 213, 189)
$ink     = [System.Drawing.Color]::FromArgb(255, 58, 44, 30)
$inkSoft = [System.Drawing.Color]::FromArgb(255, 120, 104, 82)
$accent  = [System.Drawing.Color]::FromArgb(255, 140, 96, 58)
$lock    = [System.Drawing.Color]::FromArgb(255, 168, 158, 140)
$chipOn  = [System.Drawing.Color]::FromArgb(255, 176, 132, 84)
$chipOff = [System.Drawing.Color]::FromArgb(255, 214, 204, 182)
$barBg   = [System.Drawing.Color]::FromArgb(255, 66, 48, 32)
$boxBg   = [System.Drawing.Color]::FromArgb(255, 208, 196, 170)

$fontTitle = MakeFont 26 'Bold'
$fontH     = MakeFont 20 'Bold'
$font      = MakeFont 17
$fontSmall = MakeFont 15
$fontTiny  = MakeFont 13

$bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$bgBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 32, 34, 40))
$g.FillRectangle($bgBrush, 0, 0, $W, $H)
$bgBrush.Dispose()
Text $g '草图 A — 图鉴总览（左目录 + 右正文 + 右上 3D 模型预览）' $fontH ([System.Drawing.Color]::FromArgb(255, 235, 235, 240)) 24 18

$x0 = 60; $y0 = 70
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]$x0), ([int]$y0), 880, 500) $barBg 10
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+4)), ([int]($y0+4)), 872, 52) ([System.Drawing.Color]::FromArgb(255, 86, 64, 44)) 8
Text $g '图鉴 · 誊写台' $fontH ([System.Drawing.Color]::FromArgb(255, 246, 238, 222)) ($x0+20) ($y0+16)
Text $g '已记录 3 / 8' $fontSmall ([System.Drawing.Color]::FromArgb(255, 220, 206, 182)) ($x0+520) ($y0+20)
Text $g '⚙      ✕' $font ([System.Drawing.Color]::FromArgb(255, 220, 206, 182)) ($x0+780) ($y0+16)

# ---------------- left page ----------------
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+10)), ([int]($y0+64)), 336, 420) $paper 6
Text $g '搜索' $fontSmall $inkSoft ($x0+26) ($y0+78)
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+70)), ([int]($y0+76)), 200, 26) $paper2 4
Text $g '输入名字 / 拉丁名 …' $fontTiny $inkSoft ($x0+80) ($y0+82)
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+276)), ([int]($y0+76)), 28, 26) $chipOff 4
Text $g '🔍' $fontTiny $inkSoft ($x0+283) ($y0+80)

Text $g '标签筛选（同族并集）' $fontSmall $inkSoft ($x0+26) ($y0+118)
$chipY = $y0 + 140
$chips = @(
    @('物质', $true), @('元素', $false), @('脊索', $true), @('外壳', $false),
    @('外生息', $false), @('内燃', $false), @('拟态', $true), @('领地', $false),
    @('幽暗', $true), @('辉耀', $false), @('绒', $true), @('触肢', $false)
)
$cx = $x0 + 26; $cy = $chipY
foreach ($chip in $chips) {
    $label = $chip[0]; $on = $chip[1]
    $cw = 26 + ($label.Length * 17)
    if (($cx + $cw) -gt ($x0 + 330)) { $cx = $x0 + 26; $cy += 32 }
    Fill-Round $g (New-Object System.Drawing.Rectangle ([int]$cx), ([int]$cy), ([int]$cw), 24) $(if ($on) { $chipOn } else { $chipOff }) 10
    Text $g $label $fontTiny $(if ($on) { [System.Drawing.Color]::White } else { $ink }) ($cx+10) ($cy+4)
    $cx += $cw + 8
}

Text $g '物种目录' $fontSmall $inkSoft ($x0+26) ($cy+44)
$rowY = $cy + 68
$rows = @(
    @('阿拉克涅', 'Arakne', '●', $false),
    @('猫又', 'Nekomata', '●', $true),
    @('？？？', '—', '○', $false),
    @('？？？', '—', '○', $false)
)
foreach ($r in $rows) {
    if ($r[3]) { Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+20)), ([int]($rowY-2)), 316, 46) ([System.Drawing.Color]::FromArgb(255, 216, 202, 176)) 4 }
    Text $g $r[0] $font $ink ($x0+32) $rowY
    if ($r[1] -ne '—') { Text $g $r[1] $fontTiny $inkSoft ($x0+150) ($rowY+5) }
    Text $g $r[2] $fontSmall $accent ($x0+306) ($rowY+2)
    $rowY += 52
}

# ---------------- right page ----------------
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+356)), ([int]($y0+64)), 514, 420) $paper 6

# 3D model preview box (top-right, same idea as the inventory player preview)
$boxX = $x0 + 356 + 514 - 16 - 116
$boxY = $y0 + 74
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]$boxX), ([int]$boxY), 116, 116) $boxBg 6
Stroke-Round $g (New-Object System.Drawing.Rectangle ([int]$boxX), ([int]$boxY), 116, 116) ([System.Drawing.Color]::FromArgb(140, 140, 110, 70)) 1 6
$bodyBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 92, 78, 96))
$headBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 112, 96, 116))
$g.FillEllipse($bodyBrush, [int]($boxX+44), [int]($boxY+62), 30, 38)
$g.FillEllipse($headBrush, [int]($boxX+44), [int]($boxY+34), 30, 30)
$ear = New-Object System.Drawing.Drawing2D.GraphicsPath
$ear.AddPolygon(@(
    (New-Object System.Drawing.Point ([int]($boxX+46)), ([int]($boxY+40))),
    (New-Object System.Drawing.Point ([int]($boxX+52)), ([int]($boxY+22))),
    (New-Object System.Drawing.Point ([int]($boxX+58)), ([int]($boxY+38)))
))
$g.FillPath($headBrush, $ear)
$ear2 = New-Object System.Drawing.Drawing2D.GraphicsPath
$ear2.AddPolygon(@(
    (New-Object System.Drawing.Point ([int]($boxX+72)), ([int]($boxY+38))),
    (New-Object System.Drawing.Point ([int]($boxX+78)), ([int]($boxY+20))),
    (New-Object System.Drawing.Point ([int]($boxX+84)), ([int]($boxY+40)))
))
$g.FillPath($headBrush, $ear2)
$eyeBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 226, 176, 88))
$g.FillEllipse($eyeBrush, [int]($boxX+50), [int]($boxY+46), 5, 6)
$g.FillEllipse($eyeBrush, [int]($boxX+64), [int]($boxY+46), 5, 6)
$tailPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 92, 78, 96)), 4
$g.DrawArc($tailPen, [int]($boxX+72), [int]($boxY+66), 32, 34, 200, 190)
$g.DrawArc($tailPen, [int]($boxX+74), [int]($boxY+74), 32, 30, 200, 190)
$tailPen.Dispose(); $eyeBrush.Dispose(); $ear.Dispose(); $ear2.Dispose(); $bodyBrush.Dispose(); $headBrush.Dispose()
Text $g 'idle · 随鼠标转身' $fontTiny $inkSoft ($boxX-6) ($boxY+122)

Text $g '猫又' $fontTitle $ink ($x0+376) ($y0+76)
Text $g 'Nekomata · nekomata' $fontTiny $inkSoft ($x0+376) ($y0+112)
Text $g '物质 · 脊索 · 外生息' $fontSmall $accent ($x0+376) ($y0+136)
Text $g '拟态 · 幽暗 · 绒' $fontSmall $accent ($x0+376) ($y0+158)

# section tabs (pushed down because the tag line wraps before the preview box)
$tabs = @('身份', '分类', '习性', '战斗', '敌意', '能力', '喜好')
$tx = $x0 + 376
$tabY = $y0 + 202
foreach ($tab in $tabs) {
    $tw = 24 + ($tab.Length * 17)
    $isOn = ($tab -eq '战斗')
    Fill-Round $g (New-Object System.Drawing.Rectangle ([int]$tx), ([int]$tabY), ([int]$tw), 28) $(if ($isOn) { $paper2 } else { [System.Drawing.Color]::FromArgb(255, 214, 204, 182) }) 6
    Text $g $tab $fontTiny $(if ($isOn) { $ink } else { $inkSoft }) ($tx+12) ($tabY+6)
    $tx += $tw + 8
}

$lineY = $tabY + 44
$lines = @(
    @('最大生命', '20'),
    @('速度档', '快速'),
    @('护甲 / 韧性', '0 / 0'),
    @('近战伤害', '3'),
    @('远程', '无')
)
foreach ($l in $lines) {
    Text $g $l[0] $fontSmall $inkSoft ($x0+380) $lineY
    Text $g $l[1] $font $ink ($x0+700) $lineY
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(90, 160, 146, 120)), 1
    $g.DrawLine($pen, [float]($x0+376), [float]($lineY+22), [float]($x0+740), [float]($lineY+22))
    $pen.Dispose()
    $lineY += 34
}
Text $g '🔒 敌意 / 能力 / 喜好 —— 需缩放观察与投喂后解锁' $fontTiny $lock ($x0+380) ($lineY+4)

# footer
Fill-Round $g (New-Object System.Drawing.Rectangle ([int]($x0+10)), ([int]($y0+452)), 860, 34) ([System.Drawing.Color]::FromArgb(255, 86, 64, 44)) 6
Text $g '左键选择 · 滚轮滚动 · 拖拽预览旋转 · ESC 关闭        筛选：物质 + 脊索 + 拟态 + 幽暗 + 绒' $fontTiny ([System.Drawing.Color]::FromArgb(255, 226, 214, 192)) ($x0+24) ($y0+460)

$g.Dispose()
$bmp.Save((Join-Path $outDir 'codex-overview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

$fontTitle.Dispose(); $fontH.Dispose(); $font.Dispose(); $fontSmall.Dispose(); $fontTiny.Dispose()
Write-Host 'mockup written to docs-mockups/codex-overview.png'
