[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$sourceIcon = Join-Path $projectDirectory 'src\main\resources\app-icon.png'
$windowsIcon = Join-Path $projectDirectory 'src\main\resources\app-icon.ico'
$javaExecutable = Join-Path $projectDirectory '.tools\jdk-17\bin\javaw.exe'
$compiledApp = Join-Path $projectDirectory 'target\classes\com\certiflow\App.class'
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Certiflow.lnk'
foreach ($requiredPath in @($sourceIcon, $javaExecutable, $compiledApp)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "Missing file: $requiredPath" }
}
# Package the chosen artwork into Windows icon sizes without changing the source.
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile($sourceIcon)
$frames = [System.Collections.Generic.List[byte[]]]::new()
$sizes = @(16, 24, 32, 48, 64, 128, 256)
try {
    foreach ($size in $sizes) {
        $bitmap = [System.Drawing.Bitmap]::new($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $buffer = [System.IO.MemoryStream]::new()
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $scale = [Math]::Min($size / $source.Width, $size / $source.Height)
            $width = [int][Math]::Round($source.Width * $scale)
            $height = [int][Math]::Round($source.Height * $scale)
            $graphics.DrawImage($source, [int](($size - $width) / 2), [int](($size - $height) / 2), $width, $height)
            $bitmap.Save($buffer, [System.Drawing.Imaging.ImageFormat]::Png)
            $frames.Add($buffer.ToArray())
        } finally { $buffer.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $source.Dispose() }
$output = [System.IO.File]::Create($windowsIcon)
$writer = [System.IO.BinaryWriter]::new($output)
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    for ($index = 0; $index -lt $sizes.Count; $index++) {
        $dimension = if ($sizes[$index] -eq 256) { 0 } else { $sizes[$index] }
        $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
        $writer.Write([byte]0); $writer.Write([byte]0)
        $writer.Write([uint16]1); $writer.Write([uint16]32)
        $writer.Write([uint32]$frames[$index].Length); $writer.Write([uint32]$offset)
        $offset += $frames[$index].Length
    }
    foreach ($frame in $frames) { $writer.Write([byte[]]$frame) }
} finally { $writer.Dispose() }
$shell = New-Object -ComObject WScript.Shell
try {
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $javaExecutable
    $shortcut.Arguments = '-Dfile.encoding=UTF-8 -Dcertiflow.data.dir=data --module-path "lib\javafx" --add-modules javafx.controls,javafx.fxml --class-path "target\classes;lib\ikonli\*;lib\cloud\*" com.certiflow.App'
    $shortcut.WorkingDirectory = $projectDirectory
    $shortcut.IconLocation = $windowsIcon + ',0'
    $shortcut.Description = 'Open Certiflow'
    $shortcut.WindowStyle = 1
    $shortcut.Save()
} finally { [System.Runtime.InteropServices.Marshal]::ReleaseComObject($shell) | Out-Null }
Write-Output "Desktop shortcut ready: $shortcutPath"
