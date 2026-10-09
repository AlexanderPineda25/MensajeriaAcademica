# AGENTS.md — guía de orientación rápida

> Para cualquier agente de IA o persona nueva. Objetivo: entender cómo funciona el código en
> **5 minutos**. Si con esto no basta, el índice está en §8. El estado vigente y pendientes están en
> `ESTADO_ROADMAP.md`; este archivo no registra ejecuciones ni historial.

## 1. Qué es esto, en 4 líneas

Aplicación de escritorio **cliente/servidor** para mensajería académica. Un servidor central en
**Java** y **tres clientes intercambiables** en **JavaFX**, **PySide6** y **WPF**, que se hablan por
**TCP + JSON** (no HTTP).

Contrato de red en una línea: **cabecera de 4 bytes big-endian con la longitud, seguida de JSON
UTF-8** (`universidad-mensajeria/common-protocol/PROTOCOLO.md`, `TramaCodec`).

## 2. Mapa del repositorio en 30 segundos

```
<raíz>/
├── AGENTS.md, README.md, REQUERIMIENTOS.md (QUÉ, RF-*), PLAN.md (CÓMO), ESTADO_ROADMAP.md (qué falta)
├── ANALISIS_TECNICO.md (auditoría histórica), PLAN_PATRONES_UI.md, PLAN_PARIDAD_CLIENTES.md
├── PLAN_ARQUITECTURA_CLIENTE_JAVA.md, GUIA_ACEPTACION.md, DESPLIEGUE.md, INSTALACION.md
├── contrato/          ← fuente ÚNICA de lo compartido: tokens JSON + generadores y verificadores
├── DIAGRAMAS/         ← fuentes/renders canónicos (ver DIAGRAMAS/README.md); incluye legados migrados
├── docs/archivo/      ← histórico, no normativo
└── universidad-mensajeria/ ← EL MONOREPO (solo aquí se compila; java.version=21)
    ├── pom.xml, Dockerfile, docker-compose.yml, empaquetar-todo.cmd
    ├── common-protocol/ dto + codec (sin Spring, sin UI; TramaCodec + schema-local.sql canónico)
    ├── server/  12 módulos: 5 int-* (puertos, sin Spring) + 5 comp-* (adaptadores) + utilerias + servidor-app
    ├── client/  client-interfaces + client-app (JavaFX)
    ├── client-python/ (PySide6 + qfluentwidgets, SQLite)
    ├── client-dotnet/ (WPF .NET 8 + WPF-UI, SQLite)
    └── client-dotnet.Tests/ (xUnit; OJO: apunta a net10.0, la app es net8.0-windows)
```

Total Maven: 19 módulos (3 raíz + 2 common-protocol + 12 server + 2 client) + POM padre.

**Dato clave:** `int-*` y `comp-*` **no usan Spring**. El único módulo con Spring es `servidor-app`,
que compila **solo contra los `int-*`** (los `comp-*` son `<scope>runtime</scope>`); los adaptadores
se descubren con `ServiceLoader` vía `META-INF/services`. Los 5 `Proveedor*` parecen sin uso por eso.

## 3. Los 4 procesos y cómo se hablan

| Proceso | Entry point | UI / BD local |
|---|---|---|
| Servidor | `server/servidor-app/.../server/ServerApplication.java` | JLine + JavaFX 21.0.6 (admin) / MySQL 8.4 (Postgres 16 / Oracle opcionales) |
| Cliente JavaFX | `client/client-app/.../cliente/ClienteMain.java` | JavaFX 22.0.2 + AtlantaFX 2.1.0 (FXML+CSS) / H2 por cuenta |
| Cliente Python | `client-python/main.py` | PySide6 + PySide6-Fluent-Widgets / SQLite por cuenta |
| Cliente WPF | `client-dotnet/App.xaml.cs` | WPF .NET 8 + WPF-UI / SQLite por cuenta |

```
JavaFX ─┐
Python ─┼── TCP/JSON ──▶ IP_SERVIDOR:5000 (TLS: 5001) ──▶ Servidor ──▶ MySQL
WPF   ──┘                                                      ├── uploads/<nombre [código]>/<hash>/
                                                               └── logs/
```

Puertos TCP por defecto en los 4 `client.properties`/`server.properties`: **5000 plano, 5001 TLS**.
MySQL Docker: host **`localhost:3307`** → contenedor `mysql:3306` (los tests usan el host).

**Las dos reglas que más se violan por ignorancia:**

1. **`localhost` no sirve como dirección de servidor** en un cliente que está en otro PC.
2. **Los clientes NUNCA tocan la base del servidor.** Solo el servidor → MySQL.

## 4. Build y test: los comandos exactos

```powershell
Set-Location universidad-mensajeria
docker compose up -d mysql          # OBLIGATORIO: los tests de servidor fallan sin MySQL

mvn -pl server/servidor-app,client/client-app -am test    # servidor + JavaFX
mvn -pl client/client-app -am test                        # solo JavaFX
```

El `-am` (**also-make**) no es opcional: compila los módulos hermanos (`common-protocol`,
`client-interfaces`). Foco en un solo test:

```powershell
mvn -pl server/servidor-app -am test -Dtest=NombreTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl client/client-app -am test -Dtest=NombreTest -Dsurefire.failIfNoSpecifiedTests=false
Set-Location client-python; .\.venv\Scripts\python.exe -m pytest tests/test_modulo.py -k filtro
dotnet test client-dotnet.Tests\Mensajeria.Tests.csproj --filter "FullyQualifiedName~Nombre"
```

```powershell
# Verificación global de paridad (tokens + estado + puertos + 3 suites + build WPF): exit 0/1
.\universidad-mensajeria\client-python\.venv\Scripts\python.exe contrato\verificar.py
dotnet build client-dotnet/Mensajeria.csproj -c Release   # valida el XAML generado
```

Notas que se adivinan mal:

- `client-python/pyproject.toml` solo existe para `uv run`; las dependencias reales se fijan en
  `requirements.txt` (`pillow`, `PySide6`, `PySide6-Fluent-Widgets`).
- Tests WPF requieren **SDK 10** aunque la app sea `net8.0-windows`. No hay CI workflows en
  `.github/`; la verificación es local (`contrato/verificar.py`).
- Estado de suites: ver `ESTADO_ROADMAP.md` §1 y los reportes Surefire presentes. La base del
  servidor **no está en verde** (fallos de umbral en `CargaMixtaTest`); no afirmarlo.

### Distribuciones (lo que se instala en cada PC)

```powershell
Set-Location universidad-mensajeria
.\empaquetar-todo.cmd   # las 4: servidor + JavaFX + Python + `dotnet publish ... -o dist\MensajeriaWPF`
```

Equivalentes por pieza: `dist\distribuir-servidor.cmd`, `client\dist-client.cmd`,
`client-python\distribuir.cmd`. Los 2 scripts de Java usan **`mvn install`, nunca `package`**:
`dependency:copy-dependencies` resuelve los módulos hermanos desde el repo local.

## 5. Invariantes — no romper esto

Rompes algo que funciona aunque las suites sigan en verde si tocas:

| # | Invariante | Por qué |
|---|---|---|
| 1 | **Protocolo neutro de lenguaje.** Serialización **Gson**, nunca nativa de Java | `RNF-04`; los 3 clientes reimplementan el framing |
| 2 | **Tope de trama: 16 MiB** (`TramaCodec.TAMANO_MAXIMO_TRAMA`). Archivos **fragmentados** (`ARCHIVO_INICIO` → N×`ARCHIVO_PARTE` → `ARCHIVO_FIN`) | Un adjunto grande no cabe en una trama |
| 3 | **`servidor-app` compila SOLO contra `int-*`.** Los `comp-*` son `runtime` | Romperlo rompe la inversión de dependencias |
| 4 | **Pool de hilos CONCRETOS con token bucket**, no `ExecutorService` (`ManejadorConexion extends Thread`) | Object Pool explícito (`RF-S43..S48`) |
| 5 | **Sin registro por red.** Alta = editar `usuarios_iniciales.csv`; el intento por red responde `ERROR` | `RF-S27` |
| 6 | **Filtros de imagen SECUENCIALES, cada uno genera y guarda una imagen nueva** (5 derivadas) | `RF-S16`, `RF-S17`; no es `if/else` |
| 7 | **Login incrementa "accesos"; presencia = sesión viva.** No son lo mismo | Mezclarlos falsea informes 2–4 |
| 8 | **`LOGOUT` cierra una sesión; `KICK` cierra las OTRAS del mismo código**; presencia off al cerrar la última | `RF-S31`; lo prueba `SMOKE-03` |
| 9 | **Respuesta = TEXTO ORDINARIO** con prefijo `Respuesta a Nombre [CÓDIGO]: "…"` | Sin vínculo estructurado; cambiarlo rompe interoperabilidad |
| 10 | **BD local aislada por cuenta, sin FKs.** `historial_local.origen/destino` son `TEXT` con el código | El esquema local **no tiene claves foráneas** |
| 11 | **Paleta generada, no editada.** 4 CSS + 2 `.xaml` + `ui/tema_tokens.py` salen de `contrato/generar_tokens.py` | Se edita `contrato/tokens-diseno.json` y se regenera (`--check` en CI local) |
| 12 | **Estados en `contrato/estado-mensaje.json`; comprobar, no asumir.** `PENDIENTE→ENVIADO→ENTREGADO→LEIDO` + `ERROR` terminal, en 5 copias / 3 lenguajes / 2 motores | Corre `contrato/verificar_estado_mensaje.py` si tocas una copia |
| 13 | **Puertos en `contrato/puertos.json`: 23, no 12.** 9 viven en `client-app`/`presentacion` | Añadir un método sin añadirlo al JSON rompe `contrato/verificar_puertos.py` |

## 6. Trampas verificadas en el código

### 6.1 Parece muerto y NO lo es — no borrar sin comprobar framework/reflexión

`LoginController` (0 refs Java; lo instancia `login.fxml` con `fx:controller=`), `ServerApplication` /
`PasswordConfig` / `FlywayMigrationAdapter` / `JpaPersistenciaAdapter` / `DataSourceAdapter`
(anotaciones Spring), los 5 `Proveedor*` (ServiceLoader), clases de estilo JavaFX en CSS
(`.split-pane`, `.list-cell`, `.combo-box`), `FabricaDelDespachador.NULA` (contrato SPI),
`FiltroMain` (CLI usada por su test). Reglas B1–B6 en `PLAN_PATRONES_UI.md` §2.1.

### 6.2 Lo que SÍ es código muerto

Foto con conteo en `ANALISIS_TECNICO.md` §2 (38 items al 2026-10-05; parte ya borrada):
`sesionesInfo()`, `solicitarHistorialServidor()`, columna `ipDestinatario` (siempre NULL),
`_guardar_propio()`, pinceles `Telegram*Brush`. **Verifica que el símbolo aún exista** antes de
actuar.

### 6.3 `SEC-05`/`SEC-06` están cerrados

Autenticación obligatoria por defecto; solo `LOGIN` público. Detalle vigente en
`ESTADO_ROADMAP.md` §9; la auditoría conserva el historial.

### 6.4 Los 3 clientes no son espejos exactos — replicar la lógica en los 3

JavaFX tiene ViewModel parcial y frontera por constructor (`FabricaControladores`); Python **parchea**
la fachada con `setattr` y no tiene interfaz de historial; .NET inyecta `IFachadaCliente`; solo
Python valida TLS fail-fast (`transversal/tls.py`). Python partió su chat en componentes
(`constructor_vista_chat`, `paginador_historial_chat`, …); .NET concentra el chat en
`ChatWindow.xaml.cs`. Si tocas negocio, **replícala en los 3** o rompes la matriz de 6 direcciones
(`GUIA_ACEPTACION.md`).

### 6.5 Detalles que rompen suposiciones

- Estados `PENDIENTE→ENVIADO→ENTREGADO→LEIDO` + `ERROR` como `CASE` SQL **idéntico** en los 3
  clientes; canónico en `DIAGRAMAS/estados/st-mensaje.mmd`.
- Python y .NET **comparten el mismo SQLite** en un PC. .NET escribe fechas UTC con `Z`
  (`ToString("O")`); Python normaliza aware→naive (`transversal/fechas._leer`, misma semántica que
  `FormatoFecha.TryLeer`). Si añades fecha al historial, lee/escribe **ambos formatos** en los 3.
  El servidor nunca envía zona (`LocalDateTime.now()`).
- Tests E2E usan **semilla propia** (`src/test/resources/usuarios_iniciales.csv`) y **dejan
  `activo=0`** en esa BD; el arranque normal reactiva. Si el login dice "usuario no registrado",
  reactiva con `UPDATE usuarios SET activo=1`.
- `Usuarios` = `ConcurrentHashMap` **en memoria**; la tabla `usuarios` la persiste `servidor-app`.
- `Servicios` trata límite `0` como **sin límite**; el pool usa token bucket, no semáforo.
- `ProtocoloComunicacion.enviar` declara `throws IOException` frente a `throws Exception` de la
  interfaz: *narrowing* legítimo.

## 7. Cómo trabajar aquí

| Si tu tarea es… | Empieza por | Antes de editar, lee |
|---|---|---|
| Función de negocio | `REQUERIMIENTOS.md` (el `RF-*`) | `universidad-mensajeria/common-protocol/PROTOCOLO.md` si toca red |
| Servidor | `PLAN.md` §2 y §9 | `DIAGRAMAS/clases/arq-vista-general.mmd`; regla 3 de §5 |
| Trama TCP | `universidad-mensajeria/common-protocol/PROTOCOLO.md` + `commands/Comandos.java` | Nada de `comp-*` en `servidor-app` |
| Un cliente | `DIAGRAMAS/clases/clases-client-{java,dotnet,python}.mmd` | §6.4: **replicar en los 3** |
| UI | `DIAGRAMAS/textos-ui.md` + mockups | `PLAN_PATRONES_UI.md` antes de añadir |
| Arquitectura JavaFX | `PLAN_ARQUITECTURA_CLIENTE_JAVA.md` (parcial; no declarar Clean completa) | `ESTADO_ROADMAP.md` §6 |
| Color/medida UI | `contrato/tokens-diseno.json` y regenerar | **Nunca editar CSS/`.xaml` generados** (`contrato/README.md`) |
| Igualar clientes | `PLAN_PARIDAD_CLIENTES.md` | `contrato/verificar.py` (8 pasos) |
| Esquema servidor | `server/servidor-app/src/main/resources/db/migration/{mysql,postgres,oracle}/` (**los 3**) | `DIAGRAMAS/modelo-relacional/er-modelo-servidor.mmd` |
| Filtro imagen | `server/utilerias/` + `TuberiaFactory` | Regla 6 de §5 |
| Documentar | `DIAGRAMAS/README.md` (regla de 3 pasos) | Un `.mmd` sin PNG **no cuenta** |

## 8. Convenciones e índice

- **Dominio en español** (nada de `manager`, `util2`); javadoc en español; **Python** `snake_case` +
  hints públicos; **C#** `PascalCase` público / `camelCase` privado.
- **Comentarios solo en contratos públicos y en el *por qué*.** Sin hardcode: `localhost:5000`,
  credenciales, rutas → `application.properties` / `server.properties` / `client.properties`.
- **Literales UI en código**, sin recursos; si añades uno, igual en los 3 clientes. Excepciones de
  dominio (`UsuarioNoEncontradoException`), no `RuntimeException` genérica.

| Pregunta | Archivo |
|---|---|
| Qué debe hacer / por qué así / qué falta | `REQUERIMIENTOS.md` / `PLAN.md` / `ESTADO_ROADMAP.md` |
| Trama exacta / esquema local | `universidad-mensajeria/common-protocol/PROTOCOLO.md` / `universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md` |
| Dónde está cada clase / textos UI | `DIAGRAMAS/` (`README.md` es el índice) / `DIAGRAMAS/textos-ui.md` |
| Paridad / paleta / ¿siguen iguales? | `PLAN_PARIDAD_CLIENTES.md` / `contrato/tokens-diseno.json` / `contrato/verificar.py` |
| Problemas ya encontrados | `ANALISIS_TECNICO.md` (histórico; el estado vigente manda en el roadmap) |
| Preparar PC / operar / comprobar | `INSTALACION.md` (+`AUTOMATIZACION_ENTORNO.md`) / `DESPLIEGUE.md` / `GUIA_ACEPTACION.md` |
| Servidor / JavaFX / Python / WPF | `server/servidor-app/README.md` / `client/README.md` / `client-python/README.md` / `client-dotnet/README.md` |
