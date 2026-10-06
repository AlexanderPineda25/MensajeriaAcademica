# DIAGRAMAS

Diagramas del proyecto cliente-servidor, derivados del código y de los `.sql` reales. Cada
diagrama viene en dos formatos: **source editable** (lo que se revisa y se regenera) y **render**
(PNG/SVG/PDF, lo que se lee en el informe).

Actualizado: **2026-10-06** — estado posterior a DIAG-07/08: el índice y renders canónicos están en `DIAGRAMAS/`; las carpetas legadas se conservan hasta revisar los candidatos de §3.
Los 16 diagramas agregados en la auditoría inicial incluyen casos de uso, secuencia, actividad,
estados, despliegue y clases de Python/.NET.

## 0. Estado de los renders de esta ronda

> ~~Los diagramas Mermaid nuevos están SIN render.~~ **Resuelto 2026-10-06 (DIAG-07/08,
> Lote G):** `mmdc` 12 + `node` disponibles; los 45 `.mmd` de `DIAGRAMAS/`
> renderizan a PNG+SVG con **0 FALLO** (`.\render.ps1 -Solo mermaid`, exit 0)
> y cada PNG nuevo/migrado se revisó visualmente. Notas:
>
> - `usecaseDiagram` no existe en mermaid: los 3 `uc-*` se reescribieron como
>   `flowchart TB` con el mismo contenido.
> - Esta build de mermaid no acepta notas multilínea (`note ... end note`):
>   las 13 afectadas se pasaron a una línea con `<br/>`; `note "x" as N1` no
>   vale en `classDiagram-v2` (se fusionó en `note for`).
> - En `flowchart`, las etiquetas `[...]` con `"`, `(...)`, `;` o `<` sin
>   entrecomillar rompen el parse: se entrecomillaron (comillas internas a `'`).

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
| Ancho por **nombre de archivo** | `$anchos` fija 12 nombres de archivo (incluye ER, arquitectura y fuentes migradas); el resto sale a **2000 px** | Si un diagrama nuevo sale ilegible, añadir su `BaseName` al mapa |
| El PDF depende del **nombre**, no del tipo | El gate es `BaseName -like 'er-modelo-*'`. Si un ER se renombra, **pierde el PDF** en silencio | Mantener el prefijo `er-modelo-` en los ER |
| Código de salida | **DIAG-08 corregido:** el contador `$script:fallos` termina con `exit 1` si algún render falla y `exit 0` si no hubo fallos | Usar el exit code; comprobar también visualmente el PNG |
| `Test-Herramienta` **no aborta** | Si falta una herramienta, avisa y salta esa sección; las demás siguen | Normal, pero no confíes en el exit code |
| Rama `.puml` sin uso actual | No hay ningún `.puml` en el repo; `-Solo plantuml` no hace nada | Mantenerla mientras no se adopte PlantUML; revisar por separado si cambia la herramienta |
| Rama de Excalidraw puede tragarse un fallo real | `try{}catch{}` + `2>$null` neutraliza el error, y luego el éxito se juzga por `$LASTEXITCODE`, que puede venir de una llamada anterior | Contexto al leer un "OK" sospechoso |

## 2. Índice completo

### 2.1 Casos de uso — **NUEVO** (no existía ningún `usecaseDiagram` en el repo)

| Archivo | Contenido | Origen |
|---|---|---|
| `casos/uc-cliente.mmd` | 21 casos del **actor Usuario**, con las relaciones `<<extend>>`-like entre ellos (UC-05 → UC-03/UC-04) y las 5 de almacenamiento local | `REQUERIMIENTOS.md` §4.1–4.3 |
| `casos/uc-servidor.mmd` | 20 casos del **actor Administrador** + usuario conectado como actor secundario, agrupados en arranque / operación / informes / supervisión | `REQUERIMIENTOS.md` §3.1–3.8 |
| `casos/uc-sistema.mmd` | Los 2 actores sobre el mismo sistema, con el cruce que importa: qué acciones del usuario alimentan qué informe | vista de conjunto |

> **Formato.** mermaid no tiene `usecaseDiagram`: los 3 están escritos como
> `flowchart TB` (actores = elipses, casos = rectángulos por subgrafo,
> inclusiones = flechas punteadas). Mismo contenido, sintaxis renderizable
> con `mmdc` 12 (verificado 2026-10-06).

### 2.2 Secuencia — **NUEVO** (antes solo había 3, en `DIAGRAMAS_ENTREGA`)

| Archivo | Contenido |
|---|---|
| `secuencia/seq-conexion-y-trama.mmd` | Apertura TCP/TLS, adquisición del pool, JSON enmarcado (4 bytes big-endian + JSON UTF-8), despacho, LOGIN público y validación de sesión para los comandos protegidos; `SEC-05` está cerrado |
| `secuencia/seq-login.mmd` — migrado de `ENTREGA/02` | Login simple Usuario→Login→Fachada→Servidor (OK/error). Vista de secuencia; la de decisión es `flujos/act-login.mmd` |
| `secuencia/seq-envio-mensaje.mmd` — migrado de `ENTREGA/03` | Envío con acuses PENDIENTE→ENVIADO→ENTREGADO→LEIDO y rama sin red. Vista de secuencia; la de decisión es `flujos/act-envio-mensaje.mmd` |
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
| `flujos/act-difusion.mmd` — migrado de `ENTREGA/04` | Difusión administrativa: BROADCAST → pool de entrega acotado → progreso N/total → resumen |

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
| `clases/arq-flujo-datos.mmd` — migrado de `ENTREGA/01` | — | La otra vista de arquitectura: flujo de datos TCP→despachador→fachada→persistencia (no puertos) |
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
| `clases/clases-servidor-app.mmd` — migrado de `FINALES/03` | `servidor-app` | Fachada, núcleo y vistas: qué hace cada uno (sin atributos) |
| `clases/clases-inicio-vistas.mmd` — migrado de `FINALES/05` | `servidor-app` | Arranque (`InicioServidor`+`Fabrica`) y UI del servidor (consola+escritorio). Cubre el hueco de §3.4 |
| `clases/clases-fachada-casos.mmd` — migrado de `FINALES/06` | `servidor-app` | `FachadaDeServicios` + los 12 casos de uso como clases |
| `clases/clases-nucleo.mmd` — migrado de `FINALES/07` | `servidor-app` | Núcleo: mensajes, eventos, conexiones y almacén (puertos + adaptadores) |
| `clases/clases-persistencia.mmd` — migrado de `FINALES/08` | `servidor-app` | Persistencia JPA: adaptador, repositorios y entidades (único diagrama JPA) |

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
| `mockups/mockup-serv-usuarios.excalidraw` — migrado de `FINALES` | Pestaña Usuarios del servidor | `VistaEscritorio.java` |
| `mockups/mockup-serv-conectados.excalidraw` — migrado de `FINALES` | Pestaña Conectados | `VistaEscritorio.java` |
| `mockups/mockup-serv-mensajes.excalidraw` — migrado de `FINALES` | Pestaña Mensajes | `VistaEscritorio.java` |
| `mockups/mockup-serv-informes.excalidraw` — migrado de `FINALES` | Pestaña Informes | `VistaEscritorio.java` |
| `mockups/mockup-serv-logs.excalidraw` — migrado de `FINALES` | Pestaña Logs | `VistaEscritorio.java` |
| `mockups/mockup-serv-pool.excalidraw` — migrado de `FINALES` | Estado del pool | `VistaEscritorio.java` |
| `mockups/mockup-serv-difundir.excalidraw` — migrado de `FINALES` | Difundir a todos | `VistaEscritorio.java` |
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

**Estado: migración ejecutada 2026-10-06 (DIAG-07, Lote G).** Pasos 1–4 de §3.3
hechos: 5 clases de FINALES + 4 de ENTREGA + 7 mockups del servidor copiados
a `DIAGRAMAS/` con los nombres de §3.2 (ENTREGA 02/03 como
`secuencia/seq-login.mmd` y `secuencia/seq-envio-mensaje.mmd`), `$anchos`
ampliado, todo renderizado con 0 FALLO y revisado, índice §2 actualizado.
Paso 5 pendiente: las carpetas viejas **no están vacías** (quedan los archivos
con veredicto Retirar/Fusionar de §3.2) y **no se borraron**.

### 3.1 Inventario actual y línea base anterior a DIAG-07

Inventario físico consultado el 2026-10-06: `DIAGRAMAS/` contiene **45 `.mmd`**, con 45 PNG y
45 SVG renderizados, más 15 fuentes `.excalidraw` y sus 15 PNG. Los legados conservan 4 `.mmd` +
4 PNG en `DIAGRAMAS_ENTREGA/` y 6 `.mmd` + 6 PNG + 5 `.excalidraw` + 5 PNG en
`DIAGRAMAS_FINALES/`. Los números «37 en 3 carpetas» de la auditoría eran la línea base anterior
a DIAG-07, no el inventario de hoy.

- `DIAGRAMAS/` es el catálogo canónico: sus fuentes derivan del código y los `.sql` reales,
  llevan atributos, `notes`, las clases `Proveedor` SPI, el inventario de los 13 índices del ER, y
  cubren MySQL + Oracle + Postgres.
- La tabla §3.2 registra la propuesta de conciliación anterior. Los originales se dejan en sitio;
  cada candidato se puede conservar, fusionar o retirar solo después de verificar que la versión
  canónica cubre sus relaciones y detalles.

### 3.2 Archivos legados que aún están presentes y opciones de conciliación

| Archivo que permanece físicamente | Equivalente/destino posible | Recomendación para revisión futura |
|---|---|---|
| `DIAGRAMAS_ENTREGA/05-clases-servidor.mmd` + `.png` | `clases/clases-servidor-app.mmd`, `clases-inicio-vistas.mmd`, `clases-fachada-casos.mmd`, `clases-nucleo.mmd`, `clases-persistencia.mmd` | Comparar clases y relaciones exclusivas; retirar el legado solo si el conjunto canónico lo cubre. |
| `DIAGRAMAS_ENTREGA/06-clases-cliente.mmd` + `.png` | `clases/clases-client-interfaces.mmd`, `clases-client-app.mmd`, `clases-client-python.mmd`, `clases-client-dotnet.mmd` | Fusionar la vista cruzada de toolkits si aporta relaciones únicas; después cotejar antes de retirar. |
| `DIAGRAMAS_ENTREGA/07-er-servidor.mmd` + `.png` | `modelo-relacional/er-modelo-servidor.mmd` | Candidato a retiro tras comparar entidades, columnas y restricciones. |
| `DIAGRAMAS_ENTREGA/08-er-local.mmd` + `.png` | `modelo-relacional/er-modelo-local.mmd` | Candidato a retiro tras comparar columnas y restricciones. |
| `DIAGRAMAS_FINALES/clases/01-componentes-servidor.mmd` + `.png` | `clases/arq-vista-general.mmd`, `clases-servidor-app.mmd`, `clases-inicio-vistas.mmd` | Fusionar los detalles únicos; retirar solo si el contenido queda cubierto. |
| `DIAGRAMAS_FINALES/clases/02-clases-puertos-adaptadores.mmd` + `.png` | Diagramas `clases-int-*`, `clases-componentes-*` y `clases-comp-*` | Candidato a retiro después de cotejar puertos, implementaciones y relaciones. |
| `DIAGRAMAS_FINALES/clases/04-clases-cliente.mmd` + `.png` | Diagramas por toolkit y `clases/arq-flujo-datos.mmd` | Conservar hasta documentar sus relaciones cruzadas o fusionarlas en la vista de conjunto. |
| `DIAGRAMAS_FINALES/clases/09-clases-base.mmd` + `.png` | Diagramas canónicos por módulo | Candidato a retiro si no contiene clases o relaciones únicas. |
| `DIAGRAMAS_FINALES/modelo-relacional/er-mysql.mmd` + `.png` | `modelo-relacional/er-modelo-servidor.mmd` | Candidato a retiro tras cotejo; conservar si se necesita como versión histórica específica. |
| `DIAGRAMAS_FINALES/modelo-relacional/er-sqlite.mmd` + `.png` | `modelo-relacional/er-modelo-local.mmd` | Candidato a retiro tras cotejar columnas y restricciones. |
| `DIAGRAMAS_FINALES/mockups/mockup-login.*`, `mockup-chat.*`, `mockup-estados.*` | Fuentes/renderes homónimos de `DIAGRAMAS/mockups/` | Los nombres coinciden, pero los hashes son distintos. Comparar visual y semánticamente; elegir qué versión conservar o marcar como histórica. |
| `DIAGRAMAS_FINALES/mockups/mockup-servidor-consola.*`, `mockup-servidor-escritorio.*` | Mockups homónimos de `DIAGRAMAS/mockups/` | Mantener hasta compararlos con las vistas actuales; actualizar los canónicos antes de retirar diseños previos. |

`.*` representa la fuente editable y su PNG. Las diferencias de hash no determinan cuál versión es
válida. El historial de fuentes migradas se conserva en el texto de §3 y en `PLAN_MEJORAS.md`; la
tabla anterior lista únicamente lo que permanece en las carpetas legadas a la fecha del inventario.
No se retiró ningún archivo en esta revisión.

### 3.3 Cómo se ejecuta la consolidación

En este orden, para que no haya ningún momento sin diagrama:

1. **Copiar** (no mover) cada único a su destino en `DIAGRAMAS/`, con el nombre de §3.2.
2. **Actualizar `$anchos`** de `render.ps1` si alguno de los copiados necesita ancho propio.
3. **Renderizar** y **mirar** los nuevos PNG.
4. Actualizar este índice (§2) con los nombres definitivos.
5. Presentar el inventario al responsable y decidir si se conserva, se comprime como archivo
   histórico o se retira. Comparar referencias, fuentes y renders, y actualizar los índices antes
   de cualquier limpieza posterior.

### 3.4 Qué falta después de consolidar

| Diagrama | Por qué |
|---|---|
| Fidelidad de los diagramas de clases de las **vistas del servidor** (`VistaConsola` + `VistaEscritorio`) | `clases/clases-inicio-vistas.mmd` ya existe tras migración; falta cotejar campos/relaciones con el código actual |
| **Dependencias de paquetes/módulos** como matriz | `arq-vista-general` lo hace de forma informal con etiquetas en prosa; no es una matriz de dependencias |
| Mockups de los **estados de conexión** (conectando / conectado / sin red / sesión cerrada) | Los 4 estados ya están implementados (`PAT-03` / `UIX-00a`); falta crear y renderizar los mockups |
| Mockups de los **estados vacíos** y de los **avisos inline de error** | Las features `UIX-13` y `UIX-15` ya están implementadas; falta crear y renderizar los mockups |
| Mockups de la **vista administrativa posterior a los cambios** | Los mockups de servidor migrados son diseños previos; reconciliar pestañas/ficha con `VistaEscritorio` antes de presentarlos como estado actual |

## 4. Hechos de fidelidad

Cosas que se han verificado en el código y que conviene no contradecir:

- `ProtocoloComunicacion.enviar` declara `throws IOException` mientras la interfaz declara
  `throws Exception`. Es un *narrowing* legítimo.
- **Historial de `SEC-05`:** antes del arreglo, `LogoutCommand`, `ListarCommand` y
  `ListarUsuariosCommand` no aplicaban la guarda. El hallazgo está cerrado por el SPI fail-closed,
  guardas y pruebas; no usar el diagrama histórico como evidencia de un fallo vigente. Está
  documentado en [`ANALISIS_TECNICO.md`](../ANALISIS_TECNICO.md) §1 y señalado en
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
- En la ronda DIAG-07 no se ejecutó el validador `check_scene.py` de `x9-diagrams`; los mockups se
  renderizaron y revisaron mediante el flujo headless de Excalidraw. Repetir esa validación si se
  adopta ese validador como requisito.

## 5. Referencias

- Análisis con la evidencia de por qué faltaba cada uno:
  [`ANALISIS_TECNICO.md`](../ANALISIS_TECNICO.md) §6.
- Textos literales de la UI: [`textos-ui.md`](textos-ui.md).
- Tareas del roadmap: [`ESTADO_ROADMAP.md`](../ESTADO_ROADMAP.md) (`DIAG-*`).
