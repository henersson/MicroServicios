<#
.SYNOPSIS
    Prueba integral del sistema completo: banco + revisión + RabbitMQ + gRPC.

.DESCRIPTION
    Recorre 31 comprobaciones contra el sistema levantado con Docker: el camino
    feliz de punta a punta, el camino de rechazo con la reapertura y el reenvío,
    las reglas del dominio y la salud de la mensajería.

    Crea sus propias preguntas, así que se puede correr las veces que haga falta
    y no depende de datos creados por Postman ni por corridas anteriores.

    Los pasos asíncronos (los que dependen de un evento de RabbitMQ) esperan
    sondeando cada segundo, con un máximo de 30 s. No hay `sleep` fijos.

    Código de salida: 0 si las 31 pasan, 1 si alguna falla.

.PARAMETER UrlBanco
    URL base del banco-preguntas-service. Por defecto http://localhost:8081

.PARAMETER UrlRevision
    URL base del revision-service. Por defecto http://localhost:8082

.PARAMETER UrlRabbit
    URL de la API de management de RabbitMQ. Por defecto http://localhost:15672

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1
#>
[CmdletBinding()]
param(
    [string] $UrlBanco   = 'http://localhost:8081',
    [string] $UrlRevision = 'http://localhost:8082',
    [string] $UrlRabbit  = 'http://localhost:15672'
)

$ErrorActionPreference = 'Stop'

$RaizRepo = Split-Path -Parent $PSScriptRoot
Set-Location $RaizRepo

# Credenciales de desarrollo de la API de management (las mismas del .env).
$RabbitUsuario = 'saberpro'
$RabbitClave   = 'saberpro'

$TimeoutAsincronoSegundos = 30

$script:Pasadas  = 0
$script:Falladas = 0

# ── Usuarios simulados (no hay autenticación real: se declara en cabeceras) ──
function Cabeceras {
    param([string] $UsuarioId, [string] $Rol)
    return @{ 'X-Usuario-Id' = $UsuarioId; 'X-Usuario-Rol' = $Rol }
}

$Autor         = Cabeceras 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d' 'AUTOR'
$Administrador = Cabeceras '0a0b0c0d-1111-4222-8333-444455556666' 'ADMINISTRADOR'
$Estudiante    = Cabeceras 'e1e2e3e4-5555-4666-8777-888899990000' 'ESTUDIANTE'

# ── Salida ──────────────────────────────────────────────────────────────────
function Seccion {
    param([string] $Titulo)
    Write-Host ''
    Write-Host ('=' * 78) -ForegroundColor DarkGray
    Write-Host "  $Titulo" -ForegroundColor Cyan
    Write-Host ('=' * 78) -ForegroundColor DarkGray
}

function Ok {
    param([string] $Texto, [string] $Detalle = '')
    $script:Pasadas++
    Write-Host '  [OK]    ' -ForegroundColor Green -NoNewline
    Write-Host $Texto
    if ($Detalle) { Write-Host "          $Detalle" -ForegroundColor DarkGray }
}

function Falla {
    param([string] $Texto, [string] $Detalle = '')
    $script:Falladas++
    Write-Host '  [FALLA] ' -ForegroundColor Red -NoNewline
    Write-Host $Texto
    if ($Detalle) { Write-Host "          $Detalle" -ForegroundColor Yellow }
}

function Comprobar {
    param([bool] $Condicion, [string] $Texto, [string] $Detalle = '')
    if ($Condicion) { Ok $Texto $Detalle } else { Falla $Texto $Detalle }
}

# ── Llamadas HTTP ───────────────────────────────────────────────────────────

<#
Llama a la API y devuelve el objeto de la respuesta.

Se usa Invoke-RestMethod normal: los dos servicios declaran charset=utf-8, así
que PowerShell 5.1 decodifica bien los acentos sin ayuda.
#>
function Invocar {
    param(
        [string] $Metodo,
        [string] $Url,
        [hashtable] $Cabeceras,
        [string] $Cuerpo
    )
    $parametros = @{
        Method     = $Metodo
        Uri        = $Url
        Headers    = $Cabeceras
        TimeoutSec = 30
    }
    if ($Cuerpo) {
        $parametros.ContentType = 'application/json; charset=utf-8'
        # A bytes UTF-8 a mano: si se pasa la cadena tal cual, Windows
        # PowerShell la envía en ISO-8859-1 y los acentos llegan destrozados.
        $parametros.Body = [Text.Encoding]::UTF8.GetBytes($Cuerpo)
    }
    return Invoke-RestMethod @parametros
}

<#
Llama esperando un error HTTP concreto. Devuelve el Problem Details del cuerpo,
que es donde están el `detail` y los `errores[]` que la prueba quiere mirar.
#>
function Invocar-Esperando-Error {
    param(
        [string] $Metodo,
        [string] $Url,
        [hashtable] $Cabeceras,
        [string] $Cuerpo,
        [int]    $EstadoEsperado,
        [string] $Descripcion,
        [string] $PatronEnErrores
    )
    try {
        Invocar -Metodo $Metodo -Url $Url -Cabeceras $Cabeceras -Cuerpo $Cuerpo | Out-Null
        Falla $Descripcion "se esperaba HTTP $EstadoEsperado y la petición tuvo éxito"
        return $null
    } catch {
        $respuesta = $_.Exception.Response
        if (-not $respuesta) {
            Falla $Descripcion "no hubo respuesta HTTP: $($_.Exception.Message)"
            return $null
        }
        $estado = [int] $respuesta.StatusCode

        $problema = $null
        try {
            $lector = New-Object System.IO.StreamReader(
                $respuesta.GetResponseStream(), [Text.Encoding]::UTF8)
            $texto = $lector.ReadToEnd()
            $lector.Close()
            # Con algunas respuestas, Windows PowerShell 5.1 ya consumió el cuerpo
            # y lo dejó en ErrorDetails: el flujo llega vacío.
            if (-not $texto -and $_.ErrorDetails) { $texto = $_.ErrorDetails.Message }
            if ($texto) { $problema = $texto | ConvertFrom-Json }
        } catch {
            # Un error sin cuerpo JSON no invalida la comprobación del código.
        }

        if ($estado -ne $EstadoEsperado) {
            Falla $Descripcion "se esperaba HTTP $EstadoEsperado y llegó HTTP $estado"
            return $problema
        }

        # Si se pide un patrón, el código correcto no basta: el mensaje tiene que
        # decir cuál es la regla incumplida. Es una sola comprobación.
        if ($PatronEnErrores) {
            $textoCompleto = "$($problema.detail) $($problema.errores -join ' ')"
            if ($textoCompleto -notmatch $PatronEnErrores) {
                Falla $Descripcion "HTTP $estado correcto, pero el mensaje no cita '$PatronEnErrores'"
                return $problema
            }
        }

        $detalle = ''
        if ($problema -and $problema.detail) { $detalle = $problema.detail }
        Ok "$Descripcion -> HTTP $estado" $detalle
        return $problema
    }
}

<#
Espera a que una condición se cumpla, sondeando cada segundo.

Los pasos que dependen de un evento de RabbitMQ no son instantáneos. Esperar
activamente y no con un `sleep` fijo hace que la prueba pase rápido cuando el
sistema va bien y no falle por tiempos cuando va lento.
#>
function Esperar-Hasta {
    param(
        [scriptblock] $Condicion,
        [string] $Descripcion,
        [int] $TimeoutSegundos = $TimeoutAsincronoSegundos
    )
    $limite = (Get-Date).AddSeconds($TimeoutSegundos)
    $intentos = 0
    while ((Get-Date) -lt $limite) {
        $intentos++
        try {
            if (& $Condicion) {
                Ok $Descripcion "cumplido en $intentos intento(s)"
                return $true
            }
        } catch {
            # Todavía no está listo; se vuelve a intentar.
        }
        Start-Sleep -Seconds 1
    }
    Falla $Descripcion "no se cumplió en $TimeoutSegundos s ($intentos intentos)"
    return $false
}

# ── RabbitMQ ────────────────────────────────────────────────────────────────
function Mensajes-En-Cola {
    param([string] $Cola)
    $par = "${RabbitUsuario}:${RabbitClave}"
    $basico = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($par))
    $q = Invoke-RestMethod -Uri "$UrlRabbit/api/queues/%2F/$Cola" `
        -Headers @{ Authorization = "Basic $basico" } -TimeoutSec 20
    return [int] $q.messages
}

# ── Contenido de las preguntas de prueba ────────────────────────────────────
# Con tildes a propósito: si el charset estuviera mal, se vería aquí.

$OpcionesValidas = @(
    @{ texto = 'El desacoplamiento temporal: Matrículas confirma la matrícula aunque Notificaciones esté caído.'; esCorrecta = $true },
    @{ texto = 'El desacoplamiento de despliegue, porque el broker permite desplegar ambos servicios a la vez.'; esCorrecta = $false },
    @{ texto = 'La consistencia fuerte, porque el broker actualiza las dos bases en la misma transacción.'; esCorrecta = $false },
    @{ texto = 'La idempotencia, porque un broker nunca entrega el mismo mensaje más de una vez.'; esCorrecta = $false }
)

$ContextoConTildes = 'Un equipo desarrolla una plataforma de matrículas con dos microservicios: Matrículas y Notificaciones. Cuando un estudiante se matricula, Matrículas debe avisar a Notificaciones para que envíe el correo de confirmación. El arquitecto propone publicar un evento en un broker en vez de llamar por HTTP.'
$PreguntaDirectaConTildes = '¿Qué propiedad del acoplamiento entre servicios se está priorizando con esa decisión?'

function Cuerpo-Pregunta {
    param(
        [array]  $Opciones = $OpcionesValidas,
        [string] $Contexto = $ContextoConTildes,
        [string] $Subtema  = 'Mensajería asíncrona'
    )
    return @{
        contexto           = $Contexto
        preguntaDirecta    = $PreguntaDirectaConTildes
        opciones           = $Opciones
        justificacion      = 'Publicar un evento elimina la dependencia temporal: la matrícula se confirma aunque Notificaciones esté caído, y el mensaje espera en la cola.'
        bibliografia       = @('Hohpe, G. y Woolf, B. (2003). Enterprise Integration Patterns. Addison-Wesley.')
        competenciaCodigo  = 'ING-SOFT'
        competenciaNombre  = 'Diseño de Software y Arquitectura'
        tema               = 'Arquitectura de Software'
        subtema            = $Subtema
        nivelDificultad    = 'MEDIO'
    } | ConvertTo-Json -Depth 5
}

$FormatoCompleto = @{
    puntajes = @{
        CLARIDAD_CONTEXTO           = 5
        PERTINENCIA_COMPETENCIA     = 4
        PLAUSIBILIDAD_DISTRACTORES  = 4
        COHERENCIA_GRAMATICAL       = 4
        UNICIDAD_RESPUESTA_CORRECTA = 5
        CALIDAD_JUSTIFICACION       = 4
    }
} | ConvertTo-Json -Depth 3

# El mismo formato sin COHERENCIA_GRAMATICAL: así quedaba completo antes de que
# la parte gramatical de la invariante 3 pasara a ser un criterio del revisor.
$FormatoSinCoherencia = @{
    puntajes = @{
        CLARIDAD_CONTEXTO           = 5
        PERTINENCIA_COMPETENCIA     = 4
        PLAUSIBILIDAD_DISTRACTORES  = 4
        UNICIDAD_RESPUESTA_CORRECTA = 5
        CALIDAD_JUSTIFICACION       = 4
    }
} | ConvertTo-Json -Depth 3

<#
Lleva una pregunta nueva hasta EN_REVISION y devuelve su id y su revisión.
Lo usan el camino feliz, el de rechazo y el caso de la invariante 10.
#>
function Nueva-Pregunta-En-Revision {
    param([string] $Subtema)

    $pregunta = Invocar -Metodo Post -Url "$UrlBanco/api/v1/preguntas" `
        -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Subtema $Subtema)
    # Se envía desde EN_CONSTRUCCION, y a ese estado se llega editándola.
    Invocar -Metodo Put -Url "$UrlBanco/api/v1/preguntas/$($pregunta.id)" `
        -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Subtema $Subtema) | Out-Null
    Invocar -Metodo Post -Url "$UrlBanco/api/v1/preguntas/$($pregunta.id)/enviar-a-revision" `
        -Cabeceras $Autor | Out-Null

    $limite = (Get-Date).AddSeconds($TimeoutAsincronoSegundos)
    while ((Get-Date) -lt $limite) {
        try {
            $actual = Invocar -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$($pregunta.id)" -Cabeceras $Autor
            if ($actual.estado -eq 'EN_REVISION') { break }
        } catch {
            # todavía no
        }
        Start-Sleep -Seconds 1
    }

    $revisiones = Invocar -Metodo Get `
        -Url "$UrlRevision/api/v1/revisiones?preguntaId=$($pregunta.id)" -Cabeceras $Administrador
    return @{ Pregunta = $pregunta; Revision = $revisiones[0] }
}

# ═════════════════════════════════════════════════════════════════════════════
Seccion 'SISTEMA LISTO'
# ═════════════════════════════════════════════════════════════════════════════

# 1
$salud = Invoke-RestMethod "$UrlBanco/actuator/health" -TimeoutSec 20
Comprobar ($salud.status -eq 'UP' -and $salud.components.db.status -eq 'UP' `
        -and $salud.components.rabbit.status -eq 'UP') `
    '1. El banco está UP' `
    "db=$($salud.components.db.status) rabbit=$($salud.components.rabbit.status)"

# 2
$saludRevision = Invoke-RestMethod "$UrlRevision/health" -TimeoutSec 20
Comprobar ($saludRevision.status -eq 'UP') '2. Revisión está UP'

# 3
$revisores = Invocar -Metodo Get -Url "$UrlRevision/api/v1/revisores" -Cabeceras $Administrador
$activos = @($revisores | Where-Object { $_.activo })
Comprobar ($activos.Count -ge 2) '3. Hay al menos 2 revisores activos' `
    "$($activos.Count) revisores registrados"

# ═════════════════════════════════════════════════════════════════════════════
Seccion 'CAMINO FELIZ: de BORRADOR a PUBLICADA'
# ═════════════════════════════════════════════════════════════════════════════

# 4
$pregunta = Invocar -Metodo Post -Url "$UrlBanco/api/v1/preguntas" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta)
$idFeliz = $pregunta.id
Comprobar ($pregunta.estado -eq 'BORRADOR' -and $idFeliz) `
    '4. Pregunta creada en BORRADOR' "id=$idFeliz"

# 5
$trabajada = Invocar -Metodo Put -Url "$UrlBanco/api/v1/preguntas/$idFeliz" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta)
Comprobar ($trabajada.estado -eq 'EN_CONSTRUCCION') `
    '5. El autor la edita: pasa a EN_CONSTRUCCION'

# 6
$enviada = Invocar -Metodo Post `
    -Url "$UrlBanco/api/v1/preguntas/$idFeliz/enviar-a-revision" -Cabeceras $Autor
Comprobar ($enviada.estado -eq 'PENDIENTE_REVISION') '6. Enviada a revisión (PENDIENTE_REVISION)'

# 7
Esperar-Hasta {
    (Invocar -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$idFeliz" -Cabeceras $Autor).estado -eq 'EN_REVISION'
} '7. El banco pasa solo a EN_REVISION (evento + gRPC + evento)' | Out-Null

# 8
$revisiones = Invocar -Metodo Get `
    -Url "$UrlRevision/api/v1/revisiones?preguntaId=$idFeliz" -Cabeceras $Administrador
$revision = $revisiones[0]
Comprobar ($revision -and $revision.revisorId -and $revision.estado -eq 'ASIGNADA') `
    '8. La revisión existe, con revisor asignado y en ASIGNADA' `
    "revision=$($revision.revisionId) revisor=$($revision.revisorId)"

$Revisor = Cabeceras $revision.revisorId 'REVISOR'
$idRevisionFeliz = $revision.revisionId

# 9
Comprobar ($revision.snapshot.opciones.Count -eq 4 `
        -and $revision.snapshot.contexto -eq $ContextoConTildes) `
    '9. El snapshot llegó completo por gRPC, con las tildes intactas' `
    "$($revision.snapshot.opciones.Count) opciones, competencia $($revision.snapshot.competenciaCodigo)"

# 10
$conFormato = Invocar -Metodo Put `
    -Url "$UrlRevision/api/v1/revisiones/$idRevisionFeliz/formato" `
    -Cabeceras $Revisor -Cuerpo $FormatoCompleto
Comprobar ($conFormato.estado -eq 'EN_EVALUACION' -and $conFormato.promedio -ge 3.0) `
    '10. Formato completo (6 criterios) guardado (EN_EVALUACION)' "promedio=$($conFormato.promedio)"

# 11
$observacion = @{ texto = 'La pregunta está bien construida. Solo sugiero precisar en el contexto que el broker es persistente.' } | ConvertTo-Json
$conObservacion = Invocar -Metodo Post `
    -Url "$UrlRevision/api/v1/revisiones/$idRevisionFeliz/observaciones" `
    -Cabeceras $Revisor -Cuerpo $observacion
Comprobar ($conObservacion.observaciones.Count -ge 1) '11. Observación agregada' `
    "$($conObservacion.observaciones.Count) observación(es)"

# 12
$aprobada = Invocar -Metodo Post `
    -Url "$UrlRevision/api/v1/revisiones/$idRevisionFeliz/decision" `
    -Cabeceras $Revisor -Cuerpo (@{ decision = 'APROBAR' } | ConvertTo-Json)
Comprobar ($aprobada.estado -eq 'APROBADA') '12. Revisión APROBADA'

# 13
Esperar-Hasta {
    (Invocar -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$idFeliz" -Cabeceras $Autor).estado -eq 'APROBADA'
} '13. El banco pasa solo a APROBADA (PreguntaAprobadaTecnicamente)' | Out-Null

# 14  (se cuentan los mensajes ANTES de publicar, para la comprobación 15)
$antesDePublicar = Mensajes-En-Cola 'simulacros.catalogo-preguntas'
$publicada = Invocar -Metodo Post `
    -Url "$UrlBanco/api/v1/preguntas/$idFeliz/publicar" -Cabeceras $Administrador
Comprobar ($publicada.estado -eq 'PUBLICADA') '14. El administrador la publica (PUBLICADA)'

# 15
Esperar-Hasta {
    (Mensajes-En-Cola 'simulacros.catalogo-preguntas') -ge ($antesDePublicar + 1)
} '15. PreguntaPublicada llegó a simulacros.catalogo-preguntas' | Out-Null

# ═════════════════════════════════════════════════════════════════════════════
Seccion 'CAMINO DE RECHAZO: RECHAZADA, el autor la reabre y la reenvía'
# ═════════════════════════════════════════════════════════════════════════════

# 16
$caso = Nueva-Pregunta-En-Revision -Subtema 'Colas y brokers'
$idRechazo = $caso.Pregunta.id
$revisionRechazo = $caso.Revision
$RevisorRechazo = Cabeceras $revisionRechazo.revisorId 'REVISOR'
$estadoRechazo = (Invocar -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$idRechazo" -Cabeceras $Autor).estado
Comprobar ($estadoRechazo -eq 'EN_REVISION' -and $revisionRechazo.revisionId) `
    '16. Una segunda pregunta llega a EN_REVISION' "id=$idRechazo"

# 17
Invocar -Metodo Put -Url "$UrlRevision/api/v1/revisiones/$($revisionRechazo.revisionId)/formato" `
    -Cabeceras $RevisorRechazo -Cuerpo $FormatoCompleto | Out-Null
foreach ($texto in @(
        'El distractor número 3 repite la idea de la opción correcta y se descarta demasiado rápido.',
        'Falta precisar en el contexto cuántos estudiantes se matriculan por minuto.')) {
    Invocar -Metodo Post -Url "$UrlRevision/api/v1/revisiones/$($revisionRechazo.revisionId)/observaciones" `
        -Cabeceras $RevisorRechazo -Cuerpo (@{ texto = $texto } | ConvertTo-Json) | Out-Null
}
$rechazada = Invocar -Metodo Post `
    -Url "$UrlRevision/api/v1/revisiones/$($revisionRechazo.revisionId)/decision" `
    -Cabeceras $RevisorRechazo -Cuerpo (@{ decision = 'RECHAZAR' } | ConvertTo-Json)
Comprobar ($rechazada.estado -eq 'RECHAZADA' -and $rechazada.observaciones.Count -eq 2) `
    '17. Formato + 2 observaciones + rechazar -> RECHAZADA' `
    "$($rechazada.observaciones.Count) observaciones"

# 18
Esperar-Hasta {
    $actual = Invocar -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$idRechazo" -Cabeceras $Autor
    $actual.estado -eq 'RECHAZADA' -and -not [string]::IsNullOrWhiteSpace($actual.observacionesUltimaRevision)
} '18. El banco pasa solo a RECHAZADA y observacionesUltimaRevision no está vacío' | Out-Null

# 19
$reabierta = Invocar -Metodo Put -Url "$UrlBanco/api/v1/preguntas/$idRechazo" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Subtema 'Colas y brokers')
Comprobar ($reabierta.estado -eq 'EN_CONSTRUCCION' `
        -and -not [string]::IsNullOrWhiteSpace($reabierta.observacionesUltimaRevision)) `
    '19. El autor la edita: RECHAZADA -> EN_CONSTRUCCION, con las observaciones a la vista'

# 20
Invocar -Metodo Post -Url "$UrlBanco/api/v1/preguntas/$idRechazo/enviar-a-revision" `
    -Cabeceras $Autor | Out-Null
Esperar-Hasta {
    $actual = Invocar -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$idRechazo" -Cabeceras $Autor
    # Por la tubería: en PowerShell 5.1, @() sobre la lista que devuelve
    # Invoke-RestMethod la envolvería en otra lista de un solo elemento.
    $revisionesRechazo = @(Invocar -Metodo Get `
        -Url "$UrlRevision/api/v1/revisiones?preguntaId=$idRechazo" -Cabeceras $Administrador |
        ForEach-Object { $_ })
    $actual.estado -eq 'EN_REVISION' -and $revisionesRechazo.Count -ge 2
} '20. Reenviada: vuelve sola a EN_REVISION con una revisión nueva' | Out-Null

# ═════════════════════════════════════════════════════════════════════════════
Seccion 'REGLAS DEL DOMINIO'
# ═════════════════════════════════════════════════════════════════════════════

# 21
$sinTrabajar = Invocar -Metodo Post -Url "$UrlBanco/api/v1/preguntas" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Subtema 'Invariante 6')
Invocar-Esperando-Error -Metodo Post `
    -Url "$UrlBanco/api/v1/preguntas/$($sinTrabajar.id)/enviar-a-revision" `
    -Cabeceras $Autor -EstadoEsperado 409 -PatronEnErrores 'EN_CONSTRUCCION' `
    -Descripcion '21. Enviar a revisión desde BORRADOR -> 409 (invariante 6, sin saltos)' | Out-Null

# 22
$dosDistractores = @($OpcionesValidas[0], $OpcionesValidas[1], $OpcionesValidas[2])
Invocar-Esperando-Error -Metodo Post -Url "$UrlBanco/api/v1/preguntas" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Opciones $dosDistractores) `
    -EstadoEsperado 400 -PatronEnErrores 'Invariante 1' `
    -Descripcion '22. Crear con 2 distractores (3 opciones) -> 400 citando la invariante 1' | Out-Null

# 23
$cincoOpciones = @(
    $OpcionesValidas[0], $OpcionesValidas[1], $OpcionesValidas[2], $OpcionesValidas[3],
    @{ texto = 'La tolerancia a particiones, porque el teorema CAP obliga a usar mensajería asíncrona.'; esCorrecta = $false }
)
Invocar-Esperando-Error -Metodo Post -Url "$UrlBanco/api/v1/preguntas" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Opciones $cincoOpciones) `
    -EstadoEsperado 400 -PatronEnErrores 'exactamente 4 opciones' `
    -Descripcion '23. Crear con 5 opciones (4 distractores) -> 400 citando la invariante 1' | Out-Null

# 24
$conTodasLasAnteriores = @(
    $OpcionesValidas[0], $OpcionesValidas[1], $OpcionesValidas[2],
    @{ texto = 'Todas las anteriores son correctas.'; esCorrecta = $false }
)
Invocar-Esperando-Error -Metodo Post -Url "$UrlBanco/api/v1/preguntas" `
    -Cabeceras $Autor -Cuerpo (Cuerpo-Pregunta -Opciones $conTodasLasAnteriores) `
    -EstadoEsperado 400 -PatronEnErrores 'Invariante 2' `
    -Descripcion '24. Crear con "Todas las anteriores" -> 400 citando la invariante 2' | Out-Null

# 25
$casoFormato = Nueva-Pregunta-En-Revision -Subtema 'Patrones de integración'
$RevisorFormato = Cabeceras $casoFormato.Revision.revisorId 'REVISOR'
Invocar -Metodo Put -Url "$UrlRevision/api/v1/revisiones/$($casoFormato.Revision.revisionId)/formato" `
    -Cabeceras $RevisorFormato -Cuerpo $FormatoSinCoherencia | Out-Null
Invocar-Esperando-Error -Metodo Post `
    -Url "$UrlRevision/api/v1/revisiones/$($casoFormato.Revision.revisionId)/decision" `
    -Cabeceras $RevisorFormato -Cuerpo (@{ decision = 'APROBAR' } | ConvertTo-Json) `
    -EstadoEsperado 400 -PatronEnErrores 'COHERENCIA_GRAMATICAL' `
    -Descripcion '25. Aprobar sin juzgar la coherencia gramatical (invariantes 3 y 10)' | Out-Null

# 26
Invocar-Esperando-Error -Metodo Post -Url "$UrlBanco/api/v1/preguntas/$idFeliz/publicar" `
    -Cabeceras $Estudiante -EstadoEsperado 403 `
    -Descripcion '26. Un ESTUDIANTE no puede publicar' | Out-Null

# 27
Invocar-Esperando-Error -Metodo Get -Url "$UrlBanco/api/v1/preguntas/$idFeliz" `
    -Cabeceras @{} -EstadoEsperado 401 `
    -Descripcion '27. Banco sin cabeceras de usuario' | Out-Null

# 28
Invocar-Esperando-Error -Metodo Get -Url "$UrlRevision/api/v1/revisiones" `
    -Cabeceras @{} -EstadoEsperado 401 `
    -Descripcion '28. Revisión sin cabeceras de usuario' | Out-Null

# 29
Invocar-Esperando-Error -Metodo Delete -Url "$UrlBanco/api/v1/preguntas/$idFeliz" `
    -Cabeceras $Administrador -EstadoEsperado 405 -PatronEnErrores 'Invariante 8' `
    -Descripcion '29. DELETE de una pregunta -> 405: no se borra, se archiva (invariante 8)' | Out-Null

# 30
Invocar-Esperando-Error -Metodo Get -Url "$UrlBanco/api/v1/no-existe" `
    -Cabeceras $Administrador -EstadoEsperado 404 `
    -Descripcion '30. Una ruta que no existe en el banco -> 404' | Out-Null

# ═════════════════════════════════════════════════════════════════════════════
Seccion 'MENSAJERÍA'
# ═════════════════════════════════════════════════════════════════════════════

# 31
$dlqConMensajes = @()
foreach ($dlq in @('revision.preguntas-enviadas.dlq',
                   'banco.resultados-revision.dlq',
                   'simulacros.catalogo-preguntas.dlq')) {
    $n = Mensajes-En-Cola $dlq
    if ($n -ne 0) { $dlqConMensajes += "$dlq=$n" }
}
Comprobar ($dlqConMensajes.Count -eq 0) '31. Las 3 DLQ están vacías' `
    $(if ($dlqConMensajes.Count -eq 0) { 'ningún evento fallido' } else { $dlqConMensajes -join ' ' })

# ═════════════════════════════════════════════════════════════════════════════
Seccion 'RESUMEN'
# ═════════════════════════════════════════════════════════════════════════════

$total = $script:Pasadas + $script:Falladas
Write-Host "  Comprobaciones: $total"
Write-Host "  Pasadas:        $($script:Pasadas)" -ForegroundColor Green
if ($script:Falladas -gt 0) {
    Write-Host "  Falladas:       $($script:Falladas)" -ForegroundColor Red
} else {
    Write-Host "  Falladas:       0"
}

Write-Host ''
Write-Host '  Preguntas usadas en esta corrida:'
Write-Host "    camino feliz  : $idFeliz (PUBLICADA)"
Write-Host "    camino rechazo: $idRechazo (rechazada, reabierta y reenviada: EN_REVISION)"
Write-Host "    invariante 6  : $($sinTrabajar.id) (BORRADOR, no se pudo enviar)"
Write-Host "    invariante 10 : $($casoFormato.Pregunta.id) (EN_REVISION, sin decidir)"

Write-Host ''
if ($script:Falladas -eq 0) {
    Write-Host '  LA PRUEBA INTEGRAL PASÓ COMPLETA.' -ForegroundColor Green
    exit 0
} else {
    Write-Host '  LA PRUEBA INTEGRAL FALLÓ.' -ForegroundColor Red
    exit 1
}
