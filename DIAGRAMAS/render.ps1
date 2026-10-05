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

# Ancho de render por prefijo de archivo. Los ER del servidor son muy
# anchos y sin --size salen ilegibles al 100%.
$anchos = @{
    'er-modelo-servidor' = 3400
    'er-modelo-local'    = 2200
    'arq-vista-general'  = 2600
}

function Test-Herramienta {
    param([string]$Comando, [string]$Paquete)
    if (-not (Get-Command $Comando -ErrorAction SilentlyContinue)) {
        Write-Warning "$Comando no esta en el PATH. Instala: $Paquete"
        return $false
    }
    return $true
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
            & mmdc -i $f.FullName -o $salida --size $ancho -b white -q
            if ($LASTEXITCODE -ne 0) {
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
            $salida = Join-Path $_.DirectoryName "$($_.BaseName).pdf"
            & mmdc -i $_.FullName -o $salida -b white -q
            if ($LASTEXITCODE -eq 0) {
                Write-Host ("  OK  {0,-34} {1}" -f $_.Name, 'pdf')
            } else {
                Write-Warning "FALLO $($_.Name) -> pdf"
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
        # El aviso "non-Latin text detected" sale por stderr con exit 0, pero
        # PowerShell 5.1 lo convierte en error terminante (Stop). Se atrapa y
        # se ignora: lo que vale es $LASTEXITCODE, que queda intacto.
        try {
            & npx -y mcp-excalidraw-server render $s.FullName --out $salida 2>$null
        } catch { }
        if ($LASTEXITCODE -ne 0) {
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
Write-Host 'Listo. Revisa los PNG antes de darlos por entregados.'
