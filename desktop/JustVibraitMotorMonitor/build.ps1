param([switch]$SkipTests)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root 'src'
$outDir = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$csc = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) {
    $csc = 'C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
if (-not (Test-Path $csc)) {
    throw 'csc.exe (.NET Framework compiler) not found.'
}

$out = Join-Path $outDir 'JustVibraitMotorMonitor.exe'
$files = @(
    Get-ChildItem -Path (Join-Path $src '*.cs') |
        Sort-Object Name |
        ForEach-Object { $_.FullName }
)

$arguments = @(
    '/nologo',
    '/target:winexe',
    '/platform:anycpu',
    '/optimize+',
    '/codepage:65001',
    '/utf8output',
    ('/out:' + $out),
    ('/win32manifest:' + (Join-Path $src 'app.manifest')),
    '/reference:System.dll',
    '/reference:System.Core.dll',
    '/reference:System.Drawing.dll',
    '/reference:System.Windows.Forms.dll'
) + $files

& $csc @arguments
if ($LASTEXITCODE -ne 0) {
    throw "C# compiler failed with exit code $LASTEXITCODE"
}

$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host ''
Write-Host ('Built: ' + $out)
Write-Host ('Size:  ' + (Get-Item $out).Length + ' bytes')
Write-Host ('SHA-256: ' + $hash)

if (-not $SkipTests) {
    & (Join-Path $root 'selftest.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'Self-tests failed.'
    }
}
