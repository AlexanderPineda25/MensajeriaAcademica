# DIAGRAMAS

Diagramas del proyecto cliente-servidor, derivados del código y de los `.sql` reales. Cada
diagrama viene en dos formatos: **source editable** (lo que se revisa y se regenera) y **render**
(PNG/SVG/PDF, lo que se lee en el informe).

Actualizado: **2026-10-05** — se reescribió el índice, se añadieron 16 diagramas que faltaban
(casos de uso, secuencia, actividad, estados, despliegue, clases de Python y .NET) y se documentó
el plan de consolidación de las carpetas `DIAGRAMAS_ENTREGA` y `DIAGRAMAS_FINALES`.

## 0. Estado de los renders de esta ronda

> ⚠️ **Los 16 `.mmd` nuevos están SIN render.** En este entorno **no hay `mmdc`, ni `plantuml`,
> ni `node` instalados**, así que no se pudo generar ningún PNG/SVG.
>
> Para completarlos hace falta:
>
> ```powershell
> npm i -g @mermaid-js/mermaid-cli
> cd DIAGRAMAS
> .\render.ps1 -Solo mermaid
> ```
>
> Y después **alguien tiene que mirar cada PNG** (§1, regla de los 3 pasos). Un diagrama sin
> render revisado no cuenta como terminado.

<a id="regenerar"></a>

## 1. Regenerar

```powershell
.\render.ps1                 # todo
.\render.ps1 -Solo mermaid   # solo los .mmd
.\render.ps1 -Solo mockups   # solo los .excalidraw
```

**Regla de los 3 pasos:** un diagrama no está terminado hasta que (1) existe su source, (2) existe
su render, y (3) alguien lo miró. Si `render.ps1` reporta un `FALLO`, el source está mal: no
entregues source sin renderizar.

### Cómo descubre los archivos (y por qué las carpetas viejas no se renderizan)

`render.ps1` se ancla a `$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path`, es decir **a su
propia carpeta**. Recorre con `Get-ChildItem -Recurse`, así que cualquier subcarpeta **nueva** de
`DIAGRAMAS/` se recoge sola. Pero `DIAGRAMAS_ENTREGA` y `DIAGRAMAS_FINALES` están **fuera** de
`$raiz` y **nunca se regeneran**: sus PNG se hicieron a mano.

### Limitaciones de `render.ps1` que hay que tener en cuenta al añadir diagramas

| Limitación | Detalle | Qué hacer |
|---|---|---|
| Ancho por **nombre de archivo** | El mapa `$anchos` solo tiene `er-modelo-servidor` (3400), `er-modelo-local` (2200), `arq-vista-general` (2600). Todo lo demás sale a **2000 px** | Si un diagrama nuevo sale ilegible, añadir su `BaseName` al mapa |
| El PDF depende del **nombre**, no del tipo | El gate es `BaseName -like 'er-modelo-*'`. Si un ER se renombra, **pierde el PDF** en silencio | Mantener el prefijo `er-modelo-` en los ER |
| El script **siempre sale con código 0** | Termina con "Listo..." aunque hayan fallado N renders | En CI, contar los `FALLO` aparte |
| `Test-Herramienta` **no aborta** | Si falta una herramienta, avisa y salta esa sección; las demás siguen | Normal, pero no confíes en el exit code |
| Rama `.puml` **muerta hoy** | No hay ningún `.puml` en el repo; `-Solo plantuml` no hace nada | Dejar o borrar la rama |
| Rama de Excalidraw puede tragarse un fallo real | `try{}catch{}` + `2>$null` neutraliza el error, y luego el éxito se juzga por `$LASTEXITCODE`, que puede venir de una llamada anterior | Contexto al leer un "OK" sospechoso |

## 2. Índice completo

### 2.1 Casos de uso — **NUEVO** (no existía ningún `usecaseDiagram` en el repo)

| Archivo | Contenido | Origen |
|---|---|---|
| `casos/uc-cliente.mmd` | 21 casos del **actor Usuario**, con las relaciones `<<extend>>`-like entre ellos (UC-05 → UC-03/UC-04) y las 5 de almacenamiento local | `REQUERIMIENTOS.md` §4.1–4.3 |
| `casos/uc-servidor.mmd` | 20 casos del **actor Administrador** + usuario conectado como actor secundario, agrupados en arranque / operación / informes / supervisión | `REQUERIMIENTOS.md` §3.1–3.8 |
| `casos/uc-sistema.mmd` | Los 2 actores sobre el mismo sistema, con el cruce que importa: qué acciones del usuario alimentan qué informe | vista de conjunto |

### 2.2 Secuencia — **NUEVO** (antes solo había 3, en `DIAGRAMAS_ENTREGA`)

| Archivo | Contenido |
|---|---|
| `secuencia/seq-conexion-y-trama.mmd` | Apertura TCP/TLS,Pool acquire, framed JSON (4 bytes big-endian + JSON UTF-8), despacho, y los **3 comandos sin guarda** de `SEC-05` marcados en la propia figura |
| `secuencia/seq-observador-vistas.mmd` | El patrón Observer (RF-S42): `suscribirEventos`, un evento de mensaje y uno de desconexión, y cómo decide la vista |
| `secuencia/seq-object-pool.mmd` | `adquirir` → usar → `devolver`, con los 3 caminos de salida (cliente se va, corte de red, error del manejador) |
| `secuencia/seq-tuberia-filtros.mmd` | Las **3 tuberías** (texto, imagen, archivo) en un solo diagrama, con la generación de la imagen derivada en cada filtro |

### 2.3 Actividad (árboles de decisión) — **NUEVO**

| Archivo | Contenido |
|---|---|
| `flujos/act-login.mmd` | Decisión completa, **incluido el camino de error de TLS** (fail-fast) y los 4 motivos de cierre de sesión |
| `flujos/act-envio-mensaje.mmd` | Texto / solo imagen / texto+imagen, los 2 caminos de fallo (timeout, sin red), los acuses y la regla de **no realimentar** |
| `flujos/act-archivo-fragmentado.mmd` | `ARCHIVO_INICIO → N×ARCHIVO_PARTE → ARCHIVO_FIN`, verificación de bytes, y la trampa del `Files.move` concurrente ya resuelta |
| `flujos/act-descarga.mmd` | Descarga con y sin filtro, nombre de destino, y los 4 fallos de identificación de imagen |
| `flujos/act-reconexion-cola.mmd` | Los **dos caminos distintos** que se confunden al implementarlo: la cola de envíos y los acuses pendientes |

### 2.4 Estados — **NUEVO** (no existía ningún `stateDiagram` en el repo)

| Archivo | Contenido |
|---|---|
| `estados/st-mensaje.mmd` | La máquina de estados **canónica**: `PENDIENTE → ENVIADO → ENTREGADO → LEIDO`, más `ERROR`. Es la referencia contra la cual deben parecerse los 3 `CASE` SQL duplicados |
| `estados/st-sesion.mmd` | `ANONIMA → AUTENTICADA → INACTIVA / CERRADA / RECHAZADA`, con la distinción LOGOUT vs KICK |
| `estados/st-transferencia-archivo.mmd` | Transferencia fragmentada; por qué un `ARCHIVO_FIN` perdido **no** deja un archivo a medias registrado |

### 2.5 Despliegue — **NUEVO**

| Archivo | Contenido |
|---|---|
| `despliegue/despliegue-topologia.mmd` | Los 4 procesos en LAN, qué puerto cruza, y **la prohibición explícita de cliente → base del servidor** |

### 2.6 Clases por componente

Un archivo por componente. La convención de nombres del servidor *es* la arquitectura: `int-*` son
puertos, `comp-*` son adaptadores.

| Archivo | Componente | Contenido |
|---|---|---|
| `clases/arq-vista-general.mmd` | — | Vista global: puertos, adaptadores, SPI, `servidor-app`, los 3 clientes |
| `clases/clases-dto-contrato.mmd` | `common-protocol/dto` | `Mensaje` (29 componentes), `Mensaje.Builder`, `TipoMensaje` |
| `clases/clases-dto-consultas.mmd` | `common-protocol/dto` | Informes, archivos, `ResultadoMensajeDTO`, `PaginaDTO<T>` |
| `clases/clases-dto-sesion.mmd` | `common-protocol/dto` | `IdSesion`, `EstadoServidor`, usuarios, `TipoAccion`, excepciones |
| `clases/clases-codec.mmd` | `common-protocol/codec` | `TramaCodec` sobre Gson |
| `clases/clases-utilerias.mmd` | `server/utilerias` | Tubería de filtros, `FiltroVisualBase` sellada, 10 filtros |
| `clases/clases-int-protocolo-comunicacion.mmd` | `int-protocolo-comunicacion` | Puerto de transporte, `ReceptorDeTramas` |
| `clases/clases-comp-protocolo-comunicacion.mmd` | `comp-protocolo-comunicacion` | Adaptador TCP/TLS, pool de hilos con token bucket |
| `clases/clases-int-cliente-servidor.mmd` | `int-cliente-servidor` | Puerto de aplicación y jerarquía `SalidaClienteServidor` |
| `clases/clases-comp-protocolo-clienteservidor.mmd` | `comp-protocolo-clienteservidor` | `Despachador`, `FabricaDelDespachador` y la jerarquía `SalidaClienteServidor` |
| `clases/comandos-servidor.mmd` | `comp-protocolo-clienteservidor` | Los 15 `Command`, agrupados por la salida que necesitan |
| `clases/clases-componentes-usuarios.mmd` | `int-usuarios` + `comp-usuarios` | Puerto y adaptador; directorio en memoria |
| `clases/clases-componentes-servicios.mmd` | `int-servicios` + `comp-servicios` | Límites e informes |
| `clases/clases-componentes-colas.mmd` | `int-gestor-mensajes` + `comp-gestor-colas` | Cola e índice de mensajes |
| `clases/clases-client-interfaces.mmd` | `client/client-interfaces` | Los 4 puertos del cliente Java + records |
| `clases/clases-client-app.mmd` | `client/client-app` | Adaptadores, `FachadaCliente` y presentación JavaFX |
| `clases/clases-client-python.mmd` — **NUEVO** | `client-python` | PySide6 completo, con anotado lo que está acoplado: la ventana parchea la fachada con `setattr` y la interroga con `getattr`; `HistorialLocal` **no tiene interfaz**; el grafo cambia de forma en runtime |
| `clases/clases-client-dotnet.mmd` — **NUEVO** | `client-dotnet` | WPF completo. Se ve que `MensajeVM` sí notifica y `UsuarioVM` **no**; y que `OnDesconectado` existe pero **nadie se suscribe** |

### 2.7 Mockups

Fuente `.excalidraw` (canvas vivo, editable en excalidraw.com) + PNG.

| Archivo | Pantalla | Origen de los textos |
|---|---|---|
| `mockups/mockup-login.excalidraw` | Iniciar sesión (3 clientes) | `LoginWindow.xaml`, `login_window.py`, `LoginController.java` |
| `mockups/mockup-chat-telegram-java.excalidraw` | Chat, Telegram oscuro — JavaFX | `chat.fxml`, `ChatController.java`, `chat.css` |
| `mockups/mockup-chat-telegram-python.excalidraw` | Chat, Telegram oscuro — Python Qt | `chat_window.py`, `tema.py` |
| `mockups/mockup-chat-telegram-dotnet.excalidraw` | Chat, Telegram oscuro — WPF | `ChatWindow.xaml`, `Themes/Tema*.xaml` |
| `mockups/mockup-estados.excalidraw` | Estados de entrega de la burbuja | `_MessageRow.actualizar_estado`, `MensajeCell`, `EstadoMensaje` |
| `mockups/mockup-servidor-consola.excalidraw` | Consola administrativa | `VistaConsola.java`, `TablaAscii.java` |
| `mockups/mockup-servidor-escritorio.excalidraw` | Vista escritorio (compuesta: usuarios, pool, difundir) | `VistaEscritorio.java`, `estilos/escritorio.css` |
| `mockups/mockup-chat.excalidraw` | Chat, tema claro (**anterior a los 3 Telegram**) | — |

> **Los 3 `mockup-chat-telegram-*` son el diseño vigente** y este índice es la primera vez que se
> listan; la versión anterior del README solo mencionaba 5 mockups y se los saltaba. Los 3 son
> clones: 92 elementos idénticos y **una** línea de texto distinta (`JavaFX` / `Python Qt` / `WPF`),
> lo que es correcto porque el diseño es el mismo y solo cambia el toolkit.

### 2.8 Modelo relacional

Son **dos modelos**, no uno. Es el error más fácil de cometer aquí.

| Archivo | Modelo | Tablas | Origen |
|---|---|---|---|
| `modelo-relacional/er-modelo-servidor.mmd` | Servidor | 7 | `db/migration/{mysql,oracle,postgres}/V1__init.sql` |
| `modelo-relacional/er-modelo-local.mmd` | Local SQLite | 3 | `schema-local.sql` (3 copias idénticas) |

**Servidor.** `usuarios`, `sesiones_activas`, `conexiones`, `archivos`, `mensajes`,
`registro_acciones`, `configuracion_limites`. El **modelo lógico es idéntico en los tres
dialectos**; solo cambia el DDL. El diagrama es uno solo: generar tres insinuaría tres modelos
donde hay uno.

`mensajes` referencia `usuarios` **dos veces** (remitente y destinatario), por eso el diagrama usa
los alias `USUARIOS_REMITENTE` y `USUARIOS_DESTINATARIO`: sin ellos queda ilegible.
`V2__tope_archivo_50mb.sql` sube `max_tamano_archivo` a **50 MB** y el diagrama ya muestra el valor
final.

**Local.** `historial_local`, `cache_usuarios`, `pendientes_envio`. **No tienen claves foráneas.**
`historial_local.origen`/`destino` guardan el código como `TEXT`, no un `id` con FK; el diagrama
marca esas relaciones como *lógicas*, porque afirmar integridad referencial ahí sería falso.

## 3. Plan de consolidación (DIAGRAMAS vs ENTREGA vs FINALES)

**Estado: planificado, no ejecutado.** No se ha movido ni borrado nada.

### 3.1 Qué hay hoy

37 `.mmd` en 3 carpetas, con solapamiento pesado. Los 37 archivos tienen la **misma fecha de
modificación** (artefacto de checkout), así que la antigüedad hay que deducirla del contenido:

- `DIAGRAMAS/` **gana en todo lo duplicado**: sus 18 `.mmd` derivan del código y los `.sql` reales,
  llevan atributos, `notes`, las clases `Proveedor` SPI, el inventario de los 13 índices del ER, y
  cubren MySQL + Oracle + Postgres.
- `DIAGRAMAS_FINALES/` **pierde en lo duplicado** pero tiene **6 `.mmd` únicos** y **7 mockups
  únicos**.
- `DIAGRAMAS_ENTREGA/` tiene **4 diagramas únicos** y el resto son subconjuntos.

### 3.2 Veredicto por archivo

| Archivo de las carpetas viejas | ¿Cubierto por `DIAGRAMAS/`? | Acción propuesta |
|---|---|---|
| `FINALES/clases/02-clases-puertos-adaptadores` | Sí, por 7 archivos | **Retirar** |
| `FINALES/clases/09-clases-base` | Sí, por 3 archivos | **Retirar** |
| `FINALES/clases/01-componentes-servidor` | Parcial (~50 %) | **Fusionar** lo único (`InicioServidor`, las 2 vistas, `Base`) dentro de `arq-vista-general` |
| `FINALES/clases/04-clases-cliente` | Solo el subgrafo Java | **Conservar como `clases/clientes-3-toolkits.mmd`**: es el único que muestra `.NET` y `Python` |
| `FINALES/clases/03-clases-fachada-nucleo` | **No** | **Migrar** como `clases/clases-servidor-app.mmd` |
| `FINALES/clases/05-clases-inicio-vistas` | **No** | **Migrar** como `clases/clases-inicio-vistas.mmd` |
| `FINALES/clases/06-clases-fachada-casos` | **No** | **Migrar** como `clases/clases-fachada-casos.mmd` (los 12 casos de uso como clases) |
| `FINALES/clases/07-clases-nucleo` | **No** | **Migrar** como `clases/clases-nucleo.mmd` |
| `FINALES/clases/08-clases-persistencia` | **No** | **Migrar** como `clases/clases-persistencia.mmd` (único diagrama JPA) |
| `FINALES/modelo-relacional/er-mysql` | Sí, y es **menos** completo | **Retirar** (pierde Oracle/Postgres y todos los índices) |
| `FINALES/modelo-relacional/er-sqlite` | Sí | **Retirar** |
| `FINALES/mockups/*` (12) | 5 con versión mejor en `DIAGRAMAS/` | **Migrar los 7 de servidor** (`-usuarios`, `-conectados`, `-mensajes`, `-informes`, `-logs`, `-pool`, `-difundir`) y **retirar los 5 duplicados** |
| `ENTREGA/01-arquitectura` | No (otra pregunta: flujo de datos, no puertos) | **Migrar** como `clases/arq-flujo-datos.mmd` |
| `ENTREGA/02-flujo-login` | **Parcial**: ahora `secuencia/seq-conexion-y-trama.mmd` + `flujos/act-login.mmd` | **Retirar** (queda cubierto y mejor) |
| `ENTREGA/03-flujo-mensaje` | **Parcial**: ahora `flujos/act-envio-mensaje.mmd` | **Retirar** |
| `ENTREGA/04-flujo-difusion` | **No** | **Migrar** como `flujos/act-difusion.mmd` |
| `ENTREGA/05`, `06` | Sí | **Retirar** |
| `ENTREGA/07-er-servidor`, `08-er-local` | Sí | **Retirar** |

**Resultado esperado:** `DIAGRAMAS_ENTREGA` y `DIAGRAMAS_FINALES` **vacías y borradas**, todo el
contenido único migrado a `DIAGRAMAS/`, y una sola carpeta que `render.ps1` sí regenera.

### 3.3 Cómo se ejecuta la consolidación

En este orden, para que no haya ningún momento sin diagrama:

1. **Copiar** (no mover) cada único a su destino en `DIAGRAMAS/`, con el nombre de §3.2.
2. **Actualizar `$anchos`** de `render.ps1` si alguno de los copiados necesita ancho propio.
3. **Renderizar** y **mirar** los nuevos PNG.
4. Actualizar este índice (§2) con los nombres definitivos.
5. Solo entonces, **borrar** `DIAGRAMAS_ENTREGA` y `DIAGRAMAS_FINALES`.

### 3.4 Qué falta después de consolidar

| Diagrama | Por qué |
|---|---|
| Diagramas de clases de las **vistas del servidor** (`VistaConsola` + `VistaEscritorio`) | Solo existe un eco en `FINALES/05`, sin campos y sin JavaFX. Se resuelve con la migración de §3.2 |
| **Dependencias de paquetes/módulos** como matriz | `arq-vista-general` lo hace de forma informal con etiquetas en prosa; no es una matriz de dependencias |
| Mockups de los **estados de conexión** (conectando / conectado / sin red / sesión cerrada) | Son los 4 estados de `PAT-03` y **ningún cliente los muestra todavía** (`UIX-00a`). Se dibujarán cuando exista la feature |
| Mockups de los **estados vacíos** y de los **diálogos de error** | `UIX-13`, `UIX-15` |

## 4. Hechos de fidelidad

Cosas que se han verificado en el código y que conviene no contradecir:

- `ProtocoloComunicacion.enviar` declara `throws IOException` mientras la interfaz declara
  `throws Exception`. Es un *narrowing* legítimo.
- **`LogoutCommand`, `ListarCommand` y `ListarUsuariosCommand` reciben `respuestas` en el
  constructor pero no lo guardan en ningún campo** — y por eso tampoco aplican la guarda de
  autenticación que sí aplican los otros 12. Es el hallazgo `SEC-05` de
  [`ANALISIS_TECNICO.md`](../ANALISIS_TECNICO.md) §1, y está dibujado en
  `secuencia/seq-conexion-y-trama.mmd`.
- `FabricaDelDespachador.NULA` se asigna pero **recibe 0 llamadas**: `Proveedor` sobreescribe
  `crear(FachadaSalida)`, así que la ruta sin salida nunca se toma.
- `ManejadorConexion extends Thread`: el pool es un conjunto de hilos concretos con *token bucket*
  (`fichas`/`ultimoRecargoNs`), no un `ExecutorService`.
- `Usuarios` guarda el directorio en un `ConcurrentHashMap` **en memoria**. La tabla `usuarios` la
  persiste `servidor-app`.
- `Servicios` trata un límite de `0` como *sin límite*.
- Hay **10 filtros** en `utilerias`, no 11: 1 de archivo + 6 de imagen + 3 de texto, más
  `FiltroVisualBase` (base abstracta de los 5 visuales) e `Imagenes` (helper estático).
- El filtro de imagen produce **5 derivadas**; `FINALES/09` decía "5 filtros visuales" y se
  confundía con los 6 de imagen.
- La tabla `mensajes` tiene la columna **`ip_destinatario` que nunca se escribe ni se lee**
  (`MUERT-12`). La `ipDestinatario` del DTO de red sí está viva: no confundir.
- Sin Python instalado en esta máquina, así que el verificador `check_scene.py` de `x9-diagrams`
  no se ejecuta; los mockups se validan con el render headless del propio Excalidraw.

## 5. Referencias

- Análisis con la evidencia de por qué faltaba cada uno:
  [`ANALISIS_TECNICO.md`](../ANALISIS_TECNICO.md) §6.
- Textos literales de la UI: [`textos-ui.md`](textos-ui.md).
- Tareas del roadmap: [`ESTADO_ROADMAP.md`](../ESTADO_ROADMAP.md) (`DIAG-*`).
