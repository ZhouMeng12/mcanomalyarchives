<#
.SYNOPSIS
    MCreator 生成区守卫 —— 检查 / 一键还原被 MCreator 重新生成覆盖的粉羊相关文件。

.DESCRIPTION
    粉羊有部分代码不得不放在 MCreator 的生成文件里（实体类、渲染器、实体注册表），
    MCreator 重新生成代码会把它们还原成默认模板（羊→怪物、羊模型→默认渲染），此前已发生 3 次以上。
    本脚本 + manifest.json + canonical/ 快照解决这个问题：

      check     检查每个受保护项是否完好（有漂移 → 退出码 1，可用于构建前拦截）
      apply     把漂移项还原成 canonical 快照（只动清单里列出的文件/代码块）
      snapshot  把当前工作副本提升为新的"已知良好版本"（改完机制、编译通过后运行）
      status    人类可读的清单总览

    机制代码本身位于 src/main/java/net/mcreator/mcanomalyarchives/anomaly/ 与 events/，
    不属于生成区，不需要本脚本保护。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action check
    powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action apply

.NOTES
    只做文件系统操作，不依赖 MCreator / Gradle。PowerShell 5.1 兼容（本文件必须带 UTF-8 BOM）。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('check', 'apply', 'snapshot', 'status')]
    [string]$Action,
    [string]$Root   # 工作区根目录，默认脚本上两级
)

$ErrorActionPreference = 'Stop'

$guardDir = $PSScriptRoot
if (-not $Root) { $Root = (Resolve-Path (Join-Path $guardDir '..\..')).Path }
$Root = (Resolve-Path $Root).Path

$manifestPath = Join-Path $guardDir 'manifest.json'
$canonicalDir = Join-Path $guardDir 'canonical'
if (-not (Test-Path $manifestPath)) { throw "找不到清单文件: $manifestPath" }
$manifest = [System.IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json

# ---------- 基础工具（UTF-8 无 BOM；按原文件行尾风格读写，绝不改变字节） ----------

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Read-Text([string]$path) { return [System.IO.File]::ReadAllText($path) }

function Write-Text([string]$path, [string]$text) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $text, $Utf8NoBom)
}

function Resolve-WorkPath([string]$relative) { return (Join-Path $Root ($relative -replace '/', '\')) }

# 返回 @{ Sep; Lines }：Sep 是文件行尾（CRLF/LF），Lines 是切分后的数组
function Get-TextInfo([string]$text) {
    $sep = "`n"
    if ($text.Contains("`r`n")) { $sep = "`r`n" }
    $lines = @($text.Split([string[]]@($sep), [System.StringSplitOptions]::None))
    return [pscustomobject]@{ Sep = $sep; Lines = $lines }
}

# 取锚点区间内的代码块；返回 @{ Start; End; Text; Sep; Lines } 或 $null
function Get-Block([string]$fileText, [string]$startAnchor, [string]$endAnchor) {
    $info = Get-TextInfo $fileText
    $start = -1
    for ($i = 0; $i -lt $info.Lines.Count; $i++) { if ($info.Lines[$i].Contains($startAnchor)) { $start = $i; break } }
    if ($start -lt 0) { return $null }
    $end = -1
    for ($i = $start; $i -lt $info.Lines.Count; $i++) { if ($info.Lines[$i].Contains($endAnchor)) { $end = $i; break } }
    if ($end -lt 0) { return $null }
    return [pscustomobject]@{
        Start = $start; End = $end; Sep = $info.Sep; Lines = $info.Lines
        Text = ($info.Lines[$start..$end] -join $info.Sep)
    }
}

# ---------- 检查 ----------

function Test-Item($item) {
    $r = [ordered]@{ Id = $item.id; Status = 'ok'; Detail = ''; Item = $item }
    $path = Resolve-WorkPath $item.path
    if ($item.kind -ne 'exists' -and -not (Test-Path $path)) {
        $r.Status = 'missing'; $r.Detail = '文件不存在'
        return [pscustomobject]$r
    }
    switch ($item.kind) {
        'full' {
            $text = Read-Text $path
            $missing = @($item.expectAll | Where-Object { -not $text.Contains($_) })
            if ($missing.Count) { $r.Status = 'drift'; $r.Detail = '缺少标记: ' + ($missing -join ' | ') }
        }
        'block' {
            $block = Get-Block (Read-Text $path) $item.startAnchor $item.endAnchor
            if ($null -eq $block) { $r.Status = 'drift'; $r.Detail = '找不到锚点代码块' }
            else {
                $missing = @($item.expectAll | Where-Object { -not $block.Text.Contains($_) })
                if ($missing.Count) { $r.Status = 'drift'; $r.Detail = '代码块缺少: ' + ($missing -join ' | ') }
            }
        }
        'contains' {
            $text = Read-Text $path
            $missing = @($item.mustContain | Where-Object { -not $text.Contains($_) })
            if ($missing.Count) { $r.Status = 'drift'; $r.Detail = '缺少: ' + ($missing -join ' | ') }
        }
        'exists' {
            $missing = @($item.exists | Where-Object { -not (Test-Path (Resolve-WorkPath $_)) })
            if ($missing.Count) { $r.Status = 'missing'; $r.Detail = '缺少文件: ' + ($missing -join ' | ') }
        }
        'noMatch' {
            $searchRoot = Resolve-WorkPath $item.searchRoot
            if (-not (Test-Path $searchRoot)) { $r.Status = 'missing'; $r.Detail = '搜索目录不存在' }
            else {
                $hits = @()
                foreach ($file in Get-ChildItem -Path $searchRoot -Recurse -Filter $item.glob -File) {
                    if ((Read-Text $file.FullName) -match $item.pattern) { $hits += $file.FullName.Substring($Root.Length + 1) }
                }
                if ($hits.Count) {
                    $r.Status = 'drift'
                    $r.Detail = '残留 ' + $hits.Count + ' 个文件: ' + (($hits | Select-Object -First 3) -join ', ')
                }
            }
        }
        default { $r.Status = 'drift'; $r.Detail = "未知 kind: $($item.kind)" }
    }
    return [pscustomobject]$r
}

# ---------- 还原 ----------

function Repair-Item($item) {
    if ($item.kind -ne 'full' -and $item.kind -ne 'block') { return $false } # contains/exists/noMatch 交给 fixHint
    $src = Join-Path $canonicalDir $item.canonical
    if (-not (Test-Path $src)) { throw "缺少快照: $src（先运行 -Action snapshot）" }
    $dst = Resolve-WorkPath $item.path
    if ($item.kind -eq 'full') {
        $dir = Split-Path $dst -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        Copy-Item -Path $src -Destination $dst -Force      # 字节级复制，编码/行尾原样
        return $true
    }
    $block = Get-Block (Read-Text $dst) $item.startAnchor $item.endAnchor
    if ($null -eq $block) { throw "找不到锚点代码块，需手工修复: $($item.path)" }
    $canonicalText = (Read-Text $src).TrimEnd("`r", "`n")
    $newLines = @()
    if ($block.Start -gt 0) { $newLines += @($block.Lines[0..($block.Start - 1)]) }
    $newLines += @($canonicalText -split "`r?`n")
    if ($block.End -lt $block.Lines.Count - 1) { $newLines += @($block.Lines[($block.End + 1)..($block.Lines.Count - 1)]) }
    Write-Text $dst ($newLines -join $block.Sep)
    return $true
}

# ---------- 输出 ----------

function Invoke-Check { return @(foreach ($item in $manifest.items) { Test-Item $item }) }

function Show-Results($results) {
    $width = ($results | ForEach-Object { $_.Id.Length } | Measure-Object -Maximum).Maximum
    foreach ($r in $results) {
        $tag = switch ($r.Status) { 'ok' { 'OK    ' } 'drift' { 'DRIFT ' } 'missing' { 'MISS  ' } default { '?     ' } }
        $color = switch ($r.Status) { 'ok' { 'DarkGray' } 'drift' { 'Yellow' } default { 'Red' } }
        Write-Host ("  [{0}] {1} {2}" -f $tag, $r.Id.PadRight($width), $r.Detail) -ForegroundColor $color
    }
}

$results = Invoke-Check

switch ($Action) {
    'check' {
        Write-Host ""
        Write-Host "MCreator 生成区守卫 · 检查 (root: $Root)" -ForegroundColor Cyan
        Show-Results $results
        $bad = @($results | Where-Object { $_.Status -ne 'ok' })
        Write-Host ""
        if ($bad.Count -eq 0) { Write-Host "全部 $($results.Count) 项完好。" -ForegroundColor Green; exit 0 }
        Write-Host "$($bad.Count)/$($results.Count) 项需要处理：运行 -Action apply 自动还原（或按 fixHint 手工修复）。" -ForegroundColor Yellow
        exit 1
    }
    'apply' {
        Write-Host ""
        Write-Host "MCreator 生成区守卫 · 还原 (root: $Root)" -ForegroundColor Cyan
        $bad = @($results | Where-Object { $_.Status -ne 'ok' })
        if ($bad.Count -eq 0) { Write-Host "无需还原，全部 $($results.Count) 项完好。" -ForegroundColor Green; exit 0 }
        foreach ($r in $bad) {
            try {
                if (Repair-Item $r.Item) { Write-Host ("  [FIXED]  {0}" -f $r.Id) -ForegroundColor Green }
                else {
                    $hint = if ($r.Item.fixHint) { $r.Item.fixHint } else { '需手工修复' }
                    Write-Host ("  [MANUAL] {0} -> {1}" -f $r.Id, $hint) -ForegroundColor Yellow
                }
            }
            catch { Write-Host ("  [FAIL]   {0}: {1}" -f $r.Id, $_.Exception.Message) -ForegroundColor Red }
        }
        Write-Host ""
        $after = Invoke-Check
        Show-Results $after
        $left = @($after | Where-Object { $_.Status -ne 'ok' })
        if ($left.Count -eq 0) {
            Write-Host "还原完成，全部 $($after.Count) 项完好。建议接着跑：.\gradlew.bat build" -ForegroundColor Green
            exit 0
        }
        Write-Host "仍有 $($left.Count) 项未还原（见上方 MANUAL 提示）。" -ForegroundColor Yellow
        exit 1
    }
    'snapshot' {
        Write-Host ""
        Write-Host "MCreator 生成区守卫 · 快照 (root: $Root)" -ForegroundColor Cyan
        if (-not (Test-Path $canonicalDir)) { New-Item -ItemType Directory -Path $canonicalDir -Force | Out-Null }
        # 只允许用『检查通过』的状态刷新基线。
        # 否则会出现最坑的情况：MCreator 刚把注册表改回 MONSTER，snapshot 把这份坏状态存成基线，
        # 之后 apply 反而"忠实地"还原成坏的（本脚本 2026-09-12 真的踩过一次）。
        $statusById = @{}
        foreach ($r in $results) { $statusById[$r.Id] = $r.Status }
        foreach ($item in $manifest.items) {
            if ($item.kind -ne 'full' -and $item.kind -ne 'block') {
                Write-Host ("  [----] {0} ({1})：不参与快照" -f $item.id, $item.kind) -ForegroundColor DarkGray
                continue
            }
            $src = Resolve-WorkPath $item.path
            if (-not (Test-Path $src)) { Write-Host ("  [SKIP] {0}: 文件不存在" -f $item.id) -ForegroundColor Red; continue }
            $target = Join-Path $canonicalDir $item.canonical
            $itemStatus = $statusById[$item.id]
            if ($itemStatus -ne 'ok' -and (Test-Path $target)) {
                Write-Host ("  [拒绝] {0}: 当前状态检查不通过，不用它覆盖已有基线（先 -Action apply 还原）" -f $item.id) -ForegroundColor Red
                continue
            }
            if ($itemStatus -ne 'ok') {
                Write-Host ("  [警告] {0}: 首次建立基线，但当前状态检查不通过，请确认这是你要的状态" -f $item.id) -ForegroundColor Yellow
            }
            if ($item.kind -eq 'full') {
                Copy-Item -Path $src -Destination $target -Force
            }
            else {
                $block = Get-Block (Read-Text $src) $item.startAnchor $item.endAnchor
                if ($null -eq $block) { Write-Host ("  [SKIP] {0}: 找不到锚点" -f $item.id) -ForegroundColor Red; continue }
                Write-Text $target $block.Text
            }
            Write-Host ("  [SNAP] {0} -> canonical/{1}" -f $item.id, $item.canonical) -ForegroundColor Green
        }
        Write-Host ""
        $after = Invoke-Check
        Show-Results $after
        $bad = @($after | Where-Object { $_.Status -ne 'ok' })
        if ($bad.Count) {
            Write-Host "注意：快照后仍有 $($bad.Count) 项不正常，请先修好再重新快照（否则会把坏状态固化成基线）。" -ForegroundColor Yellow
            exit 1
        }
        Write-Host "快照完成，当前状态已作为基线。" -ForegroundColor Green
        exit 0
    }
    'status' {
        Write-Host ""
        Write-Host "MCreator 生成区守卫 · 清单总览" -ForegroundColor Cyan
        foreach ($item in $manifest.items) {
            Write-Host ("  {0,-26} [{1,-8}] {2}" -f $item.id, $item.kind, $item.why)
        }
        Write-Host ""
        Write-Host "用法：-Action check | apply | snapshot | status" -ForegroundColor DarkGray
        exit 0
    }
}
