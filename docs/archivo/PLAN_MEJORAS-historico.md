# PLAN DE MEJORAS — ARCHIVO HISTÓRICO (clientes + servidor)

> **Archivado el 2026-10-07.** Diario de lo ejecutado el 2026-10-04/05.
> El tablero vigente es [`../../ESTADO_ROADMAP.md`](../../ESTADO_ROADMAP.md);
> no usar este archivo como lista de pendientes.

> Plan ejecutado el 2026-10-04 (ver "Registro de ejecución" al final).
> Cada punto trae: síntoma → causa investigada en el código (archivo:línea)
> → solución propuesta → cómo verificarlo.

## Orden de ejecución recomendado

El punto 4 (crash de Python) va primero: sin selección de conversación no
hay envío, y los diálogos de error en cascada explican varios de los otros
síntomas. Después 5 y 2 (envío y descarga), luego 1, 3, 6 y 7 (UI), y al
final 8, 9 y 10 (servidor).

---

## 1. Cita de respuesta estilo WhatsApp/Telegram en los 3 clientes

**Síntoma:** al responder, la cita se mete como texto dentro del campo de
escritura. Se quiere la burbuja citada arriba, tenue/transparente, como en
WhatsApp/Telegram.

**Causa (verificada):**
- Python `client-python/ui/chat_window.py:665-675` (`_responder`): escribe
  `Respuesta a X: "..."` en `lbl_respuesta` Y ADEMÁS lo inyecta en
  `entry_msg`. Lo que se envía es texto plano con la cita pegada.
- .NET `client-dotnet/Views/ChatWindow.xaml.cs:400-416`
  (`BtnResponder_Click`): mismo patrón, `cita + "\n" + txtMensaje.Text`.
- Java `client/.../presentacion/ChatController.java:268-274`
  (`alResponder`) ya tiene barra dedicada (`barraRespuesta` +
  `etiquetaRespuesta`), pero al enviar hace
  `texto = prefijo + texto` (`ChatController.java:219-223`), así que el
  resultado en el hilo también es texto pegado.
- El protocolo NO tiene campo de respuesta: el record `Mensaje`
  (`common-protocol/dto`, 29 componentes) no trae `respuestaA`/`cita`.
  La cita solo existe como formato dentro de `contenido`.

**Solución propuesta (dos niveles, hacer en orden):**
1. **Nivel UI (sin tocar protocolo):** unificar los 3 clientes en el
   patrón que ya usa Java: barra de cita dedicada sobre el campo de
   entrada (fondo tenue, borde de acento a la izquierda, botón ✕ para
   cancelar), y al enviar anteponer
   `FormatoRespuesta.prefijo(...)` al contenido. Es lo que Java ya hace;
   hay que replicar la barra en Python (`lbl_respuesta` ya existe como
   etiqueta: convertirla en tarjeta con estilo + botón quitar) y en .NET
   (`txtRespuesta` ya existe: darle estilo de tarjeta + botón quitar en
   vez de inyectar en `txtMensaje`).
2. **Nivel protocolo (opcional, después):** agregar `respuestaA`
   (id del mensaje citado) + `citaExtracto` al record `Mensaje`, pasarlos
   por `TramaCodec`, persistirlos (migración: columna en `mensajes` del
   servidor y en `historial_local` de los 3 clientes SQLite) y renderizar
   la burbuja citada dentro de cada mensaje del hilo. Toca los 3
   lenguajes + servidor + 2 esquemas SQL: no hacerlo a medias.

**Verificación:** matriz 3 clientes × (responder texto / imagen / archivo /
cancelar cita con ✕ / enviar sin cita después de cancelar). La cita
cancelada no debe dejar rastro en el texto enviado.

---

## 2. Botón Enviar con solo imagen o solo archivo

**Síntoma:** el botón Enviar debería habilitarse al adjuntar solo imagen o
solo archivo, sin texto.

**Causa (verificada — el código YA lo contempla en los 3):**
- Python `chat_window.py:677-679`: habilita con
  `texto.strip() or imagen_pendiente`, y `_fijar_imagen_adjunta`
  (`:765-794`) fija `imagen_pendiente` para cualquier archivo.
- .NET `ChatWindow.xaml.cs:471-474`: habilita con texto o
  `_imagenPendiente` o `_archivoPendiente`.
- Java `ChatController.java:164-165` (`puedeEnviar`): texto o `imagen`
  (que cubre también archivo genérico, `:233-241`).

**Entonces lo más probable es que el fallo real esté aguas arriba:**
1. **Java:** el adjunto solo existe cuando termina la lectura asíncrona
   (`cargarArchivo`, `:310-348`). Archivos > 50 MiB se rechazan en
   segundo plano (`:316`) y el único aviso es una etiqueta de estado: el
   usuario percibe "botón muerto para archivos".
2. **Python:** si la selección de conversación falló antes (ver punto 4),
   `usuario_seleccionado` es `None` y el botón queda deshabilitado para
   TODO, no solo adjuntos. Y en `dropEvent` (`:833-847`), si la ruta no es
   archivo local, `_fijar_imagen_adjunta` retorna sin avisar.
3. **.NET:** `BtnAdjuntar`/`BtnAdjuntarArchivo` retornan en silencio si no
   hay `_usuarioDestino` (`:478` y equivalente): mismo efecto visible.

**Solución propuesta:**
1. Reproducir con matriz 3 clientes × (solo texto / solo imagen / solo
   archivo / texto+adjunto / adjunto > 50 MB) y anotar en qué celda falla.
2. Avisos visibles donde hoy hay silencio: Java ya avisa tamaño; agregar
   aviso cuando el adjunto se descarta (drop inválido en Python, sin
   conversación en .NET/Python).
3. Solo si la matriz muestra un caso real sin habilitar, tocar la
   condición de ese cliente. No tocar las tres a ciegas.

**Verificación:** la matriz anterior en verde + el botón vuelve a
deshabilitarse durante el envío y se rehabilita al terminar (ya ocurre:
Python `:687` + `:446`, .NET `:425` + `:455`, Java `enviando`).

---

## 3. Servidor/escritorio: descargar imagen CON filtros + Nombre[codigo]

**Síntoma A:** en la pestaña Mensajes, aplicar un filtro (Grises, Sepia…)
y pulsar Descargar guarda mal (guarda el original, no lo visto).
**Síntoma B:** en esa misma vista debería verse Nombre[codigo] en vez del
código pelado.

**Causa (verificada):**
- `VistaEscritorio.java:532-557` (`aplicarFiltroVisual`): el filtro solo
  se pinta en `vistaMensaje` (preview en memoria). `descargarSeleccionado`
  (`:484-513`) ignora por completo el preview y descarga siempre los bytes
  originales (`bytesArchivoParaVista`) o un `.txt` con el contenido.
- `mostrarDetalle` (`:404-481`): `detalleTitulo` se arma con
  `m.remitente()` / `m.destinatario()` crudos (`:415-416`), sin resolver
  nombres. La vista SÍ tiene de dónde resolver: `Fachada`
  (`transversal/fachada/Fachada.java:28`) expone `usuariosRegistrados()`
  con `UsuarioResumen` (codigo/nombres/apellidos).

**Solución propuesta:**
1. **Descarga fiel al preview:** en `descargarSeleccionado`, si
   `comboFiltroVisual != "Original"` y hay `bytesImagenActual`, reaplicar
   `aplicarFiltroVista(base, opcion)` (función pura ya probada en
   `VistaEscritorioFiltrosTest`) y guardar ESE resultado como PNG. Si el
   formato no es decodificable por ImageIO, avisar que solo se puede
   descargar el original (el mensaje ya existe para la vista).
2. **Nombre[codigo]:** mapa `codigo → "Nombres Apellidos [codigo]"` desde
   `usuariosRegistrados()`, con fallback al código si no aparece
   (cuentas borradas) y etiqueta `SERVIDOR` para `CUENTA_SISTEMA`/
   broadcast. Usarlo en `detalleTitulo` y en `detalleMeta` donde aplique.

**Verificación:** descargar con cada uno de los 6 filtros y comparar hash
contra el original (debe diferir salvo "Original"); detalle de un mensaje
con remitente borrado (muestra código) y de un broadcast (muestra
SERVIDOR).

---

## 4. Cliente Python: crash shiboken + subventanas + envío roto

**Síntoma:** al seleccionar conversación:
`RuntimeError: libshiboken: Internal C++ object
(PySide6.QtWidgets.QListWidgetItem) already deleted` en
`chat_window.py:544`. Además se abren muchas ventanas al iniciar y al
enviar, y el envío no funciona bien.

**Causa del crash (verificada, cadena completa):**
1. `lista_usuarios` emite `currentItemChanged` → `_al_seleccionar_usuario`
   (`:531-557`).
2. Dentro, `:541` llama `_filtrar_usuarios`, que hace
   `self.lista_usuarios.clear()` (`:502`) + `setCurrentItem` (`:529`):
   **borra el item `actual` en pleno manejo de su propia señal**
   (y además re-dispara la señal: reentrancia).
3. `:544` usa `actual.data(...)` sobre el objeto ya liberado → crash.
   El `try/except` de `:539-543` solo envuelve el bloque de no-leídos,
   no la línea 544.

**Causa de las ventanas (verificada parcial, falta reproducir):**
- Cada envío fallido abre un modal `QMessageBox.critical("No se pudo
  enviar")` (`:386`); cada descarga fallida otro (`:393`); cada descarga
  OK otro informativo (`:486-488`); cierre de sesión otro (`:1014`).
  Sin red, cada reintento = una ventana nueva. `main.py:27-36` además
  crea un `ChatWindow` nuevo por login.
- `login_window.py` no crea ventanas (usa etiquetas inline, verificado).
  Si al arrancar aparecen ventanas sin tocar nada, el sospechoso es un
  modal disparado desde un callback de red/presencia: reproducir con
  `QT_LOGGING_RULES` y contar `QMessageBox` por minuto.

**Solución propuesta:**
1. **Crash:** leer `codigo` e `identidad` a locales ANTES de
   `_filtrar_usuarios`, bloquear señales durante la reconstrucción
   (`blockSignals(True/False)`) o diferir el `setCurrentItem`, y blindar
   con `sip.isdeleted(actual)` al inicio del handler.
2. **Ventanas:** un solo `ChatWindow` reutilizado (no `new` por login);
   sustituir los modales por error con un banner inline persistente
   (como `lbl_estado_red`/`lbl_offline` que ya existen) + detalle en log;
   reservar `QMessageBox` para confirmaciones del usuario
   (salir/salir-en-todos) y resultado de descarga.
3. Re-probar envío contra servidor caído y contra servidor OK: el crash
   impedía seleccionar conversación, lo que por sí solo explica "no
   funciona correctamente" y el botón siempre deshabilitado (punto 2).

**Verificación:** seleccionar conversación 20 veces seguidas con filtro
"Conectados" alternado (antes crasheaba); prueba de estrés sin red: cero
modales, un solo banner; un solo `ChatWindow` en `QApplication.topLevelWidgets()`
tras 3 login/logout.

---

## 5. .NET no descarga archivos

**Síntoma:** el botón "Descargar archivo" nunca completa la descarga.

**Causa (verificada a medias — hay dos candidatas):**
- El click exige `vm.ArchivoId` no vacío y si no, muestra `"El servidor
  no entregó el identificador de este archivo."`
  (`ChatWindow.xaml.cs:588-592`).
- El mapeo `ArchivoId = Valor(row, "archivoId")` (`:270`) es consistente
  con lo que guarda `HistorialLocal.cs:110` (`row["archivoId"]`), así que
  el vacío viene de aguas arriba:
  - **Candidata A (local):** filas legacy sin `archivo_id` (la columna se
    agregó por migración: `LocalStore.cs:272-275,374-378`) o mensajes de
    imagen cuyo contenido va en base64 local y nunca recibieron archivoId.
  - **Candidata B (servidor):** `FachadaCliente.cs:795-796` solo llama
    `ActualizarArchivoId` si la respuesta trae `"archivoId"` string; si el
    servidor no lo devuelve para ese tipo de trama, el local queda vacío
    para siempre. La ruta de respaldo (`CargarImagenAsync`, `:351-364`)
    intenta descargar o usa base64 local, pero el botón de descarga de
    ARCHIVOS no tiene ese fallback.

**Solución propuesta:**
1. Diagnosticar primero: loguear `ArchivoId` vacío con
   `(Id, tipo, nombreArchivo)` y cruzar contra la respuesta cruda del
   servidor para ese `archivoId`. Eso decide entre A y B sin adivinar.
2. Si A: rellenar `archivo_id` para filas legacy cuando llegue del
   historial remoto (ya existe el camino `ActualizarArchivoId`).
3. Si B: mismo trato que imágenes — fallback a contenido local cuando
   exista, y mensaje que diga QUÉ falta (id) en vez de genérico.
4. Agregar a `BtnDescargarArchivo_Click` deshabilitado del botón mientras
   descarga (hoy se puede pulsar dos veces; Python sí lo deshabilita:
   `chat_window.py:990`).

**Verificación:** descargar un archivo recibido ANTES del fix (fila legacy)
y uno recibido DESPUÉS; ambos deben guardar bytes idénticos al original
(comparar SHA-256).

---

## 6. Login: servidor/puerto solo visibles (como .NET) en los 3 clientes

**Síntoma:** hoy Python y Java permiten editar servidor/puerto; .NET los
muestra deshabilitados desde `client.properties`. Deben quedar los 3 como
.NET.

**Causa (verificada):**
- .NET: `txtHost`/`txtPuerto` con `IsEnabled=False` + etiqueta
  `"Servidor actual: {host}:{puerto} · se configura en
  client.properties."` → referencia a imitar.
- Python `ui/login_window.py:63-84`: `entry_host` editable +
  `spin_puerto` editable; `login()` encima GUARDA lo editado si
  "Recordar servidor" está marcado (`:150-156`).
- Java `LoginController.java:42-49` muestra config, pero `:119-137` LEE
  los campos y los GUARDA en properties al entrar. Doble problema:
  editable + persiste lo que el usuario teclee.

**Solución propuesta (igual en Python y Java):**
1. Campos a solo-lectura (`setReadOnly(True)` / `setEditable(false)` +
   sin foco de tabulación) con los valores de `client.properties`.
2. Eliminar el guardado silencioso (Python `:150-156`, Java `:136-137`);
   el checkbox "Recordar servidor" pierde sentido → quitarlo o
   dejarlo solo como "recordar CÓDIGO" (decidir, no a medias: si se quita,
   quitar también su lectura en el arranque).
3. Mantener la etiqueta "Servidor actual: host:puerto · se configura en
   client.properties." (los 3 ya la tienen con ese texto).

**Verificación:** intentar editar con teclado y con pegado (debe ser
imposible); borrar `client.properties` → debe mostrar el fallback
`localhost:5000` con el aviso existente, sin crashear; el login usa esos
valores aunque el usuario no toque nada.

---

## 7. Login .NET: confirmar antes de cerrar el programa

**Síntoma:** cerrar la ventana de login sale del programa sin preguntar.

**Causa (verificada):** `Views/LoginWindow.xaml.cs` no tiene `OnClosing`
(grep vacío para `OnClosing|Closing|MessageBox`). `App.xaml.cs:10-15`
abre el login como primera ventana y el `ShutdownMode` por defecto de WPF
(`OnLastWindowClose`) hace que cerrarla termine la app en silencio
(`OnExit` solo desconecta).

**Solución propuesta:** `OnClosing` en `LoginWindow` con el mismo patrón
que ya usa el chat (`ChatWindow.xaml.cs:100-112`): pregunta
`"¿Deseas cerrar el programa?"` / título `"Confirmar salida"`, `YesNo`,
default `No`; solo `Yes` deja cerrar. No tocar `App.xaml.cs`.

**Verificación:** X y Alt+F4 preguntan; "No" deja el login intacto y la
sesión/fachada sin cambios; "Sí" sale con código 0 y desconecta
(`OnExit` ya lo hace).

---

## 8. Difundir a todos desde el servidor con hilos + progreso

**Síntoma:** la difusión masiva se siente lenta/opaca; se piden hilos o
similar.

**Causa (verificada — ya hay concurrencia, el problema es otro):**
- `FachadaDeServicios.difundirAdministrativo` (`:160-172`) solo encola UN
  mensaje BROADCAST. El abanico está en `procesarDifusion` (`:436-477`):
  un `CompletableFuture` por usuario con `mensajes.procesarTexto` (que a
  su vez es `supplyAsync` sobre `poolFiltros`, `Mensajes.java:57-61`).
- PERO el `protocolo.enviar(s, entrega)` bloqueante corre dentro de
  `thenAccept` sin executor → **corre en `ForkJoinPool.commonPool`**, y un
  cliente lento retiene un hilo común. Sin límite de paralelismo ni
  progreso: la UI solo muestra `"Difusion encolada."`
  (`VistaEscritorio.java:280`, `VistaConsola.java:273`) y el resultado
  (`fallidos`) solo queda en el log (`:474-475`).
- Costo extra: `procesarTexto` por destinatario repite pipeline completo
  (SHA/contadores) para un contenido IDÉNTICO.

**Solución propuesta:**
1. Executor dedicado y acotado para entrega (`newFixedThreadPool` o
   virtual threads según el Java del servidor; pasarlo a `thenAcceptAsync`)
   + timeout por envío, para aislar el pool común de clientes lentos.
2. Progreso real a las dos vistas: la fachada expone callback/contadores
   (`difundidos/total/fallidos`) y `VistaEscritorio` lo muestra en
   `estadoDifundir` (`"Difusión 12/50…" → "Difusión completa: 48
   entregas, 2 fallos"`) y `VistaConsola` imprime avance (hoy:
   `"Difusion encolada."` y silencio).
3. (Opcional, después) pipeline único + N persistencias para el caso
   broadcast, en vez de N pipelines idénticos. Requiere tocar `Mensajes`:
   medir antes con N=50/200 usuarios para justificarlo.

**Verificación:** difundir a N usuarios con 1 cliente lento (socket que no
lee): el resto recibe en < X s y el pool común no se agota; la UI muestra
progreso y el resumen final coincide con el log (incluye `fallidos`).

---

## 9. Escritorio: maestro-detalle en Informes-mensajes y en Usuarios

**Síntoma:** la pestaña Mensajes (la mejor vista) tiene maestro-detalle;
se quiere lo mismo para los mensajes dentro de Informes, y detalle al
click en Usuarios.

**Causa (verificada — es ausencia, no bug):**
- `pestanaInformes` (`VistaEscritorio.java:623-647`) pinta el `InformeDTO`
  genérico (columnas/filas string) sin listener de selección ni panel de
  detalle. Pero el informe de mensajes SÍ trae la clave: columna 0 = id
  (`Servicios.java:105-113`: `"Id", "Fecha", "Remitente [código]", …`).
- `pestanaUsuarios` (`:209-219`) es solo `TableView<UsuarioResumen>` sin
  selección ni detalle. Los datos para el detalle ya existen por fachada:
  `mensajesDetalle(filtro)` (`Fachada.java:64`), `informeUsuarios(filtro
  por codigo)`, `cerrarConexionAdmin`, fechas en `informeUsuarios`.

**Solución propuesta:**
1. **Informes→mensajes:** si `comboTipo == "mensajes"`, listener de
   selección que parsea el id de la columna 0, lo busca en una lista
   cacheada de `mensajesDetalle(filtroActual)` y reutiliza el MISMO panel
   de detalle de la pestaña Mensajes (extraer `mostrarDetalle` +
   `detalleTitulo/detalleContenido/detalleMeta/vistaMensaje/filtros` a un
   componente compartido, no duplicarlo).
2. **Usuarios:** panel lateral de detalle al seleccionar: nombre,
   código, programa, fechas, conectado, sesiones vivas + acciones
   `"Cerrar sesiones"` (usa el `cerrarConexionAdmin` que ya usa la
   pestaña Conectados, `:241`) y `"Ver mensajes"` (salta a la pestaña
   Mensajes con el filtro de código puesto).

**Verificación:** seleccionar fila de informe-mensajes muestra el mismo
detalle (contenido + imagen + filtros) que la pestaña Mensajes; click en
usuario muestra su detalle; "Ver mensajes" cambia de pestaña con el filtro
aplicado; informe de tipo no-mensajes sigue sin detalle (sin crash por
parseo de id).

---

## 10. Mejora general de UI de los 3 clientes

**Síntoma (transversal):** pulir la UI de los 3 clientes con el mismo
lenguaje visual.

**Causa:** cada cliente creció por su lado: mismos textos con tildes
distintas (`.NET` con tildes, Python sin tildes en títulos), placeholders
distintos (`"Escribe un mensaje (Enter para enviar)"` vs `"Escribe un
mensaje"`), confirmaciones con textos levemente distintos, y patrones
duplicados con implementaciones distintas (cita, adjunto, estados).

**Solución propuesta (hacer DESPUÉS de 1-7, que ya unifican conducta):**
1. Congelar el catálogo de textos en `DIAGRAMAS/README.md` o en un
   `textos-ui.md` (qué dice cada pantalla en cada estado) y alinear los 3
   clientes a él.
2. Regla de estilo compartida: misma paleta (los hex ya coinciden en
   gran parte: `#1F3A5F`, `#147D73`, `#B91C1C`…), mismos radios y focos de
   teclado (Enter=enviar, Esc=cancelar cita/adjunto) en los 3.
3. Actualizar los 3 mockups de `DIAGRAMAS/mockups/` con el resultado.

**Verificación:** recorrido guiado idéntico en los 3 clientes con la
misma checklist; diff de textos contra el catálogo.

---

## Registro de ejecución (2026-10-04)

Implementados los puntos 1–9 (el 10 queda como pasada final pendiente).
Resumen por archivo:

**Cliente Python** (`client-python/`)
- `ui/chat_window.py`: fix del crash shiboken (`_al_seleccionar_usuario`
  con snapshot + guard `_seleccionando` + `try/except RuntimeError`);
  tarjeta de cita (`cita_container` + botón ✕, sin inyectar en el campo,
  cita antepuesta al enviar, se limpia al cambiar de conversación);
  errores de envío/descarga a banner inline (`_aviso_red`) en vez de
  modal; aviso visible en drop inválido.
- `ui/login_window.py`: servidor/puerto deshabilitados, eliminado
  "Recordar servidor" y el guardado silencioso.
- `main.py`: una sola ventana de chat (reutiliza la existente).
- `fachada/fachada_cliente.py`: **bug real** — el reintento de pendientes
  de archivo usaba claves fuera de contrato (`nombre/mime/tamano/indice/
  base64/sha256`); ahora usa `nombreArchivo/tamanoArchivo/indiceParte/
  contenidoImagen/hashSha256/archivoId`. El servidor siempre lo rechazaba.
- `tests/test_ui.py`, `tests/test_archivo_transferencia.py`:
  actualizados al contrato (`tamanoArchivo/indiceParte/contenidoImagen/
  hashSha256`, tarjeta de cita).
- Tests: **42/42 en verde** (`pytest tests`).

**Cliente .NET** (`client-dotnet/`)
- `Views/ChatWindow.xaml(.cs)`: tarjeta de cita (`bordeRespuesta` con
  acento + `btnQuitarRespuesta`), cita por campos (no inyección), se
  limpia al enviar y al cambiar de conversación; descarga con botón
  ocupado-deshabilitado + respaldo a contenido local base64 + log de
  `ArchivoId` vacío; aviso en adjunto sin conversación.
- `Views/LoginWindow.xaml.cs`: `OnClosing` con confirmación
  ("¿Deseas cerrar el programa?"), sin preguntar al navegar al chat.
- Tests: **35/35 en verde** (`dotnet test`).

**Cliente Java** (`client/`)
- `LoginController.java` + `vistas/login.fxml`: campos solo lectura,
  eliminado checkbox y guardado silencioso.
- Tests: **55/55 en verde** (`mvn -pl client/client-app -am test`).

**Servidor** (`server/servidor-app/`)
- `FachadaDeServicios.java`: pool de entrega acotado (4–16 hilos daemon,
  `AbortPolicy` → cuenta en `fallidos`), `thenAcceptAsync` en las dos ramas
  de difusión, progreso `Difusión N/total` por eventos (lo ven consola y
  escritorio en vivo), `apagarEntrega()`.
- `Fabrica.java`: apaga el pool de entrega junto al de filtros.
- `VistaEscritorio.java`: descarga respeta el filtro elegido en vista
  (`<nombre>-<Filtro>.png`); detalle con Nombre[codigo] (+ `SERVIDOR
  (sistema)`, fallback al código); `DetalleMensaje` compartido entre
  pestañas Mensajes e Informes (detalle por id de columna 0 cuando el
  tipo es mensajes); maestro-detalle en Usuarios (ficha + Cerrar sesiones
  + Ver mensajes).
- Tests: suite completa del reactor **en verde** (`mvn -pl
  server/servidor-app -am test`, 52 tests incl. E2E). Nota: correr SIN
  `-am` falla por artefactos viejos en el repo local (`ConfigRed`,
  `Imagenes.esImagenValida`); con `-am` todo pasa.

**Diagramas**
- `DIAGRAMAS_ENTREGA/` (nueva, 8 `.mmd` + 8 `.png`, solo flujo):
  arquitectura, login, envío, difusión, clases servidor, clases cliente,
  ER servidor, ER local.
- ER revisados contra los 3 `V1__init.sql` + 3 `schema-local.sql`:
  tablas/columnas/FK idénticas en los 3 motores (verificado por script);
  **corrección real encontrada**: Oracle NO crea 5 índices
  (`idx_sesion_activa`, `idx_conexion_activa`, `idx_archivo_propietario`,
  `idx_mensaje_dest`, `idx_accion_tipo`) y los tipos físicos difieren
  (NUMBER/VARCHAR2/CLOB vs BIGINT/BOOLEAN/TEXT). Anotado en
  `DIAGRAMAS/modelo-relacional/er-modelo-servidor.mmd` y reflejado en
  `DIAGRAMAS_ENTREGA/07-er-servidor.mmd` (tipos conceptuales + nota).

**Pendiente / follow-up:** punto 10 (catálogo de textos + checklist
guiada en los 3 clientes), refrescar `DIAGRAMAS/mockups/` con la UI nueva
(cita en tarjeta, login solo-lectura), y prueba manual E2E con los 3
clientes contra servidor real (login → envío texto/imagen/archivo →
descarga → difusión).

## Conformidad con ComponentesServidor.png (2026-10-04, después del plan)

Revisión pedida: en `DIAGRAMAS_ENTREGA/01-arquitectura.png` la Fachada
llegaba a la BD. Verificado en código: `FachadaDeServicios` llamaba
directo a `almacenar` en 3 lecturas de vistas (`archivoParaVista`,
`bytesArchivoParaVista`, `fechasPorCodigo`), saltándose los casos (contra
su propio javadoc) y contra la referencia (Fachada solo alcanza
persistencia vía Mensajes/LogDeEventos/casos). Las vistas
(`VistaConsola`, `VistaEscritorio`) solo usan `Fachada`: conformes.
Corregido:
- `ArchivoVistaCaso` + `FechasUsuariosCaso` nuevos en `casosdeuso`,
  cableados en `Casos` y `Fabrica`; la Fachada delega y **ya no depende
  de `InterfazAlmacenarInformacion`** (campo, parámetro e import
  eliminados; test actualizado).
- `DIAGRAMAS_ENTREGA/01-arquitectura.mmd/png`: Fachada → componentes →
  Persistencia → BD (quitadas FAC→BD y COMP→BD; los `comp-*` nunca
  tocaron BD: solo `int-*`/`dto`/`codec`, mapas en memoria y lógica pura).
- Suite `mvn -pl server/servidor-app -am test` en verde. Nota: un
  `SocketTimeout` en `broadcastLlegaATodosSalvoAlEmisor` apareció solo con
  la máquina bajo carga paralela (compilaciones + tests concurrentes);
  en máquina tranquila los 2 tests de broadcast pasan; el log mostró que
  la ruta `exceptionally` → `fallidos` → ACK funcionó según diseño.
  Sin `-am` la suite falla por artefactos viejos del repo Maven local
  (`ConfigRed`, `Imagenes.esImagenValida`): usar siempre `-am`.

## Punto 10 + roadmap (2026-10-04, con subagentes)

**Punto 10 (textos UI):** `DIAGRAMAS/textos-ui.md` con tabla
concepto|Java|Python|.NET|canónico por pantalla + paleta + teclado +
checklist; literales alineados (tildes, placeholders, confirmaciones) en
los 3 clientes; hueco conocido: Esc=cancelar no existía en ninguno (luego
implementado, ver abajo). Tests 42/42, 35/35, 55/55.

**Esc=cancelar (cierre del hueco):** Java (`alTeclaEntrada` → cancela cita
y adjunto), .NET (`TxtMensaje_KeyDown` Escape → `LimpiarCita` +
adjuntos), Python (`QShortcut` Esc → `_cancelar_composicion`, sin tocar el
texto). Test Python nuevo en verde.

**Mockups al día:** `mockup-login` sin checkbox + campos solo-lectura +
anotaciones vigentes; `mockup-chat` con tarjeta de cita + ✕.

**Guía de aceptación:** sección "Smoke de los cambios 2026-10-04" con los
7 casos nuevos (cita, adjunto-solo, login, descarga con filtro,
maestro-detalle, difusión con progreso, reintento offline Python).

**Seguridad (subagente):** TLS con fail-fast (`validarTls`: clave
`CAMBIAR`/vacía, almacén ausente, colisión de puertos) + 6 tests nuevos;
rate-limit respeta `0` = sin límite; secretos por entorno
(`RED_TLS_CLAVE`, `DB_*_PASS`) con mismos defaults locales; `.gitignore`
para keystores; sin fugas de claves en logs (verificado por grep).
Python sin TLS: fuera de alcance (tocaría cliente + operativa).
Utilerias 11/11, comp-protocolo 15/15, subset servidor-app 16/16.

**Distribución (subagente):** `Dockerfile` multi-stage corregido (COPY con
nombre exacto, caché BuildKit, `EXPOSE 5000 5001`, healthcheck TCP,
volúmenes nombrados en compose, restart policies); `DESPLIEGUE.md` con
despliegue, ACTUALIZACIÓN, BACKUP/RESTORE e instaladores nativos;
`docker compose config` en verde. **Imagen `mensajeria-servidor:1.0.0`
construida y verificada por el coordinador.**

**Rendimiento (subagente, solo medición):** `SimuladorCargaTest` = 50
sesiones × 2 textos cortos contra MySQL real → **100 ACKs en 8,5 s =
11,8 tramas/s**, pool 1/100, 0 rechazos. Cuellos: pipeline de texto en
`poolFiltros` de 4 hilos (~85 ms/msg), ~5 escrituras BD por texto
(auditoría en camino crítico del ACK), Hikari 10 como primer techo hacia
100 sesiones. Propuestas: filtros.hilos 4→8/16, Hikari 10→20-30, sacar
`ETAPA_FILTRADO` del camino crítico. `poolEntrega` no se ejercitó (solo
broadcast): medir al probar difusión masiva.

**No hecho (consciente):** notificación al minimizar y salas temáticas
(fila "Producto"): requieren decisión de producto + cambio de protocolo
(salas: modelo, enrutado, 3 UIs). Quedan como propuesta, no a medias.

## Ronda pruebas reales (2026-10-05)

- **Python duplicaba al salir/entrar:** cargas solapadas sin filtro.
  Token `_gen_historial` que descarta respuestas viejas + 2 tests.
- **Python no descargaba:** botón solo para `es_archivo` con id y sin
  refresco; imágenes sin descarga. Ahora `refrescar_descarga()` +
  descarga de imágenes vía `obtener_imagen` + 2 tests. **47/47 pytest.**
- **Java cita ilegible:** bloque `message-quote` separado en la burbuja.
- **.NET NRE al enviar:** foto de sesión (`SesionEnvio`) en
  texto/imagen/archivo/fragmentos/reintentos/historial + mensaje amable.
  **35/35 dotnet.**
- **JVM:** manifest `Enable-Native-Access` (verificado en el JAR),
  JavaFX 25.0.1, Flyway 11.14.1, `UsuarioDTO.toString()` sin hash + CSV
  sin contenido. Cliente **55/55**, servidor **78/78**.
- **16 fallos de suite NO eran código:** el CSV de producción reemplazó a
  los usuarios de prueba. Nuevo `src/test/resources/usuarios_iniciales.csv`
  (A001–D004 + SERVIDOR) que hace sombra al real. Ojo: correr tests
  desactiva las cuentas reales en esa BD; el arranque normal las reactiva.

## Distribucion y rendimiento (2026-10-05, cierre de 3.3 y 3.4)

### Entorno (este equipo es distinto al de la ronda anterior)

Maven no estaba instalado: puesto en `C:\Tools\apache-maven-3.9.9` con `JAVA_HOME`
al JDK 24 (el proyecto pide 21+). El SDK 10 de .NET tampoco (los tests son
`net10.0` y el SDK del sistema es 8): instalado en
`%LOCALAPPDATA%\Microsoft\dotnet10` con el script oficial, sin tocar el PATH
global. El `.venv` de Python apunto a `C:\Users\AlexP\...`, de otro equipo, y
fallaba; recreado con `py -3.13`. Docker Desktop estaba instalado pero parado.

### Bug de concurrencia en difusion (no era de distribucion ni de rendimiento)

**Sintoma:** `MensajeriaE2ETest.broadcastAdministrativoLlegaASesionesSinUsuarioDeSistema`
fallaba con `SocketTimeoutException`, y el log mostraba 11 de 65
`No se pudo persistir difusion SERVIDOR -> ...: no se pudo procesar el texto`.

**Causa:** `AlmacenarInformacion.promocionarAUploads` construye el destino como
`uploads/<propietario del remitente>/textos/<hash>/`. La difusion administrativa
manda el MISMO contenido desde el MISMO remitente (`SERVIDOR`) a los 65
usuarios, asi que las 65 copias comparten hash y por tanto la misma ruta final.
Varias tareas de `poolFiltros` ejecutaban `Files.move(..., REPLACE_EXISTING)`
sobre el mismo archivo a la vez, y en Windows eso lanza excepcion: las copias
perdedoras se perdian y su destinatario nunca recibia el BROADCAST.

**Solucion:** candado por ruta de destino (`ConcurrentHashMap` de rutas a
objetos) y, si el destino ya existe, se descarta el temporal en vez de
sobrescribir. El contenido es identico porque el hash es identico, asi que
promover dos veces a la misma ruta es idempotente por definicion. Aplica igual
a los derivados 01..05.

**Prueba:** `ProcesarTextoArchivoUploadsTest.difusionDelMismoTextoEnParaleloNoPierdeCopias`
(24 copias del mismo texto con pool real de 8 hilos). Comprobado que FALLA sin el
fix y pasa con el.

### DIST-01: el compose de produccion rompia al arrancar

`docker-compose.prod.yml` era un override correcto pero heredaba los volumenes
con nombre `<proyecto>_mysqldata` del compose de desarrollo. MySQL solo aplica
`MYSQL_ROOT_PASSWORD` en el primer arranque del volumen, asi que el volumen ya
inicializado con la clave de ejemplo dejaba al servidor de produccion en bucle
de reinicio con `Access denied for user 'root'@...`. Fix: `volumes:` con `name:`
propio en el override (`mensajeria-prod-*`). Verificado: build, arranque healthy,
`config` valido, y `config` que falla a proposito sin `.env.prod`.

### DIST-02: los tres `.cmd` no funcionaban en una maquina limpia

- `client\dist-client.cmd`: paso 1 usaba `package` y paso 2
  `dependency:copy-dependencies`, que resuelve `dto`/`codec`/`client-interfaces`
  desde el repo local. Sin `install` Maven no los encuentra: fallo
  `Could not resolve dependencies`. Ahora paso 1 es `install`.
- El mismo script buscaba los nativos JavaFX con
  `for %%F in ("%USERPROFILE%\.m2\...\...-win.jar")`. Un glob entrecomillado en
  `for` no expande comodines, asi que `FXCOUNT` se quedaba en 0 y el script
  abortaba aunque los JAR existieran. Ahora se copian desde `lib\`, donde
  Maven ya resolvio los `*-win.jar`.
- `client-python\distribuir.cmd`: el paso 3 usaba `compileall`, que regenera
  `__pycache__` dentro de la distribucion. Anadida la limpieza tras compilar.
- Los tres terminaban con `exit /b` del codigo de `findstr`, que devuelve 1
  cuando no filtra lineas: el script|reportaba fallo aunque todo estuviera
  bien. Ahora `exit /b 0` explicito al final.
- Nuevo `dist\distribuir-servidor.cmd` + `server\servidor-app\start-server.cmd`:
  el servidor no tenia carpeta nativa; el lanzador es archivo versionado, no un
  here-doc, porque escribirlo con `echo` dentro de un bloque entre parentesis
  rompe al chocar los `^` de continuacion con el cierre del bloque.
- Copia de Java arrancada de verdad desde `%TEMP%`: imprime su banner, levanta
  JavaFX y abre la UI.

### DIST-03: checklist de actualizacion y backup, ejecutada de verdad

Sobre el stack de produccion: backup de BD + `uploads` + `logs`, restore con
borrado del arbol previo (comprobado que un archivo de prueba vuelve), y
`up -d --build` conservando `flyway_schema_history`, un usuario marcado en la BD
(la semilla CSV lo desactiva, no lo borra: comportamiento correcto de
`reconciliarUsuarios`) y el contenido de `uploads`. Detalles que se
documentaron porque fallan en silencio: la clave va entrecomillada en
`-p$clave` (sin comillas PowerShell se come el `!`), y hay que parar el
servidor antes de respaldar la BD.

### PERF-01/02/03: con numeros, no a ciegas

Nuevo `CargaMixtaTest` (100 sesiones x 3 textos + 1 imagen = 400 tramas, con
p95 del ACK, y difusion masiva con un cliente lento que nunca lee).

| Config | tramas/s | p95 ACK |
|---|---:|---:|
| Hikari 4 / filtros 4 | 58,7 | 2701 ms |
| Hikari 10 / filtros 4 (base) | 77,6-81,2 | 1524-1719 ms |
| Hikari 10 / filtros 16 | 87,4 | 1189 ms |
| Hikari 30 / filtros 4 | 88,9 | 1516 ms |
| Hikari 30 / filtros 16 | 96,1-98,5 | 1088-1185 ms |

PERF-02 aplicado: `pool.maximumPoolSize` 10->30 (y `minimumIdle` 2->5),
`filtros.hilos` 4->16. Tres repeticiones por punto, la base es reproducible
(79/77,6/80,0). Bajar Hikari a 4 cae a 59 tr/s: el 10 era un techo real, no un
default. `ETAPA_FILTRADO` fuera del ACK no hizo falta. Aislados los cambios dan
89 y 87 tr/s; el combo es lo que rinde.

PERF-03: 99 de 99 destinatarios en 2-6 s con un cliente lento cuyo socket nunca
lee. El reparto de hilos `entrega-N` no hace starving.

Dos cosas que costaron encontrar y conviene no volver a tropezar: el servidor
confirma una imagen con `IMAGE_FILTERED_RESULT`, no con `ACK` (esperar solo ACK
expira el test), y `invokeAll()` ESPERA a que terminen las tareas, asi que para
medir una difusion hay que `submit()` una a una.

**Suites al cierre:** servidor **90/90** (87 previos + 1 del fix + 2 de carga),
cliente Java **58/58**, Python **60** + 5 omitidas, .NET **35/35**.

---

## Ronda de planificación 2026-10-05 (documentación, sin aplicar código)

Esta ronda **no modificó código**. Se auditó el proyecto completo y se escribió la planificaciónón.
Evidencia con `archivo:línea` en [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md); plan ejecutable en
[`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md); tablero en [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md).

### Qué se escribió

| Artefacto | Qué es |
|---|---|
| `ANALISIS_TECNICO.md` (nuevo) | Auditoría: 1 hallazgo de seguridad, 38 items de código muerto, 6 bugs de UI P0, 13 P1, 13 P2, 6 patrones faltantes, deriva de literales y paleta |
| `PLAN_PATRONES_UI.md` (nuevo) | 7 fases de refactor de clientes con criterios medibles |
| `DIAGRAMAS/{casos,secuencia,flujos,estados,despliegue}/` + 2 `.mmd` de clases (nuevo) | 16 diagramas que faltaban, como fuente `.mmd` |
| `DIAGRAMAS/README.md` (reescrito) | Catálogo completo + plan de consolidación de `DIAGRAMAS_ENTREGA` / `DIAGRAMAS_FINALES` |
| `ESTADO_ROADMAP.md` (reescrito) | Familias nuevas `SEC-`, `MUERTE-`, `UIX-`, `PAT-`, `VIS-`, `DIAG-`, `PROD-` |
| `REQUERIMIENTOS.md` (actualizado) | `RF-S34/S35`, `RF-C23..C31`, `RNF-16..20`, `PD-09..13` y control de cambios 11–15 |
| `PLAN.md` §14 (nuevo) | Capa cliente, patrones añadidos y política de código muerto |
| `GUIA_ACEPTACION.md` (actualizado) | Comprobaciones de seguridad, matriz de estados de conexión, capturas y contraste |
| `README.md`, `REQ.md` | Índice documental y estado actual |

### El hallazgo que bloquea producción

`SEC-05`. `LISTAR_USUARIOS` es uno de los **3 de 15** comandos que no aplican
`Comandos.exigirAutenticacion(...)`, y el despachador no filtra por sesión
(`Despachador.java:55-62`). Una sesión anónima recibe el directorio completo —código, nombres,
apellidos, programa— sin código ni contraseña (`ListarUsuariosCommand.java:14-20`,
`ListarCaso.java:45-62`).

Lo importante no es el `if` que falta, sino por qué no se vio: los 3 comandos **reciben
`respuestas` en el constructor y no lo usan**, así que su firma promete una capacidad que el cuerpo
no tiene. Por eso la solución añade **dos** requisitos (`RF-S34` para el rechazo, `RF-S35` para que
la firma no pueda mentir) y un **test parametrizado** que recorra el registro de `Despachador` en
vez de una lista escrita a mano.

### Lo que se encontró y casi se reporta mal

Vale la pena dejarlo escrito porque es la clase de error que se repite:

- **Cero referencias no significa código muerto.** `@Configuration` sin referencias, `LoginController`
  (viva por `fx:controller=` del FXML), los 5 `Proveedor*` (vivos por `ServiceLoader`), los 49 tests
  (vivos por el escaneo de JUnit), `VistaEscritorio.start` (lo invoca JavaFX) y las clases de estilo
  `.split-pane` / `.list-cell` / `.combo-box` (integradas en JavaFX). Todo eso tiene 0 referencias
  y vive. Por eso las reglas B1–B6 son parte del entregable, no un comentario.
- **`FiltroMain` no es código muerto** aunque no lo use producción: es una CLI opcional documentada.
- **`NULA` en `FabricaDelDespachador` recibe 0 llamadas**, pero `crear()` es un método obligatorio
  del contrato SPI. Es "probablemente muerto", no muerto.
- **Los 37 `.mmd` tienen la misma fecha de modificación** (artefacto de checkout), así que la
   antigüedad de cada diagrama hubo que deducirla del contenido, no del timestamp.

### Bugs de UI que son funciones rotas, no estilo

| ID | Qué no hace |
|---|---|
| `UIX-00a` | .NET: existe `OnDesconectado` y existe `bannerOffline`, pero **nadie se suscribe**: la pérdida de conexión es invisible |
| `UIX-00b` | Python: el botón "Reintentar imagen" **nunca reintenta** — el `_ejecutar` está después del `raise` |
| `UIX-00c` | Java: `alError()` y `alDesconexion()` **están vacíos**; son un comentario, no código |
| `UIX-00d` | Java: el fallo de persistencia **se traga** con `catch (Exception e) { }` |
| `UIX-00e` | Python: `login()` corre en el hilo de la GUI y **congela la ventana hasta 15 s** |
| `UIX-00f` | .NET: **"Conectando…" se pinta dentro de la caja roja de error** |

### El hueco arquitectónico de los 3 clientes

La capa de presentación no es espejo, y ahí está la deuda:

- Java **construye bien** el grafo en `ClienteMain` y acto seguido lo tira con un localizador
  estático → `ChatController` **no se puede construir en un test**.
- Python es peor: la ventana **parchea la fachada** con `setattr`, la interroga con
  `getattr`/`hasattr`, y el grafo **cambia de forma en runtime** al hacer login.
- .NET tiene el constructor con DI desde `FachadaCliente.cs:52-61` y **solo lo usan los tests**.

La prueba de que no hay ViewModel: `client-python/tests/test_ui.py` tiene que llamar a **3 métodos
privados** y su doble sustituye la fachada **entera**, porque no hay costura por la que inyectar un
ViewModel. De ahí sale el criterio medible de `PAT-VM`: **0** métodos privados llamados desde tests.

### Deriva del contrato compartido

Los 3 clientes repiten **73 cadenas idénticas**. El caso grave es la **máquina de estados de
mensaje**, escrita como un `CASE` SQL con la misma precedencia **3 veces**: si se añade un estado y
se olvida una pista, el historial de un cliente discrepa del de otro.

La paleta pasó algo parecido: los **11 colores del tema oscuro coinciden exactamente** en los 3
(alguien copió), pero **el tema claro no coincide en ninguno** — burbuja propia `#E4F3EC` /
`#E1F5FE` / `#D9FDD3` y acento `#2456A6` / `#147D73` / defecto de WPF-UI. Se inventó tres veces.

### Diagramas: qué faltaba de verdad

Verificado por regex sobre los 37 `.mmd` del repo: solo existían `flowchart`, `classDiagram`,
`erDiagram` y `sequenceDiagram`. **Cero** `usecaseDiagram`, **cero** `stateDiagram`, **cero** árboles
de decisión. Y no había ni un `classDiagram` de los clientes Python ni .NET.

Se escribieron 16 fuentes. **Sin render**: en este entorno no hay `mmdc`, ni `plantuml`, ni `node`.
Como la regla de `DIAGRAMAS/README.md` §1 exige source + render + mirar el PNG, **`DIAG-10`
(instalar el toolchain) bloquea en la práctica a `DIAG-01..06`**, y está anotado así.

### Consolidación de diagramas

`DIAGRAMAS/` gana en todo lo duplicado (deriva del código y los `.sql` reales, con atributos,
notas, el inventario de 13 índices y los 3 motores). Pero se le **pierden** 6 diagramas de clase
únicos de `DIAGRAMAS_FINALES` y **7 mockups** de pantallas del servidor, y `DIAGRAMAS_ENTREGA`
aporta 4 diagramas únicos. El plan está en `DIAGRAMAS/README.md` §3.3, con la regla de **copiar
antes de borrar** y actualizar `$anchos` de `render.ps1`.

### Lo que queda sin aplicar

Nada de esta ronda toca código. El orden propuesto está en `ESTADO_ROADMAP.md` §12 y empieza por
`SEC-05`, cuyo primer paso es el **test**, no el fix.
