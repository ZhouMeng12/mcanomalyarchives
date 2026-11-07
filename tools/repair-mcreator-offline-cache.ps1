param(
    [string]$SourceHome = (Join-Path $env:USERPROFILE '.gradle'),
    [string]$DestinationHome = (Join-Path $env:USERPROFILE '.mcreator\gradle')
)
$ErrorActionPreference = 'Stop'

$sourceCaches = Join-Path ([IO.Path]::GetFullPath($SourceHome)) 'caches'
$destinationCaches = Join-Path ([IO.Path]::GetFullPath($DestinationHome)) 'caches'
if ($sourceCaches -eq $destinationCaches) { throw 'Source and destination Gradle homes must differ.' }
if (-not (Test-Path -LiteralPath (Join-Path $sourceCaches 'modules-2'))) { throw 'The source dependency cache is missing.' }
New-Item -ItemType Directory -Path $destinationCaches -Force | Out-Null

# Keep the previous repository metadata before merging the compatible dependency cache.
# Artifact files and existing unrelated modules are retained; lock files are never copied.
$backup = Join-Path $DestinationHome ('cache-metadata-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
foreach ($metadata in Get-ChildItem -LiteralPath (Join-Path $destinationCaches 'modules-2') -Directory -Filter 'metadata-*' -ErrorAction SilentlyContinue) {
    New-Item -ItemType Directory -Path $backup -Force | Out-Null
    Copy-Item -LiteralPath $metadata.FullName -Destination $backup -Recurse
}

foreach ($name in @('modules-2', 'neoformruntime')) {
    $source = Join-Path $sourceCaches $name
    if (-not (Test-Path -LiteralPath $source)) { continue }
    $destination = Join-Path $destinationCaches $name
    & robocopy $source $destination /E /XF '*.lock' 'gc.properties' /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -ge 8) { throw "Cache copy failed for $name (robocopy exit $LASTEXITCODE)." }
    Write-Host "Ready: $destination"
}
Write-Host 'MCreator offline dependency cache has been seeded. Close active builds before running this helper again.'
