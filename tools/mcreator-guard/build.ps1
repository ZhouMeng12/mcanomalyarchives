<#
.SYNOPSIS
    安全构建：先跑生成区守卫检查，通过后再调用 gradle 构建。

.DESCRIPTION
    加这一层的原因：MCreator 重新生成代码后，粉羊的薄壳/渲染器/注册表可能已被还原，
    此时直接 build 出来的包是坏的（羊变怪物、粉羊消失）。本脚本让漂移在构建前就暴露。

      1. guard.ps1 -Action check
      2. 有漂移 → 直接失败并提示运行 -Action apply（不会构建出坏包）
      3. 无漂移 → .\gradlew.bat build

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/build.ps1
    powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/build.ps1 -AutoFix   # 自动先还原再构建
#>
[CmdletBinding()]
param(
    [switch]$AutoFix
)

$ErrorActionPreference = 'Stop'
$guard = Join-Path $PSScriptRoot 'guard.ps1'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

& $guard -Action check -Root $root
if ($LASTEXITCODE -ne 0) {
    if (-not $AutoFix) {
        Write-Host "构建已阻止：生成区有漂移。运行下面这条命令还原后重试（或加 -AutoFix）：" -ForegroundColor Red
        Write-Host "  powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action apply" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "检测到漂移，先自动还原..." -ForegroundColor Yellow
    & $guard -Action apply -Root $root
    if ($LASTEXITCODE -ne 0) { Write-Host "还原未完成，构建中止。" -ForegroundColor Red; exit 1 }
}

Write-Host ""
Write-Host "开始构建..." -ForegroundColor Cyan
Push-Location $root
try {
    # 捕获输出自己判断成败：gradlew 在只有 javac 警告时会以 1 退出（stderr 有警告），
    # 直接看退出码会把"构建成功但有警告"误判成失败。
    $log = & (Join-Path $root 'gradlew.bat') build --console=plain 2>&1
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}

$log | Where-Object { $_ -match 'error:|错误:|FAILED|BUILD |Task :compileJava' } | ForEach-Object { Write-Host "  $_" }

if ($log -match 'BUILD SUCCESSFUL') {
    Write-Host ""
    Write-Host "构建成功：build\libs\" -ForegroundColor Green
    Get-ChildItem (Join-Path $root 'build\libs') -Filter *.jar | ForEach-Object {
        Write-Host ("  {0}  ({1:N2} MB, {2})" -f $_.Name, ($_.Length / 1MB), $_.LastWriteTime)
    }
    exit 0
}

Write-Host ""
Write-Host "构建失败（gradle 退出码 $code）：没有出现 BUILD SUCCESSFUL。" -ForegroundColor Red
exit 1
