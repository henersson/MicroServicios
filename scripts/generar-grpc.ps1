<#
.SYNOPSIS
    Genera el código Python del cliente gRPC a partir de contracts/proto.

.DESCRIPTION
    Ejecuta grpc_tools.protoc sobre
    contracts/proto/banco_preguntas/v1/banco_preguntas.proto y deja el resultado
    en revision-service/app/infraestructura/grpc_cliente/generado/.

    El código generado NO se versiona: es un artefacto derivado del contrato,
    igual que las clases Java que genera Maven en el banco. Se regenera aquí y,
    al construir la imagen, dentro del Dockerfile.

.NOTES
    ── El problema de los imports absolutos ─────────────────────────────────
    protoc genera en `banco_preguntas_pb2_grpc.py` una línea así:

        import banco_preguntas_pb2 as banco__preguntas__pb2

    Es un import ABSOLUTO de nivel superior. Funciona si el archivo está suelto
    en el directorio de trabajo, pero falla con
    `ModuleNotFoundError: No module named 'banco_preguntas_pb2'` en cuanto el
    archivo vive dentro de un paquete, que es justo nuestro caso.

    Es un problema conocido de protoc (google/protobuf#1491) y no tiene opción
    de línea de comandos que lo arregle. La solución de este script es
    reescribir esa línea a un import RELATIVO al paquete:

        from . import banco_preguntas_pb2 as banco__preguntas__pb2

    Se hace aquí, de forma automática y repetible, y NUNCA editando el archivo a
    mano: el código generado se borra y se vuelve a crear en cada build, así que
    una edición manual se perdería en la siguiente regeneración.

.PARAMETER Limpiar
    Borra el directorio de salida antes de generar.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\generar-grpc.ps1
#>
[CmdletBinding()]
param(
    [switch] $Limpiar
)

$ErrorActionPreference = 'Stop'

$RaizRepo = Split-Path -Parent $PSScriptRoot
Set-Location $RaizRepo

$DirProto   = Join-Path $RaizRepo 'contracts\proto\banco_preguntas\v1'
$ArchivoProto = 'banco_preguntas.proto'
$DirSalida  = Join-Path $RaizRepo 'revision-service\app\infraestructura\grpc_cliente\generado'

# Se prefiere el Python del entorno virtual del servicio; si no existe, el del
# sistema (en este equipo no está el lanzador `py`).
$PythonVenv = Join-Path $RaizRepo 'revision-service\.venv\Scripts\python.exe'
$Python = if (Test-Path $PythonVenv) { $PythonVenv } else { 'python' }

Write-Host "==> Generando el cliente gRPC desde $ArchivoProto" -ForegroundColor Cyan
Write-Host "    Python: $Python" -ForegroundColor DarkGray

& $Python -c "import grpc_tools" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'Falta grpcio-tools. Instálalo con:' -ForegroundColor Red
    Write-Host '  .\revision-service\.venv\Scripts\python.exe -m pip install -r revision-service\requirements.txt'
    exit 1
}

if ($Limpiar -and (Test-Path $DirSalida)) {
    Remove-Item -Recurse -Force $DirSalida
}
if (-not (Test-Path $DirSalida)) {
    New-Item -ItemType Directory -Path $DirSalida -Force | Out-Null
}

# ── 1. Generar ──────────────────────────────────────────────────────────────
# El -I apunta a la carpeta que CONTIENE el .proto (no a contracts/proto), para
# que la salida quede plana: sin esto protoc replicaría el árbol
# banco_preguntas/v1/ dentro del paquete y los imports se complicarían más.
& $Python -m grpc_tools.protoc `
    "--proto_path=$DirProto" `
    "--python_out=$DirSalida" `
    "--grpc_python_out=$DirSalida" `
    "--pyi_out=$DirSalida" `
    $ArchivoProto

if ($LASTEXITCODE -ne 0) {
    Write-Error 'protoc falló. Revisa la salida de arriba.'
    exit 1
}

# ── 2. Corregir el import absoluto (ver la nota de arriba) ──────────────────
$ArchivoGrpc = Join-Path $DirSalida 'banco_preguntas_pb2_grpc.py'
if (-not (Test-Path $ArchivoGrpc)) {
    Write-Error "No se generó $ArchivoGrpc"
    exit 1
}

$contenido = Get-Content $ArchivoGrpc -Raw
$corregido = $contenido -replace '(?m)^import\s+(banco_preguntas_pb2)\s+as\s+(\S+)$', 'from . import $1 as $2'

if ($corregido -eq $contenido) {
    # Puede que una versión futura de protoc ya genere el import relativo. No es
    # un error, pero conviene saberlo para poder quitar este paso algún día.
    Write-Host '    !   No hubo import absoluto que corregir (¿lo arregló protoc?).' -ForegroundColor Yellow
} else {
    Set-Content -Path $ArchivoGrpc -Value $corregido -Encoding utf8 -NoNewline
    Write-Host '    OK  Import absoluto reescrito a relativo en banco_preguntas_pb2_grpc.py' -ForegroundColor Green
}

# ── 3. Marcar el directorio como paquete ────────────────────────────────────
$Init = Join-Path $DirSalida '__init__.py'
@'
"""Código gRPC generado automáticamente desde contracts/proto.

NO EDITAR A MANO: se borra y se regenera en cada build con
`scripts/generar-grpc.ps1` y dentro del Dockerfile. Cualquier cambio manual se
perderá en la siguiente regeneración.
"""
'@ | Set-Content -Path $Init -Encoding utf8

# ── 4. Comprobar que de verdad se puede importar ────────────────────────────
Push-Location (Join-Path $RaizRepo 'revision-service')
try {
    & $Python -c "from app.infraestructura.grpc_cliente.generado import banco_preguntas_pb2 as pb, banco_preguntas_pb2_grpc as pbg; print('    OK  Importa correctamente. Mensajes:', len(pb.DESCRIPTOR.message_types_by_name), '| Servicio:', list(pb.DESCRIPTOR.services_by_name))"
    if ($LASTEXITCODE -ne 0) { throw 'El código generado no se puede importar.' }
} finally {
    Pop-Location
}

Write-Host ''
Get-ChildItem $DirSalida -File | ForEach-Object {
    Write-Host ("    {0,-40} {1,6} bytes" -f $_.Name, $_.Length) -ForegroundColor DarkGray
}
Write-Host ''
Write-Host 'Cliente gRPC generado.' -ForegroundColor Green
