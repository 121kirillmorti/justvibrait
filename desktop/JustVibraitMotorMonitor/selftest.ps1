$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tests = Join-Path $root 'tests'
$bin = Join-Path $tests 'bin'
New-Item -ItemType Directory -Force -Path $bin | Out-Null

$csc = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) {
    $csc = 'C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe'
}

$exe = Join-Path $bin 'SelfTest.exe'
$files = @(
    (Join-Path $root 'src\TelemetryPacket.cs'),
    (Join-Path $root 'src\UdpTelemetryListener.cs'),
    (Join-Path $tests 'SelfTest.cs')
)

$arguments = @(
    '/nologo',
    '/target:exe',
    '/platform:anycpu',
    '/optimize+',
    '/codepage:65001',
    '/utf8output',
    ('/out:' + $exe),
    '/reference:System.dll',
    '/reference:System.Core.dll'
) + $files

& $csc @arguments
if ($LASTEXITCODE -ne 0) {
    throw "C# test compiler failed with exit code $LASTEXITCODE"
}

& $exe
exit $LASTEXITCODE
