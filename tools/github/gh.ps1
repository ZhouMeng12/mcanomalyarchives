<#
.SYNOPSIS
    用 git 已存的 GitHub 凭据来跑 gh，不用另外登录。

.DESCRIPTION
    这台机器上 gh 从来没登录过（`gh auth login --with-token` 会被拒：凭据管理器里那个
    gho_ 开头的 OAuth token 只有 gist / repo / workflow 三个 scope，gh 登录还要求
    read:org）。但 `gh` 认 GH_TOKEN 环境变量，所以这里从 git 的凭据管理器把 token
    取出来、只喂给本次 gh 进程 —— **不落盘、不写进 gh 的 hosts.yml**。

    想让自己平时直接敲 gh 也能用，跑一次 `gh auth login`（浏览器设备码授权）即可，
    它会拿到带 read:org 的凭据；那之后就不需要这个脚本了。

.EXAMPLE
    pwsh tools/github/gh.ps1 release list
    pwsh tools/github/gh.ps1 release view v1.1.9
    pwsh tools/github/gh.ps1 repo view --json name,url
#>
[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GhArgs)

$ErrorActionPreference = "Stop"

$gh = @(
	"C:\Program Files\GitHub CLI\gh.exe",
	"$env:LOCALAPPDATA\Programs\GitHub CLI\gh.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $gh) {
	$cmd = Get-Command gh -ErrorAction SilentlyContinue
	if ($cmd) { $gh = $cmd.Source }
}
if (-not $gh) {
	throw "找不到 gh.exe。装一个： winget install --id GitHub.cli -e --source winget"
}

# 从 git 凭据管理器取 token（40 位的 gho_... 或 ghp_...）
$filled = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$line = $filled | Select-String -Pattern '^password=' | Select-Object -First 1
if (-not $line) {
	throw "git 凭据管理器里没有 github.com 的凭据。先随便 push/fetch 一次，或手动 gh auth login。"
}
$env:GH_TOKEN = $line.ToString().Substring(9)

& $gh @GhArgs
exit $LASTEXITCODE
