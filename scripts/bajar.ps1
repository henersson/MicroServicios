<#
.SYNOPSIS
    Apaga el sistema.

.DESCRIPTION
    Detiene y elimina los contenedores. Por defecto CONSERVA los volúmenes, de
    modo que las preguntas, revisiones y mensajes encolados siguen ahí al volver
    a levantar.

.PARAMETER Volumenes
    Borra también los volúmenes de datos (PostgreSQL y RabbitMQ). Deja el
    entorno como recién clonado: al levantar de nuevo se aplican las migraciones
    y los datos semilla desde cero. No borra las imágenes, así que después se
    puede arrancar con levantar.ps1 -SinConstruir.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\bajar.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\bajar.ps1 -Volumenes
#>
[CmdletBinding()]
param(
    [switch] $Volumenes
)

$ErrorActionPreference = 'Stop'

$RaizRepo = Split-Path -Parent $PSScriptRoot
Set-Location $RaizRepo

$argumentos = @('compose', 'down')
if ($Volumenes) { $argumentos += '--volumes' }

Write-Host "==> docker $($argumentos -join ' ')" -ForegroundColor Cyan
& docker @argumentos
if ($LASTEXITCODE -ne 0) {
    Write-Error 'Falló `docker compose down`. Revisa la salida de arriba.'
    exit 1
}

Write-Host ''
if ($Volumenes) {
    Write-Host 'Sistema apagado y datos borrados. Las imágenes se conservan.' -ForegroundColor Green
} else {
    Write-Host 'Sistema apagado. Los datos se conservan en los volúmenes.' -ForegroundColor Green
}
