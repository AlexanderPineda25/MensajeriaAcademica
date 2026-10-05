# AGENTS.md — guía de orientación rápida

> **Para cualquier agente de IA o persona nueva.** Objetivo: entender cómo funciona el código en
> **5 minutos**, sin leerse las ~9 600 líneas fuente ni los 4 200 de documentación.
>
> Lee este archivo **primero**. Si con esto no te basta, el índice está en §9.

## 1. Qué es esto, en 4 líneas

Aplicación de escritorio **cliente/servidor** para mensajería de una comunidad académica
universitaria. Un servidor central en **Java** y **tres clientes intercambiables** escritos en
**JavaFX**, **PySide6** y **WPF**, que se hablan por **TCP + JSON** (no HTTP).

Todo lo que hay que saber del contrato de red cabe en una línea: **cabecera de 4 bytes
big-endian con la longitud del contenido, seguido de JSON UTF-8**.

## 2. Mapa del repositorio en 30 segundos

```
ClienteServidor-main/
├── AGENTS.md                 ← estás aquí
├── INSTALACION.md            ← qué instalar (JDK 21+, Maven, Docker, Python, .NET)
├── REQUERIMIENTOS.md         ← QUÉ debe hacer (RF-*, RNF-*, PD-*)
├── PLAN.md                   ← CÓMO está construido y por qué
├── ESTADO_ROADMAP.md         ← qué falta (manda hacia adelante)
├── ANALISIS_TECNICO.md       ← auditoría con evidencia archivo:línea
├── PLAN_PATRONES_UI.md       ← plan de refactor de clientes
├── GUIA_ACEPTACION.md        ← cómo se comprueba
├── DESPLIEGUE.md             ← operar / instalar / respaldar
│
├── DIAGRAMAS/                ← 18 .mmd de clases/ER + 4 .mmd nuevos + mockups
├── DIAGRAMAS_ENTREGA/        ← ⚠ duplicados, plan de consolidación pendiente
├── DIAGRAMAS_FINALES/        ← ⚠ duplicados, contiene material único por migrar
│
└── universidad-mensajeria/   ← EL MONOREPOS (solo aquí se compila)
    ├── pom.xml               ← reactor Maven padre, java.version=21
    ├── Dockerfile            ← build multi-stage maven:3.9+JDK21 → JRE21
    ├── docker-compose.yml    ← mysql:8.4 + postgres:16 + oracle + servidor
    │
    ├── common-protocol/      ← CONTRATO COMPARTIDO, sin Spring y sin UI
    │   ├── dto/              ←   25 records/enums + schema-local.sql canónico
    │   └── codec/            ←   TramaCodec (framing JSON sobre Gson)
    │
    ├── server/               ← SERVIDOR Java 21 + Spring Boot 3.5.7
    │   ├── int-*  (5 módulos)= PORTS:  solo interfaces. Sin Spring.
    │   ├── comp-* (5 módulos)= ADAPTERS: implementaciones. Se cargan por ServiceLoader.
    │   ├── utilerias/         ←   tuberías de filtros (Pipes & Filters)
    │   └── servidor-app/     ←   ÚNICO módulo con Spring. Despliegable.
    │
    ├── client/               ← CLIENTE JAVA (el "principal")
    │   ├── client-interfaces/←   4 puertos + records
    │   └── client-app/       ←   adaptadores + FachadaCliente + JavaFX (FXML+CSS)
    │
    ├── client-python/        ← CLIENTE ESPEJO (PySide6 + qfluentwidgets, SQLite)
    ├── client-dotnet/        ← CLIENTE ESPEJO (WPF + WPF-UI, SQLite)
    └── client-dotnet.Tests/  ← tests xUnit (OJO: target net10.0, la app es net8.0)
```

**Dato clave:** `int-*` y `comp-*` **no usan Spring**. El único módulo con Spring es
`servidor-app`, y lo compila **solo contra los `int-*`**: los adaptadores se descubren en runtime
con `ServiceLoader` y se registran en `META-INF/services`. Por eso hay 5 `Proveedor*` que parecen
no usar nadie.

## 3. Los 4 procesos y cómo se hablan

| Proceso | Entry point | UI | BD local |
|---|---|---|---|
| **Servidor** | `server/servidor-app/src/main/java/.../ServerApplication.java` | JLine (consola) + JavaFX (escritorio admin) | — (MySQL/Postgres/Oracle) |
| **Cliente JavaFX** | `client/client-app/.../ClienteMain.java` | JavaFX 25 + AtlantaFX (FXML + CSS) | H2 por cuenta |
| **Cliente Python** | `client-python/main.py` | PySide6 + PySide6-Fluent-Widgets | SQLite por cuenta |
| **Cliente WPF** | `client-dotnet/App.xaml.cs` | WPF .NET 8 + WPF-UI | SQLite por cuenta |

```
JavaFX ─┐
Python ─┼── TCP/JSON ──▶ IP_SERVIDOR:5000 (TLS: 5001) ──▶ Servidor ──▶ MySQL
WPF   ──┘                                                      │
                                                               ├── uploads/<nombre [código]>/<hash>/
                                                               └── logs/
```

**Las dos reglas que más se violan por ignorancia:**

1. **`localhost` no sirve como dirección de servidor** en un cliente que está en otro PC.
2. **Los clientes NUNCA tocan la base del servidor.** Solo el servidor → MySQL. Si ves un cliente
   abriendo una conexión de base de datos, es un bug.

## 4. Build y test: los comandos exactos

```powershell
Set-Location universidad-mensajeria
docker compose up -d mysql          # OBLIGATORIO: los tests de servidor fallan sin MySQL

mvn -pl server/servidor-app,client/client-app -am test    # servidor + JavaFX
mvn -pl client/client-app -am test                         # solo JavaFX
```

El `-am` (**also-make**) no es opcional: compila los módulos hermanos (`common-protocol`,
`client-interfaces`) de los que dependen.

```powershell
Set-Location client-python
.\.venv\Scripts\python.exe -m pytest tests        # o: -m unittest discover -s tests
Set-Location ..
dotnet test client-dotnet.Tests\Mensajeria.Tests.csproj    # requiere SDK 10
dotnet build client-dotnet/Mensajera.csproj -c Release
```

Volumen verificado el 2026-10-05: servidor **90** tests, JavaFX **58**, Python **60** (+5 omitidas),
.NET **35**.

### Generar las 4 distribuciones (esto es lo que se instala en cada PC)

```powershell
Set-Location universidad-mensajeria
cmd dist\distribuir-servidor.cmd
cmd client\dist-client.cmd
cmd client-python\distribuir.cmd
dotnet publish client-dotnet\Mensajeria.csproj -c Release -r win-x64 --self-contained true -o dist\MensajeriaWPF
```

Los 2 scripts de Java usan `mvn install`, **nunca `package`**:
`dependency:copy-dependencies` resuelve los módulos hermanos desde el repo local y con `package`
no están. Solo falla en máquinas limpias.

## 5. Invariantes — no romper esto

Si tocas una de estas reglas, rompe algo que funciona aunque las suites sigan en verde.

| # | Invariante | Por qué |
|---|---|---|
| 1 | **El protocolo es neutro de lenguaje.** Serialización **Gson**, nunca serialización nativa de Java. Cualquier cliente en cualquier lenguaje debe poder leerlo | Es requisito de grado (`RNF-04`). Los 3 clientes reimplementan el framing |
| 2 | **Tope de trama: 16 MiB** en `TramaCodec`. Los archivos viajan **fragmentados** (`ARCHIVO_INICIO` → N×`ARCHIVO_PARTE` → `ARCHIVO_FIN`) | Un JPEG de 8 MB no cabe cómodo en una línea de consola |
| 3 | **`servidor-app` compila SOLO contra `int-*`.** Los `comp-*` son `runtime` | Si añades un `import` de un `comp-*` en `servidor-app`, rompes la inversión de dependencias |
| 4 | **El pool es de hilos CONCRETOS con token bucket**, no un `ExecutorService`. `ManejadorConexion extends Thread` | El patrón Object Pool es requisito explícito (`RF-S43..S48`) |
| 5 | **No existe registro de usuarios por red.** El alta es solo editar `usuarios_iniciales.csv` | `RF-S27`. Un intento por red debe responderse `ERROR` |
| 6 | **Los filtros de imagen son SECUENCIALES y cada uno genera una imagen nueva que se guarda** (5 derivadas) | `RF-S16`, `RF-S17`. No es un `if/else` de filtros |
| 7 | **Login incrementa "accesos"; presencia es estado de sesión viva.** No son lo mismo | Mezclarlos falsea los informes 2–4 |
| 8 | **`LOGOUT` cierra una sesión; `KICK` cierra las OTRAS del mismo código.** La presencia se apaga al cerrar la última | `RF-S31`, y es lo que prueba `SMOKE-03` |
| 9 | **La respuesta a un mensaje viaja como TEXTO ORDINARIO** con prefijo `Respuesta a Nombre [CÓDIGO]: "…"` | Sin vínculo estructurado. Cambiarlo rompe la interoperabilidad entre los 3 clientes |
| 10 | **La base local se aísla por cuenta.** `historial_local.origen/destino` guardan el código como `TEXT`, **no** una FK | Afirmar integridad referencial ahí sería falso. El esquema local **no tiene claves foráneas** |

## 6. Trampas verificadas en el código

Estas no se deducen leyendo: se comprobaron. Perder tiempo aquí es tiempo perdido.

### 6.1 Lo que parece código muerto y **NO** lo es

| Parece muerto | Por qué vive |
|---|---|
| `LoginController` (0 referencias Java) | Lo instancia `login.fxml:15` con `fx:controller=` |
| `ServerApplication`, `PasswordConfig`, `FlywayMigrationAdapter`, `JpaPersistenciaAdapter`, `DataSourceAdapter` | Anotaciones de Spring (`@SpringBootApplication`, `@Configuration`, `@Component`) |
| Los 5 `Proveedor*` | Los carga `ServiceLoader` desde `META-INF/services` |
| Los 49 archivos de test | Escaneo de classpath de JUnit |
| `.split-pane`, `.list-cell`, `.combo-box` en los CSS | Clases de estilo **integradas** de JavaFX, se aplican en runtime |
| `FabricaDelDespachador.NULA` | Contrato SPI obligatorio (`crear()`), aunque reciba 0 llamadas |
| `FiltroMain` | CLI opcional documentada, usada por su test |

**Regla:** antes de borrar algo, `rg` no basta. Comprueba framework, reflexión y convención de
toolkit. Está desarrollado en `PLAN_PATRONES_UI.md` §2.1 (reglas B1–B6).

### 6.2 Lo que SÍ es código muerto (38 items inventariados)

Lista exacta con conteo de referencias en `ANALISIS_TECNICO.md` §2. Ejemplos: `sesionesInfo()`,
`solicitarHistorialServidor()`, `ipDestinatario` (columna siempre NULL), `_guardar_propio()`,
`bannerOffline` (XAML muerto), 9 pinceles `Telegram*Brush`, `ui/registro_window.py` (stub de 3
líneas).

### 6.3 Bug abierto que hay que conocer

**`SEC-05` — `LISTAR_USUARIOS` responde SIN autenticación.** Los comandos 13–15 de 15 no aplican
`Comandos.exigirAutenticacion(...)` y el despachador no filtra por sesión. Una sesión anónima
recibe el directorio completo (código, nombres, apellidos, programa). Detalle y cadena de llamadas
en `ANALISIS_TECNICO.md` §1. **Bloquea producción.**

### 6.4 Diferencias entre los 3 clientes (no son espejos exactos)

| | JavaFX | Python | .NET |
|---|---|---|---|
| ViewModel | ninguno | ninguno | parcial (`MensajeVM` solo) |
| Frontera de vista | localizador **estático** | **parchea** la fachada con `setattr` | `IFachadaCliente` inyectada |
| Archivo mayor | `ChatController.java` 774 líneas | `chat_window.py` **1 322** | `ChatWindow.xaml.cs` 790 |
| Validación TLS fail-fast | ❌ | ✅ (`transversal/tls.py`) | ❌ |
| Historial con interfaz | ✅ | ❌ **no tiene** | ✅ |

Si tocas una lógica de negocio, **replícala en los 3** o rompe la matriz de 6 direcciones
(`GUIA_ACEPTACION.md`).

### 6.5 La máquina de estados del mensaje está escrita 3 veces

`PENDIENTE → ENVIADO → ENTREGADO → LEIDO`, más `ERROR`, como un `CASE` SQL **idéntico** en los 3
clientes (`HistorialLocal.java`, `historial_local.py`, `HistorialLocal.cs`). Si añades un estado,
hay que acordarse de los 3 sitios o un cliente discrepa de otro. La referencia canónica está en
`DIAGRAMAS/estados/st-mensaje.mmd`.

### 6.6 Detalles que romperían una suposición

- `ProtocoloComunicacion.enviar` declara `throws IOException` y la interfaz `throws Exception`: es
  un *narrowing* legítimo.
- `Usuarios` guarda el directorio en un `ConcurrentHashMap` **en memoria**. La tabla `usuarios` la
  persiste `servidor-app`. Son dos cosas distintas.
- `Servicios` trata un límite de `0` como **sin límite**.
- El pool de red usa token bucket (`fichas`/`ultimoRecargoNs`), no una cola con semáforo.
- Los tests E2E usan **semilla propia** (`src/test/resources/usuarios_iniciales.csv`): correr
  tests desactiva cuentas reales en esa BD (el arranque normal las reactiva).
- Los tests de servidor **requieren MySQL** y fallan a propósito sin él.

## 7. Cómo trabajar aquí

| Si tu tarea es… | Empieza por | Antes de editar, lee |
|---|---|---|
| Entender una función de negocio | `REQUERIMIENTOS.md` busca el `RF-*` | `PROTOCOLO.md` si toca la red |
| Tocar el servidor | `PLAN.md` §2 (arquitectura) y §9 (patrones) | `DIAGRAMAS/clases/arq-vista-general.mmd` |
| Añadir una trama TCP | `PROTOCOLO.md` + `commands/Comandos.java` | **Regla 3 de §5**: nada de `comp-*` en `servidor-app` |
| Tocar un cliente | `DIAGRAMAS/clases/clases-client-{java,dotnet,python}.mmd` | §6.4: **replicar en los 3** |
| Cambiar la UI | `DIAGRAMAS/textos-ui.md` (literales exactos) + los mockups | `PLAN_PATRONES_UI.md` antes de añadir |
| Cambiar el esquema | `db/migration/{mysql,postgres,oracle}/` (**los 3**) | `DIAGRAMAS/modelo-relacional/er-modelo-servidor.mmd` |
| Añadir un filtro | `server/utilerias/` + `TuberiaFactory` | Regla 6 de §5: secuencial, cada uno genera imagen |
| Documentar | `DIAGRAMAS/README.md` (regla de 3 pasos) | Un `.mmd` sin PNG **no cuenta** |

## 8. Convenciones

- **Idioma del dominio: español.** Nombres de dominio en español del negocio; nada de
  `manager`, `util2`, `dataTmp`.
- **Comentarios solo en contratos públicos y decisiones de *por qué*.** El *qué* lo dice el código.
- **Sin hardcode:** `localhost:5000`, credenciales, rutas → `application.properties`,
  `server.properties` o `client.properties` según el proceso.
- **Java:** español en javadoc, identificadores de dominio en español, 17 módulos Maven.
- **Python:** `snake_case`, type hints en las firmas públicas.
- **C#:** `PascalCase` público, `camelCase` privado.
- **Los literales de UI están dentro del código**, sin archivos de recurso. Si añades uno, que
  coincida con los otros 2 clientes (ver `VIS-02` en `ESTADO_ROADMAP.md`).
- **Excepciones de dominio** (`UsuarioNoEncontradoException`), no `RuntimeException` genérica.

## 9. Índice: dónde está cada cosa

| Pregunta | Archivo |
|---|---|
| Qué debe hacer el sistema | [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) |
| Por qué está así | [`PLAN.md`](PLAN.md) |
| Qué falta y en qué orden | [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md) |
| Dónde está cada clase | `DIAGRAMAS/` (`README.md` es el índice) |
| Formato exacto de la trama | [`universidad-mensajeria/common-protocol/PROTOCOLO.md`](universidad-mensajeria/common-protocol/PROTOCOLO.md) |
| Esquema local de los clientes | [`universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md`](universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md) |
| Textos literales de la UI | [`DIAGRAMAS/textos-ui.md`](DIAGRAMAS/textos-ui.md) |
| Problemas ya encontrados | [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) |
| Cómo instalar un PC | [`INSTALACION.md`](INSTALACION.md), [`DESPLIEGUE.md`](DESPLIEGUE.md) |
| Cómo comprobar que funciona | [`GUIA_ACEPTACION.md`](GUIA_ACEPTACION.md) |

### Mapa de unclick a un archivo

| Necesito… | Abre |
|---|---|
| El servidor entero | `server/servidor-app/README.md` |
| Solo el cliente JavaFX | `client/README.md` |
| Solo el cliente Python | `client-python/README.md` |
| Solo el cliente WPF | `client-dotnet/README.md` |
| La tubería de filtros | `DIAGRAMAS/clases/clases-utilerias.mmd` |
| Los 15 comandos TCP | `DIAGRAMAS/clases/comandos-servidor.mmd` |
| Las 7 tablas del servidor | `DIAGRAMAS/modelo-relacional/er-modelo-servidor.mmd` |

## 10. Estado del repo al momento de escribir esto

- Suites en verde (2026-10-05). Docker, distribución y rendimiento medidos y documentados.
- **1 bloqueante de seguridad abierto** (`SEC-05`, §6.3).
- 38 items de código muerto inventariados, **ninguno borrado todavía**.
- 6 bugs de interfaz que son funciones rotas, pendientes.
- `DIAGRAMAS_ENTREGA/` y `DIAGRAMAS_FINALES/` son duplicados con material único por migrar;
  el plan está en `DIAGRAMAS/README.md` §3.
