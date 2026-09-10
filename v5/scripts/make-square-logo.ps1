# Produce a trimmed, square-padded variant of a transparent logo PNG.
# Same pipeline as make-favicons.ps1: bbox trim -> pad to square + 1.5% -> resize.
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Dest,
    [int]$Size = 256
)

Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::FromFile($Source)

$minX = $src.Width; $minY = $src.Height; $maxX = -1; $maxY = -1
for ($y = 0; $y -lt $src.Height; $y++) {
    for ($x = 0; $x -lt $src.Width; $x++) {
        if ($src.GetPixel($x, $y).A -gt 8) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
if ($maxX -lt 0) { throw "No non-transparent pixels found in $Source" }
$bw = $maxX - $minX + 1
$bh = $maxY - $minY + 1

$crop = New-Object System.Drawing.Bitmap($bw, $bh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($crop)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $bw, $bh)), (New-Object System.Drawing.Rectangle($minX, $minY, $bw, $bh)), [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()

$side = [Math]::Max($bw, $bh)
$pad = [Math]::Ceiling($side * 0.015)
$sq = $side + 2 * $pad
$square = New-Object System.Drawing.Bitmap($sq, $sq, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($square)
$g.Clear([System.Drawing.Color]::Transparent)
$ox = $pad + [int](($side - $bw) / 2)
$oy = $pad + [int](($side - $bh) / 2)
$g.DrawImage($crop, $ox, $oy, $bw, $bh)
$g.Dispose()

$out = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($out)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
$g.DrawImage($square, 0, 0, $Size, $Size)
$g.Dispose()
$out.Save($Dest, [System.Drawing.Imaging.ImageFormat]::Png)

$out.Dispose(); $square.Dispose(); $crop.Dispose(); $src.Dispose()
Write-Output "Wrote $Dest (${Size}x${Size}) from bbox ${bw}x${bh}"
