# One NieR run on the PC, for debugging the boot: games\nier\tools\run_pc.ps1 -Name boot1 -Seconds 60 [-Headless]
# Writes private\nier\runs\<Name>\nx.log (and frames\*.ppm with -DumpEvery). The process is killed after -Seconds.
param(
    [Parameter(Mandatory)][string]$Name,
    [int]$Seconds = 60,
    [switch]$Headless,
    [switch]$Docked,                     # 1080p (now the port's default)
    [switch]$Handheld,                   # 720p
    [int]$DumpEvery = 0,                 # NX_DUMP_FRAMES: every Nth presented frame to frames\NNNNN.ppm
    [string]$Env = "",                   # other switches: "NX_A=1;NX_B=2"
    [string]$Keys = "",                  # NX_INPUT_SCRIPT ("30:A:0.3;40:DOWN:0.3"): its ';' would split -Env
    [int]$ProfileAt = 0,                 # after this many seconds, sample host pcs for 10 s (profile.txt in the run)
    [string]$Exe = "private\nier\build\nier.exe"
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\..\.."
Set-Location $root
if (Get-Process nier -ErrorAction SilentlyContinue) { Write-Error "a nier process is already running"; exit 2 }
$run = "private\nier\runs\$Name"
$frames = "private\nier\frames"          # where the runtime writes them (<data root>/frames)
if (Test-Path $run) { Remove-Item -Recurse -Force $run }
New-Item -ItemType Directory -Force "$run\frames" | Out-Null
if (Test-Path $frames) { Remove-Item -Recurse -Force $frames }

$vars = @{ NX_LOG = "$run\nx.log"; NX_MEM_LIMIT_GB = "10"; NX_GPU = "discrete"; NX_FPS_CAP = "60" }
if ($Handheld) { $vars.NX_HANDHELD = "1" } else { Remove-Item env:NX_HANDHELD -ErrorAction SilentlyContinue }
if ($Headless) { $vars.NX_HEADLESS = "1"; $vars.NX_MUTE = "1" }
if ($DumpEvery) { $vars.NX_DUMP_FRAMES = "$DumpEvery" }
foreach ($kv in ($Env -split ";")) { if ($kv -match "^([^=]+)=(.*)$") { $vars[$Matches[1]] = $Matches[2] } }
if ($Keys) { $vars.NX_INPUT_SCRIPT = $Keys }
foreach ($k in $vars.Keys) { Set-Item "env:$k" $vars[$k] }

$p = Start-Process -FilePath $Exe -PassThru -NoNewWindow -RedirectStandardError "$run\stderr.txt" -RedirectStandardOutput "$run\stdout.txt"
# Poll instead of WaitForExit(ms): run from bash (powershell -File) with redirected output, the timed wait never came back.
$sw = [Diagnostics.Stopwatch]::StartNew()
$profiled = $ProfileAt -le 0 -or $ProfileAt -ge $Seconds
while (-not $p.HasExited -and $sw.Elapsed.TotalSeconds -lt $Seconds) {
    if (-not $profiled -and $sw.Elapsed.TotalSeconds -ge $ProfileAt) { Set-Content -Path "private\nier\profile" -Value "10"; $profiled = $true }
    Start-Sleep -Milliseconds 250
}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force; Write-Host "killed after $Seconds s" } else { Write-Host "exited with $($p.ExitCode)" }
Start-Sleep -Milliseconds 500
if (Test-Path $frames) { Get-ChildItem $frames | Move-Item -Destination "$run\frames" }
if (Test-Path "private\nier\profile.txt") { Move-Item "private\nier\profile.txt" "$run\profile.txt" -Force }
$log = Get-Item "$run\nx.log" -ErrorAction SilentlyContinue
if ($log) { Write-Host "nx.log: $($log.Length) bytes, $((Get-Content $log.FullName | Measure-Object -Line).Lines) lines" }
Write-Host "frames: $((Get-ChildItem "$run\frames").Count)"
