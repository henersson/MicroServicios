<#
.SYNOPSIS
    Levanta el sistema completo en Docker: los 2 microservicios, RabbitMQ y las
    2 bases de datos.

.DESCRIPTION
    Comprueba que Docker esté corriendo, crea el archivo .env a partir de
    .env.example si aún no existe, construye las imágenes, levanta los 5
    contenedores y espera a que todos reporten estado "healthy" antes de
    devolver el control.

    Los microservicios se ejecutan solo en Docker: no hay una opción para
    levantar la infraestructura por separado.

.PARAMETER TiempoEsperaSegundos
    Cuánto esperar a que los contenedores estén sanos. Por defecto 240, que da
    margen para construir las imágenes la primera vez.

.PARAMETER SinConstruir
    Levanta sin reconstruir las imágenes: usa las que ya están en el equipo.
    Tarda unos 35 segundos y no necesita conexión a internet, así que es la
    forma de arrancar el día de la sustentación. Si falta alguna de las dos
    imágenes del proyecto, el script avisa y termina sin levantar nada.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\levantar.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\levantar.ps1 -SinConstruir
#>
[CmdletBinding()]
param(
    [int] $TiempoEsperaSegundos = 240,
    [switch] $SinConstruir
)

$ErrorActionPreference = 'Stop'

# Todo el script trabaja desde la raíz del repositorio, sin importar desde
# dónde se invoque.
$RaizRepo = Split-Path -Parent $PSScriptRoot
Set-Location $RaizRepo

function Escribir-Paso  { param([string]$Texto) Write-Host "==> $Texto" -ForegroundColor Cyan }
function Escribir-Ok    { param([string]$Texto) Write-Host "    OK  $Texto" -ForegroundColor Green }
function Escribir-Aviso { param([string]$Texto) Write-Host "    !   $Texto" -ForegroundColor Yellow }

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

# -- 1. Docker debe estar corriendo ------------------------------------------
Escribir-Paso 'Verificando que Docker esté disponible'
if (-not (Docker-Responde @('info', '--format', '{{.ServerVersion}}'))) {
    Write-Host ''
    Write-Host 'ACCIÓN REQUERIDA: abre Docker Desktop.' -ForegroundColor Red
    Write-Host 'Sin el demonio de Docker no se puede levantar nada.'
    Write-Host 'Ábrelo desde el menú Inicio, espera a que el icono de la ballena deje de'
    Write-Host 'animarse y vuelve a correr este script.'
    exit 1
}
Escribir-Ok 'Docker responde'

# -- 2. Archivo .env --------------------------------------------------------
Escribir-Paso 'Verificando el archivo .env'
if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Escribir-Aviso 'No existía .env: se creó a partir de .env.example (credenciales de desarrollo).'
} else {
    Escribir-Ok '.env ya existe'
}

# -- 3. Construir y levantar ------------------------------------------------
if ($SinConstruir) {
    # Sin `--build` no se descarga ni se compila nada, pero entonces las dos
    # imágenes del proyecto tienen que existir ya en el equipo.
    Escribir-Paso 'Comprobando que las imágenes del proyecto ya estén construidas'
    $faltantes = @()
    foreach ($imagen in @('saberpro/banco-preguntas-service:1.0.0',
                          'saberpro/revision-service:1.0.0')) {
        if (-not (Docker-Responde @('image', 'inspect', $imagen, '--format', '{{.Id}}'))) { $faltantes += $imagen }
    }
    if ($faltantes.Count -gt 0) {
        Write-Host ''
        Write-Host 'Las imágenes no están construidas: ejecuta levantar.ps1 sin -SinConstruir' -ForegroundColor Red
        Write-Host 'con conexión a internet.' -ForegroundColor Red
        Write-Host ''
        Write-Host 'Faltan:'
        foreach ($imagen in $faltantes) { Write-Host "  $imagen" }
        exit 1
    }
    Escribir-Ok 'Las 2 imágenes del proyecto están en el equipo'

    Escribir-Paso 'Levantando sin reconstruir: docker compose up -d'
    & docker compose up -d
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Falló `docker compose up -d`. Revisa la salida de arriba.'
        exit 1
    }
} else {
    Escribir-Paso 'Construyendo y levantando: docker compose up --build -d'
    & docker compose up --build -d
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'Falló `docker compose up --build -d`. Revisa la salida de arriba.'
        exit 1
    }
}

# -- 4. Esperar a que estén sanos -------------------------------------------
# `docker compose up` devuelve en cuanto los contenedores arrancan, no cuando
# están listos para recibir conexiones. Sin esta espera, la prueba integral
# fallaría por hablar con un servicio que todavía está migrando la base.
Escribir-Paso "Esperando a que los contenedores estén sanos (máximo $TiempoEsperaSegundos s)"
$limite = (Get-Date).AddSeconds($TiempoEsperaSegundos)
$sanos = $false

while ((Get-Date) -lt $limite) {
    $ids = & docker compose ps --quiet
    if ($LASTEXITCODE -ne 0 -or -not $ids) { Start-Sleep -Seconds 2; continue }

    $pendientes = @()
    foreach ($id in $ids) {
        if (-not $id) { continue }
        # Los contenedores sin healthcheck declarado reportan cadena vacía: se
        # dan por buenos si están "running".
        $estado = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $id).Trim()
        $nombre = (& docker inspect --format '{{.Name}}' $id).Trim('/', ' ')
        if ($estado -eq 'unhealthy') {
            Write-Error "El contenedor $nombre quedó unhealthy. Revisa: docker compose logs $nombre"
            exit 1
        }
        if ($estado -ne 'healthy' -and $estado -ne 'running') {
            $pendientes += "$nombre ($estado)"
        }
    }

    if ($pendientes.Count -eq 0) { $sanos = $true; break }
    Write-Host "    esperando: $($pendientes -join ', ')"
    Start-Sleep -Seconds 3
}

if (-not $sanos) {
    Write-Error "Se agotó la espera de $TiempoEsperaSegundos s. Revisa: docker compose ps  y  docker compose logs"
    exit 1
}
Escribir-Ok 'Los 5 contenedores están sanos'

# -- 5. Resumen -------------------------------------------------------------
Write-Host ''
& docker compose ps
Write-Host ''
Write-Host 'Sistema listo:' -ForegroundColor Green
Write-Host '  Swagger del banco       http://localhost:8081/swagger-ui.html'
Write-Host '  Swagger de revisión     http://localhost:8082/docs'
Write-Host '  RabbitMQ (consola)      http://localhost:15672   usuario: saberpro / saberpro'
Write-Host ''
Write-Host 'Puertos:' -ForegroundColor Green
Write-Host '  banco REST / gRPC       8081 / 9091'
Write-Host '  revisión REST           8082'
Write-Host '  RabbitMQ AMQP / consola 5672 / 15672'
Write-Host '  PostgreSQL banco        5433  (bd banco_preguntas)'
Write-Host '  PostgreSQL revisión     5434  (bd revision)'
Write-Host ''
Write-Host 'Siguiente paso: powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1' -ForegroundColor Cyan
