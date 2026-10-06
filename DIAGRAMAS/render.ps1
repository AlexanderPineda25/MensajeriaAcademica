# Regenera todos los diagramas desde su fuente editable.
#
#   .\render.ps1                 # regenera todo
#   .\render.ps1 -Solo mermaid   # solo los .mmd
#   .\render.ps1 -Solo mockups   # solo los .excalidraw
#
# Requisitos: mmdc (npm i -g @mermaid-js/mermaid-cli), plantuml (PATH),
#             Node para los mockups de Excalidraw.
# Un diagrama no está terminado hasta que existe su PNG y lo has mirado.

[CmdletBinding()]
param(
    [ValidateSet('todo', 'mermaid', 'plantuml', 'mockups')]
    [string]$Solo = 'todo'
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path

# DIAG-08: contador global de renders fallidos. Cada rama que escribe un
# aviso "FALLO" lo incrementa; al final el script sale con codigo 1 si hubo
# alguno, para que CI detecte un render roto (antes siempre salia con 0).
$script:fallos = 0

# Ancho de render por prefijo de archivo. Los ER del servidor son muy
# anchos y sin --size salen ilegibles al 100%.
# DIAG-07: los 9 migrados de ENTREGA/FINALES se verificaron legibles a 2000
# (ancho por defecto); quedan fijados aqui para que un cambio del defecto
# no los vuelva ilegibles en silencio.
$anchos = @{
    'er-modelo-servidor' = 3400
    'er-modelo-local'    = 2200
    'arq-vista-general'  = 2600
    'arq-flujo-datos'    = 2000
    'seq-login'          = 2000
    'seq-envio-mensaje' = 2000
    'act-difusion'       = 2000
    'clases-servidor-app'  = 2000
    'clases-inicio-vistas' = 2000
    'clases-fachada-casos' = 2000
    'clases-nucleo'        = 2000
    'clases-persistencia'  = 2000
}

function Test-Herramienta {
    param([string]$Comando, [string]$Paquete)
    if (-not (Get-Command $Comando -ErrorAction SilentlyContinue)) {
        Write-Warning "$Comando no esta en el PATH. Instala: $Paquete"
        return $false
    }
    return $true
}

function Invoke-Nativo {
    # Ejecuta un comando nativo (mmdc/npx via shim npm) devolviendo su exit
    # code sin que su stderr mate el script: los shims .ps1 de npm heredan
    # $ErrorActionPreference='Stop' y el mero aviso de node a stderr se
    # volvia error terminante que abortaba el bucle en el primer archivo.
    param([scriptblock]$Llamada)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Llamada 2>$null
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Render-Mermaid {
    $archivos = Get-ChildItem -Path $raiz -Recurse -Filter *.mmd
    if (-not $archivos) { Write-Host 'No hay .mmd que renderizar.'; return }
    if (-not (Test-Herramienta 'mmdc' 'npm i -g @mermaid-js/mermaid-cli')) { return }

    foreach ($f in $archivos) {
        $base = $f.BaseName
        $ancho = if ($anchos.ContainsKey($base)) { $anchos[$base] } else { 2000 }

        foreach ($formato in @('png', 'svg')) {
            $salida = Join-Path $f.DirectoryName "$base.$formato"
            # Via Invoke-Nativo: el shim npm de mmdc reenvia avisos de node a
            # stderr y PowerShell 5.1 con 'Stop' los convierte en error
            # terminante que mataria el bucle. El exito se juzga por el exit
            # code que devuelve el helper, que queda intacto.
            $codigo = Invoke-Nativo { & mmdc -i $f.FullName -o $salida --size $ancho -b white -q }
            if ($codigo -ne 0) {
                $script:fallos++
                Write-Warning "FALLO $($f.Name) -> $formato"
            } else {
                Write-Host ("  OK  {0,-34} {1}" -f $f.Name, $formato)
            }
        }
    }

    # El PDF solo tiene sentido para los ER del servidor: es lo que va al informe.
    Get-ChildItem -Path $raiz -Recurse -Filter *.mmd |
        Where-Object { $_.BaseName -like 'er-modelo-*' } |
        ForEach-Object {
            # `$_` no sobrevive dentro del scriptblock del helper: se captura
            # en una local del bucle antes de llamar.
            $actual = $_
            $salida = Join-Path $actual.DirectoryName "$($actual.BaseName).pdf"
            $codigoPdf = Invoke-Nativo { & mmdc -i $actual.FullName -o $salida -b white -q }
            if ($codigoPdf -eq 0) {
                Write-Host ("  OK  {0,-34} {1}" -f $actual.Name, 'pdf')
            } else {
                $script:fallos++
                Write-Warning "FALLO $($actual.Name) -> pdf"
            }
        }
}

function Render-PlantUML {
    $archivos = Get-ChildItem -Path $raiz -Recurse -Filter *.puml
    if (-not $archivos) { Write-Host 'No hay .puml que renderizar.'; return }
    if (-not (Test-Herramienta 'plantuml' 'descarga el jar a ~/.local/bin/plantuml.jar')) { return }

    foreach ($f in $archivos) {
        & plantuml -tpng $f.FullName
        if ($LASTEXITCODE -ne 0) {
            # Causa frecuente: BOM UTF-8. Verifica y avisa.
            $script:fallos++
            $b = [System.IO.File]::ReadAllBytes($f.FullName)
            if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) {
                Write-Warning "FALLO $($f.Name): tiene BOM. PlantUML no lo acepta; reescribe sin BOM."
            } else {
                Write-Warning "FALLO $($f.Name) -> revisa el source"
            }
        } else {
            Write-Host ("  OK  {0,-34} {1}" -f $f.Name, 'png')
        }
    }
}

function Render-Mockups {
    $escenas = Get-ChildItem -Path $raiz -Recurse -Filter *.excalidraw
    if (-not $escenas) { Write-Host 'No hay escenas .excalidraw.'; return }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        Write-Warning 'node no esta en el PATH.'
        return
    }

    foreach ($s in $escenas) {
        $salida = Join-Path $s.DirectoryName "$($s.BaseName).png"
        # `render` es headless: no necesita el servidor de canvas ni un navegador.
        # Via Invoke-Nativo para que el exit code sea fiable (antes se juzgaba
        # por un $LASTEXITCODE que podia venir de una llamada anterior).
        $codigoMock = Invoke-Nativo { & npx -y mcp-excalidraw-server render $s.FullName --out $salida }
        if ($codigoMock -ne 0) {
            $script:fallos++
            Write-Warning "FALLO $($s.Name)"
        } else {
            Write-Host ("  OK  {0,-34} {1}" -f $s.Name, 'png')
        }
    }
}

Write-Host ''
Write-Host "Regenerando diagramas en $raiz"
Write-Host ''

if ($Solo -in @('todo', 'mermaid')) { Write-Host '[mermaid]'; Render-Mermaid }
if ($Solo -in @('todo', 'plantuml')) { Write-Host '[plantuml]'; Render-PlantUML }
if ($Solo -in @('todo', 'mockups')) { Write-Host '[mockups]'; Render-Mockups }

Write-Host ''
if ($script:fallos -gt 0) {
    Write-Warning "$($script:fallos) render(es) fallaron. Revisa los FALLO de arriba."
    exit 1
}
Write-Host 'Listo. Revisa los PNG antes de darlos por entregados.'
