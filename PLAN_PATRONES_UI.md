# Plan de patrones de diseño y mejora de UI — los 3 clientes

Actualizado: **2026-10-06**. Este archivo conserva el **plan y sus decisiones de implementación**
derivados de [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md). Varios lotes ya están aplicados; el
estado actual de cada tarea se mantiene en [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md). Las mediciones,
líneas de código y colores en tablas posteriores pueden ser el estado previo a cada lote, no la
lectura actual del código.

> **Alcance original.** Las fases de este plan cubren sobre todo los tres clientes
> (`client/`, `client-python/`, `client-dotnet/`). La consola administrativa del servidor tuvo una
> ronda posterior propia: sus cambios y verificaciones se describen en `ESTADO_ROADMAP.md`,
> `GUIA_ACEPTACION.md` y `server/servidor-app/README.md`. El hallazgo de seguridad `SEC-05` también
> está cerrado; véase el roadmap.
>
> **Ronda de arquitectura 2026-10-06:** se ejecutó `PAT-01` solo en el cliente Java. Se añadieron
> inversión de dependencias, inyección por constructor, una fábrica de controladores FXML y una
> fábrica de composición. Python y .NET no se modificaron; sus tareas continúan en el roadmap.
>
> **Refuerzo SOLID/arquitectura por capas 2026-10-06 (solo Java):** se segregaron los puertos del
> login y de adjuntos/historial; `LoginController` y `ChatController` reciben el ejecutor de
> fondo desde la composición; `ChatController` recibe `EventoCliente` en vez del DTO de red; la
> traducción del JSON del servidor vive en `MapeadorDatosServidor` tras su interfaz; la gestión de
> descargas e imágenes se extrajo a `GestorAdjuntosChat`.
> Se conserva la fachada agregada y el protocolo. Esto fortalece las fronteras sin completar
> todavía `PAT-VM`: `ChatController` mantiene estado de pantalla, selección y paginación, por lo
> que su separación completa a MVVM sigue pendiente.
>
> **Continuación del 2026-10-06:** `ChatViewModel` ya centraliza estado de conversación (adjunto,
> respuesta, offset, directorio y recientes) y `ChatController` delega en él las operaciones; el
> controlador todavía coordina selección, eventos, paginación y ciclo de vida JavaFX (968 líneas).
> El contrato de transporte y su receptor ahora están en `client-interfaces`, con `RespuestaRed`
> neutral, y la traducción de DTO se realiza en `ConexionCliente`. Una ejecución limpia pasa 127
> pruebas (0 fallos/errores), ambos FXML cargan mediante la fábrica real y la compilación conjunta
> cliente/servidor pasa. Sigue pendiente el smoke visual conectado; no se declara completado
> `PAT-VM` ni Clean Architecture.

## 0. Por qué ahora y por qué en este orden

Tres razones, en orden de peso:

1. **La auditoría detectó 6 bugs de UI que eran funciones rotas**, no de estilo. Un botón de reintentar que no
   reintenta y un aviso de "sin conexión" que nunca aparece son deuda de la misma categoría
   que un error de compilación: el usuario pidió una función y no la tiene.
2. **Nada nuevo se puede añadir sin antes partir los archivos grandes.** `chat_window.py` son
   1 322 líneas y `ChatController.java` 774, y los dos mezclan vista, estado, paginación,
   caché y red. Agregar búsqueda en el historial (UIX-06) sobre esa base produce un archivo de
   1 600 líneas que nadie va a mantener.
3. **El código muerto se limpia antes de los patrones.** `MUERT-19` (`_tareas_imagen` creciendo
   sin límite) y `MUERT-37` (`bannerOffline`, luego cableado a UIX-00a) están **dentro** de los archivos que se
   van a partir. Partir primero y limpiar después significa tocar dos veces lo mismo.

**Orden derivado:** bugs → limpieza → cimientos (composition root + estado de conexión) →
MVVM → Command → los demás patrones → pulido visual.

## 1. Fase A — Bugs de UI (P0). Sin refactor, solo corregir

Precondición: ninguna. Cada uno es un cambio pequeño y verificable por separado.

| ID | Tarea | Cliente | Evidencia | Aceptación |
|---|---|---|---|---|
| **UIX-00a** | Suscribir `OnDesconectado` en `ChatWindow` y cambiar `bannerOffline.Visibility` desde ahí | .NET | `FachadaCliente.cs:41`, `ChatWindow.xaml:29` | Con el cliente abierto: matar el proceso del servidor → el banner ámbar aparece en los 3 clientes en ≤ 2 s, con el mismo texto que Java y Python |
| **UIX-00b** | Sacar el `_ejecutar("imagen", cargar)` de después del `raise` en `cargar()` | Python | `chat_window.py:1249-1250` | Romper la red, enviar imagen, reconectar, pulsar **Reintentar imagen** → la imagen aparece, sin recargar la aplicación |
| **UIX-00c** | Implementar `alError()` y `alDesconexion()`; dejar de descartar en silencio | Java | `FachadaCliente.java:767-775` | Servidor cae durante un envío → el usuario ve un mensaje, no la nada. Test de fachada que compruebe que `alError` se invoca |
| **UIX-00d** | Loguear (y no tragar) el fallo de persistencia al recibir | Java | `FachadaCliente.java:756-758` | Con la BD local bloqueada en solo lectura: llega un mensaje → aparece en pantalla, y el log del cliente lo dice |
| **UIX-00e** | Mover `login()` del hilo GUI al pool de tareas | Python | `login_window.py:204` | Con el servidor inalcanzable: la ventana **responde** los 15 s (se puede mover, el botón cambia), no se congela |
| **UIX-00f** | Tercer estado visual en el login: neutro para "Conectando…" | .NET | `LoginWindow.xaml.cs:85-86`, `LoginWindow.xaml:52-55` | Con lentitud artificial: "Conectando…" sale en gris sobre fondo neutro; el rojo queda reservado al error |

> **UIX-00a y UIX-00c son el mismo defecto en dos idiomas.** Java y Python ya tienen Observer
> para la conexión; .NET tiene el evento y no lo conecta. Arreglarlo "a mano" en .NET sería la
> cuarta reimplementación de la misma idea — por eso el estado de conexión unificado es
> `PAT-03` y se hace en la Fase C, no antes.

## 2. Fase B — Limpieza de código muerto (P0/P1)

Detalle completo con los 38 items en [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) §2.
Aquí solo el **criterio de parada**, que es lo que evita que esto se vaya de las manos.

### 2.1 Reglas para decidir qué se borra

| Regla | Formulación |
|---|---|
| **B1 — Cero referencias o nada** | Un símbolo con **0** referencias fuera de su definición **y** sin anotación de framework / reflexión / contrato SPI / convención de toolkit se borra. |
| **B2 — Nunca borrar por búsqueda de texto** | `.split-pane`, `.list-cell`, `.combo-box` son **clases de estilo integradas** de JavaFX; `chat.fxml:15 fx:controller=` es lo que hace viva `LoginController`; `ServiceLoader` es lo que hace vivos los 5 `Proveedor*`; el escaneo deJUnit es lo que hace vivos los 49 tests. Cada uno tiene 0 refs y vive. |
| **B3 — Clase `@Configuration`/`@Component` nunca es muerta** | Aunque el nombre no aparezca, Spring la instancia. |
| **B4 — El enum se borra solo si no hay filas históricas** | `RegistroAccion.java:32` es `@Enumerated(STRING)`. Quitar `CONECTADO`/`HISTORIAL`/`DESCARGA` (`MUERT-09..11`) exige **primero** comprobar la BD de producción. |
| **B5 — dead feature ≠ código muerto** | `difundir()` sin botón (`ANALISIS_TECNICO.md` §2.2) es una decisión de producto, no basura. Va a decisión, no a borrado. |
| **B6 — Lo que se borre va con test** | Un item que no tiene cobertura no se borra: se añade el test y luego se borra. |

### 2.2 Lotes de borrado

| Lote | Contenido | Riesgo | Verificación |
|---|---|---|---|
| **MUERTE-1** | 13 símbolos Java + 3 parámetros `respuestas` + 12 imports Java (`MUERT-01..13`) | Bajo. Todo con 0–2 refs | `mvn -pl server/servidor-app,client/client-app -am test` en verde; el E2E de login sigue pasando |
| **MUERTE-2** | 9 pinceles `Telegram*` + 3 constantes `TipoMensaje.cs` + `RutaDbActual` + `cuentaExistia` + 12 `using` (`MUERT-25..30`) | Bajo. Todo en .NET | `dotnet test` + `dotnet build -c Release`; comparación visual del chat en ambos temas contra `DIAGRAMAS/mockups/mockup-chat-telegram-dotnet.png` |
| **MUERTE-3** | `_guardar_propio`, `mostrar_aviso`, `_tareas_imagen`, tokens muertos, 5 imports, campos de `ServidorStub`, 4 métodos de fake (`MUERT-16..24`) | Bajo **con una excepción**: `_tareas_imagen` es un leak, así que su borrado es una corrección, no solo limpieza | `pytest tests` en verde |
| **MUERTE-4** | CSS y XAML muertos (`MUERT-31..38`) | **Medio**: `.split-pane-divider` es clase integrada (B2) y los estilos se notan a ojo | Antes de borrar cada clase CSS: arrancar la app y confirmar que el control sigue con su estilo. Comparar capturas antes/después |
| **MUERTE-5** | Deduplicación (§2.5 del análisis): 3 parsers de properties → 1; `fallbackSchema` muerto; 2 migradores de esquema duplicados; `_leer_modo`/`_guardar_modo` unificados; tabla MIME unificada | Medio: toca I/O | Tests existentes + un test nuevo que verifique que los 3 clientes leen las mismas claves de `client.properties` |
| **MUERTE-6** | Decisión de `difundir()` y de `idioma=` y `pillow` | Decisión | Registrado en el roadmap con la justificación |

### 2.3 Lo que este lote **arregla** de paso

| Ítem | Qué es | Ganancia real |
|---|---|---|
| `MUERT-33` | `.sidebar-title` declarado dos veces con valores que se contradicen en `chat-claro.css` | El título del sidebar se ve del color equivocado en tema claro |
| `MUERT-28` | `_ = cuentaExistia;` | Anula el aviso del compilador de variable sin usar, lo que **esconde** los MUERT reales |
| `MUERT-19` | `_tareas_imagen` | Leak: crece con cada imagen cargada y nunca se limpia |
| `MUERT-25` | `BtnAdjuntarArchivo_Click` | Un handler que sugiere un botón que no existe |
| Regla B4 | `TipoAccion` | El Javadoc de `LoginConteoDTO.java:5` afirma que se agrupa por "LOGIN/CONECTADO" cuando `JpaPersistenciaAdapter.java:245` solo pasa `LOGIN`. Documentación que miente |

## 3. Fase C — Cimientos de arquitectura (P0/P1)

Tres cambios de los que dependen casi todos los demás. Por eso van **antes** de MVVM.

### 3.1 PAT-01 — Composition root real (eliminar el localizador de servicio)

**Hallazgo histórico.** Java construía el grafo en `ClienteMain` y después lo escondía en un
localizador estático de `ClienteApp`; `LoginController` y `ChatController` lo recuperaban durante
`initialize()`. Python construye colaboradores con defaults y .NET difiere parte de la composición
al login. Esta ronda corrige únicamente el camino Java.

**Implementación Java (2026-10-06).**

| Principio / componente | Cambio |
|---|---|
| Inversión de dependencias | Los puertos de consumidor (`InterfazInicioSesion`, `InterfazChatCliente`, adjuntos y eventos) viven en `client-interfaces`; `FachadaCliente` los implementa. `InterfazNavegacionCliente` separa la navegación del shell JavaFX |
| Inyección de dependencias | `LoginController` recibe `InterfazInicioSesion`, ejecutor, configuración y navegación. `ChatController` recibe `InterfazChatCliente`, ejecutor y navegación. Ninguno consulta un servicio estático |
| Fábrica de composición | `FabricaCliente` crea los adaptadores concretos y el `ClienteRuntime`, que posee y cierra los recursos |
| Fábrica de controladores | `FabricaControladores` entrega los controladores a `FXMLLoader.setControllerFactory`, incluyendo sus dependencias explícitas |
| Composition root | `ClienteApp.init()` crea el runtime y configura la fábrica FXML. `ClienteMain` solo delega en `Application.launch`; se eliminó el localizador global de fachada |
| Contratos | `EstadoConexion` y `PaginaHistorialRemota` viven en `client-interfaces`, para que el puerto no dependa de tipos definidos por `client-app` |

Python y .NET quedan fuera de esta ronda y no se modificaron. En la ronda posterior del 2026-10-06,
`GestorAdjuntosChat` pasó a depender de `InterfazArchivosConversacion`, la fachada recibió
`InterfazMapeadorDatosServidor` y la UI pasó a observar `EventoCliente`; los detalles están en
`ESTADO_ROADMAP.md` §6.3.

En la continuación arquitectónica del 2026-10-06, el ciclo de sesión/reconexión se extrajo a
`GestorReconexionSesion`: conserva credenciales solo en memoria, aplica backoff, reautentica y
solicita restaurar el estado tras reconectar. La fachada delega ese ciclo; la extracción pasó la
suite limpia del cliente (127 pruebas, 0 fallos/errores) y la compilación conjunta de servidor y
cliente. El smoke visual conectado sigue pendiente; esta extracción no completa PAT-ARQ-JAVA.

En el cierre de esta ronda, `ChatController` dejó de recibir `InterfazFachadaCliente`: ahora recibe
`InterfazChatCliente`, un puerto consumidor que agrupa solo los casos de uso de esa pantalla. La
fábrica recibe los puertos de login y chat por separado; `FachadaCliente` sigue implementando ambos
y `InterfazFachadaCliente` conserva el contrato agregado para componentes existentes.

**Verificación de aquella ronda.** Los 17 módulos compilaron con
`mvn -pl server/servidor-app,client/client-app -am -DskipTests compile`, incluidos
`servidor-app`, `client-interfaces` y `client-app`. Después se ejecutaron 83 pruebas del cliente
Java, todas en verde. El chequeo de fuente confirmó que los controladores ya no invocan
`ClienteApp.fachada()`. La suite actual de 127 pruebas y el smoke visual pendiente se registran
arriba; la carga automatizada FXML ya está cubierta.

**Pendiente fuera del alcance Java:** aplicar el mismo corte de composición a Python y .NET, y
agregar cobertura automatizada para la fábrica FXML y la construcción de controladores.

**Por qué antes de MVVM:** el ViewModel se inyecta. Si el canal de inyección es un estático,
se arrastra el problema.

### 3.2 PAT-03 — `EstadoConexion` observable, en los 3 clientes

**El problema.** Es la causa directa de `UIX-00a` y de `UIX-00c`. Cada cliente improvisa:

| Cliente | Cómo sabe hoy que se cayó |
|---|---|
| Java | **Sondea** `fachada.conectado()` cada 30 s (`RESPALDO_SEGUNDOS=30`, `:57`) porque `alDesconexion` está vacío (`:772-775`) |
| Python | Cambia el banner ámbar **dentro del resultado de la tarea `directorio`** (`:583-585`), no en `_procesar_desconexion` (`:1181-1182`). Es decir: solo si además se refrescó el directorio |
| .NET | No se entera: `OnDesconectado` sin suscriptores |

**Qué hacer.** Un solo concepto en los 3, con los mismos 4 estados y las mismas transiciones:

```
Conectando → Conectado → SinRed → Conectando
                  ↓          ↓
            SesionCerrada  Conectando
```

`Conectando` (abriendo socket, TLS handshake) · `Conectado` (autenticado) · `SinRed` (caída o
servidor inalcanzable; la cola local sigue activa) · `SesionCerrada` (LOGOUT, `KICK` o
cierre por inactividad → volver al login con aviso).

Implementación por toolkit, una sola fuente de verdad cada uno:
JavaFX → `ReadOnlyObjectProperty<EstadoConexion>` en la fachada, observado por la vista.
Python → `Signal` en `_UiEvents` (`chat_window.py:25-31`, que ya existe) + `Property`.
.NET → `INotifyPropertyChanged` sobre `EstadoConexion`, que además **activa `bannerOffline`**
por binding (mata `MUERT-37` de un plumazo).

**Aceptación.** Matriz de 4 transiciones × 3 clientes en `GUIA_ACEPTACION.md`, con el texto
del aviso y el tiempo de reacción anotados. El sondeo de 30 s de Java **desaparece** (o
baja a intervalo de respaldo, no de detección).

### 3.3 PAT-02 — `CommandHandler` obligatorio en comandos nuevos del servidor

No es un cliente, pero es la misma categoría de deuda que `PAT-*` y ya está abierta por
`SEC-05`. Se pone aquí porque comparte el motivo:

- Hoy la guarda de autenticación la aplica **cada comando por separado** (12 de 15 lo hacen;
  `SEC-05` demuestra que basta con uno que se olvide). El despachador no pone puerta global
  (`Despachador.java:55-62`).
- Un `CommandHandler` nuevo puede compilar y pasar todos los tests existentes **y** ser
  insecure, porque el test solo comprueba el camino feliz de un usuario ya autenticado.

**Qué hacer.** Dos partes, y las dos importan:
1. Cerrar `SEC-05` (los 3 comandos + `ListarCaso`).
2. Un test parametrizado que itere **todos** los tipos registrados en `Despachador` y exige
   `ERROR` al invocarlos con una sesión anónima. Sin ese test, el hueco vuelve.

## 4. Fase D — MVVM en los 3 clientes (P1, el cambio grande)

### 4.1 Qué se extrae, exactamente

| Cliente | Estado actual | Objetivo |
|---|---|---|
| JavaFX | `ChatController.java` **968 líneas** en la continuación 2026-10-06: sigue manejando selección, eventos, envío, paginación, búsqueda y ciclo de vida | Completar `ChatViewModel` para coordinación del chat; el controlador baja a **≈250 líneas** de cableado FXML |
| Python | `chat_window.py` **1 322 líneas**: 20 atributos mutables, `_construir_ui` (`:243-440`), y `_procesar_tarea` (`:536-661`), un `if/elif` de **100 líneas por 8 tipos de tarea** que *es* un presenter sin tipar | `ChatViewModel(QObject)` con `Property` + `Signal`. La ventana baja a **≈500 líneas** |
| .NET | `MensajeVM` ya es un VM real (`:33-90`); `UsuarioVM` (`:21-31`) **no notifica**; la ventana guarda 10 campos de estado (`:98-105`); 8 handlers `async void` | `ChatViewModel : INotifyPropertyChanged`. `UsuarioVM` pasa a notificar y el filtro se vuelve observable (mata el `Clear()`+re-`Add()` de `:195-196,202`) |

### 4.2 Cómo se comprueba que funcionó

| Métrica | Antes | Objetivo |
|---|---|---|
| `ChatController.java` / `chat_window.py` / `ChatWindow.xaml.cs` | 968 actual (774 previo) / 1 322 / 790 | **≤ 350** / **≤ 550** / **≤ 400** |
| Métodos privados que los tests tienen que llamar | 3 (`test_ui.py:136,152,163,171`) | **0** |
| `bind()` reales en el cliente Java | 1 (`:147-150`) | **≥ 8** (los `BooleanProperty` del ViewModel) |

El criterio de 0 métodos privados llamado desde test es el que de verdad importa: significa
que la lógica se alcanza por una costura pública.

### 4.3 Orden dentro de la fase

1. **Extraer el ViewModel sin cambiar comportamiento.** Los tests existentes deben pasar sin
   tocarlos. Si hay que tocar un test, es que el refactor cambió comportamiento →-mal paso.
2. **Convertir los `if/elif` de `_procesar_tarea` en una tabla** por tipo de tarea, con su
   `Property` destino. Es donde está la mitad del acoplamiento de Python.
3. **Mover la tabla MIME** a un `FormatoContenido` compartido (mata la triplicación de §5.2 del
   análisis).
4. Solo entonces, añadir funcionalidad nueva sobre el ViewModel.

> **No reescribir la ventana desde cero.** El riesgo real de MVVM aquí es que cada cliente
> "mejore" su UI de forma distinta y se rompa la paridad que sostiene la matriz de 6
> direcciones (`GUIA_ACEPTACION.md`). El orden correcto es *extraer → verificar paridad →
> enhancing*.

## 5. Fase E — Command / ICommand (P1)

**El dolor concreto.** "Puedo enviar" está implementado **3 veces**:
`ChatController.java:174-176`, `chat_window.py:937-939`, `ChatWindow.xaml.cs:572-575`. Y las
acciones de UI están cableadas a mano: 9 métodos `@FXML` en Java, `.clicked.connect` en Python
(8 conexiones), y **0 `ICommand`** en .NET, así que ningún botón se puede habilitar de forma
declarativa.

**Comandos a unificar** (idénticos en los 3): `Enviar` · `AdjuntarImagen` · `AdjuntarArchivo` ·
`QuitarAdjunto` · `Responder` · `CancelarRespuesta` · `CargarAnteriores` · `Actualizar`
· `Salir` · `SalirEnTodos` · `CerrarSesionesAjenas`.

**Aceptación.** `IsEnabled` / `setDisable` de todos los botones se deriva de `CanExecute`, no
de una línea imperativa. Las 3 implementaciones de "puedo enviar" quedan en **1** por cliente
(que es lo correcto: cada toolkit necesita la suya, lo que no debe haber es 3 dentro del mismo).

## 6. Fase F — Los adaptadores (P2)

| ID | Patrón | Qué | Por qué ahora |
|---|---|---|---|
| **PAT-04** | Adapter del almacén local | Un contrato `HistorialLocal` con `H2HistorialLocal` + `SqliteHistorialLocal`, y el **upsert portable** `DELETE + INSERT` que ya está documentado (`HistorialLocal.java:17`) pero se usa solo en Java | Python **no tiene interfaz**: la vista la salta y llama `fachada.historial` (`chat_window.py:521-523`), y el SQL `ON CONFLICT` está *inline*. El `DELETE + INSERT` elimina la dependencia de dialecto de los 3 |
| **PAT-05** | Adapter de transporte | Un `Transporte { Pedir, Enviar }` con `TcpTransporte` y `TlsTransporte` | 3 implementaciones con 3 estrategias, y **validación TLS inconsistente**: Python valida fail-fast (`tls.py:17-45`), Java no valida, .NET sustituye el puerto en silencio (`ConexionRed.cs:43`) y nunca lee `tls.confianza`. Se cierra `SEG-01` al mismo tiempo |
| **PAT-06** | Repository con cobertura del esquema | Test de equivalencia del `schema-local.sql` en .NET | `test_schema_distribuible.py:34` protege la copia Python; **la .NET no tiene equivalente** y puede divergir en silencio |

## 7. Fase G — Mejoras de UI por pantalla (P1/P2)

Las filas siguientes conservan la especificación y el diagnóstico originales. No usar sus frases
«hoy no existe» como afirmación vigente cuando ya tienen lote de cierre: `UIX-01..16`, `UIX-17`,
`UIX-18`, parte de `UIX-19`, `UIX-24` y `UIX-29` tienen implementación posterior. El estado y los
casos pendientes están en `ESTADO_ROADMAP.md` §5.

### 7.1 Login

| ID | Tarea | Clientes | Nota |
|---|---|---|---|
| UIX-24 | Foco inicial en el campo de código | .NET | Java y Python ya lo hacen |
| UIX-23 | mnemónicos / accelerator keys (`Alt+C`, `Alt+P`, `Enter`) | los 3 | hoy no hay ninguno |
| UIX-19 | nombre accesible en botones de icono | Python, .NET | Java ya tiene 4 |
| — | Tooltip que explique **por qué** servidor y puerto no son editables | los 3 | se ven como campos de formulario y no lo son (`login.fxml:30,32` con `focusTraversable="false"`) |
| — | Ventana redimensionable | .NET (`SizeToContent`), Java (tamaño fijo 440×620, sin `minWidth`) | Python ya es redimensionable |
| — | Cuarto estado: "usuario no registrado" con qué hacer después | los 3 | hoy solo hay una frase roja |

### 7.2 Chat — lista de mensajes

| ID | Tarea | Prioridad | Nota |
|---|---|---|---|
| UIX-01 | **Virtualizar** la lista de mensajes | **P1 Python** | Implementado en Lote E: `QListView` + `MensajeListModel`/`MensajeDelegate`; la descripción anterior del `QListWidget` es línea base histórica. |
| UIX-13 | Estados vacíos en las 3 pantallas (conversación vacía, búsqueda sin resultados, sin contactos) | P1 | hoy un usuario nuevo ve un rectángulo negro y no sabe si la app está rota |
| UIX-05 | Agrupar mensajes consecutivos (ocultar remitente en una racha) | P1 | como Telegram. Los 3 lo pintan siempre |
| UIX-04 | Separadores de día | P1 | hoy la fecha de los mensajes de ayer **desaparece** en Java (`substring(11,16)`) |
| UIX-03 | Formato de fecha unificado y relativo | P1 | hoy: `HH:mm` (Java, pierde la fecha), crudo (Python), `yyyy-MM-dd HH:mm` (.NET) |
| UIX-02 | Distintivo de no leídos en los 3 + filtro "No leídos" | P1 | Java lo tiene; Python usa un truco de texto con regex; .NET no tiene nada |
| UIX-15 | Mostrar avisos y errores recuperables inline en .NET | P1 | **Hecho (Lote F):** se migran los 9 avisos/errores modales a un banner accesible con nivel y cierre manual; se conservan las 3 confirmaciones existentes |
| UIX-14 | Indicador de carga del historial | P2 | hoy solo texto de estado en Java |
| UIX-16 | Anclaje de scroll al cargar anteriores | P2 | el `setValue(anterior + 50)` de Python está codificado a mano |
| — | Agrupar mensajes propios consecutivos | P2 | |

### 7.3 Chat — imágenes

| ID | Tarea | Prioridad | Nota |
|---|---|---|---|
| UIX-08 | Zoom y pan: rueda / `+` / `-` / `F` ajustar / `0` real / `Esc` cerrar | P1 | hoy ventana fija de 900×720 en los 3: una foto de 4 000 px es ilegible |
| UIX-09 | Cancelar la carga al desplazar la imagen fuera de vista | P1 | el trabajo se sigue haciendo en segundo plano; en Python además crecía (`MUERT-19`) |
| — | Ancho de burbuja relativo al ancho de ventana | P2 | 460 / 580 / 720 px fijos: a 1 400 px de ventana el layout no responde |

### 7.4 Chat — búsqueda y accesibilidad

| ID | Tarea | Prioridad | Nota |
|---|---|---|---|
| UIX-06 | **Buscar dentro del historial** con `Ctrl+F` | P1 | Implementado en Lote B en los tres clientes; la evidencia de ausencia de DAO pertenece al diagnóstico original. |
| UIX-07 | Debounce en la búsqueda | P1 | hoy se filtra en cada tecla |
| UIX-17 | Corregir los contrastes que fallan WCAG AA | P1 | ver abajo |
| UIX-18 | Diferenciar visualmente "Enviado" y "Entregado" en .NET | P1 | comparten color; solo se distinguen por el glifo |
| UIX-21 | Cuerpo del mensaje seleccionable y copiable | P2 | Python sí; Java y .NET no |
| UIX-22 | mnemónicos en el chat | P2 | |
| UIX-23 | Escala de tipografía / alta resolución | P2 | Java mata el escalado de AtlantaFX con `setAll`; Python tiene ~25 `font-size` inline |

**Contrastes concretos a corregir (`UIX-17`).**

| Archivo:línea | Actual | Sobre | Ratio | Necesita |
|---|---|---|---|---|
| `chat-claro.css:100-103` `.chat-list-preview` | `#78858B` | `#ffffff` | **3.8:1** | 4.5:1 |
| `chat-claro.css:56-57` placeholder | `#8b979c` | `#f1f4f5` | **2.7:1** | 4.5:1 |
| `tema.py:53` `prompt` | `#7A8A99` | `#FFFFFF` | **4.2:1** | 4.5:1 |
| `tema.py:63` `fecha` | `#687A80` | — | **4.4:1** (límite) | 4.5:1 |

### 7.5 Contrato visual compartido

| ID | Tarea | Nota |
|---|---|---|
| UIX-25..29 | Cerrar los defectos de tematización pendientes; `UIX-25` y `UIX-29` ya están resueltos | §3.4 del análisis y estado actual en el roadmap |
| **VIS-01** | **Fijar una única paleta por tema, no 3** | el tema oscuro coincide en los 3 clientes (11 colores idénticos). **El claro no coincide en ninguno**: burbuja propia `#E4F3EC` / `#E1F5FE` / `#D9FDD3` y acento `#2456A6` / `#147D73` / defecto de WPF-UI. La fuente de verdad pasa a ser el mismo sitio que ya usa cada toolkit: `chat.css`, `tema.py`, `Tema*.xaml` |
| **VIS-02** | **Centralizar los 73 literales compartidos** | §5.1 del análisis. El caso grave es la **máquina de estados de mensaje**, escrita como `CASE` SQL 3 veces con la misma precedencia: si se añade un estado y se olvida una pista, el historial de un cliente discrepa del de otro. Ojo: `Respuesta a {identidad}: "…"` es **visible en el protocolo** y no se puede cambiar sin tocar los 3 clientes a la vez |

## 8. Qué NO entra en este plan, y por qué

| Descartado | Motivo |
|---|---|
| **i18n** (resource files, `QTranslator`, `.resx`) | Ninguno de los 3 tiene mecanismo (0 resultados) y el enunciado no lo pide. **Sí** entra centralizar literales (`VIS-02`), que es lo que hace visible la deriva |
| **Reescribir la UI desde cero** | Rompe la paridad de las 6 direcciones que sostiene `GUIA_ACEPTACION.md`. Se extrae, se verifica, luego se mejora |
| **`NotificationCenter` / bandeja del sistema** | `PROD-01` del roadmap, necesita decisión de producto y alcance por SO |
| **Salas temáticas** | `PROD-02`, requiere modelo + enrutado + 3 UIs + cambio de protocolo |
| **Migrar de toolkit** | Requisito de grado: los clientes **deben** poder estar en lenguajes distintos de Java (`RNF-04`) |
| **`Singleton` (patrón clásico)** | El ciclo de vida ya lo lleva Spring en el servidor y el composition root en los clientes. Añadir un `INSTANCE` global sería un retroceso |

## 9. Definición de terminado (por fase)

Una fase está cerrada cuando:

1. Las suites de los 3 clientes pasan **sin modificar los tests** (salvo los que la fase
   declara nuevos). Un test que hay que tocar para que pase el código significa que cambió el
   comportamiento.
2. La matriz de interoperabilidad de `GUIA_ACEPTACION.md` sigue en verde para las 6
   direcciones.
3. Los literales nuevos no se escriben **inline**: van al sitio de tokens del toolkit
   (`VIS-01`, `VIS-02`).
4. `ANALISIS_TECNICO.md` §2 está en cero, o el item restante tiene justificación escrita.
5. No hay ninguna clase de JavaFX eliminada sin haber arrancado la aplicación y mirado el
   control (regla B2).
6. La tabla de tamaños de §4.2 se cumple.

## 10. Referencias

- Evidencia de cada afirmación: [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md).
- Tareas con estado y prioridad: [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md).
- IDs normativos: [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md).
- Textos exactos de la UI: [`DIAGRAMAS/textos-ui.md`](DIAGRAMAS/textos-ui.md).
- Pruebas manuales: [`GUIA_ACEPTACION.md`](GUIA_ACEPTACION.md).
