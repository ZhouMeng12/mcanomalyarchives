param([string]$ClientJar = 'C:\Users\Administrator\.gradle\caches\neoformruntime\artifacts\minecraft_1.21.1_client.jar')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$assets = Join-Path $root 'src\main\resources\assets\mcanomalyarchives'
$utf8 = New-Object Text.UTF8Encoding($false)
$zip = [IO.Compression.ZipFile]::OpenRead($ClientJar)
try {
    foreach ($texture in @('item/bow', 'item/bow_pulling_0', 'item/bow_pulling_1', 'item/bow_pulling_2', 'item/arrow', 'item/music_disc_cat', 'entity/projectiles/arrow')) {
        $entry = $zip.GetEntry("assets/minecraft/textures/$texture.png")
        if ($null -eq $entry) { throw "Missing vanilla texture $texture" }
        $stream = $entry.Open()
        $bitmap = New-Object Drawing.Bitmap($stream)
        try {
            for ($y = 0; $y -lt $bitmap.Height; $y++) {
                for ($x = 0; $x -lt $bitmap.Width; $x++) {
                    $pixel = $bitmap.GetPixel($x, $y)
                    $bitmap.SetPixel($x, $y, [Drawing.Color]::FromArgb($pixel.A, 255-$pixel.R, 255-$pixel.G, 255-$pixel.B))
                }
            }
            $negative = $texture -replace '([^/]+)$', 'negative_$1'
            $negative = $negative -replace 'negative_music_disc_cat$', 'negative_music_disc'
            $out = Join-Path $assets "textures/$negative.png"
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($out)) | Out-Null
            $bitmap.Save($out, [Drawing.Imaging.ImageFormat]::Png)
        } finally { $bitmap.Dispose(); $stream.Dispose() }
    }
    foreach ($model in @('bow', 'bow_pulling_0', 'bow_pulling_1', 'bow_pulling_2', 'arrow')) {
        $entry = $zip.GetEntry("assets/minecraft/models/item/$model.json")
        $reader = New-Object IO.StreamReader($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $text = $text.Replace('item/bow', 'mcanomalyarchives:item/negative_bow').Replace('item/arrow', 'mcanomalyarchives:item/negative_arrow')
        [IO.File]::WriteAllText((Join-Path $assets "models/item/negative_$model.json"), $text, $utf8)
    }
} finally { $zip.Dispose() }

# Nearest-neighbour preview only; the game always uses the original 16x16 texture.
$disc = New-Object Drawing.Bitmap((Join-Path $assets 'textures/item/negative_music_disc.png'))
$preview = New-Object Drawing.Bitmap(256, 256)
$graphics = [Drawing.Graphics]::FromImage($preview)
try {
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.DrawImage($disc, 0, 0, 256, 256)
    $preview.Save((Join-Path $PSScriptRoot 'source/negative-music-disc-cat-preview.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $preview.Dispose(); $disc.Dispose() }
