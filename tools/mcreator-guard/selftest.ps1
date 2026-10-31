# Test the real guard in an isolated fixture, without changing the workspace manifest.
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixture = Join-Path $workspace ('build\guard-selftest\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$script = Join-Path $fixture 'guard.ps1'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'guard.ps1') -Destination $script
$runner = (Get-Process -Id $PID).Path
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $fixture 'present.txt'), 'present', $utf8)
$cases = @(
    @{ Name = 'present file'; Field = 'exists'; Files = @('present.txt'); Expected = 0 },
    @{ Name = 'missing file'; Field = 'exists'; Files = @('missing.txt'); Expected = 1 },
    @{ Name = 'misspelled field'; Field = 'paths'; Files = @('missing.txt'); Expected = 1 },
    @{ Name = 'empty file list'; Field = 'exists'; Files = @(); Expected = 1 }
)
foreach ($case in $cases) {
    $item = @{ id = 'fixture'; kind = 'exists'; why = 'selftest' }
    $item[$case.Field] = $case.Files
    $manifest = @{ version = 1; items = @($item) } | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText((Join-Path $fixture 'manifest.json'), $manifest, $utf8)
    $output = & $runner -NoProfile -ExecutionPolicy Bypass -File $script -Root $fixture -Action check 2>&1
    if ($LASTEXITCODE -ne $case.Expected) {
        throw ('Guard selftest failed: ' + $case.Name + '; exit=' + $LASTEXITCODE + '; output=' + ($output -join "`n"))
    }
}
Write-Host ('Guard selftest: ' + $cases.Count + ' cases passed.')
