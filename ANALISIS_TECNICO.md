# Análisis técnico — auditoría de código, UI y patrones

Actualizado: **2026-10-05**. Este documento es la **base de evidencia** de la ronda de
planificación 2026-10-05. No es una lista de deseos: cada hallazgo lleva `archivo:línea` y el
conteo de referencias que lo sostiene.

> **Regla de este documento.** Nada de lo que está aquí se ha modificado. Solo se planificó.
> El tablero que manda hacia adelante es [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md); el plan
> ejecutable de refactor es [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md).

## 0. Cómo se leyó el código

| Método | Alcance | Sirve para |
|---|---|---|
| Conteo de referencias con regex sobre el repo entero | 223 `.java`, 34 `.py`, 28 `.cs` + 377 no-código (FXML, XAML, SQL, properties) | Separar **muerto cierto** (0 refs fuera de su definición) de **probablemente muerto** |
| Lectura archivo por archivo de la capa de presentación | los 3 clientes completos | Hallazgos de UI/UX y de architecture |
| Conteo de literales y de paleta por cliente | los 11 archivos de presentación | Contrato visual compartido |

Advertencia metodológica: `rg` no está instalado en esta máquina; los conteos salen de un
equivalente con el motor de regex de .NET. Un caso que **sí** cambió resultados: la búsqueda
sin distinción de mayúsculas produjo más de 20 falsos positivos (`CONECTADO` emparejando con
el campo `conectado`). Todos los conteos de este documento son **case-sensitive**.

<a id="hallazgo-seguridad"></a>

## 1. Hallazgo de seguridad — P0, único de severidad alta

### SEC-05 · `LISTAR_USUARIOS` responde sin autenticación

**Cadena verificada, extremo a extremo:**

| Eslabón | Archivo:línea | Qué hace |
|---|---|---|
| 1. El despachador no pone puerta global | `server/comp-protocolo-clienteservidor/.../impl/Despachador.java:55-62` | `procesar()` busca el `CommandHandler` y lo invoca. **No consulta `sesion.autenticada()`**. |
| 2. La guarda es responsabilidad de cada `Command` | `.../commands/Comandos.java:14-20` | `exigirAutenticacion(...)` existe y responde `ERROR "autenticate primero con LOGIN"`. |
| 3. 12 de 15 comandos sí la llaman | `.../commands/TextoCommand.java:23` (y los otros 11) | `if (!Comandos.exigirAutenticacion(mensaje, sesion, respuestas)) return;` |
| 4. **`ListarUsuariosCommand` no la llama** | `.../commands/ListarUsuariosCommand.java:14-20` | Recibe `respuestas` y **no lo guarda en ningún campo**; su `handle` va directo a `salida.listarUsuarios(sesion, mensaje.id())`. |
| 5. El caso de uso tampoco valida | `server/servidor-app/.../casosdeuso/ListarCaso.java:45-62` | `listarUsuarios` arma el directorio completo y lo envía. **Cero comprobaciones de sesión.** |

**Consecuencia.** Cualquiera que abra un socket TCP contra `5000` y envíe una trama
`LISTAR_USUARIOS` — **sin código ni contraseña** — recibe el directorio entero de la
comunidad académica: código, nombres, apellidos y programa (4 campos por usuario,
`ListarCaso.java:48-49`).

**Alcance.** `LISTAR_CONECTADOS` y `LOGOUT` comparten el mismo hueco
(`ListarCommand.java:14-20`, `LogoutCommand.java:14-20`). Los dos son de menor gravedad
—`LOGOUT` solo cierra la sesión anónima que ya es del atacante y `LISTAR_CONECTADOS` solo
revela quién está en línea— pero el patrón es el mismo y lo que hay que cerrar es el patrón.

**Por qué está vivo hoy.** Ninguna suite lo detecta porque las 3 clases reciben `respuestas`
en el constructor y `FabricaDelDespachador.java:44-46,56` lo pasa, así que el cableado *parece*
completo. Un test que solo compruebe "responde bien a un usuario autenticado" pasa igual.

**Cierre propuesto (no aplicado):** añadir la guarda a los 3 comandos **y** un test de
regresión que envíe cada trama sin autenticar y exija `ERROR`. El test es la parte importante:
sin él, el hueco vuelve el próximo vez que alguien añade un comando nuevo.

### Corolario — el fallo de guarda no es visible en la firma

Que `respuestas` sea un parámetro **no usado** en los 3 comandos es exactamente lo que hace
indetectable el fallo: la firma dice "este comando puede responder errores", el cuerpo dice lo
contrario. Es un argumento para `PUG-04` (revisor obligatorio de comandos nuevos).

## 2. Código muerto

Leyenda: **C** = muerto cierto (0 referencias fuera de su propia definición).
**P** = probablemente muerto (0 referencias estáticas, pero alcanzable por framework,
reflexión, anotación o contrato SPI).

### 2.1 Servidor Java — 13 símbolos C + 2 P

| ID | Archivo:línea | Símbolo | Refs | Tipo | Acción |
|---|---|---|---|---|---|
| MUERT-01 | `servidor-app/.../transversal/fachada/Fachada.java:69-71` + `fachadadeservicios/FachadaDeServicios.java:265-268` | `archivoParaVista(long)` | 2 (las 2 declaraciones) | C | Borrar `default` + override. `VistaEscritorio` solo usa `bytesArchivoParaVista` |
| MUERT-02 | `client/client-app/.../fachada/FachadaCliente.java:465-467` | `sesionesInfo()` | 1 | C | Borrar. Devuelve el literal `-1` |
| MUERT-03 | `client/client-app/.../fachada/FachadaCliente.java:487-490` | `solicitarHistorialServidor(String,int)` | 1 | C | Borrar. Reemplazado por `traerHistorial` (`:501`) en el mismo call site |
| MUERT-04 | `common-protocol/dto/.../interno/UsuarioInformeDTO.java:12-15` | `conConectado(boolean)` | 1 | C | Borrar el helper `with*`; el valor va por el constructor canónico (`FachadaDeServicios.java:218`) |
| MUERT-05 | `common-protocol/dto/.../interno/EstadoServidor.java:16-20` | constructor de 6 args | 0 llamadas | C | Borrar. El único `new` del repo usa el de 10 args (`FachadaDeServicios.java:148`) |
| MUERT-06 | `servidor-app/.../vistaescritorio/VistaEscritorio.java:109-111` | `fachadaParaTests(Fachada)` | 1 | C | Borrar. El test usa el estático `aplicarFiltroVista`, nunca este método |
| MUERT-07 | `servidor-app/.../persistencia/entidades/Usuario.java:83` | `nombreCompleto()` | 1 | C | Borrar. No es accesor JPA (no es `getX`/`isX`) |
| MUERT-08 | `common-protocol/dto/.../tipos/TipoMensaje.java:12` | `REGISTRO_RESPUESTA` | 1 | C | Borrar. Ausente de `Despachador.mapaTipos()` |
| MUERT-09 | `common-protocol/dto/.../interno/TipoAccion.java:16` | `HISTORIAL` | 1 | C | Borrar. Nunca se pasa a `eventos.registrar` |
| MUERT-10 | `common-protocol/dto/.../interno/TipoAccion.java:17` | `DESCARGA` | 1 | C | Ídem |
| MUERT-11 | `common-protocol/dto/.../interno/TipoAccion.java:5` | `CONECTADO` | 3¹ | C | Borrar + arreglar el Javadoc obsoleto de `LoginConteoDTO.java:5` |
| MUERT-12 | `servidor-app/.../persistencia/entidades/Mensaje.java:64-65` | campo `ipDestinatario` | 1 | C | Quitar campo + columna `ip_destinatario` por migración Flyway. Siempre NULL |
| MUERT-13 | `.../commands/{Logout,Listar,ListarUsuarios}Command.java:14` | parámetro `respuestas` | decl. sin uso | C | Quitar el parámetro. Ver §1: **el hueco de SEC-05 está justo aquí** |
| MUERT-14 | `.../impl/FabricaDelDespachador.java:63-142` | `NULA` (Null Object anónimo) | 1 (`:149`) | **P** | Ver nota abajo |
| MUERT-15 | `server/utilerias/.../FiltroMain.java` (clase entera) | `FiltroMain` | 0 prod. / 7 en test | **P** | CLI opcional documentada en `PLAN.md §3.6`. Decidir: entregable o mover a módulo `tools` |

¹ `CONECTADO` = su declaración + el Javadoc de `LoginConteoDTO.java:5` + la etiqueta de UI
`"CONECTADO"` en `VistaConsola.java:227`. Ninguna de las dos últimas es un uso.

> **MUERT-14, en detalle.** `NULA` se declara en `FabricaDelDespachador.java:63-142` y su única
> referencia es `:149`, dentro de `Proveedor.crear()`. Pero `Proveedor` **sobreescribe**
> `crear(FachadaSalida)` en `:153`, así que `Fabrica.montar` (`:174`) nunca pasa por `NULA`;
> y `ServiceLoader.load(...)` (`Fabrica.java:114`) solo *instancia* al proveedor, no llama
> `crear()`. El objeto se asigna en la inicialización de clase y **recibe 0 llamadas**.
> No es "muerto cierto" porque `crear()` es un método obligatorio del contrato
> `ProveedorClienteServidor`. Salida: quitar `crear()` del `default` del SPI y borrar las 80
> líneas, o dejarlas con un comentario que explique por qué existen.

**Verificado como NO muerto** (para que nadie los toque): los 10 filtros de `utilerias`
(los 10 aparecen en `TuberiaFactory` **y** en `FiltroMain.REGISTRO`), los 12 casos de uso de
`casosdeuso/` (los 12 se instancian en `Fabrica.java` y sus 12 accesores se invocan en 20
sitios de `FachadaDeServicios.java`), las 25 clases de `common-protocol/dto/interno`, los 49
archivos de test (0 `@Disabled`/`@Ignore`), y las clases con 0 refs Java que viven por
anotación: `ServerApplication`, `PasswordConfig`, `FlywayMigrationAdapter`,
`JpaPersistenciaAdapter`, `DataSourceAdapter`, `FlywayGroupProperties`, `LoginController`
(vive por `login.fxml:15 fx:controller=`), `VistaEscritorio.start` (lo invoca JavaFX) y los
5 `Proveedor*` (los carga `ServiceLoader`).

### 2.2 Cliente Python — 6 C

| ID | Archivo:línea | Símbolo | Refs | Acción |
|---|---|---|---|---|
| MUERT-16 | `client-python/fachada/fachada_cliente.py:618-620` | `_guardar_propio(...)` | 1 | Borrar. Envoltorio de `historial.guardar_mensaje`, y los 6 call sites ya llaman a `historial` directo (`:172, 219, 282, 360, 475, 656`) |
| MUERT-17 | `client-python/ui/login_window.py:186-188` | `mostrar_aviso(texto)` | 1 | Borrar. El aviso se resuelve inline en `:123-125` y `ChatWindow` nunca lo llama |
| MUERT-18 | `client-python/ui/registro_window.py:1-3` | módulo completo | 0 en producción¹ | Ver nota |
| MUERT-19 | `client-python/ui/chat_window.py:212` + `:1200` | `self._tareas_imagen` | 2 | **No es solo muerto: crece sin límite.** Se añade (`:1200`) y nunca se lee ni se limpia. 2 líneas borradas y un leak de memoria menos |
| MUERT-20 | `client-python/componentes/autenticacion.py:2` | `import uuid` | 0 | Import no usado |
| MUERT-21 | `client-python/tests/test_archivo_transferencia.py:7,10`, `test_fachada.py:6`, `test_tls.py:19` | 4 imports | 0 | Imports no usados |
| MUERT-22 | `client-python/tests/test_protocolo.py:37,38,47` | `ServidorStub.recibidos`, `.empujes` | 2² | `recibidos` solo recibe `.append` y las aserciones leen **otro** atributo (`TestProtocolo.recibidos`, `:73,76,95,98`) |
| MUERT-23 | `client-python/tests/test_ui.py:41,44`, `test_archivo_transferencia.py:52`, `test_fachada.py:45` | 4 métodos de fake | 1³ | Métodos de doble de prueba que ningún test invoca |
| MUERT-24 | `client-python/ui/tema.py:24,25,44,53,54,73` | tokens `prompt`, `campo`, `boton_enviar` (×2 modos) | 2 cada uno⁴ | Entradas de token que nadie lee |

¹ Sus únicas 2 referencias están en `tests/test_archivo_transferencia.py:13,74`, y ambas son
el *test que comprueba que no existe registro*. Nota: el `.gitignore` raíz no cubre
`client-python/dist/`, así que la copia distribuida (`dist/MensajeriaPython/ui/registro_window.py`)
también arrastra el stub.
² Ambas referencias están en la línea de asignación.
³ Solo la definición.
⁴ Las 2 son las propias líneas de definición del token.

**`MUERT-18` con contexto.** `ui/registro_window.py` son 3 líneas: `__all__ = []` y un
comentario de "módulo eliminado". El registro por red se quitó en Fase 10.2 (`RF-S27`), y el
cliente .NET **sí** lo limpió del todo: `RegistroWindow` no existe como tipo, y su única
mención es un comentario que lo declara inalcanzable
(`client-dotnet/Views/LoginWindow.xaml.cs:115-116`). El stub de Python es el resto que
sobró. Se puede borrar el archivo, pero el test que lo referencia debe reescribirse para
comprobar la ausencia por atributo (`not hasattr(...)`), que es lo que realmente quiere
afirmar.

**Falta cubrir dos cosas más del cliente Python:**
- `client-python/client.properties:5` `idioma=es` — **0 lecturas de código en todo el repo.**
  Está en la lista blanca de `transversal/config.py:25` y documentado en `README.md:48`.
- `client-python/requirements.txt:1` `pillow==10.4.0` — **0 imports.** La única mención del
  repo es una frase de `README.md:9`.

**Código de característica inalcanzable desde la UI (decisión de producto, no limpieza):**
`FachadaCliente.difundir(texto)` (`fachada_cliente.py:346-362`) y `DESTINO_BROADCAST`
(`:25`) — el único call site es `tests/test_fachada.py:93`, y `chat_window.py` **no tiene**
ningún control de difusión (`BROADCAST` → 0 hits en `ui/`). Existe la misma situation en
. NET (`FachadaCliente.cs:490-508`, `IFachadaCliente.cs:30`, único caller
`client-dotnet.Tests/FachadaTests.cs:126`). El servidor sí tiene difusión, pero es la del
administrador. Decisión pendiente: botón de difusión en los clientes, o borrar el camino.

### 2.3 Cliente .NET — 6 C

| ID | Archivo:línea | Símbolo | Refs | Acción |
|---|---|---|---|---|
| MUERT-25 | `client-dotnet/Views/ChatWindow.xaml.cs:648-654` | `BtnAdjuntarArchivo_Click` | 1 | **Handler sin cablear.** Ningún `Click=` del XAML lo nombra; el adjunto genérico ya entra por `BtnAdjuntar_Click` (`:577-585`) y `ZonaEnvio_Drop` (`:593-602`) |
| MUERT-26 | `client-dotnet/App.xaml:13-21` | 9 pinceles `Telegram*` | 1 cada uno⁵ | Paleta previa a `UI-09`, superada por `Themes/Tema*.xaml` |
| MUERT-27 | `client-dotnet/Fachada/FachadaCliente.cs:36` | `RutaDbActual` | 1 | Propiedad pública sin uso |
| MUERT-28 | `client-dotnet/Store/LocalStore.cs:81` + `:246` | `cuentaExistia` | 2⁶ | Variable que se escribe y nunca se lee. El `:246` es un `_ = cuentaExistia;` que además **anula el aviso del compilador** |
| MUERT-29 | `client-dotnet/Transversal/TipoMensaje.cs:9,10,28` | `REGISTRO`, `REGISTRO_RESPUESTA`, `LISTAR_USUARIOS_RESPUESTA` | 0⁷ | Constantes de un protocolo que el cliente ya no habla |
| MUERT-30 | 12 `using` sin usar | `Autenticacion.cs:1,3`; `CacheUsuarios.cs:3`; `HistorialLocal.cs:3`; `TramaCodec.cs:2,3`; `IAutenticacion.cs:1`; `TipoMensaje.cs:1`; tests `TramaCodecTests.cs:5,6,7,8` | 0⁸ | Ruido que esconde los reales |

⁵ Solo la línea `x:Key`. ⁶ Ambas en el mismo archivo. ⁷ Ninguna referencia calificada.
⁸ Verificado con barrido de tokens de todo el archivo.

### 2.4 CSS y XAML muertos

| ID | Ubicación | Elemento | Evidencia | Tipo |
|---|---|---|---|---|
| MUERT-31 | `client/.../estilos/chat.css:297-304` y `chat-claro.css:285-292` | `.broadcast-pill` (8 declaraciones) | 0 refs en `.java`/`.fxml` | C |
| MUERT-32 | `chat.css:34-38` vs `:306-308` | `.sidebar-title` declarado **dos veces** | la segunda no añade nada | C |
| MUERT-33 | `chat-claro.css:34-38` vs `:294-296` | `.sidebar-title` declarado dos veces **con valores que se contradicen** | la última gana: el título del sidebar se pinta `#1F3A5F` en vez del `#25343b` pretendido | C + bug visual |
| MUERT-34 | `chat-claro.css:51-57` vs `:65-67` | `.filter-select` segunda regla | mismo valor, bloque no-op | C |
| MUERT-35 | `client/.../chat.fxml:23` | `fx:id="botonRefrescar"` sin campo `@FXML` en `ChatController.java:61-78` | nodo muerto | C |
| MUERT-36 | `client/.../presentacion/ChatController.java:167-172` | `actualizarEstadoEnviar()` inalcanzable | `:168` corta en `isBound()` | C |
| MUERT-37 | `client-dotnet/Views/ChatWindow.xaml:29-33` | `bannerOffline` | 0 refs en `.cs`, `Visibility="Collapsed"` fijo | C + **bug funcional**, ver §3.1 |
| MUERT-38 | `client-dotnet/Views/ChatWindow.xaml:84,95,102,111` | `x:Name` de 4 botones | 0 refs en code-behind (funcionan por `Click=`) | C |

> **No borrar a ciegas.** `.split-pane`, `.split-pane-divider`, `.list-cell` y `.combo-box`
> tienen 0 referencias en `.java`/`.fxml` porque son **clases de estilo integradas** que
> JavaFX pone en runtime. `.split-pane-divider` en concreto (`chat.css:12-15`) necesita
> comprobarse con la aplicación corriendo antes de tocarlo.

### 2.5 Deduplicación que también sale de aquí

No es código muerto sino el mismo concepto escrito dos veces. Sale gratis con los anteriores.

| Ubicación | Duplicación | Nota |
|---|---|---|
| `client-dotnet/Fachada/FachadaCliente.cs:63-84`, `Views/LoginWindow.xaml.cs:38-62`, `Temas/Tema.cs:20-51` | **3 analizadores/gestores propios de `client.properties` en el mismo proyecto** | `CargarServidor` **ignora `tls.*` y `db.url`**, así que el servidor que *muestra* el login puede no ser el que *usa* |
| `client-dotnet/Store/LocalStore.cs:306-352` (`fallbackSchema`) vs `Store/schema-local.sql` vs `common-protocol/.../schema-local.sql` | **3 copias del DDL**, y `Mensajeria.csproj:18-20` ya garantiza que el `.sql` se copie | el fallback es inalcanzable |
| `client-dotnet/Store/LocalStore.cs:257-278` (`AsegurarColumnas`) vs `:356-382` (`MigrarHistorial`) | 2 implementaciones del mismo "añade `estado` + `archivo_id` si falta" | `MigrarHistorial` se invoca desde `Inicializar()` (`:353`), justo después de que `Inicializar()` ya ejecutara el esquema en `:300` |
| `client-python/ui/chat_window.py:442-464` vs `ui/login_window.py:135-157` | `_leer_modo`/`_guardar_modo`/`_cambiar_tema` **byte a byte idénticos** | un arreglo en una ventana no llega a la otra |
| `client/.../ChatController.java:752-773`, `chat_window.py:1027,1045-1048`, `ChatWindow.xaml.cs:604-608` | tabla MIME (`.png/.jpg/...`) reescrita 3 veces | |
| `client-python/tests/test_schema_distribuible.py:34` | protege la copia Python del esquema canónico | **la copia .NET no tiene equivalente** → puede divergir en silencio |

## 3. Bugs funcionales encontrados en la capa de presentación

Estos no son deuda estética: son comportamientos que el usuario no pidió y no puede usar.

### 3.1 P0 — funciones que existen y no hacen nada

| ID | Cliente | Qué pasa | Evidencia |
|---|---|---|---|
| UIX-00a | .NET | **La pérdida de conexión es invisible.** `OnDesconectado` existe en el transporte y en la fachada, y `bannerOffline` existe en el XAML, pero **ninguna vista se suscribe** y nadie cambia el `Visibility`. El aviso Offline de los 3 clientes solo aparece en Java y Python. | `FachadaCliente.cs:41` (declaración) · `:122` (única invocación) · `IConexionRed.cs:13`, `ConexionRed.cs:31,141` · `ChatWindow.xaml:29` (nada más) · **0 refs en `Views/*.cs`** |
| UIX-00b | Python | **El botón "Reintentar imagen" nunca reintenta.** El `_ejecutar("imagen", cargar)` está **después** del `raise`, dentro del `def cargar`. | `chat_window.py:1249` (raise) → `:1250` (inalcanzable) |
| UIX-00c | Java | **`alError()` y `alDesconexion()` están vacíos.** No son manejadores: son un comentario en el cuerpo. Los errores de red asíncronos se descartan en silencio. | `FachadaCliente.java:767-770` y `:772-775` |
| UIX-00d | Java | **El fallo de persistencia se traga.** `catch (Exception e) { }` alrededor de la escritura en H2, en el camino de recepción. | `FachadaCliente.java:756-758` |
| UIX-00e | Python | **`login()` se ejecuta en el hilo de la GUI.** Puede bloquear hasta 15 s y congela la ventana entera. Java lo hace bien con un `Task`. | `login_window.py:204` vs `LoginController.java:86-125`; timeout en `conexion_red.py:17,131` |
| UIX-00f | .NET | **"Conectando…" se pinta dentro de la caja roja de error.** El texto de carga se escribe en `txtError` mientras `bordeError.Visibility = Visible` (fondo `#FDECEA`, texto `#B91C1C`). El estado de carga parece un error. | `LoginWindow.xaml.cs:85-86` vs `LoginWindow.xaml:52-55` |

### 3.2 P1 — consistencia rota entre las 3 pistas

| ID | Asunto | Java | Python | .NET |
|---|---|---|---|---|
| UIX-01 | **Virtualización de la lista de mensajes** | ✅ `ListView` (`chat.fxml:47`) | ❌ **`QListWidget` + un `QWidget` por fila**, con `setUniformItemSizes(False)` explícito (`:341`) | ✅ `VirtualizingPanel` + `Recycling` (`ChatWindow.xaml:121`) |
| UIX-02 | **Distintivo de no leídos** | ✅ pastilla real (`UsuarioCell.java:63-66`) + filtro "No leídos" | ⚠️ truco de texto `"  (N)"` limpiado por regex (`chat_window.py:701,703`), sin filtro | ❌ **no existe** (`UsuarioVM` no tiene el campo) |
| UIX-03 | **Formato de fecha** | ⚠️ `substring(11,16)`: **descarta la fecha** y puede render vacío (`MensajeCell.java:165-166`) | ⚠️ cadena cruda, sin formatear (llega `T10:00:00` y `2026-01-01 10:00:00` mezclados) (`chat_window.py:863,119`) | ✅ `yyyy-MM-dd HH:mm`, el mejor de los 3 (`ChatWindow.xaml.cs:778-784`) |
| UIX-04 | **Separadores de día** | ❌ | ❌ | ❌ |
| UIX-05 | **Agrupación de mensajes consecutivos** | ❌ el remitente se pinta en **cada** burbuja (`MensajeCell.java:82-87`) | ❌ siempre (`chat_window.py:78-83`) | ❌ siempre (`ChatWindow.xaml:130`) |
| UIX-06 | **Búsqueda en el historial** | ❌ | ❌ | ❌ — no hay `LIKE` en ninguno de los 3 DAO |
| UIX-07 | **Búsqueda sin debounce** | ❌ `ChatController.java:125` | ❌ `chat_window.py:264` | ❌ `ChatWindow.xaml.cs:202` |
| UIX-08 | **Zoom / pan en imagen** | ❌ ventana fija 900×720 (`MensajeCell.java:246-255`) | ❌ fija 900×700 (`chat_window.py:147-166`) | ❌ 900×720 (`ChatWindow.xaml.cs:456-479`) |
| UIX-09 | **Cancelación de carga de imagen** | ❌ `idsCargaImagen` no se limpia al reciclar (`:538`) | ❌ `_tareas_imagen` sin leer (MUERT-19) | ❌ |
| UIX-10 | **Ancho máx. de burbuja fijo** | 460 px (`:80`) | 580 px (`:61`) | 720 px (`ChatWindow.xaml:127`) — no responde al ancho de ventana |
| UIX-11 | **Sidebar redimensionable** | ✅ `SplitPane 0.30` | ✅ `QSplitter` | ❌ `ColumnDefinition` fija, **sin `GridSplitter`** (`ChatWindow.xaml:9-12`) |
| UIX-12 | **`Shift+Enter` = salto de línea** | ✅ | ❌ **imposible**: el campo es un `LineEdit` (`chat_window.py:412`) | ⚠️ `TextWrapping="Wrap"` pero Enter está interceptado → también imposible |
| UIX-13 | **Estados vacíos** | ❌ lista en blanco si no hay mensajes | ❌ | ❌ — en los 3, un usuario nuevo ve un rectángulo negro sin explicación |
| UIX-14 | **Indicador de carga del historial** | ⚠️ solo texto de estado (`:393`) | ❌ nada | ❌ nada |
| UIX-15 | **`.NET` usa `MessageBox` para errores recuperables** | estado inline ✅ | mixto: inline + 7 `QMessageBox` | ❌ **10 `MessageBox` modales**, incluido "sin red" (`ChatWindow.xaml.cs:153,543,546,547,663,674,719,723,739,747`) |
| UIX-16 | **Anclaje de scroll al "cargar anteriores"** | ❌ | ⚠️ `setValue(anterior + 50)` — **+50 filas fijas y codificadas** (`:881`) | ⚠️ `ScrollIntoView(first)` |

### 3.3 Accesibilidad

| ID | Asunto | Evidencia |
|---|---|---|
| UIX-17 | **Contraste por debajo de WCAG AA en tema claro** | `chat-claro.css:100-103` `.chat-list-preview` `#78858B` sobre `#ffffff` ≈ **3.8:1** (necesita 4.5:1) a 11 px · `chat-claro.css:56-57` placeholder `#8b979c` sobre `#f1f4f5` ≈ **2.7:1** · `tema.py:53` `prompt: #7A8A99` sobre `#FFFFFF` ≈ **4.2:1** |
| UIX-18 | **Los ticks "Enviado" y "Entregado" son indistinguibles en .NET** | `TemaOscuro.xaml:22` `VistoOtroBrush #FFE8EEF2` es **el mismo color que el texto del cuerpo**. Solo se distinguen por el glifo. Idéntico en `TemaClaro.xaml:22` |
| UIX-19 | **Botones solo-icono sin nombre accesible** | Java ✅ 4 sitios (`chat.fxml:24,58,65,69` + `MensajeCell.java:173`) · Python ❌ 0 `setAccessibleName` · .NET ❌ 0 `AutomationProperties` |
| UIX-20 | **Presencia solo por color** | Java mitigado por tooltip (`UsuarioCell.java:42`) · Python ✅ usa forma `●/○` (`chat_window.py:702`) · .NET solo color (`ChatWindow.xaml.cs:30`), mitigado por el texto "Presencia" |
| UIX-21 | **Cuerpo del mensaje no seleccionable** | ❌ JavaFX `Label` · ✅ Python `TextSelectableByMouse` (`:87`) · ❌ .NET `TextBlock` |
| UIX-22 | **Mnemónicos / accelerator keys** | ❌ en los 3 |
| UIX-23 | **Escala de tipografía / DPI** | Java: `-fx-font-size: 13px` fijo en 4 CSS y **el UA stylesheet de AtlantaFX se sobrescribe con `setAll`** (`ClienteApp.java:38` vs `:121`), lo que mata su escalado · Python: ~25 `setStyleSheet("font-size: Npx")` inline, ninguno derivado de la fuente de la app · .NET: sin `FontSize` global ni manifiesto DPI |
| UIX-24 | **Foco inicial en el login de .NET** | ❌ ninguna llamada a `requestFocus`; el usuario tiene que hacer clic. Java ✅ y Python ✅ |

### 3.4 Errores de tematización

| ID | Bug | Evidencia |
|---|---|---|
| UIX-25 | `.sidebar-title` duplicado y contradictorio (el tema claro se ve mal) | ver MUERT-33 |
| UIX-26 | Python adjunta QSS de **tema claro** para los iconos de adjunto sin imagen, en tema oscuro | `chat_window.py:1050-1052` (`#CCD6D8`, `#FFFFFF`) |
| UIX-27 | Cambiar de tema **reconstruye toda la lista** de mensajes en .NET | `ChatWindow.xaml.cs:249-250` — el propio comentario lo admite |
| UIX-28 | .NET usa `StaticResource` en `App.xaml:24-25` pero **reasigna por reflexión** | `Tema.cs:89-96`; el comentario en `:87-88` admite *"los recursos XAML están congelados"* |
| UIX-29 | Python arranca en tema claro mientras `tema.py:7-9` dice oscuro por defecto | `main.py:20` |

## 4. Arquitectura de presentación y patrones

### 4.1 El estado de la capa de presentación

| | JavaFX | PySide6 | WPF |
|---|---|---|---|
| ViewModel / Presenter | **ninguno** | **ninguno** | **parcial** — solo `MensajeVM` con `INotifyPropertyChanged` |
| Acoplamiento vista→fachada | `ClienteApp.fachada()` — **localizador de servicio estático** | la ventana **parchea la fachada** con `setattr` y la interroga con `getattr`/`hasattr` | `IFachadaCliente` inyectada ✅ — **el único cliente con interfaz en la frontera** |
| Archivo más grande | `ChatController.java` **774 líneas** | `chat_window.py` **1 322 líneas** | `ChatWindow.xaml.cs` **790 líneas** |
| Bindings reales | 1 `bind()` en todo el cliente (`ChatController.java:147-150`) | **0** (`Property`/`Signal` de estado no existen) | `{Binding}` + `INotifyPropertyChanged` en `MensajeVM` |
| `ICommand` / equivalente | ❌ 9 métodos `@FXML` (`chat.fxml:23,32,33,34,44,58,64,68,73`) | ❌ `.clicked.connect` directo | ❌ **0 `ICommand`**; todo es `Click=` a code-behind |

**Los tres fugas hacia dentro de la fachada.** El síntoma de que no hay Presenter:

| Fuga | Evidencia |
|---|---|
| La **ventana de Python reescribe la fachada**: le inyecta métodos ligados con `setattr`, y los retira al hacer logout | `chat_window.py:236-239` ↔ `:631-634`, `:1316-1319` |
| La ventana de Python **alcanza `fachada.historial`** (atributo interno) para contar no leídos | `chat_window.py:521-523` |
| El controlador de Java **sondea `fachada.conectado()`** porque no hay evento de conexión, y **lee un contador de diagnóstico** de la fachada | `ChatController.java:743`, `:467` (`suscriptores()`) |

**Prueba de que no hay costura testeable:** `client-python/tests/test_ui.py` tiene que llamar
a métodos privados — `_al_seleccionar_usuario`, `_pintar_historial`, `_procesar_tarea`
(`:136,152,163,171`) — y su doble `FachadaFalsa` (`:18-48`) sustituye la fachada **entera**.
No hay forma de inyectar un ViewModel porque no existe.

### 4.2 Patrones ya presentes (verificados)

| Patrón | Java | Python | .NET |
|---|---|---|---|
| **Fachada** | `FachadaCliente.java:41` | `fachada_cliente.py:31` | `FachadaCliente.cs:20` + `IFachadaCliente` |
| **Observer / eventos** | `ReceptorMensajes.java:9-22` + `FachadaCliente.java:698-776` | 4 callbacks (`fachada_cliente.py:38-41`), cableados en `chat_window.py:236-239` | 4 `event Action` (`FachadaCliente.cs:38-41`), `+=` en `ChatWindow.xaml.cs:114-116` |
| **Adapter (transporte)** | `ConexionCliente.java:330-345` + `TramaCodec` | `transversal/protocolo.py` | `Componentes/TramaCodec.cs` |
| **Adapter (TLS)** | `ConexionCliente.java:62` vs `:69-99` | `conexion_red.py:48-65` + `tls.py` | `ConexionRed.cs:36-52` |
| **Repository** | `InterfazHistorialLocal` + `HistorialLocal.java:19` | ❌ **sin interfaz**, acceso directo con SQL SQLite *inline* | `IHistorialLocal` + `HistorialLocal.cs:12` |
| **Strategy** | ⚠️ solo un `ThreadFactory` inyectable (`:83`) | ❌ | ✅ `ConexionRed.cs:24 ValidarCertificado` — nadie lo asigna |
| **Command** | ❌ en la capa de cliente (sí en el servidor: 15 clases) | ❌ | ❌ |

### 4.3 Los 6 patrones que faltan, con el dolor que justifican

| Patrón | Dolor concreto que lo justifica |
|---|---|
| **MVVM** (ViewModel + binding) | `ChatController.java` mezcla vista + estado + paginación + MIME + caché de imagen; se modifica desde 9 sitios distintos (`:129,135,139,143,169,235,285,315,458,462,464`) con **1** `bind()`. `chat_window.py` tiene 20 atributos mutables y un despachador `if/elif` de 100 líneas que *es* un presenter sin tipar (`:536-661`). .NET ya va ⅓: `UsuarioVM` (`:21-31`) **no notifica** y `AplicarFiltro` hace `Clear()` + re-`Add()` en cada tecla (`:195-196,202`) — el síntoma clásico de no tener filtro observable. |
| **Command / ICommand** | 3 implementaciones duplicadas de "puedo enviar": `ChatController.java:174-176`, `chat_window.py:937-939`, `ChatWindow.xaml.cs:572-575`. Con `CanExecute` desaparece la terna y `IsEnabled` pasa a ser declarativo. |
| **Observer del estado de conexión** | Es la causa directa de `UIX-00a`: Java **sondea** `conectado()` cada 30 s (`RESPALDO_SEGUNDOS=30`, `:57`) porque `alDesconexion` está vacío; Python solo cambia el banner ámbar dentro del resultado de `directorio` (`:583-585`), no en `_procesar_desconexion` (`:1181-1182`); .NET no tiene suscripción. **Un solo `EstadoConexion` observable lo arregla en los 3.** |
| **Composition root con inyección real** | Java: `ClienteMain.java:20-45` es un root legítimo, pero lo destruye un localizador estático (`ClienteApp.java:20,29-34`) → `ChatController` **no se puede construir en un test**. Python: `main.py:23` `FachadaCliente()` sin argumentos, 6 colaboradores cableados con defaults `or` (`:35-56`), y el grafo **cambia de forma en runtime** (`:87-88`). .NET: layed el grafo dentro de `ConectarAsync` con un truco de campos `readonly` (`:104,112-115`); el ctor con DI existe (`:52-61`) y solo lo usan los tests. |
| **Adapter del almacén local** | El esquema SQL **sí** es compartido (3 copias), los **puertos no**: Java dice "el motor es intercambiable" (`InterfazHistorialLocal.java:10-13`) pero solo existe H2 (`LocalStore.java:107`, con `MERGE INTO … KEY(...)` exclusivo de H2 en `:186-190,228-230`); Python no tiene interfaz y usa `ON CONFLICT` *inline*; .NET tiene interfaz pero SQL de SQLite. Existe ya la forma **portable** documentada (`HistorialLocal.java:17`: upsert con `DELETE + INSERT`, sin sintaxis de motor) y **no se usa en Python ni en .NET**. |
| **Adapter de transporte (TCP vs TLS)** | Tres implementaciones con tres estrategias distintas, y **validación TLS inconsistente**: Python valida fail-fast (`tls.py:17-45`, llamado en `conexion_red.py:44-45`); Java **no valida en ningún sitio**; .NET sustituye el puerto en silencio cuando `TlsPuerto == 0` (`ConexionRed.cs:43`) y nunca lee `tls.confianza`. |

## 5. Contrato visual y de texto compartido

### 5.1 Los tres clientes repiten 73 cadenas idénticas

Se verificó que el mismo texto aparece en los 3, con estas ubicaciones (muestra; la lista
completa está en `DIAGRAMAS/textos-ui.md`):

| Texto | Java | Python | .NET |
|---|---|---|---|
| `Usuario no registrado o datos incorrectos.` | `LoginController.java:105` | `login_window.py:208` | `LoginWindow.xaml.cs:99` |
| `Ingresa el código y la contraseña.` | `LoginController.java:78` | `login_window.py:194` | `LoginWindow.xaml.cs:76` |
| `⏳ Pendiente` / `✓ Enviado` / `✓✓ Entregado` / `✓✓ Leído` | `MensajeCell.java:221,223,215,212` | `chat_window.py:1289-1292` | `ChatWindow.xaml.cs:759` |
| `Cargando imagen…` / `Reintentar imagen` | `MensajeCell.java:113,123` | `chat_window.py:1199,106` | `ChatWindow.xaml.cs:367,142` |
| `Respuesta a {identidad}: "…"` (**visible en el protocolo**) | `FormatoRespuesta.java:19` | `chat_window.py:910` | `ChatWindow.xaml.cs:490,519` |
| `envia sesion primero` / `elige un destinatario` / `el contenido no puede estar vacio` | `FachadaCliente.java:669`, `ValidacionCliente.java:32,26` | `fachada_cliente.py:608,167,349` | `FachadaCliente.cs:272,341,887` |
| `50 MB` tope de archivo | `ChatController.java:332` | `fachada_cliente.py:28` | `FachadaCliente.cs:381` |
| `PENDIENTE/ENVIADO/ENTREGADO/LEIDO/ERROR` (máquina de estados, SQL `CASE` idéntico) | `HistorialLocal.java:42-56` | `historial_local.py:28-30,141-144,178-180` | `HistorialLocal.cs:44-48`, `LocalStore.cs:149-151` |

**El caso grave es el de la máquina de estados:** el mismo `CASE` SQL con la misma precedencia
está escrito 3 veces. Si mañana se añade un estado, hay que acordarse de los 3 sitios, y el
historial de un cliente puede mostrar `LEIDO` donde otro muestra `ENTREGADO`.

### 5.2 La paleta está triplicada y además ha divergido

| Color | Java | Python | .NET |
|---|---|---|---|
| Fondo ventana | `#0E1621` `chat.css:4` | `#0E1621` `tema.py:18` | `#FF0E1621` `App.xaml:17` |
| Sidebar | `#182533` `chat.css:18` | `#182533` `tema.py:19` | `#FF182533` `App.xaml:18` |
| Acento (burbuja propia) | `#7B61B8` `chat.css:141` | `#7B61B8` `tema.py:27` | `#FF7B61B8` `App.xaml:13` |
| Tick de leído | `#5EB1FF` `chat.css:201` | `#5EB1FF` `tema.py:35` | `#FF5EB1FF` `App.xaml:21` |
| **Burbuja propia en tema claro** | **`#E4F3EC`** `chat-claro.css:143` | **`#E1F5FE`** `tema.py:56` | **`#D9FDD3`** `TemaClaro.xaml:14` |
| **Acento en tema claro** | **`#2456A6`** `login-claro.css:60` | **`#147D73`** `main.py:21` | **por defecto de WPF-UI** |

Los 11 colores del tema oscuro coinciden exactamente en los 3. **El tema claro no coincide en
ninguno** — 3 valores distintos para la burbuja propia y 3 para el acento. Eso es deriva visual
real, no una diferencia de toolkit: el oscuro se copió, el claro se inventó tres veces.

### 5.3 Ninguno de los 3 clientes tiene i18n

| | Literales en la capa de presentación | Líneas que asignan texto de UI |
|---|---|---|
| JavaFX | **418** en 1 511 líneas | 79 |
| Python | **574** en 1 656 líneas | 79 |
| .NET | **446** en 1 130 líneas | 68 |

Búsqueda de mecanismo de recursos: 0 `ResourceBundle`, 0 `QTranslator`, 0 `.resx` — los únicos
positivos fueron `e.getMessage()` (excepciones de Java) y la clave `idioma` sin usar. El
proyecto es para una comunidad académica **hispanohablante** y el enunciado no pide i18n
(`RNF-*` no la menciona), así que **i18n no entra en el roadmap**; lo que sí entra es
**centralizar los literales** para que la deriva de §5.1 sea visible y corregible.

## 6. Estado de los diagramas

Resumen del problema, con el detalle en [`DIAGRAMAS/README.md`](DIAGRAMAS/README.md).

- **37 `.mmd` repartidos en 3 carpetas** con solapamiento pesado. `DIAGRAMAS/` gana en todo lo
  duplicado (18 `.mmd` derivados del código y los `.sql` reales; versiones ENTRAGAs y FINALES
  son subconjuntos) pero **pierde 5 diagramas de clase únicos** de FINALES
  (`03-clases-fachada-nucleo`, `05-clases-inicio-vistas`, `06-clases-fachada-casos`,
  `07-clases-nucleo`, `08-clases-persistencia`) y **7 mockups de pantalla del servidor**.
- `DIAGRAMAS_ENTREGA` aporta 4 diagramas **únicos**: `01-arquitectura` (vista de flujo de datos,
  distinta de la vista de puertos), `02-flujo-login` y `03-flujo-mensaje` (los **únicos**
  diagramas de secuencia del repo) y `04-flujo-difusion`.
- Tipos de diagrama **ausentes por completo** en las 3 carpetas: `usecaseDiagram`,
  `stateDiagram`, `activityDiagram`. Verifiqué por regex sobre los 37 archivos: solo existen
  `flowchart`, `classDiagram`, `erDiagram` y `sequenceDiagram`.
- Sin diagramas de clase de los clientes **Python** ni **.NET**: solo aparecen como una caja de
  5 nodos dentro de `04-clases-cliente.mmd` de FINALES.
- `DIAGRAMAS/README.md` §1 documenta 5 mockups y **no lista los 3 `mockup-chat-telegram-*`**,
  que son el diseño más reciente. El índice está desactualizado.
- `render.ps1` solo recorre `DIAGRAMAS/` (`$raiz` = la carpeta del propio script), así que
  `DIAGRAMAS_ENTREGA` y `DIAGRAMAS_FINALES` **no se regeneran**. Y **no hay `mmdc`, `plantuml`
  ni `node` instalados en este entorno**, así que los `.png` nuevos están pendientes.

## 7. Índice de hallazgos

| Familia | Cantidad | IDs | Bloquea producción |
|---|---|---|---|
| Seguridad | 1 P0 + 3 P1 | `SEC-05`, `SEG-01..04` | **Sí: SEC-05** |
| Código muerto | 38 items | `MUERT-01..38` | No, pero entorpece todo lo demás |
| Bugs de UI | 6 P0 + 16 P1 + 13 P2 | `UIX-00a..29` | Los 6 P0, sí |
| Patrones de cliente | 6 | `PAT-01..06` | No |
| Diagramas | 6 grupos | `DIAG-01..06` | No |

## 8. Referencias

- [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md) — tablero; aquí cada hallazgo tiene su tarea.
- [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) — fases, orden y criterios de aceptación.
- [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) — IDs normativos (`RF-*`, `RNF-*`, `PD-*`).
- [`DIAGRAMAS/README.md`](DIAGRAMAS/README.md) — catálogo de diagramas y plan de consolidación.
- [`PLAN.md`](PLAN.md) — arquitectura y decisiones de diseño vigentes.
