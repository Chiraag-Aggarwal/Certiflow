Add-Type -AssemblyName System.Drawing
$pngPath = "c:\Users\prane\OneDrive\Documents\Projects\Java project\certiflow-app\src\main\resources\logo.png"
$icoPath = "c:\Users\prane\OneDrive\Documents\Projects\Java project\certiflow-app\src\main\resources\logo.ico"
$tempPngPath = "c:\Users\prane\OneDrive\Documents\Projects\Java project\certiflow-app\src\main\resources\logo_temp.png"

$img = [System.Drawing.Image]::FromFile($pngPath)
$bitmap = New-Object System.Drawing.Bitmap($img)
$img.Dispose()

# Remove near-white pixels to fix the anti-aliasing halo
for ($x = 0; $x -lt $bitmap.Width; $x++) {
    for ($y = 0; $y -lt $bitmap.Height; $y++) {
        $color = $bitmap.GetPixel($x, $y)
        # If pixel is close to white (anti-aliasing halo), make it transparent
        if ($color.R -gt 220 -and $color.G -gt 220 -and $color.B -gt 220) {
            $bitmap.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
        }
    }
}

$bitmap.Save($tempPngPath, [System.Drawing.Imaging.ImageFormat]::Png)

# Create a 256x256 scaled version for the ICO
$ratio = $bitmap.Width / $bitmap.Height
if ($ratio -gt 1) {
    $newWidth = 256
    $newHeight = [int][Math]::Round(256 / $ratio)
    $xOffset = 0
    $yOffset = [int][Math]::Round((256 - $newHeight) / 2)
} elseif ($ratio -lt 1) {
    $newHeight = 256
    $newWidth = [int][Math]::Round(256 * $ratio)
    $xOffset = [int][Math]::Round((256 - $newWidth) / 2)
    $yOffset = 0
} else {
    $newWidth = 256
    $newHeight = 256
    $xOffset = 0
    $yOffset = 0
}

$ms = New-Object System.IO.MemoryStream
$scaledBitmap = New-Object System.Drawing.Bitmap([int]256, [int]256)
$g = [System.Drawing.Graphics]::FromImage($scaledBitmap)
$g.Clear([System.Drawing.Color]::Transparent)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($bitmap, $xOffset, $yOffset, $newWidth, $newHeight)
$g.Dispose()

$scaledBitmap.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
$pngBytes = $ms.ToArray()
$ms.Close()
$scaledBitmap.Dispose()
$bitmap.Dispose()

Remove-Item -Path $pngPath -Force -ErrorAction SilentlyContinue
Rename-Item -Path $tempPngPath -NewName "logo.png"

$size = $pngBytes.Length
$sizeBytes = [System.BitConverter]::GetBytes([int]$size)
$offsetBytes = [System.BitConverter]::GetBytes([int]22)
$header = [byte[]]@(0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 32, 0)
$header[6] = 0
$header[7] = 0

$fs = [System.IO.File]::Create($icoPath)
$fs.Write($header, 0, 14)
$fs.Write($sizeBytes, 0, 4)
$fs.Write($offsetBytes, 0, 4)
$fs.Write($pngBytes, 0, $size)
$fs.Close()

$WshShell = New-Object -comObject WScript.Shell
$DesktopPath = [Environment]::GetFolderPath("Desktop")
$Shortcut = $WshShell.CreateShortcut("$DesktopPath\Certiflow App.lnk")
$Shortcut.IconLocation = "$icoPath"
$Shortcut.Save()

ie4uinit.exe -show
