# Diagnóstico SOMENTE LEITURA (nenhum pacote é desativado).
# Executar do PowerShell do Windows depois de conectar pelo ADB.
param(
    [string]$AdbPath = "$env:USERPROFILE\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
)
if (!(Test-Path $AdbPath)) { throw "adb.exe não encontrado: $AdbPath" }
$devices = & $AdbPath devices -l
Write-Host $devices
if (-not ($devices -match '\sdevice\b')) { throw "Nenhuma TV ADB autorizada. Verifique a conexão." }
$folder = Join-Path $PSScriptRoot "diagnostico-tcl"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$jobs = @(
    @{ name = 'sistema'; args = @('shell','getprop','ro.build.version.release') },
    @{ name = 'home'; args = @('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME') },
    @{ name = 'apps_usuario'; args = @('shell','pm','list','packages','-3') },
    @{ name = 'apps_sistema'; args = @('shell','pm','list','packages','-s') },
    @{ name = 'apps_desativados'; args = @('shell','pm','list','packages','-d') },
    @{ name = 'memoria'; args = @('shell','dumpsys','meminfo') },
    @{ name = 'cpu'; args = @('shell','dumpsys','cpuinfo') },
    @{ name = 'armazenamento'; args = @('shell','df','-h','/data') }
)
foreach ($job in $jobs) {
    Write-Host "Coletando $($job.name)..."
    & $AdbPath @($job.args) 2>&1 | Out-File (Join-Path $folder "$($job.name).txt") -Encoding utf8
}
Write-Host "Pronto. Arquivos salvos em $folder. NÃO compartilhe esses logs publicamente sem revisar seu conteúdo."
