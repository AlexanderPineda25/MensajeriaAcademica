# Despliegue por computador

El repositorio es el entorno de construcción. Para instalar el sistema, prepara una distribución por
proceso y lleva solo esa salida al computador que la ejecutará. No instales el monorepo completo en
cada PC.

## Topología y puertos

```text
Cliente JavaFX ─┐
Cliente Python ─┼── TCP/JSON → IP_SERVIDOR:5000 ── Servidor ── MySQL
Cliente WPF ────┘                                  ├── server/uploads/ (monorepo)
                                                   └── server/logs/ (monorepo)
```

| Conexión | Puerto predeterminado | Acceso requerido |
|---|---:|---|
| Clientes → servidor | `5000/TCP` | Entrada en firewall del servidor, limitada a la LAN |
| Servidor → MySQL de Docker de desarrollo | `3307/TCP` host; `3306` contenedor | Solo el equipo servidor |
| Servidor → MySQL instalado | Habitualmente `3306/TCP` | Solo el equipo servidor |
| PostgreSQL/Oracle opcionales | `5432/TCP` / `1521/TCP` | Solo si se habilita ese motor |

Los clientes nunca se conectan a MySQL. En una configuración remota, `localhost` es el mismo PC
del cliente; configura la IP LAN o DNS del equipo servidor. No se usa HTTP/REST.

## Flujo de mensajes

Cada cliente abre una conexión TCP al servidor y envía JSON UTF-8 precedido por una longitud de
4 bytes en orden big-endian. El servidor autentica la sesión, consulta el directorio y la presencia,
y enruta texto, imágenes, historial y acuses de entrega/lectura. El protocolo compartido documenta
los tipos y campos exactos en [PROTOCOLO.md](universidad-mensajeria/common-protocol/PROTOCOLO.md).

MySQL y la carpeta persistente `server/uploads/` pertenecen al servidor. H2/SQLite almacenan el historial
local de cada cliente y no se comparten entre computadores. La presencia es estado de sesión en
vivo del servidor. Para una imagen, el cliente envía sus bytes por TCP; el servidor guarda el
original y produce cinco derivados secuenciales: `01_grises.png`, `02_sepia.png`,
`03_giro180.png`, `04_brillo.png` y `05_reduccion.png`. El historial transmite metadatos, no bytes;
el cliente descarga la imagen bajo demanda con `archivoId`.

## Distribuciones

| PC | Copiar/instalar | Runtime |
|---|---|---|
| Servidor | `dist\MensajeriaServidor\` (JAR + lanzador + `config`/`uploads`/`logs`) | JRE 21 o superior (probado en 25) y MySQL accesible |
| Cliente JavaFX | `dist\MensajeriaClienteJava\` (JAR + `lib`/`javafx` + configuración + launcher) | JRE 21 o superior (probado en 25) |
| Cliente Python | `MensajeriaPython\` sin `.venv`, con `requirements.txt` y esquema local | Python 3.13 recomendado; crear venv e instalar requirements |
| Cliente .NET | `dist\MensajeriaWPF\` (salida de `dotnet publish`) | Windows; self-contained incluye runtime .NET |

Si el servidor imprime `sun.misc.Unsafe::allocateMemory` desde `com.sun.marlin.OffHeapArray`,
consulta la sección [Advertencia de JavaFX](universidad-mensajeria/server/servidor-app/README.md#advertencia-de-javafx-y-sunmiscunsafe).
La dependencia actual es JavaFX Graphics 21.0.6; la traza identifica una advertencia de su
renderizador. No confundas ese aviso con que el listener TCP haya fallado.

Los cuatro scripts que arman esas carpetas están en *Instaladores nativos (DIST-2)*,
más abajo.

Las carpetas fuente Java no son todas autónomas: el servidor y JavaFX se construyen desde el
reactor Maven porque usan módulos hermanos. Los README de componentes contienen comandos exactos de
construcción/publicación y copia:

- [Servidor y DBeaver](universidad-mensajeria/server/servidor-app/README.md)
- [Cliente JavaFX](universidad-mensajeria/client/README.md)
- [Cliente Python](universidad-mensajeria/client-python/README.md)
- [Cliente .NET/WPF](universidad-mensajeria/client-dotnet/README.md)

## Orden de instalación

1. Prepara MySQL y el usuario de servicio en el PC servidor. `docker-compose.yml` sirve para
   desarrollo; sus credenciales son públicas y no son aptas para producción.
2. Configura la URL/credenciales de base de datos, `red.puerto` y la ruta persistente de uploads;
   arranca el JAR. Las migraciones Flyway se aplican en el inicio.
3. Permite entrada a `5000/TCP` en el firewall privado del servidor. El servidor usa de forma
   predeterminada el puerto configurable `red.puerto=5000`.
4. Configura `host` y `puerto` en cada `client.properties`; usa IP LAN/DNS, no localhost.
5. Prueba desde cada cliente:

```powershell
Test-NetConnection 192.168.1.20 -Port 5000
```

Solo el servidor debe alcanzar la base de datos. No abras el puerto MySQL a la LAN de clientes.

## Rotación de secretos en producción (SEG-03)

Los properties traen defectos **solo de desarrollo** (`12345`, `CAMBIAR`). Con
`MENSAJERIA_PROD=true` el servidor **no arranca** si el motor activo o la clave
TLS siguen con esos valores. La comprobación ocurre al crear el `DataSource`
(`DataSourceAdapter`), no después: si se hiciera más tarde, MySQL rechazaría la
conexión y el único síntoma sería un `Access denied` sin pista de qué variable
fijar. El mensaje nombra la variable exacta:

```text
MENSAJERIA_PROD=true pero db.mysql.pass sigue siendo el default de desarrollo:
fije DB_MYSQL_PASS con la clave real y reinicie (ver DESPLIEGUE.md "Rotacion de secretos")
```

Rotar = cambiar el secreto en su dueño, reflejarlo en entorno y reiniciar; nunca
editar claves reales dentro del repo.

| Secreto | Propiedad | Variable de entorno |
|---|---|---|
| Clave MySQL | `db.mysql.pass` | `DB_MYSQL_PASS` |
| Clave PostgreSQL | `db.postgres.pass` | `DB_POSTGRES_PASS` |
| Clave Oracle | `db.oracle.pass` | `DB_ORACLE_PASS` |
| Clave del keystore TLS | `red.tls.clave` | `RED_TLS_CLAVE` |

Procedimiento (MySQL de ejemplo; análogo para los otros motores):

```powershell
Set-Location universidad-mensajeria
# 1. Cambia la clave en el dueño (MySQL): NUEVA en vez de la anterior.
docker compose exec -T mysql mysql -uroot -p12345 -e "ALTER USER 'root'@'%' IDENTIFIED BY 'NUEVA'; FLUSH PRIVILEGES;"
# 2. Refleja la clave en entorno (compose override, NO en el repo):
#    docker-compose.prod.yml con:
#    services:
#      servidor:
#        environment:
#          MENSAJERIA_PROD: "true"
#          DB_MYSQL_PASS: "NUEVA"
# 3. Reinicia solo el servidor (MySQL no necesita reinicio por cambio de clave):
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d servidor
docker compose logs --tail=50 servidor   # sin trazas de claves: solo nombres de variables
```

Keystore TLS (si `red.tls.habilitado=true`):

```powershell
keytool -genkeypair -alias mensajeria -keyalg RSA -keysize 2048 -storetype PKCS12 `
  -keystore config/keystore.p12 -validity 825 -storepass NUEVA -keypass NUEVA `
  -dname "CN=servidor-mensajeria"
# + RED_TLS_CLAVE=NUEVA en el override y reinicio del servidor como arriba.
```

Verificación tras rotar: `Test-NetConnection IP_SERVIDOR -Port 5000`, login
desde un cliente (la app no guarda claves: solo `host`/`puerto`), y un envío de
texto. Si el servidor no arranca, el log indica la variable exacta (`DB_*_PASS`
o `RED_TLS_CLAVE`). Recomendación: rotar claves de BD cada 90 días y el
keystore cada 825 días (validez del certificado generado arriba).

## Servidor en contenedor (Docker Compose)

Despliegue reproducible del servidor + MySQL desde `universidad-mensajeria/`
(Docker 29.x, Compose v2; no requiere Java ni Maven en el host):

```powershell
Set-Location universidad-mensajeria
docker compose build servidor   # construye mensajeria-servidor:1.0.0 (multi-stage Maven 3.9 + JDK 21 / JRE 21)
docker compose up -d mysql      # arranca MySQL y espera a que este sano
docker compose up -d servidor   # arranca el servidor (espera a mysql sano via depends_on)
docker compose ps               # verifica: mysql healthy, servidor healthy/running
docker compose logs -f servidor # sigue el arranque; Flyway migra al iniciar
```

Notas:

- El compose publica `5000/TCP` (mensajeria) y `3307` en el host (MySQL desarrollo).
  `5001` solo se mapea si se habilita TLS (`red.tls.habilitado=true` + keystore en `/app/config`).
- La configuracion del contenedor vive en `command:` del servicio `servidor`
  (`--db.mysql.url` apunta al host interno `mysql:3306`, `--red.puerto=5000`).
  Ver variables documentadas en el `Dockerfile` y `docker-compose.yml`.
- Persistencia en volumenes nombrados: `mysqldata` (BD), `uploads_data` (`/app/uploads`),
  `logs_data` (`/app/logs`). Sobreviven a `docker compose down` y a reconstrucciones.
  (Antes se usaban bind mounts `./server/uploads` y `./server/logs`; si vienes de esa
  version, copia el arbol a los volumenes una vez con `docker cp` antes de arrancar.)
- `docker compose config` debe pasar sin construir; es la validacion sintactica previa.
  En produccion el comando lleva el override y `--env-file`; sin `.env.prod` falla a
  proposito, lo que confirma que los secretos no estan en el compose versionado.

## Producción (DIST-01): compose con secretos

`docker-compose.prod.yml` es un override sobre el compose de desarrollo. Aplica lo que
este no puede: MySQL **sin puertos publicados** (solo red interna del compose),
nombres de contenedor propios para no chocar con el stack de desarrollo, secretos por
entorno y `MENSAJERIA_PROD=true` (el servidor no arranca con los defaults de ejemplo,
ver *Rotación de secretos*).

```powershell
Set-Location universidad-mensajeria
Copy-Item .env.prod.example .env.prod     # luego fija las claves de verdad
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod config
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod build servidor
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d mysql servidor
```

Sin `.env.prod` el `config` falla a propósito (`${DB_MYSQL_PASS:?...}`): es la
validación de que los secretos no viven en el repo. `.env.prod` está en `.gitignore`.

**Los volúmenes de producción tienen nombre propio** (`mensajeria-prod-mysqldata`,
`mensajeria-prod-uploads`, `mensajeria-prod-logs`), no `<proyecto>_*`. No es
estético: MySQL solo aplica `MYSQL_ROOT_PASSWORD` en el primer arranque del volumen,
así que si producción reutiliza el volumen de desarrollo queda inicializado con la
clave de ejemplo y el servidor entra en bucle de reinicio con
`Access denied for user 'root'@...`. Con volúmenes separados ambos stacks conviven.

## ACTUALIZACIÓN del servidor contenerizado (sin perder datos)

Los volumenes nombrados no se tocan al reconstruir la imagen. Para nueva version:

```powershell
Set-Location universidad-mensajeria
$envFile = "docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod"
cmd "$envFile up -d --build servidor"     # reconstruye la imagen y recrea el contenedor
cmd "$envFile logs -f --tail=50 servidor" # confirma arranque y migraciones Flyway
cmd "$envFile ps"                         # servidor healthy
```

- No uses `docker compose down -v`: `-v` borra los volumenes (BD + uploads + logs).
- `docker compose down` (sin `-v`) es seguro: conserva los volumenes.
- Flyway aplica migraciones nuevas al arrancar; no requiere paso manual de esquema.
- Para volver atras, fija el tag anterior en `image:` y repite `up -d servidor`.

Verificado extremo a extremo el 2026-10-05 (`up -d --build servidor` sobre el stack de
produccion): el contenedor se recrea y vuelve a `healthy`, `flyway_schema_history` sigue
en sus versiones, un usuario marcado en la BD sobrevive (la semilla CSV lo desactiva,
no lo borra: esto es el comportamiento correcto de `reconciliarUsuarios`), y el archivo
de prueba en `/app/uploads` sigue en su sitio.

## BACKUP / RESTORE

Qué respaldar (los tres son autoritativos y van juntos):

| Qué | Dónde (contenedor) | Volumen (prod) |
|---|---|---|
| Base MySQL (metadatos/rutas) | `mensajeria_mysql_prod` | `mensajeria-prod-mysqldata` |
| Bytes de imagen (original + 5 derivados) | `/app/uploads` | `mensajeria-prod-uploads` |
| Logs operativos | `/app/logs` | `mensajeria-prod-logs` |

En el stack de **desarrollo** los nombres son `<proyecto>_mysqldata`,
`<proyecto>_uploads_data` y `<proyecto>_logs_data`. Confirma siempre con
`docker volume ls` antes de respaldar: un `-v` a un volumen equivocado respalda
nada y no avisa.

Backup (en el host Docker, con los servicios en marcha). **Parar el servidor antes
de respaldar la BD**: un `mysqldump` sobre un servidor que sigue escribiendo puede
capturar una transacción a medias.

```powershell
Set-Location universidad-mensajeria
$fecha = Get-Date -Format 'yyyyMMdd-HHmm'
$clave = $env:DB_MYSQL_PASS     # la de .env.prod, no 12345
$destino = "$env:TEMP\backup-mensajeria"; New-Item -ItemType Directory -Force -Path $destino | Out-Null

docker stop mensajeria_servidor_prod
docker exec mensajeria_mysql_prod mysqldump -uroot "-p$clave" mensajeria_db > "$destino\bd-$fecha.sql"
docker run --rm -v "mensajeria-prod-uploads:/origen" -v "${destino}:/destino" alpine tar -czf "/destino/uploads-$fecha.tgz" -C /origen .
docker run --rm -v "mensajeria-prod-logs:/origen" -v "${destino}:/destino" alpine tar -czf "/destino/logs-$fecha.tgz" -C /origen .
docker start mensajeria_servidor_prod
```

`-p$clave` entrecomillado es obligatorio: sin comillas, el `!` de una clave fuerte se
expande en PowerShell y el `mysqldump` falla con clave truncada. `alpine` es una
imagen auxiliar que se descarga la primera vez; no necesitas `tar` en Windows.

Restore:

```powershell
Set-Location universidad-mensajeria
$clave = $env:DB_MYSQL_PASS
$destino = "$env:TEMP\backup-mensajeria"

docker stop mensajeria_servidor_prod
Get-Content "$destino\bd-$fecha.sql" -Raw | docker exec -i mensajeria_mysql_prod mysql -uroot "-p$clave" mensajeria_db
docker run --rm -v "mensajeria-prod-uploads:/destino" -v "${destino}:/origen" alpine sh -c "rm -rf /destino/* && tar -xzf /origen/uploads-$fecha.tgz -C /destino"
docker start mensajeria_servidor_prod
```

El `rm -rf /destino/*` es destructivo por diseño: es un restore completo del árbol de
uploads, no una fusión. Verificado extremo a extremo el 2026-10-05: tras el restore la
BD conserva el punto del dump (una fila insertada después desaparece, como debe ser) y
`uploads/prueba/h/original.txt` vuelve con su contenido.

Restaura BD y uploads del mismo punto: las rutas en MySQL son relativas a
`red.upload-dir` y deben coincidir con el arbol restaurado.

## Instaladores nativos (DIST-02)

Cada proceso tiene un script que arma su carpeta en `universidad-mensajeria/dist/`.
Los cuatro se ejecutan desde `universidad-mensajeria/` y son idempotentes: borran y
recrean su carpeta de salida. Ninguno copia el monorepo.

| PC | Script | Salida | Contenido |
|---|---|---|---|
| Servidor | `dist\distribuir-servidor.cmd` | `dist\MensajeriaServidor\` | `servidor.jar`, `start-server.cmd`, `config\`, `uploads\`, `logs\` vacías, `README.md`, `version.txt` |
| JavaFX | `client\dist-client.cmd` | `dist\MensajeriaClienteJava\` | `client-app.jar`, `lib\`, `javafx\`, `client.properties`, `launch-client.cmd` |
| Python | `client-python\distribuir.cmd` | `client-python\dist\MensajeriaPython\` (y copia centralizada en `dist\MensajeriaPython\` vía `empaquetar-todo.cmd`) | fuentes sin `.venv`/tests, `requirements.txt`, `store\schema-local.sql` |
| WPF | `dotnet publish` (abajo) | `dist\MensajeriaWPF\` | salida self-contained, `client.properties`, `schema-local.sql` |

```powershell
Set-Location universidad-mensajeria
cmd dist\distribuir-servidor.cmd
cmd client\dist-client.cmd
cmd client-python\distribuir.cmd
dotnet publish client-dotnet\Mensajeria.csproj -c Release -r win-x64 --self-contained true -o dist\MensajeriaWPF
```

Atajo maestro (ejecuta los 4 pasos anteriores y centraliza la salida Python en
`dist\`):

```powershell
Set-Location universidad-mensajeria
cmd /c empaquetar-todo.cmd
```

Esto deja las 4 carpetas listas en `universidad-mensajeria\dist\`:
`MensajeriaServidor\`, `MensajeriaClienteJava\`, `MensajeriaPython\` y
`MensajeriaWPF\`. Todos los scripts usan rutas relativas a su propia
ubicación (`%~dp0` / `$PSScriptRoot`): funcionan en cualquier PC sin editar
rutas absolutas.

Notas que ahorran tiempo en destino:

- Los dos scripts de Java usan `mvn install`, no `package`: `dependency:copy-dependencies`
  resuelve los módulos hermanos (`dto`, `codec`, `client-interfaces`) desde el repo local
  y con `package` no están ahí. Es un fallo que solo aparece en máquinas limpias.
- Los nativos JavaFX win (`*-win.jar`) se copian desde `lib\` (donde Maven ya los
  resolvió) a `javafx\`, que es el *module path* del launcher. El glob entrecomillado
  de `for` sobre `%USERPROFILE%\.m2` nunca expande comodines: no funciona.
- `distribuir.cmd` compila la distribución con el venv local y luego borra los
  `__pycache__` que `compileall` regenera.
- En destino: JRE 21+ para Java y el servidor; Python 3.13 + venv propio para Python;
  nada para WPF (self-contained).
- Actualizar = reemplazar solo los binarios. Conserva `client.properties`, la base local
  del cliente, MySQL, `uploads/` y `logs/`.

Cliente JavaFX (JRE 21 en destino): editar `host`/`puerto` (IP LAN, no localhost) y
arrancar con `launch-client.cmd`. Distribuir la carpeta completa; nunca solo el JAR.

Cliente Python: `py -3.13 -m venv .venv`, luego
`.venv\Scripts\python.exe -m pip install -r requirements.txt`, y arrancar con `main.py`.

Cliente WPF: ejecutar `Mensajeria.exe`; no hay nada que instalar.

## Reparto en 4 PCs (LAN, un proceso por máquina)

Cada máquina recibe **solo su carpeta**, nada del monorepo. `common-protocol`
no se copia: es código fuente que ya va empaquetado en los binarios Java, y
Python/C# llevan su propia implementación nativa del mismo contrato TCP/JSON.
Los clientes nunca se conectan a MySQL; solo el servidor la usa.

| # | Rol fijo | Carpeta que recibe | Requisitos en ese PC |
|---|---|---|---|
| PC1 | Servidor central | `dist\MensajeriaServidor\` | JRE 21+ y MySQL 8 accesible (o Docker) |
| PC2 | Cliente JavaFX | `dist\MensajeriaClienteJava\` | JRE 21+ |
| PC3 | Cliente Python | `dist\MensajeriaPython\` | Python 3.13 (el `.venv` se crea en destino) |
| PC4 | Cliente WPF | `dist\MensajeriaWPF\` | Ninguno (self-contained) |

Copiar cada carpeta por USB o red local. En destino no se edita ninguna ruta
absoluta: solo `host`/`puerto` con la IP LAN del servidor.

### PC1 — Servidor

1. Averigua su IP LAN:

   ```powershell
   ipconfig
   ```

   Anota la **Dirección IPv4** (por ejemplo `192.168.1.50`).
2. Permite la entrada solo desde la LAN privada (requiere PowerShell como
   administrador):

   ```powershell
   New-NetFirewallRule -DisplayName "Mensajeria Servidor" -Direction Inbound `
     -LocalPort 5000 -Protocol TCP -Action Allow -RemoteAddress LocalSubnet
   ```

3. Inicia MySQL. En desarrollo, desde `universidad-mensajeria/`:

   ```powershell
   Set-Location universidad-mensajeria
   docker compose up -d mysql
   ```

   En destino, MySQL 8 puede ser Docker o instalado directo. El host expone
   `3307` en desarrollo (`3306` dentro del contenedor).
4. Dentro de `MensajeriaServidor\`, arranca:

   ```powershell
   .\start-server.cmd
   ```

   Flyway migra solo. **No abras MySQL a la LAN.**

### PC2 — Cliente JavaFX

1. Edita `client.properties` (Bloc de notas):

   ```properties
   host=192.168.1.50
   puerto=5000
   ```

   Usa la IP real del PC1, nunca `localhost`.
2. Arranca con `launch-client.cmd` (lleva la carpeta completa, nunca solo el JAR).
3. Entra con una cuenta del CSV de semilla vigente
   (`server/servidor-app/src/main/resources/usuarios_iniciales.csv`).
   Reserva una cuenta para la prueba de 2 sesiones.

### PC3 — Cliente Python

1. Edita `client.properties` igual que en PC2.
2. Solo la primera vez, crea el entorno en esa carpeta (no se copia `.venv`
   entre PCs porque guarda rutas absolutas):

   ```powershell
   py -3.13 -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
   ```

   Sin launcher `py`, usa `python -m venv .venv`.
3. Arranca:

   ```powershell
   .\.venv\Scripts\python.exe main.py
   ```

### PC4 — Cliente WPF

1. Edita `client.properties` igual que en PC2.
2. Doble clic en `Mensajeria.exe`. No requiere instalación.

### Prueba cruzada mínima

1. Desde PC2, envía un texto a PC3. Debe llegar una sola vez y pasar a
   `ENTREGADO`.
2. Desde PC3, envía una imagen a PC4. Debe verse en PC4.
3. En PC1, comprueba `uploads\<Nombre Apellido [CODIGO]>\<hash>\`: original +
   5 derivadas.
4. Smoke completo por máquina en `GUIA_ACEPTACION.md` (matriz de 6 direcciones +
   sección 2026-10-04).

## Datos y mantenimiento

- MySQL y `server/uploads/` son persistencia autoritativa del servidor. Respalda ambos; la base conserva
  metadatos/rutas, mientras que los bytes de las imágenes están en disco.
- El servidor conserva el original y cinco derivados bajo
  `server/uploads/Nombre Apellido [CODIGO]/<SHA-256>/` en desarrollo. En la distribución aislada,
  la carpeta es `uploads/` junto al JAR.
- Cada cliente guarda H2/SQLite por cuenta local. No distribuyas bases, colas ni imágenes privadas
  dentro del paquete de instalación.
- Al actualizar, reemplaza solo los binarios de programa. Conserva configuración, DBs locales,
  MySQL, uploads e imágenes de usuario.

## Documentación restante

- [Pruebas y aceptación](GUIA_ACEPTACION.md)
- [Requerimientos funcionales](REQUERIMIENTOS.md)
- [Arquitectura](PLAN.md)
- [Estado y backlog](ESTADO_ROADMAP.md)
- [Contrato TCP/JSON](universidad-mensajeria/common-protocol/PROTOCOLO.md)
- [Persistencia local](universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md)
