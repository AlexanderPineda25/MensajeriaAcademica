# Preparar cualquier computador para que el programa funcione

Actualizado: **2026-10-07**. Guía de **preparación de entorno**, desde un Windows limpio hasta
"compilo y los 4 procesos arrancan". Todos los scripts usan rutas relativas a su
propia carpeta: sirven en cualquier PC sin editar rutas absolutas.

> **Qué NO es esta guía.** No es cómo desplegar: eso es [`DESPLIEGUE.md`](DESPLIEGUE.md).
> Esta es la respuesta a "¿qué instalo y cómo lo dejo actualizado?".

## 1. Qué necesita cada computador

El sistema son **4 procesos independientes**. Cada PC instala **solo lo suyo**; el monorepo se
construye **una vez** y se distribuyen las salidas.

| PC | Proceso | Necesita |
|---|---|---|
| **Servidor** | `servidor.jar` | **JDK/JRE 21+** · MySQL 8 accesible · puerto `5000` abierto |
| **Cliente JavaFX** | `client-app.jar` | **JRE 21+** · nada más |
| **Cliente Python** | `main.py` | **Python 3.13** + entorno virtual propio |
| **Cliente WPF** | `Mensajeria.exe` | **Nada**: la publicación es *self-contained* |

Solo el **PC de desarrollo** (el que tiene el repo) necesita el toolchain completo:

| Herramienta | Versión mínima | Verificada en el repo |
|---|---|---|
| **JDK** | **21** (`maven.compiler.release=21`) | 21 y 25 |
| **Maven** | 3.9 | 3.9.16 |
| **Docker Desktop** | Compose v2 | 29.x |
| **Python** | 3.13 | 3.13.14 |
| **.NET SDK 8** | para compilar el cliente WPF | `net8.0-windows` |
| **.NET SDK 10** | **solo** para correr los tests de .NET | `net10.0` |
| **Node.js** | opcional, **solo** para renderizar diagramas | — |

> ⚠️ **El único requisito duro es el JDK 21+.** Todo lo demás se puede evitar en destino porque
> las distribuciones ya son autocontenidas.

## 2. Paso 0 — Comprobar qué falta

Para el servidor, el script [`preparar-entorno-servidor.ps1`](universidad-mensajeria/herramientas/preparar-entorno-servidor.ps1)
automatiza la revisión e instalación portable de JDK 21+ y Maven 3.9.x en el perfil del usuario,
sin permisos de administrador. También detecta Docker/Compose y la conectividad a MySQL. Consulta
[AUTOMATIZACION_ENTORNO.md](AUTOMATIZACION_ENTORNO.md) para los comandos, checksums y límites de
permisos; Docker/WSL no se instalan automáticamente.

Ejecuta esto **antes** de instalar nada. Es de solo lectura: no modifica el equipo.

```powershell
function Falta($ok, $que, $como) {
  "{0,-6} {1,-22} {2}" -f $(if ($ok) { "[OK]" } else { "FALTA" }), $que, $como
}
# `cmd /c ... 2>&1` porque java -version y mvn -v escriben en stderr:
# con la redireccion de PowerShell salen como error rojo aunque la lectura sea correcta.
function Ver($cmd) { (cmd /c "$cmd 2>&1" | Select-Object -First 1) }

"=== 1. Java (debe ser 21 o superior) ==="
$jv = Ver 'java -version'
$jv
$num = if ($jv -match 'version "(\d+)') { [int]$Matches[1] } else { 0 }
Falta ($num -ge 21) "Java >= 21" "SALTAR AL PASO 0"
"JAVA_HOME = $env:JAVA_HOME"
"resuelto en: $((Get-Command java -ErrorAction SilentlyContinue).Source)"

"`n=== 2. Maven 3.9+ ==="
$mv = Ver 'mvn -v'
$mv
Falta ($mv -match 'Apache Maven 3\.9') "Maven 3.9" "Paso 1"
if ($mv -match 'Apache Maven') { (cmd /c "mvn -v 2>&1" | Select-String 'Java version') }

"`n=== 3. Docker (para MySQL) ==="
Ver 'docker --version'
Falta ([bool](Get-Command docker -ErrorAction SilentlyContinue)) "Docker Desktop" "Paso 2"

"`n=== 4. Python 3.13 ==="
Ver 'python --version'
if (Get-Command py -ErrorAction SilentlyContinue) { "launcher 'py' disponible" }
else { "sin launcher 'py': usa 'python' directamente" }

"`n=== 5. .NET (solo si compilas o testeas el cliente WPF) ==="
if (Get-Command dotnet -ErrorAction SilentlyContinue) { Ver 'dotnet --version'; cmd /c "dotnet --list-sdks 2>&1" }
else { "no esta en PATH (Paso 4)" }

"`n=== 6. Node (solo diagramas) ==="
if (Get-Command node -ErrorAction SilentlyContinue) { Ver 'node --version' }
else { "no esta en PATH (opcional, Paso 5)" }
```

**Cómo leerlo:** si la línea de Java no dice 21 o más, **todo lo demás que hagas va a fallar**.
Empieza por el Paso 0. El bloque también avisa si Maven está corriendo sobre un Java antiguo, que
es el fallo más desconcertante: Maven instalado y Java instalado, y aun así no compila.

## 3. Paso 0 — Instalar o actualizar Java (el paso crítico)

### 3.1 Instalar el JDK 21+

Abre PowerShell **normal** (sin administrador: `winget` instala por usuario si hace falta).

```powershell
# Opción A — Eclipse Temurin 21 LTS (recomendada: build abierta, updates de seguridad largos)
winget install --id EclipseAdoptium.Temurin.21.JDK --source winget --exact

# Opción B — Build de OpenJDK de Microsoft 21
winget install --id Microsoft.OpenJDK.21 --source winget --exact

# Opción C — Temurin 25 (la version con la que se valido el proyecto)
winget install --id EclipseAdoptium.Temurin.25.JDK --source winget --exact
```

Comprueba dónde quedó:

```powershell
Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -ErrorAction SilentlyContinue | Select-Object Name
```

Deja anotada la ruta exacta del JDK (en Temurin 21 es algo como
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`). La necesitas en el siguiente paso.

> Si tienes Java 17 instalado y quieres **quitarlo** para no equivocarte (Panel de control →
> *Programas* → desinstala el JDK 17). No es obligatorio, pero evita el error del §3.3.

### 3.2 Fijar `JAVA_HOME`

**Reglas que no se pueden romper:**

| Regla | Por qué |
|---|---|
| Sin `\bin` al final | Con `\bin`, Maven busca `JAVA_HOME\bin\bin\javac` y falla |
| Sin `\` al final | Alguns herramientas construyen `JAVA_HOME\...` y queda `C:\jdk-21\` |
| Debe apuntar al **JDK**, no al JRE | Un JRE no trae `javac` y no compila |

```powershell
# Sustituye la ruta por la que copiaste en 3.1
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'

# Comprobacion antes de fijarlo: esto DEBE existir
if (-not (Test-Path "$jdk\bin\javac.exe")) { "RUTA INCORRECTA: no hay javac.exe" } else { "ruta valida" }

[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'Machine')   # todo el equipo
# Si no eres administrador, quita ',Machine' y usa 'User'
```

> 🚫 **Nunca uses `setx PATH ...`.** `setx` **trunca el valor a 1024 caracteres**: si tu `PATH` es
> largo, te lo deja cortado y el equipo deja de encontrar `java`, `mvn`, `git`… El error aparece
> días después y cuesta creer que fue esto. Para variables normales `setx` sí vale; para `PATH`,
> usa `[Environment]::SetEnvironmentVariable` o el diálogo de Windows.

### 3.3 La trampa del `javapath` de Oracle

En muchos equipos con Java de Oracle, `java` **no** apunta al JDK instalado sino a un shim:

```text
C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
```

Ese shim tiene **prioridad en el `PATH`** y puede seguir apuntando a Java 17 aunque acabes de
instalar el 21. Por eso `java -version` puede seguir diciendo 17 con el JDK 21 instalado y
correctamente configurado.

**Compruébalo siempre:**

```powershell
(Get-Command java).Source
```

Si sale `...\Common Files\Oracle\Java\javapath\java.exe`, tienes dos salidas:

```powershell
# Salida A — usar la ruta explicita (sin tocar nada del sistema)
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version

# Salida B — arreglar el PATH de verdad
# 1) Panel de control -> Sistema -> Configuracion avanzada -> Variables de entorno
# 2) En "Variables de sistema", SUBE  %JAVA_HOME%\bin  por encima de
#    C:\Program Files\Common Files\Oracle\Java\javapath
# 3) Cierra y abre la terminal de nuevo
```

En este repositorio ya se pasó: `java -version` reportaba **17.0.11** con `JAVA_HOME` vacío,
mientras el proyecto exige 21. Si te pasa, no es que el build esté mal.

#### 3.3.1 El síntoma cuando ejecutas el JAR compilado

El shim no solo rompe el `mvn`. Si ya construiste y quieres arrancar el servidor con el JAR,
falla así:

```text
PS C:\...\universidad-mensajeria> java -jar server\servidor-app\target\servidor-app-1.0.0-SNAPSHOT.jar
Exception in thread "main" java.lang.UnsupportedClassVersionError:
  universidad/mensajeria/server/ServerApplication has been compiled by a more recent version of
  the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes
  class file versions up to 61.0
```

Los números son el diagnóstico completo: **65.0 = Java 21**, **61.0 = Java 17**. El JAR está bien
compilado; lo que se ejecutó fue el runtime equivocado. Con el JDK correcto, el mismo JAR arranca
sin tocar una línea de código.

Solución en la sesión actual, sin tocar nada del sistema:

```powershell
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version   # ahora debe decir 21
java -jar server\servidor-app\target\servidor-app-1.0.0-SNAPSHOT.jar
```

Alternativa equivalente, sin reordenar nada:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar server\servidor-app\target\servidor-app-1.0.0-SNAPSHOT.jar
```

> Los dos encabezados de la salida B de más arriba hacen falta, pero **el diálogo de Variables de
> entorno abre el `PATH` de sistema**, y ahí solo tienes permisos si eres administrador. Si no los
> tienes, la salida A es tu camino.

#### 3.3.2 Por qué `preparar-entorno-servidor.ps1` no lo resuelve

El script del repositorio (`universidad-mensajeria/herramientas/preparar-entorno-servidor.ps1`)
escribe `JAVA_HOME` y añade el JDK al `PATH` **de usuario**. Eso no basta contra `javapath`, y no
por un fallo del script sino por cómo compone Windows el `PATH`:

```text
PATH final = PATH de MAQUINA ; PATH de USUARIO
             ^ C:\...\Oracle\Java\javapath   <-- gana siempre
                                         ^ C:\...\tools\jdk\jdk-21...\bin  <-- llega tarde
```

El `PATH` de usuario se **añade al final**, así que nunca puede adelantar a una entrada de sistema.
Consecuencia práctica: aun siguiendo el script y cerrando la terminal, `java -version` puede seguir
diciendo 17. Las tres salidas, de menos a más invasivas:

1. `& "$env:JAVA_HOME\bin\java.exe" ...` en cada invocación.
2. `$env:Path = "$env:JAVA_HOME\bin;$env:Path"` al abrir la terminal (solo esa sesión).
3. Con permisos de administrador, mover `%JAVA_HOME%\bin` por encima de `javapath` en el `PATH`
   de **sistema**, o desinstalar el JDK 17 de Oracle (nota al final de §3.1).

Para comprobar en cuál de los tres casos estás:

```powershell
(Get-Command java).Source          # si sale ...\Oracle\Java\javapath\ -> caso 3 pendiente
java -version                      # 17 = el shim sigue ganando
& "$env:JAVA_HOME\bin\java.exe" -version   # 21 = el JDK está bien, el PATH no
```

### 3.4 Verificar (las tres líneas deben decir 21 o más)

```powershell
# 1) La terminal DEBE haberse cerrado y vuelto a abrir tras cambiar variables
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

### 3.5 Errores de Java y qué significan

| Error | Causa real | Solución |
|---|---|---|
| `error: release version 21 not supported` | El compilador activo es < 21 | `JAVA_HOME` apunta a un JDK 17, o el shim `javapath` gana. §3.2 y §3.3 |
| `Fatal error compiling: invalid target release: 21` | Igual que el anterior, visto por Maven | Lo mismo. Confirma con `mvn -v`: su línea *"Java version"* debe decir 21 |
| `JAVA_HOME is set to an invalid directory` | Tiene `\bin`, `\` final, o no existe | §3.2 |
| `Could not find or load main class` al arrancar | Falta el *module path* de JavaFX | Usa `launch-client.cmd` de la distribución, no `java -jar` |
| `mvn` corre con 17 aunque `java` dice 21 | Maven toma `JAVA_HOME`, no el `PATH` | `mvn -v` y fija `JAVA_HOME` |
| `UnsupportedClassVersionError` al hacer `java -jar` (`class file version 65.0` vs `up to 61.0`) | El JAR es de Java 21 pero se ejecuta con el runtime 17 del shim `javapath` | §3.3.1 y §3.3.2. El build no está mal |
| `Error: Unable to access jarfile ...` | Estás en la raíz del repo, no en `universidad-mensajeria\` | `Set-Location universidad-mensajeria` y repite la ruta relativa |

## 4. Paso 1 — Maven

**No hay paquete de Maven en `winget`** (comprobado: la búsqueda solo devuelve utilidades de
terceros). Instálalo desde el binario, que además **no necesita administrador**.

```powershell
$ErrorActionPreference = 'Stop'
$url  = 'https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'
$dest = "$env:USERPROFILE\tools"

New-Item -ItemType Directory -Force -Path $dest | Out-Null
Invoke-WebRequest -Uri $url -OutFile "$env:TEMP\maven.zip"
Expand-Archive -Path "$env:TEMP\maven.zip" -DestinationPath $dest -Force
Remove-Item "$env:TEMP\maven.zip"

# Anadir al PATH del usuario (SIN truncar, ver aviso del §3.2)
$bin = "$dest\apache-maven-3.9.16\bin"
$actual = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($actual -notlike "*$bin*") {
  [Environment]::SetEnvironmentVariable('Path', "$actual;$bin", 'User')
}
```

> Si `dlcdn.apache.org` no responde (es un mirror que rota), usa el archivo histórico:
> `https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip`

Abre una terminal nueva y comprueba — **las dos versiones deben ser 21+**:

```powershell
mvn -v
```

Salida esperada:

```text
Apache Maven 3.9.16 (...)
Java version: 21.0.x, vendor: Eclipse Adoptium
```

Si la línea de Java dice 17, vuelve al §3.

## 5. Paso 2 — Docker Desktop y MySQL

```powershell
winget install --id Docker.DockerDesktop --source winget --exact
```

**Reinicia Windows** tras instalarlo (Docker lo pide) y **abre Docker Desktop una vez** y espera a
que diga *Engine running*. Si no arrancas Docker Desktop, `docker` responde pero nada funciona.

MySQL no se instala: es el servicio del `compose` del repositorio.

```powershell
Set-Location universidad-mensajeria
docker compose up -d mysql
docker compose ps          # mysql debe estar "healthy"
```

MySQL de desarrollo queda en **`localhost:3307`** en el host (no en 3306: el 3306 es el del
contenedor). Usuario `root`, clave `12345`. Son **credenciales públicas de desarrollo**: con
`MENSAJERIA_PROD=true` el servidor se niega a arrancar con ellas
(ver *Rotación de secretos* en [`DESPLIEGUE.md`](DESPLIEGUE.md)).

## 6. Paso 3 — Python 3.13 y el entorno virtual

```powershell
winget install --id Python.Python.3.13 --source winget --exact
```

Luego, **dentro de `client-python`**:

```powershell
Set-Location universidad-mensajeria\client-python

# Con launcher 'py' (instalador oficial de python.org):
py -3.13 -m venv .venv

# Sin launcher 'py' (Store de Windows o donde ya tuvieras Python):
python -m venv .venv

.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe main.py
```

> ⚠️ **Nunca copies `.venv` entre computadores.** Contiene la ruta absoluta del Python que lo creó.
> En otro equipo está apuntando a una carpeta que no existe y falla con un error de intérprete
> críptico. En cada máquina se crea de nuevo. Por eso el `.gitignore` lo excluye.
>
> requirements.txt fija: `pillow==10.4.0`, `PySide6==6.11.2`, `PySide6-Fluent-Widgets==1.11.3`.

## 7. Paso 4 — .NET (solo si compilas o testeas el cliente WPF)

**Para usar el cliente WPF ya publicado no hace falta nada** (es *self-contained*). Solo si
quieres recompilarlo:

```powershell
winget install --id Microsoft.DotNet.SDK.8 --source winget --exact    # compila la app (net8.0-windows)
winget install --id Microsoft.DotNet.SDK.10 --source winget --exact   # corre los tests (net10.0)
dotnet --list-sdks
```

> **Los dos SDK son necesarios y por motivos distintos:** la app apunta a `net8.0-windows` y los
> tests a `net10.0`. Con solo el 8, `dotnet test` falla por *framework no encontrado*.

**WPF es Windowsonly.** En Linux o macOS no se puede ni compilar ni ejecutar; ahí solo funcionan
los clientes JavaFX, Python y el servidor.

## 8. Paso 5 — Node (opcional, solo diagramas)

Solo si vas a renderizar los `.mmd` de `DIAGRAMAS/`. Sin esto, los diagramas se quedan en fuente
sin PNG.

```powershell
winget install --id OpenJS.NodeJS.LTS --source winget --exact
npm i -g @mermaid-js/mermaid-cli     # aporta el comando mmdc
```

## 9. Construir el proyecto

Desde `universidad-mensajeria/` (el **monorepo**; no es la carpeta que se instala):

```powershell
Set-Location universidad-mensajeria

# MySQL arriba, o los tests de servidor fallarán a propósito
docker compose up -d mysql

# Servidor + cliente JavaFX
mvn -pl server/servidor-app,client/client-app -am test

# Solo el cliente JavaFX
mvn -pl client/client-app -am test

# Python
Set-Location client-python
.\.venv\Scripts\python.exe -m pytest tests
Set-Location ..

# .NET (requiere SDK 10)
dotnet test client-dotnet.Tests\Mensajeria.Tests.csproj
dotnet build client-dotnet\Mensajeria.csproj -c Release
```

El `-am` (**also-make**) es obligatorio: compila los módulos hermanos (`common-protocol`,
`client-interfaces`) que el servidor y el cliente necesitan.

Para **arrancar** el JAR recién construido desde el monorepo, la ruta es relativa a
`universidad-mensajeria\`:

```powershell
java -jar server\servidor-app\target\servidor-app-1.0.0-SNAPSHOT.jar
```

Dos fallos frecuentes aquí, ambos resueltos en §3.3: `Unable to access jarfile` si se ejecuta desde
la raíz del repositorio, y `UnsupportedClassVersionError` si el `java` activo es el 17 del shim
`javapath`. Este arranque es solo de desarrollo; en el equipo destino se usa el lanzador de la
distribución (§11).

## 10. Generar las 4 distribuciones

Cada proceso recibe **una carpeta**, nunca el monorepo:

```powershell
Set-Location universidad-mensajeria

cmd dist\distribuir-servidor.cmd      # -> dist\MensajeriaServidor\
cmd client\dist-client.cmd            # -> dist\MensajeriaClienteJava\
cmd client-python\distribuir.cmd      # -> client-python\dist\MensajeriaPython\
dotnet publish client-dotnet\Mensajeria.csproj -c Release -r win-x64 --self-contained true -o dist\MensajeriaWPF
```

Los dos scripts de Java usan `mvn install`, **no `package`**: `dependency:copy-dependencies`
resuelve los módulos hermanos desde el repositorio local, y con `package` no están ahí. Es un
fallo que solo aparece en máquinas limpias.

## 11. Checklist por computador destino

Copia **solo** su carpeta. Luego:

| PC | Acción | Verificación |
|---|---|---|
| Servidor | Edita la configuración de base de datos; arranca `start-server.cmd` | El log muestra Flyway migrando y el puerto abierto |
| Servidor | Firewall: **entrada** `TCP 5000`, perfil **privado**, `RemoteAddress = LocalSubnet` | `Test-NetConnection <IP_servidor> -Port 5000` desde un cliente |
| Todos | Edita `host` y `puerto` en `client.properties` con la **IP LAN o DNS** del servidor | Mismo test |
| JavaFX | Doble clic en `launch-client.cmd` | Abre la ventana de login |
| Python | `python -m venv .venv` → `pip install -r requirements.txt` → `python main.py` | Abre la ventana de login |
| WPF | Doble clic en `Mensajeria.exe` | Abre la ventana de login |

> 🚫 **`localhost` no sirve como dirección de servidor** en un cliente que está en otro equipo:
> apunta al propio PC del cliente, no al servidor. Usa la IP LAN.
>
> 🚫 **No abras MySQL a la LAN.** Solo el servidor necesita llegar a la base.

## 12. Verificación final

En el PC de desarrollo, antes de declarar el entorno bueno:

```powershell
# 1. Java correcto
mvn -v                                   # "Java version: 21" o superior

# 2. Build completo en verde
Set-Location universidad-mensajeria
mvn -pl server/servidor-app,client/client-app -am test

# 3. MySQL arriba
docker compose ps                        # mysql healthy

# 4. Los 4 artefactos de distribucion existen
'.\dist\MensajeriaServidor\servidor.jar',
'.\dist\MensajeriaClienteJava\client-app.jar',
'.\client-python\dist\MensajeriaPython\requirements.txt',
'.\dist\MensajeriaWPF\Mensajeria.exe' | ForEach-Object { "{0,-6} {1}" -f (Test-Path $_), $_ }

# 5. Conectividad real cliente -> servidor
Test-NetConnection <IP_servidor> -Port 5000
```

Los cuatro del punto 4 deben decir `True`. Si alguno dice `False`, vuelve a ejecutar el §10.

## 13. Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `release version 21 not supported` | JDK activo < 21 | §3.2 y §3.3 |
| `java -version` dice 17 con el 21 instalado | Shim `javapath` de Oracle gana el `PATH` | §3.3 |
| `UnsupportedClassVersionError` al arrancar el servidor con `java -jar` | Se ejecuta el runtime 17 sobre un JAR de Java 21 | §3.3.1 y §3.3.2 |
| `Error: Unable to access jarfile server/...` | Ruta relativa ejecutada desde la raíz del repo | `Set-Location universidad-mensajeria` |
| El servidor imprime `sun.misc.Unsafe::allocateMemory` desde `com.sun.marlin.OffHeapArray` | JavaFX Graphics 21.0.6 del servidor usa ese método interno; es una advertencia de la dependencia de renderizado, no un error de compilación | El proceso puede seguir funcionando. Opciones y cambios de compatibilidad en [README del servidor](universidad-mensajeria/server/servidor-app/README.md#advertencia-de-javafx-y-sunmiscunsafe) |
| `mvn` no se reconoce tras instalar | Terminal abierta antes de cambiar el `PATH` | Cierra y abre la terminal |
| El `PATH` dejó de funcionar | Alguien usó `setx PATH` (trunca a 1024) | Restaura el `PATH` desde *Variables de entorno* |
| `mvn -v` dice Java 17 | Maven usa `JAVA_HOME`, no el `PATH` | Corrige `JAVA_HOME` |
| Tests de servidor fallan con error de conexión | MySQL no está arriba | `docker compose up -d mysql` |
| `py` no se reconoce | El launcher no está instalado | Usa `python -m venv .venv` |
| El cliente Python falla al arrancar con error de intérprete | `.venv` copiado de otro equipo | Borra `.venv` y créalo de nuevo |
| `dotnet test` dice que falta el framework | Solo instalaste el SDK 8 | Instala también el SDK 10 |
| `Could not find or load main class` (JavaFX) | Arrancaste el JAR suelto | Usa `launch-client.cmd` con su carpeta completa |
| `Access denied for user 'root'` | Volumen de producción con la clave de ejemplo | Volúmenes con nombre propio; ver [`DESPLIEGUE.md`](DESPLIEGUE.md) |
| El cliente conecta y se desconecta solo | Firewall bloquea `5000` o `localhost` en `client.properties` | §11 |

## 14. Qué no se copia entre computers

| No copiar | Por qué |
|---|---|
| `.venv/` | Rutas absolutas del Python que lo creó. Se recrea en destino |
| `target/`, `bin/`, `obj/` | Artefactos de compilación. Se regeneran |
| `*.db`, `*.db-shm`, `*.db-wal` | Bases locales del cliente, con datos del usuario anterior |
| `uploads/`, `logs/` | Son del **servidor**, no del cliente |
| `*.p12`, `*.pfx`, `*.jks`, `.env.prod` | **Secretos.** Nunca se distribuyen en un paquete de cliente |
| El monorepo completo | Solo va en el PC de desarrollo |

Para actualizar un cliente: **reemplaza solo los binarios**. Conserva `client.properties` y la base
local del usuario.

## 15. Referencias

- [`DESPLIEGUE.md`](DESPLIEGUE.md) — despliegue por computador, backups, rotación de secretos.
- [`GUIA_ACEPTACION.md`](GUIA_ACEPTACION.md) — pruebas y matriz manual.
- [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md) — qué falta y en qué orden.
- Guías por componente: [servidor](universidad-mensajeria/server/servidor-app/README.md) ·
  [JavaFX](universidad-mensajeria/client/README.md) ·
  [Python](universidad-mensajeria/client-python/README.md) ·
  [.NET/WPF](universidad-mensajeria/client-dotnet/README.md)
