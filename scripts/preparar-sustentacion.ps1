<#
.SYNOPSIS
    Deja el portátil listo para la demostración de la sustentación.

.DESCRIPTION
    Se ejecuta unos 15 minutos antes. Comprueba Docker, las imágenes y los
    puertos, hace un arranque limpio, verifica la salud del sistema y abre los
    dos Swagger, la consola de RabbitMQ y una ventana con los logs. No construye
    imágenes: si falta alguna, avisa y ofrece construirla.

.PARAMETER SinNavegador
    No abre las pestañas del navegador ni la ventana de logs.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\preparar-sustentacion.ps1
#>
[CmdletBinding()]
param([switch] $SinNavegador)

$ErrorActionPreference = 'Stop'
$RaizRepo = Split-Path -Parent $PSScriptRoot
Set-Location $RaizRepo
$inicio = Get-Date

function Paso { param([string]$T) Write-Host "==> $T" -ForegroundColor Cyan }
function Ok   { param([string]$T) Write-Host "    [OK]    $T" -ForegroundColor Green }
function Alto { param([string]$T) Write-Host "    [ALTO]  $T" -ForegroundColor Red }
# Llama a otro script del proyecto y se queda con su salida, para no llenar la
# pantalla. Solo se muestra si el script falla.
# Windows PowerShell 5.1 convierte el stderr de un .exe en un error terminante
# cuando $ErrorActionPreference es Stop. Esto lo evita, para poder mirar el
# codigo de salida y dar un mensaje propio.
function Docker-Responde {
    param([string[]] $Argumentos)
    try {
        & docker @Argumentos 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Otro {
    param([string]$N, [string[]]$A)
    # Dentro de la funcion, Continue es local: el stderr del script hijo no mata
    # a este. La salida se guarda y solo se muestra si algo falla.
    $ErrorActionPreference = 'Continue'
    $script:UltimaSalida = & powershell -ExecutionPolicy Bypass -File "$PSScriptRoot\$N" @A 2>&1
}
# -- 1. Docker Desktop ------------------------------------------------------
Paso 'Comprobando Docker Desktop'
if (-not (Docker-Responde @('info', '--format', '{{.ServerVersion}}'))) {
    Alto 'Docker Desktop no responde.'
    Write-Host 'QUÉ HACER: abre Docker Desktop desde el menú Inicio, espera a que el icono' -ForegroundColor Yellow
    Write-Host 'de la ballena deje de animarse (1 o 2 minutos) y vuelve a ejecutar este script.' -ForegroundColor Yellow
    exit 1
}
Ok 'Docker Desktop responde'
# -- 2. Las 2 imágenes del proyecto -----------------------------------------
Paso 'Comprobando las imágenes del proyecto'
$faltantes = @()
foreach ($img in @('saberpro/banco-preguntas-service:1.0.0', 'saberpro/revision-service:1.0.0')) {
    if (-not (Docker-Responde @('image', 'inspect', $img, '--format', '{{.Id}}'))) { $faltantes += $img }
}
if ($faltantes.Count -gt 0) {
    Alto "Faltan imágenes: $($faltantes -join ', ')"
    Write-Host 'Construirlas necesita CONEXIÓN A INTERNET y tarda unos 5 minutos.' -ForegroundColor Yellow
    if ((Read-Host 'Construirlas ahora? (s/n)') -notmatch '^[sS]') { Write-Host 'Cancelado.'; exit 1 }
    Otro 'levantar.ps1' @()
    if ($LASTEXITCODE -ne 0) { Alto 'La construcción falló.'; $script:UltimaSalida | Select-Object -Last 25; exit 1 }
}
Ok 'Las 2 imágenes están en el equipo'
# -- 3. Arranque limpio -----------------------------------------------------
Paso 'Arranque limpio: bajar.ps1 -Volumenes'
Otro 'bajar.ps1' @('-Volumenes')
Ok 'Contenedores y datos anteriores borrados (las imágenes se conservan)'
# -- 4. Puertos libres ------------------------------------------------------
# Se comprueba con los contenedores ya bajados: si algo escucha en un puerto,
# es otro programa y no el proyecto.
Paso 'Comprobando que los 7 puertos estén libres'
$ocupados = @()
foreach ($p in @(8081, 8082, 9091, 5433, 5434, 5672, 15672)) {
    $con = Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $con) { continue }
    $proc = Get-Process -Id $con.OwningProcess -ErrorAction SilentlyContinue
    $quien = 'desconocido'
    if ($proc) { $quien = $proc.ProcessName }
    if ($quien -like '*docker*' -or $quien -eq 'wslrelay' -or $quien -eq 'vpnkit') { continue }
    $ocupados += "puerto $p lo usa $quien (PID $($con.OwningProcess))"
}
if ($ocupados.Count -gt 0) {
    Alto 'Hay puertos ocupados por otros programas:'
    foreach ($o in $ocupados) { Write-Host "      $o" }
    Write-Host 'QUÉ HACER: cierra ese programa, o mátalo con  Stop-Process -Id <PID> -Force' -ForegroundColor Yellow
    exit 1
}
Ok 'Los 7 puertos están libres'
# -- 5. Levantar ------------------------------------------------------------
Paso 'Levantando sin reconstruir: levantar.ps1 -SinConstruir'
Otro 'levantar.ps1' @('-SinConstruir')
if ($LASTEXITCODE -ne 0) {
    Alto 'El sistema no llegó a estar sano.'
    $script:UltimaSalida | Select-Object -Last 25
    exit 1
}
Ok 'Los 5 contenedores están sanos'
# -- 6. Comprobación rápida -------------------------------------------------
Paso 'Comprobando la salud de los dos servicios y la mensajería'
$salud = Invoke-RestMethod 'http://localhost:8081/actuator/health' -TimeoutSec 20
if ($salud.status -ne 'UP') { Alto "El banco responde $($salud.status)"; exit 1 }
Ok "Banco UP (base de datos $($salud.components.db.status), RabbitMQ $($salud.components.rabbit.status))"
if ((Invoke-RestMethod 'http://localhost:8082/health' -TimeoutSec 20).status -ne 'UP') {
    Alto 'Revisión no responde UP'; exit 1
}
Ok 'Revisión UP'

$basico = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('saberpro:saberpro'))
$colas = Invoke-RestMethod 'http://localhost:15672/api/queues/%2F' `
    -Headers @{ Authorization = "Basic $basico" } -TimeoutSec 20
$problemas = @()
foreach ($base in @('revision.preguntas-enviadas', 'banco.resultados-revision', 'simulacros.catalogo-preguntas')) {
    foreach ($cola in @($base, "$base.dlq")) {
        $q = $colas | Where-Object { $_.name -eq $cola }
        if (-not $q) { $problemas += "falta la cola $cola" }
        elseif ($cola.EndsWith('.dlq') -and $q.messages -ne 0) { $problemas += "$cola tiene $($q.messages) mensajes" }
    }
}
if ($problemas.Count -gt 0) { Alto "Mensajería: $($problemas -join '; ')"; exit 1 }
Ok 'Las 3 colas y sus 3 DLQ existen, y las DLQ están vacías'
# -- 7. Ventanas ------------------------------------------------------------
if (-not $SinNavegador) {
    Paso 'Abriendo Swagger, la consola de RabbitMQ y los logs'
    Start-Process 'http://localhost:8081/swagger-ui.html'
    Start-Process 'http://localhost:8082/docs'
    Start-Process 'http://localhost:15672/#/queues'
    Start-Process powershell -ArgumentList '-NoExit', '-Command', (
        "`$Host.UI.RawUI.WindowTitle = 'LOGS - banco y revision'; Set-Location '$RaizRepo'; " +
        'docker compose logs -f --tail 0 banco-preguntas-service revision-service')
    Ok 'Ventanas abiertas'
}
# -- 8. Resumen -------------------------------------------------------------
Write-Host "`n  TODO LISTO en $([math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)) s.`n" -ForegroundColor Green
Write-Host '  Swagger del banco       http://localhost:8081/swagger-ui.html'
Write-Host '  Swagger de revisión     http://localhost:8082/docs'
Write-Host '  Consola de RabbitMQ     http://localhost:15672   saberpro / saberpro'
Write-Host ''
Write-Host '  FALTA UNA COSA A MANO:' -ForegroundColor Yellow
Write-Host '    Abre Postman, elige la colección "SaberPro - DEMO sustentación" y el'
Write-Host '    environment "SaberPro - local (Docker)" arriba a la derecha. Después manda'
Write-Host '    las 4 peticiones de la carpeta 1: deben salir las cuatro en verde.'
Write-Host ''
Write-Host '  El guion de la demostración está en docs\GUIA_DEMO.md' -ForegroundColor Cyan
