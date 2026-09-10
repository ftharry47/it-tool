# Regenerate favicon assets from a source logo PNG.
# Pipeline: trim to bounding box of non-transparent pixels -> pad to square
# with 1.5% padding -> resize to target sizes with high-quality bicubic.
param(
    [string]$Source = "c:\Users\SriHariThangavel\Documents\Dev-IT\v4\src\main\frontend\public\logo-dark.png",
    [string]$OutDir = "c:\Users\SriHariThangavel\Documents\Dev-IT\v4\src\main\frontend\public"
)

Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::FromFile($Source)

# --- 1. Find bounding box of non-transparent pixels ---
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
Write-Output "Bounding box: ${bw}x${bh} at ($minX,$minY)"

# --- 2. Crop to bounding box ---
$crop = New-Object System.Drawing.Bitmap($bw, $bh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($crop)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $bw, $bh)), (New-Object System.Drawing.Rectangle($minX, $minY, $bw, $bh)), [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()

# --- 3. Pad to square with 1.5% padding ---
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
Write-Output "Square canvas: ${sq}x${sq} (pad=$pad)"

# --- 4. Resize to target sizes ---
function Save-Resized($bmp, [int]$size, [string]$path) {
    $out = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($bmp, 0, 0, $size, $size)
    $g.Dispose()
    $out.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Output "Wrote $path (${size}x${size})"
}

Save-Resized $square 16  (Join-Path $OutDir 'favicon-16.png')
Save-Resized $square 32  (Join-Path $OutDir 'favicon-32.png')
Save-Resized $square 48  (Join-Path $OutDir 'favicon.png')
Save-Resized $square 180 (Join-Path $OutDir 'apple-touch-icon.png')

$square.Dispose(); $crop.Dispose(); $src.Dispose()
Write-Output "Done."
