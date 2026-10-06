param(
    [switch]$SoloVerificar,
    [switch]$IniciarMySql,
    [string]$DirectorioHerramientas = (Join-Path $env:LOCALAPPDATA 'Mensajeria\herramientas')
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$MavenVersionObjetivo = '3.9.16'
$VersionJavaMinima = 21
$RaizProyecto = Split-Path -Parent $PSScriptRoot
$PropiedadesServidor = Join-Path $RaizProyecto 'server\servidor-app\src\main\resources\application.properties'

function Escribir-Estado {
    param([string]$Estado, [string]$Nombre, [string]$Detalle)
    '{0,-12} {1,-18} {2}' -f "[$Estado]", $Nombre, $Detalle
}

function Leer-Jdk {
    param([string]$Ruta)
    if ([string]::IsNullOrWhiteSpace($Ruta)) { return $null }
    $raiz = $Ruta.Trim().Trim('"')
    if ((Split-Path $raiz -Leaf) -ieq 'bin') { $raiz = Split-Path -Parent $raiz }
    if ((Split-Path $raiz -Leaf) -ieq 'javac.exe' -or (Split-Path $raiz -Leaf) -ieq 'java.exe') {
        $raiz = Split-Path -Parent (Split-Path -Parent $raiz)
    }
    $java = Join-Path $raiz 'bin\java.exe'
    $javac = Join-Path $raiz 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) { return $null }

    $salida = (& $java -version 2>&1 | Out-String)
    $coincidencia = [regex]::Match($salida, '(?:version\s+"|openjdk\s+)(?<version>\d+)(?:[._+"-]|\s)')
    if (-not $coincidencia.Success) { return $null }
    [pscustomobject]@{
        Raiz = (Resolve-Path -LiteralPath $raiz).Path
        Version = [int]$coincidencia.Groups['version'].Value
        Descripcion = (($salida -split '\r?\n' | Select-Object -First 1).Trim())
    }
}

function Buscar-JdkValido {
    $candidatos = [System.Collections.Generic.List[string]]::new()
    foreach ($ruta in @(
        $env:JAVA_HOME,
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
        $(if (Get-Command javac.exe -ErrorAction SilentlyContinue) { Split-Path -Parent (Split-Path -Parent (Get-Command javac.exe).Source) }),
        $(if (Get-Command java.exe -ErrorAction SilentlyContinue) { Split-Path -Parent (Split-Path -Parent (Get-Command java.exe).Source) }),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'),
        (Join-Path $DirectorioHerramientas 'jdk-21')
    )) {
        if (-not [string]::IsNullOrWhiteSpace($ruta)) { $candidatos.Add($ruta) }
    }

    $vistos = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($candidato in $candidatos) {
        if (-not (Test-Path -LiteralPath $candidato)) { continue }
        $rutas = @($candidato)
        if (-not (Test-Path -LiteralPath (Join-Path $candidato 'bin\javac.exe'))) {
            $rutas += Get-ChildItem -LiteralPath $candidato -Directory -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty FullName
        }
        foreach ($ruta in $rutas) {
            try { $rutaReal = (Resolve-Path -LiteralPath $ruta).Path } catch { continue }
            if (-not $vistos.Add($rutaReal)) { continue }
            $jdk = Leer-Jdk $rutaReal
            if ($null -ne $jdk -and $jdk.Version -ge $VersionJavaMinima) { return $jdk }
        }
    }
    if (Test-Path -LiteralPath $DirectorioHerramientas) {
        foreach ($ruta in (Get-ChildItem -LiteralPath $DirectorioHerramientas -Directory -Filter 'temurin-21*' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)) {
            $jdk = Leer-Jdk $ruta
            if ($null -ne $jdk -and $jdk.Version -ge $VersionJavaMinima) { return $jdk }
        }
    }
    return $null
}

function Instalar-Temurin21 {
    $api = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    Write-Host 'Descargando metadatos oficiales de Eclipse Temurin 21…'
    $activos = Invoke-RestMethod -Uri $api -Method Get
    if (-not $activos -or -not $activos[0].binary.package.link -or -not $activos[0].binary.package.checksum) {
        throw 'La API de Adoptium no devolvió el paquete y su checksum.'
    }
    $paquete = $activos[0].binary.package
    $archivo = Join-Path $env:TEMP ('temurin-21-' + [guid]::NewGuid().ToString('N') + '.zip')
    $temporal = Join-Path $DirectorioHerramientas ('.temurin-' + [guid]::NewGuid().ToString('N'))
    $destino = Join-Path $DirectorioHerramientas ('temurin-' + $activos[0].version_data.semver)
    try {
        Invoke-WebRequest -Uri $paquete.link -OutFile $archivo -UseBasicParsing
        $hash = (Get-FileHash -LiteralPath $archivo -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $paquete.checksum.ToLowerInvariant()) {
            throw 'El checksum SHA-256 de Temurin no coincide. Se canceló la instalación.'
        }
        New-Item -ItemType Directory -Path $temporal -Force | Out-Null
        Expand-Archive -LiteralPath $archivo -DestinationPath $temporal -Force
        $raizJdk = Get-ChildItem -LiteralPath $temporal -Directory -Recurse |
            Where-Object { (Test-Path (Join-Path $_.FullName 'bin\java.exe')) -and (Test-Path (Join-Path $_.FullName 'bin\javac.exe')) } |
            Select-Object -First 1
        if (-not $raizJdk) { throw 'El archivo de Temurin no contiene un JDK de Windows válido.' }
        if (Test-Path -LiteralPath $destino) {
            $existente = Leer-Jdk $destino
            if ($existente -and $existente.Version -ge $VersionJavaMinima) { return $existente }
            Remove-Item -LiteralPath $destino -Recurse -Force
        }
        Move-Item -LiteralPath $raizJdk.FullName -Destination $destino
        $jdk = Leer-Jdk $destino
        if (-not $jdk -or $jdk.Version -lt $VersionJavaMinima) { throw 'No se pudo validar el JDK portable instalado.' }
        return $jdk
    }
    finally {
        Remove-Item -LiteralPath $archivo -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $temporal -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Leer-VersionMaven {
    param([string]$Ejecutable)
    if (-not $Ejecutable -or -not (Test-Path -LiteralPath $Ejecutable)) { return $null }
    $salida = (& $Ejecutable -version 2>&1 | Out-String)
    $coincidencia = [regex]::Match($salida, 'Apache Maven (?<version>\d+\.\d+\.\d+)')
    if (-not $coincidencia.Success) { return $null }
    try { return [version]$coincidencia.Groups['version'].Value } catch { return $null }
}

function Leer-VersionJavaMaven {
    param([string]$Ejecutable)
    if (-not $Ejecutable -or -not (Test-Path -LiteralPath $Ejecutable)) { return 0 }
    $salida = (& $Ejecutable -version 2>&1 | Out-String)
    $coincidencia = [regex]::Match($salida, 'Java version:\s*(?<version>\d+)')
    if (-not $coincidencia.Success) { return 0 }
    return [int]$coincidencia.Groups['version'].Value
}

function Instalar-Maven {
    $nombre = "apache-maven-$MavenVersionObjetivo-bin.zip"
    $archivo = Join-Path $env:TEMP ('maven-' + [guid]::NewGuid().ToString('N') + '.zip')
    $checksumArchivo = "$archivo.sha512"
    $temporal = Join-Path $DirectorioHerramientas ('.maven-' + [guid]::NewGuid().ToString('N'))
    $destino = Join-Path $DirectorioHerramientas "apache-maven-$MavenVersionObjetivo"
    $descargado = $false
    try {
        foreach ($base in @(
            "https://dlcdn.apache.org/maven/maven-3/$MavenVersionObjetivo/binaries",
            "https://archive.apache.org/dist/maven/maven-3/$MavenVersionObjetivo/binaries"
        )) {
            try {
                Invoke-WebRequest -Uri "$base/$nombre" -OutFile $archivo -UseBasicParsing
                Invoke-WebRequest -Uri "$base/$nombre.sha512" -OutFile $checksumArchivo -UseBasicParsing
                $descargado = $true
                break
            } catch {
                Remove-Item -LiteralPath $archivo,$checksumArchivo -Force -ErrorAction SilentlyContinue
            }
        }
        if (-not $descargado) { throw "No se pudo descargar Maven $MavenVersionObjetivo desde los mirrors oficiales." }
        $esperado = [regex]::Match((Get-Content -LiteralPath $checksumArchivo -Raw), '(?i)\b[0-9a-f]{128}\b').Value.ToLowerInvariant()
        $obtenido = (Get-FileHash -LiteralPath $archivo -Algorithm SHA512).Hash.ToLowerInvariant()
        if (-not $esperado -or $obtenido -ne $esperado) {
            throw 'El checksum SHA-512 de Maven no coincide. Se canceló la instalación.'
        }
        New-Item -ItemType Directory -Path $temporal -Force | Out-Null
        Expand-Archive -LiteralPath $archivo -DestinationPath $temporal -Force
        $mvn = Join-Path $temporal "apache-maven-$MavenVersionObjetivo\bin\mvn.cmd"
        if (-not (Test-Path -LiteralPath $mvn)) { throw 'El archivo de Maven no contiene mvn.cmd.' }
        if (Test-Path -LiteralPath $destino) { Remove-Item -LiteralPath $destino -Recurse -Force }
        Move-Item -LiteralPath (Join-Path $temporal "apache-maven-$MavenVersionObjetivo") -Destination $destino
        $instalado = Leer-VersionMaven (Join-Path $destino 'bin\mvn.cmd')
        if ($null -eq $instalado -or $instalado -lt [version]$MavenVersionObjetivo) {
            throw 'No se pudo validar Maven portable después de extraerlo.'
        }
        return (Join-Path $destino 'bin\mvn.cmd')
    }
    finally {
        Remove-Item -LiteralPath $archivo,$checksumArchivo -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $temporal -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Guardar-VariableUsuario {
    param([string]$Nombre, [string]$Valor)
    [Environment]::SetEnvironmentVariable($Nombre, $Valor, 'User')
    [Environment]::SetEnvironmentVariable($Nombre, $Valor, 'Process')
}

function Agregar-PathUsuario {
    param([string[]]$Rutas)
    $pathUsuario = [Environment]::GetEnvironmentVariable('Path', 'User')
    $entradas = @($pathUsuario -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    foreach ($ruta in $Rutas) {
        $coincide = $entradas | Where-Object { $_.TrimEnd([char]92) -ieq $ruta.TrimEnd([char]92) }
        if (-not $coincide) {
            $entradas = @($ruta) + $entradas
        }
        $pathActual = @($env:Path -split ';' | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_) -and $_.TrimEnd([char]92) -ine $ruta.TrimEnd([char]92)
        })
        $env:Path = (@($ruta) + $pathActual) -join ';'
    }
    [Environment]::SetEnvironmentVariable('Path', ($entradas -join ';'), 'User')
}

function Probar-Puerto {
    param([string]$HostDb, [int]$PuertoDb)
    $cliente = [System.Net.Sockets.TcpClient]::new()
    try {
        $resultado = $cliente.BeginConnect($HostDb, $PuertoDb, $null, $null)
        if (-not $resultado.AsyncWaitHandle.WaitOne(1500, $false)) { return $false }
        $cliente.EndConnect($resultado)
        return $cliente.Connected
    }
    catch { return $false }
    finally { $cliente.Dispose() }
}

Write-Host 'Preparación del entorno de desarrollo del servidor Mensajería Académica' -ForegroundColor Cyan
Write-Host "Alcance: JDK $VersionJavaMinima+, Maven 3.9.x y acceso TCP a MySQL."
Write-Host "Herramientas locales: $DirectorioHerramientas"
if ($SoloVerificar) { Write-Host 'Modo de solo verificación: no instalará ni cambiará variables.' -ForegroundColor Yellow }
if (-not $SoloVerificar) { New-Item -ItemType Directory -Path $DirectorioHerramientas -Force | Out-Null }

$HerramientasObligatoriasListas = $true

$jdk = Buscar-JdkValido
if ($jdk) {
    Escribir-Estado 'OK' 'JDK' "Java $($jdk.Version) con javac · $($jdk.Raiz)"
} elseif ($SoloVerificar) {
    Escribir-Estado 'FALTA' 'JDK' 'Se requiere JDK 21 o superior; vuelva a ejecutar sin -SoloVerificar.'
    $HerramientasObligatoriasListas = $false
} else {
    Write-Host 'Instalando JDK 21 portable para este usuario…' -ForegroundColor Yellow
    $jdk = Instalar-Temurin21
    Escribir-Estado 'INSTALADO' 'JDK' "$($jdk.Descripcion) · $($jdk.Raiz)"
}

if ($jdk -and -not $SoloVerificar) {
    $env:JAVA_HOME = $jdk.Raiz
    $env:Path = "$(Join-Path $jdk.Raiz 'bin');$env:Path"
}

$mavenComando = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenRuta = if ($mavenComando) { $mavenComando.Source } else { $null }
$mavenVersion = Leer-VersionMaven $mavenRuta
$mavenJava = Leer-VersionJavaMaven $mavenRuta
$mavenBin = $null
if ($mavenVersion -and $mavenVersion.Major -eq 3 -and $mavenVersion -ge [version]'3.9.0' -and $mavenJava -ge $VersionJavaMinima) {
    Escribir-Estado 'OK' 'Maven' "Apache Maven $mavenVersion · JDK $mavenJava · $mavenRuta"
} else {
    $mavenLocal = Join-Path $DirectorioHerramientas "apache-maven-$MavenVersionObjetivo\bin\mvn.cmd"
    $mavenLocalVersion = Leer-VersionMaven $mavenLocal
    $mavenLocalJava = Leer-VersionJavaMaven $mavenLocal
    if ($mavenLocalVersion -and $mavenLocalVersion -ge [version]'3.9.0' -and $mavenLocalVersion.Major -eq 3 -and $mavenLocalJava -ge $VersionJavaMinima) {
        $mavenRuta = $mavenLocal
        $mavenVersion = $mavenLocalVersion
        $mavenJava = $mavenLocalJava
        $mavenBin = Split-Path -Parent $mavenLocal
        Escribir-Estado 'OK' 'Maven' "Apache Maven $mavenVersion portable · JDK $mavenJava · $mavenLocal"
    } elseif ($SoloVerificar) {
        Escribir-Estado 'FALTA' 'Maven' 'Se requiere Maven 3.9.x ejecutándose con JDK 21+; vuelva a ejecutar sin -SoloVerificar.'
        $HerramientasObligatoriasListas = $false
    } else {
        Write-Host "Instalando Apache Maven $MavenVersionObjetivo portable para este usuario…" -ForegroundColor Yellow
        $mavenRuta = Instalar-Maven
        $mavenBin = Split-Path -Parent $mavenRuta
        $mavenVersion = Leer-VersionMaven $mavenRuta
        $mavenJava = Leer-VersionJavaMaven $mavenRuta
        Escribir-Estado 'INSTALADO' 'Maven' "Apache Maven $mavenVersion · JDK $mavenJava · $mavenRuta"
    }
}

if (-not $SoloVerificar) {
    if ($jdk) {
        Guardar-VariableUsuario 'JAVA_HOME' $jdk.Raiz
        Agregar-PathUsuario @((Join-Path $jdk.Raiz 'bin'))
    }
    if ($mavenBin) { Agregar-PathUsuario @($mavenBin) }
    if ($jdk -and $mavenRuta) {
        $env:JAVA_HOME = $jdk.Raiz
        if ($mavenBin -and -not (($env:Path -split ';') | Where-Object { $_.TrimEnd('\') -ieq $mavenBin.TrimEnd('\') })) {
            $env:Path = "$mavenBin;$env:Path"
        }
        Escribir-Estado 'LISTO' 'Sesión' 'JAVA_HOME y PATH de usuario preparados; cierre y abra terminales nuevas.'
    }
}

$docker = Get-Command docker.exe -ErrorAction SilentlyContinue
$dockerMotor = $false
if ($docker) {
    $versionCompose = (& $docker.Source compose version 2>&1 | Out-String).Trim()
    Escribir-Estado 'OK' 'Docker Compose' $(if ($LASTEXITCODE -eq 0) { $versionCompose } else { 'CLI sin complemento Compose v2.' })
    $null = (& $docker.Source info --format '{{.ServerVersion}}' 2>$null | Out-String)
    $dockerMotor = ($LASTEXITCODE -eq 0)
    if ($dockerMotor) {
        Escribir-Estado 'OK' 'Motor Docker' 'Disponible en la sesión del usuario.'
    } else {
        Escribir-Estado 'AVISO' 'Motor Docker' 'No está iniciado o este usuario no puede acceder. El script no eleva permisos.'
    }
} else {
    Escribir-Estado 'AVISO' 'Docker' 'No está instalado. Requiere instalación/activación autorizada de Docker Desktop y WSL.'
}

$hostDb = 'localhost'
$puertoDb = 3307
if (Test-Path -LiteralPath $PropiedadesServidor) {
    $lineaDb = Get-Content -LiteralPath $PropiedadesServidor | Where-Object { $_ -match '^db\.mysql\.url=' } | Select-Object -First 1
    if ($lineaDb -match 'jdbc:mysql://(?<host>[^:/?]+)(?::(?<puerto>\d+))?') {
        $hostDb = $Matches['host']
        if ($Matches['puerto']) { $puertoDb = [int]$Matches['puerto'] } else { $puertoDb = 3306 }
    }
}

$mysqlSolicitado = $false
if ($IniciarMySql) {
    if ($dockerMotor) {
        Push-Location $RaizProyecto
        try {
            Write-Host 'Iniciando el servicio MySQL del compose del proyecto…' -ForegroundColor Yellow
            & $docker.Source compose up -d mysql
            if ($LASTEXITCODE -ne 0) { throw 'docker compose up -d mysql terminó con error.' }
            $mysqlSolicitado = $true
        }
        finally { Pop-Location }
    } else {
        Escribir-Estado 'AVISO' 'MySQL' 'No se inició: primero se necesita un motor Docker accesible o una base externa.'
    }
}

if ($mysqlSolicitado) {
    Write-Host "Esperando disponibilidad TCP de MySQL en ${hostDb}:$puertoDb (hasta 60 segundos)…"
    $limite = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $limite -and -not (Probar-Puerto $hostDb $puertoDb)) {
        Start-Sleep -Seconds 2
    }
}

if (Probar-Puerto $hostDb $puertoDb) {
    Escribir-Estado 'OK' 'MySQL TCP' "Hay un servicio accesible en ${hostDb}:$puertoDb."
} else {
    Escribir-Estado 'AVISO' 'MySQL TCP' "Sin respuesta en ${hostDb}:$puertoDb. Inicie MySQL o configure una base externa."
}

Write-Host ''
Write-Host 'La automatización terminó. Docker, WSL, firewall y servicios de base de datos requieren que el equipo ya los tenga habilitados.'
Write-Host 'No se solicitaron permisos de administrador ni se modificaron variables de ámbito de máquina.'
if (-not $HerramientasObligatoriasListas) { exit 1 }
