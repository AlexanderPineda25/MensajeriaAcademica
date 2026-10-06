# Estado y roadmap

Actualizado: **2026-10-06**. Este es el tablero de estado; la arquitectura está en
[`PLAN.md`](PLAN.md), el plan de refactor de clientes en
[`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md), la evidencia de la auditoría en
[`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md), los requisitos en
[`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) y las instrucciones para instalar cada proceso en
[`DESPLIEGUE.md`](DESPLIEGUE.md).

Regla de tableros: **este archivo manda hacia adelante** (qué falta);
[`PLAN_MEJORAS.md`](PLAN_MEJORAS.md) es el **diario de lo ya ejecutado** (qué se hizo y cómo se
verificó).

## Cómo leer este archivo

| Estado | Significa |
|---|---|
| ✅ Hecho | Implementado + suite automatizada en verde |
| 🟡 Por verificar | Código hecho, falta smoke manual / registro en matriz |
| ⬜ Por hacer | No iniciado, con alcance y criterio de aceptación definidos abajo |
| 🟠 Propuesto | Requiere decisión de producto/protocolo antes de implementarse |
| 🔴 Riesgo | Conocido, documentado, pendiente de decisión antes de producción |

> **Resumen en 30 segundos (corte 2026-10-06).** `SEC-05`/`SEC-06`, los lotes `MUERTE-1..5`,
> los defectos `UIX-00a..00f`, varios lotes P1/P2 de UI y el aviso inline .NET `UIX-15` están
> implementados. La vista administrativa del servidor tiene ficha de usuario y JLine conserva sus
> comandos con mejor jerarquía visual. `DIAG-01..08` y `DIAG-10` tienen fuente/render y herramienta
> disponibles según el inventario de `DIAGRAMAS/README.md`; `DIAG-09` sigue pendiente. Siguen
> pendientes smokes manuales, parte de `MUERTE-6`, `PAT-01` (Python/.NET), `PAT-VM`/`PAT-CMD`,
> `PAT-04`/`PAT-05`, temas `VIS-*`, y otras tareas indicadas más abajo. En la continuación de
> `PAT-ARQ-JAVA`, Java pasó 127 pruebas limpias y la compilación conjunta del servidor y el cliente;
> el smoke visual conectado sigue pendiente.

## Índice de familias

| Familia | Qué agrupa | Cantidad | Bloquea producción |
|---|---|---|---|
| `SMOKE-*` | Aceptación manual pendiente | 9 | **Sí** |
| `SEC-*` | Seguridad | 6 | ~~`SEC-05`~~ **cerrado** |
| `MUERTE-*` | Limpieza de código muerto | 6 lotes | No |
| `UIX-*` | Bugs y mejoras de interfaz | 29 | Los 6 P0, sí |
| `PAT-*` | Patrones de diseño en clientes | 8 | No |
| `VIS-*` | Contrato visual compartido | 2 | No |
| `SEG-*` / `DIST-*` / `PERF-*` / `PROD-*` | Fases previas | 15 | — |
| `DIAG-*` | Diagramas | 10 | No |
| `DOC-*` | Documentación | 4 | No |

---

## 1. Estado actual por componente

| Componente | Estado registrado | Evidencia disponible al 2026-10-06 |
|---|---|---|
| `servidor-app` | ⚠️ Dos aserciones de rendimiento fallan en los reportes presentes | Surefire: 94 casos, 2 fallos de umbral en `CargaMixtaTest`; no se detectaron errores de función en esa clase. No es una ejecución nueva de esta revisión. |
| JavaFX | ✅ Reportes presentes sin fallos | Surefire: 79 casos. |
| Adaptadores, utilerías y protocolo Java | ✅ Reportes presentes sin fallos | Surefire: 48 adaptadores + 11 utilerías + 7 codec/protocolo. |
| Python | ✅ Último resultado anotado | 100 tests aprobados en el último registro del lote E; no se volvió a ejecutar en esta revisión. |
| .NET tests | ✅ Último resultado registrado | 71/71 en la tarea UIX-15; no se volvió a ejecutar en esta revisión. |
| WPF build | ✅ Último resultado registrado | `dotnet build -c Release`: 0 warnings y 0 errores durante UIX-15. |
| WPF / cliente Java publicados, contenedor y carga | ✅ Verificación histórica | Las pruebas de distribución, arranque, contenedor y carga corresponden a los hitos fechados más abajo; revisar su fecha/entorno antes de repetir en otro PC. |

> **Interpretación de los reportes disponibles:** `CargaMixtaTest` tiene dos fallos de umbral
> temporal; sus reportes registran tramas procesadas/encoladas y ACK p95 sobre el límite. Los
> resultados históricos de rendimiento de `PERF-01/02` fueron tomados en otro entorno y no
> sustituyen esta medición. No afirmar que los umbrales están verdes; volver a medir con entorno
> controlado antes de una decisión de rendimiento.

### 1.1 Verificación manual registrada el 2026-10-05 (con la app real)

No es `SMOKE`, pero es la evidencia de que `UIX-00a` y `UIX-00f` funcionan y no solo compilan:

1. MySQL levantado, `servidor-app` arrancado (puerto 5000), cliente .NET **publicado** lanzado.
2. Login con una cuenta de `usuarios_iniciales.csv` → ventana de chat con el directorio cargado.
3. `Stop-Process` del servidor → el **banner ámbar** aparece con el texto de `EstadoConexion` y el
   `txtEstado` de la cabecera coincide. Antes de `PAT-03` no aparecía nada.
4. Al volver a levantar el servidor, `Actualizar` recupera el directorio sin que la app se caiga.
5. Login con el servidor **caído**: "Conectando…" aparece en la etiqueta **gris**, no en la caja
   roja, y el error real sale en rojo aparte.

> Ojo al reproducir: los tests E2E **dejan `activo=0`** en las cuentas de
> `src/test/resources/usuarios_iniciales.csv` de la BD de desarrollo. Si el login dice "usuario no
> registrado", ese es el motivo; se reactiva con un `UPDATE usuarios SET activo=1` y reiniciando
> el servidor (la plantilla de usuarios vive en memoria). Las cuentas de producción
> (`160004726` / `12345`) no se ven afectadas.

> Nota de entorno registrada: JDK 25.0.1 y Maven 3.9.11 están disponibles en el entorno de
> herramientas del repo. `mmdc` y Node se habilitaron el 2026-10-06; los renders vigentes y las
> carpetas legadas se detallan en `DIAGRAMAS/README.md`. `plantuml` no tiene fuentes `.puml` que
> renderizar en este proyecto.

Versiones configuradas: Java de compilación 21 (probado con JDK 25.0.1), JavaFX 21.0.6 en el
servidor y JavaFX 22.0.2 + AtlantaFX 2.1.0 en el cliente Java, Flyway 11.14.1.
El launcher del cliente habilita `--enable-native-access=javafx.graphics`. Los tests E2E usan semilla propia
(`src/test/resources/usuarios_iniciales.csv`): no dependen del CSV de producción.

### Cerrado recientemente (no reabrir sin motivo)

- **Fase 17 ✅:** tres pistas con toolkits propios y contrato TCP compartido.
- **Fase 18 ✅:** textos en `uploads/<prop>/textos/<hash>/`, genéricos por propietario, auto-refresh
  de informes, etiquetas `[INFO]/[WARN]/[ERROR]`, auditoría `LIMITE_ALCANZADO`.
- **PLAN_MEJORAS 1–9 ✅:** cita en tarjeta, solo-adjunto, descarga con filtro, crash shiboken,
  descarga .NET, login solo-lectura, confirmación de cierre, broadcast con pool acotado,
  maestro-detalle en Informes/Usuarios.
- **Ronda 2026-10-05 ✅:** duplicados Python al re-entrar, descarga Python, cita Java, NRE .NET,
  manifest JVM, CSV sombra de tests.
- **UI-01 a UI-10 + LOGIN-01/02 ✅:** revamp Telegram en los 3, modo oscuro/claro, botón `＋`
  único, doble-click responde, limpieza del login, burbuja roja unificada.
- **Tooling de UI ✅ instalado:** ver §6. No es backlog, es base ya disponible.
- **Bug de concurrencia en difusión ✅:** varias tareas de `poolFiltros` hacían `Files.move` sobre
  la misma ruta y en Windows se perdían copias. Corregido con candado por ruta en
  `AlmacenarInformacion`, cubierto por `difusionDelMismoTextoEnParaleloNoPierdeCopias`.
- **Vista administrativa del servidor ✅ (implementación 2026-10-06):** mejoras a Estado,
  Conectados, ficha de Usuario con páginas de 50, Mensajes con IP/contadores, detalle condicional
  de Informes, expansión de Logs, secciones de Pool, etiqueta de Difundir y confirmación de cierre
  que deja vivos TCP/JLine. JLine reorganiza jerarquía visual. La revisión documental no ejecutó el
  smoke visual completo: `SMOKE-09` lo registra como pendiente.

---

<a id="seguridad-bloqueante"></a>

## 2. Hallazgo histórico de seguridad — cerrado (`SEC-05`/`SEC-06`)

### Hallazgo original: `LISTAR_USUARIOS` respondía sin autenticación · **P0 cerrado**

> Este apartado documenta el estado encontrado en la auditoría, antes del arreglo. **No describe
> el comportamiento actual.** La implementación, pruebas y fecha de cierre se anotan al final de
> esta sección y en §9.

**El hallazgo.** `LISTAR_USUARIOS` es uno de los 3 comandos que **no aplican la guarda de
autenticación**. Los otros 12 sí lo hacen (`Comandos.exigirAutenticacion`). El despachador no pone
puerta global, así que la responsabilidad es de cada comando — y basta con que uno se olvide.

**Consecuencia.** Cualquiera que abra un socket TCP contra `5000` y envíe una trama
`LISTAR_USUARIOS` — **sin código ni contraseña** — recibe el directorio completo de la comunidad
académica: código, nombres, apellidos y programa.

**Cadena verificada extremo a extremo:**

| Eslabón | Archivo:línea |
|---|---|
| El despachador no filtra por sesión | `server/comp-protocolo-clienteservidor/.../impl/Despachador.java:55-62` |
| La guarda existe y funciona | `.../commands/Comandos.java:14-20` |
| 12 de 15 comandos la llaman | `.../commands/TextoCommand.java:23` y los otros 11 |
| **`ListarUsuariosCommand` no la llama** y ni siquiera guarda `respuestas` | `.../commands/ListarUsuariosCommand.java:14-20` |
| El caso de uso tampoco valida | `server/servidor-app/.../casosdeuso/ListarCaso.java:45-62` |

**Alcance.** `LISTAR_CONECTADOS` y `LOGOUT` comparten el hueco (menor gravedad: `LOGOUT` solo
cierra la sesión anónima del atacante, `LISTAR_CONECTADOS` solo revela quién está en línea). Lo que
se cierra es **el patrón**, no los 3 síntomas.

**Por qué ningún test lo detecta.** Las 3 clases reciben `respuestas` en el constructor y
`FabricaDelDespachador` lo pasa, así que el cableado *parece* completo. Un test que solo compruebe
"responde bien a un usuario autenticado" pasa igual.

- [x] **`SEC-05` — Cerrar el hueco.** ✅ Hecho 2026-10-05.
  1. `Comandos.exigirAutenticacion(...)` añadida a `ListarUsuariosCommand`, `ListarCommand` y
     `LogoutCommand` (era lo único que faltaba: los otros 12 ya la tenían).
  2. **Fallo cerrado en el SPI:** `CommandHandler.requiereAutenticacion()` vale **`true` por
     defecto** y solo `LoginCommand` lo sobreescribe a `false`. Un comando nuevo nace protegido:
     si olvida la guarda, el test lo delata porque su tipo sigue marcando `true`.
  3. **Test parametrizado** `sesionAnonimaNoEntraEnNingunComandoProtegido`: recorre los **14**
     tipos que el despachador tiene registrados y exige `ERROR "autenticate primero con LOGIN"`
     **y ninguna llamada de negocio**. Los datos salen de `despachador.comandos()`, no de una
     lista escrita a mano, así que un comando nuevo entra solo en el test.
  4. `soloLosTiposDeclaradosPublicosQuedanSinGuarda` fija el conjunto abierto en `{LOGIN}`: abrir
     otro obliga a tocar `TIPOS_PUBLICOS` y a fallar mientras no se haga.
  5. `conSesionAutenticadaLogoutYListarSiguenDelegando` evita que la guarda cierre el camino
     autenticado por error.
  6. E2E: `MensajeriaE2ETest.tramaNoAutenticadaSeRechaza` (socket crudo, 5 tramas de negocio sin
     LOGIN → `ERROR`, `contenido` y `usuariosConectados` nulos, y LOGIN posterior entra bien).
- [x] **`SEC-06` — Cerrar el surrogate de `SEC-05`.** ✅ Hecho 2026-10-05. Elegida la opción (b),
  pero **automatizada en vez de manual**: el checklist de PR lo remplace `requiereAutenticacion()`
  con default `true` más los 2 tests que lo vigilan. El parámetro `respuestas` de los 3 comandos
  dejó de ser "no usado" (ahora es el que responde el `ERROR`), así que `MUERT-13` desaparece solo.

> **Verificado con el bug reintroducido.** Se revirtió la guarda de `ListarUsuariosCommand` a
> propósito y el test falló nombrando el comando y la fuga: `expected [error:...] but was
> [error:..., usuarios]`. El test tiene dientes; no es decorativo.

---

## 3. Pendiente de verificación (código hecho, falta humano)

Esto **NO** es código por escribir. Es el smoke que las suites automáticas no cubren.

| ID | Tarea | Estado | Dónde registrar |
|---|---|---|---|
| SMOKE-01 | Matriz 6 direcciones Java↔Python↔.NET: texto, imagen sola, texto+imagen, respuesta a texto, respuesta a imagen; cita llega como texto ordinario; sin doble acuse ante `MENSAJE_LEIDO/ENTREGADO` | 🟡 Por verificar | `GUIA_ACEPTACION.md` § Matriz |
| SMOKE-02 | Dos procesos Java con cuentas distintas: orientación propia/ajena correcta por cuenta | 🟡 Por verificar | Misma matriz, § Historial |
| SMOKE-03 | Dos sesiones del mismo usuario: `LOGOUT` cierra una, presencia offline solo al cerrar la última; probar «Salir en todos» | 🟡 Por verificar | Misma matriz, § Historial |
| SMOKE-04 | Imagen nueva: `uploads/Nombre Apellido [CODIGO]/<hash>/` con original + 5 derivadas; aparece en otro cliente incluso tras reinicio; carga diferida + reintento | 🟡 Por verificar | Misma matriz, § Historial |
| SMOKE-05 | Smoke cambios 2026-10-04 (7 casos) | 🟡 Por verificar | `GUIA_ACEPTACION.md` § Smoke 2026-10-04 |
| **SMOKE-06** | **Matriz manual de transición de conexión × 4 estados × 3 clientes** (`Conectando`/`Conectado`/`SinRed`/`SesionCerrada`), con textos y tiempos medidos. `PAT-03` está implementado; falta la matriz manual completa. | 🟡 Por verificar | `GUIA_ACEPTACION.md` § Estados de conexión |
| **SMOKE-07** | **Capturas lado a lado de la misma conversación** en los 3 clientes, tema oscuro y claro; comparar después de implementar `VIS-01` | ⬜ Por hacer; captura de línea base si se ejecuta antes | `DIAGRAMAS/mockups/` |
| **SMOKE-08** | **Comprobación de contrastes WCAG AA** de los tokens de tema claro en los 3 clientes | ⬜ Por hacer | `PLAN_PATRONES_UI.md` §7.4 |
| **SMOKE-09** | **Vista administrativa del servidor**: estados vacíos/activos, doble clic y ficha paginada, IP y contadores, detalle de informe, espacio de Logs/Pool, cierre X sin parar TCP/JLine y recorrido JLine | 🟡 Por verificar | `GUIA_ACEPTACION.md` § Consola administrativa |

Criterio para dar por aceptada la paridad: SMOKE-01 a SMOKE-09 en verde con SO/versión/commit
anotados. Hasta entonces nada de lo de §5 se considera «aceptado».

---

## 4. Limpieza de código muerto

38 items, agrupados en 6 lotes. Detalle con `archivo:línea` y conteo de referencias en
[`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) §2.

**Reglas de borrado** (están en [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) §2.1 y son la parte
importante de esta sección):

- **B1** — 0 referencias fuera de la definición **y** sin anotación de framework/reflexión/SPI.
- **B2** — **nunca borrar por búsqueda de texto.** `.split-pane`, `.list-cell`, `.combo-box` son
  clases integradas de JavaFX; `login.fxml:15 fx:controller=` es lo que hace viva
  `LoginController`; `ServiceLoader` hace vivos los 5 `Proveedor*`; el escaneo de JUnit hace vivos
  los 49 tests. Todos tienen 0 referencias y viven.
- **B3** — una clase `@Configuration`/`@Component` nunca es muerta.
- **B4** — un enum solo se borra si se comprueban antes las filas históricas de producción.
- **B5** — *dead feature* ≠ código muerto: `difundir()` sin botón es decisión de producto.
- **B6** — lo que se borre va con test.

| ID | Lote | Contenido | Estado | Aceptación |
|---|---|---|---|---|
| MUERTE-1 | Java: 13 símbolos + 3 parámetros `respuestas` + 12 imports | `MUERT-01..13`. Incluye `SEC-05`/`SEC-06` | ✅ Hecho | `mvn test` verde; el E2E de login sigue pasando |
| MUERTE-2 | .NET: 9 pinceles, 3 constantes, 1 propiedad, 1 variable, 12 `using` | `MUERT-25..30` | ✅ Hecho | `dotnet test` + `build -c Release`; comparación visual de ambos temas contra el mockup |
| MUERTE-3 | Python: 6 símbolos + imports + campos de test | `MUERT-16..24` | ✅ Hecho (sin `MUERT-23`) | `pytest tests` verde. **`_tareas_imagen` es un leak**: su borrado es corrección |
| MUERTE-4 | CSS y XAML muertos | `MUERT-31..38` | ✅ Hecho (sin `MUERT-37`) | **Medio riesgo**: arrancar la app y confirmar que cada control conserva su estilo antes de borrar (regla B2) |
| MUERTE-5 | Deduplicación | 3 parsers de `client.properties` → 1; `fallbackSchema` muerto; 2 migradores de esquema duplicados; `_leer_modo`/`_guardar_modo` unificados; tabla MIME unificada | ✅ Hecho (sin tabla MIME) | Tests existentes + test nuevo de equivalencia de `client.properties` en los 3 |
| MUERTE-6 | Decisiones de producto, no limpieza | `difundir()` sin botón en Python y .NET; clave `idioma=` sin uso; dependencia `pillow` sin imports; `ui/registro_window.py` | 🟠 Propuesto | Ver §9 |

### 4.1 Lo que estos lotes arreglan de paso

- **`MUERT-33`:** el título del sidebar salía del color equivocado en tema claro (`#1F3A5F` en vez
  del `#25343b` pretendido, por una segunda regla `.sidebar-title` que ganaba). Corregido.
- **`MUERT-28`:** un `_ = cuentaExistia;` que anulaba el aviso del compilador y escondía los
  `MUERT` reales. Desaparecido con la variable.
- **`MUERT-19`:** leak de memoria cerrado (`_tareas_imagen` crecía sin límite).
- **`MUERT-37`:** handler quehalbía de un botón inexistente (`BtnAdjuntarArchivo_Click` sin `Click=`).
- **Login .NET vs fachada:** `CargarServidor` leía `client.properties` del **directorio de
  trabajo** y la fachada de la **carpeta del ejecutable**, y solo leía `host`/`puerto`. Ahora los
  tres consumidores pasan por un `ClienteConfig` con una sola ruta. Comprobado en la app
  distribuida: editando `client.properties` el login muestra el host y el puerto nuevos.
- **Tests .NET contra el esquema real:** el DDL incrustado en `LocalStore.cs` no era "inalcanzable"
  — lo ejecutaban **todos** los tests de store, porque el proyecto de tests nunca copiaba el
  `.sql`. La app usaba un esquema y los tests otro. Ahora `Mensajeria.Tests.csproj` enlaza el
  `schema-local.sql` de `client-dotnet` y `LocalStore` falla con un mensaje claro si falta, en vez
  de crear un esquema divergente en silencio.
- El Javadoc obsoleto de `LoginConteoDTO.java` que afirmaba un `GROUP BY` sobre `LOGIN/CONECTADO`
  cuando la query solo agrupa por `LOGIN`.

### 4.2 Lo que **no** se borró, y por qué (reglas B2 y B5)

| Item | Por qué se conserva |
|---|---|
| `MUERT-23` (4 métodos de los dobles de prueba: 2× `enviar_async`, `enviar_imagen`, `difundir`) | No son código muerto: son **implementaciones de la interfaz que la vista sí llama**. `fachada_cliente.py:66,506` invocan `conexion.enviar_async()` y `chat_window.py:1008` invoca `fachada.enviar_imagen()`. Que ningún test de hoyercises ese camino es irrelevante: borrar el método haría que el doble mintiera sobre su contrato y dejaría un `AttributeError` latente. |
| `MUERT-37` (`bannerOffline` del XAML) | Es el **elemento que `UIX-00a` tiene que cablear** (§5.1): su aceptación es "el banner ámbar aparece en los 3 en ≤ 2 s". Borrarlo elimina el objetivo del P0. Es una función rota, no código muerto. |
| Tabla MIME de los 3 clientes (parte de `MUERTE-5`) | No es duplicación dentro de un módulo: Java tiene un `switch` propio y Python/.NET usan el `mimetypes`/`ContentType` de su plataforma. Unificarla **cambia el tipo de trama que se envía** (Java no clasifica `.bmp` como imagen y Python/.NET sí), o sea que es una decisión de producto de paridad que pertenece a `SEG-02`/`VIS-02`, no a un lote de limpieza. Se dejó intacta y **documentada** la deriva. |
| `MUERT-14` (`NULA`), `MUERT-15` (`FiltroMain`) | Son `P` (viven por contrato SPI / CLI documentada), no `C`. No entran en limpieza. |

---

## 5. Bugs y mejoras de interfaz

Plan por fases en [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md). Evidencia completa en
[`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) §3.

### 5.1 Fase A — 6 bugs P0 (funciones rotas, no estilo) — ✅ aplicada 2026-10-05

Ninguno requirió refactor. Cada uno es un cambio pequeño y verificable por separado.

| ID | Cliente | Qué estaba roto | Qué se hizo | Aceptación | Estado |
|---|---|---|---|---|---|
| **UIX-00a** | .NET | **La pérdida de conexión es invisible.** `OnDesconectado` existía en el transporte y en la fachada, y `bannerOffline` en el XAML, pero **ninguna vista se suscribe** y nadie cambia el `Visibility` | `PAT-03`: `ChatWindow` se suscribe a `IFachadaCliente.OnEstadoConexion` y hace `bannerOffline.Visibility = Visible` | Matar el servidor con el cliente abierto → el banner ámbar aparece | ✅ Hecho y **verificado en la app real** |
| **UIX-00b** | Python | **"Reintentar imagen" nunca reintenta**: el `_ejecutar("imagen", cargar)` estaba **después** del `raise`, dentro de `def cargar` | `_ejecutar(...)` se lanza fuera de `cargar` | Romper red → reintentar → la imagen aparece sin recargar | ✅ Hecho |
| **UIX-00c** | Java | `alError()` y `alDesconexion()` **estaban vacíos**: eran un comentario | `PAT-03`: los dos avanzan el estado; `alError` registra el motivo y `alDesconexion` pasa a `SIN_RED` | El servidor cae durante un envío → el usuario ve un mensaje | ✅ Hecho |
| **UIX-00d** | Java | El fallo de persistencia **se traga** (`catch (Exception e) { }`) | Se registra el tipo de trama y el motivo en el log | BD local en solo lectura → el mensaje aparece y el log lo dice | ✅ Hecho |
| **UIX-00e** | Python | **`login()` corre en el hilo de la GUI**: bloquea hasta 15 s | Hilo `login-fondo` + vuelta al hilo de la GUI en `_terminar_login`, con guarda de doble pulsación | Servidor inalcanzable → la ventana responde | ✅ Hecho |
| **UIX-00f** | .NET | **"Conectando…" se pinta dentro de la caja roja de error** | Nueva etiqueta `txtCargando` en gris neutro; la caja roja solo se abre para errores | Con latencia artificial: "Conectando…" en gris neutro | ✅ Hecho y **verificado en la app real** |

> **Por qué `UIX-00a` y `UIX-00c` se resolvieron con `PAT-03` y no a mano.** El plan decía que
> arreglarlos "a mano" en .NET sería una cuarta reimplementación de la misma idea. Se hizo el
> cimiento (`PAT-03`) y los dos fellos se cerraron de paso.
>
> **Verificación real, no solo unit tests.** Con el servidor MySQL levantado, la app .NET
> **publicada** y Netwright: login correcto → `Stop-Process` del servidor → el banner ámbar
> aparece con el texto `Sin conexión — mensajes en cola local, se reintentan solos`, y el
> `txtEstado` de la cabecera coincide. Antes no aparecía **nada**. El criterio de "≤ 2 s" **no se
> puede afirmar con esta medición**: el tiempo medido (12 s) es casi todo ida y vuelta de las
> herramientas, no la reacción de la app. Lo que sí queda demostrado es que **no hay sondeo**:
> el estado lo empuja el evento de muerte del socket. El tiempo exacto es trabajo de `SMOKE-06`.
>
> **Un defecto de layout que solo apareció al hacerlo visible.** El banner estaba en un
> `DockPanel` sin `DockPanel.Dock`, así que competía por el espacio sobrante con el `ListView` de
> contactos y lo aplastaba. Fijado a `Dock="Top"`; comprobado en pantalla.

> `PAT-03` es la base de la matriz de transición de conexión de `SMOKE-06`, que sigue pendiente
> porque exige cronometrar los 4 estados × 3 clientes a mano.

### 5.2 Fase G — Mejoras por pantalla (P1/P2)

Lote A: UIX-02 (no leídos), UIX-03 (fecha relativa), UIX-04 (separadores), UIX-05 (agrupación). Contrato: mismo autor + mismo día + <5 min agrupa; Hoy/Ayer; insignia (N). Suites entonces: JavaFX 75, Python 83, .NET 55.

Lote B: UIX-06 (buscar Ctrl+F) + UIX-07 (debounce 300 ms). Contrato: LIKE insensible con literales escapados, 200 ids de más nuevo a más viejo.

Lote C: UIX-08 (visor zoom/pan) + UIX-09 (cancelar carga). Atajos: rueda zoom, arrastrar pan, +,-,0,F, Esc. Suites entonces: JavaFX 79, Python 91, .NET 60.

Lote D: UIX-10 (burbuja relativa 65%), UIX-11 (splitter .NET persistido), UIX-12 (Shift+Enter en Py/.NET), UIX-13 (vacíos), UIX-14 (cargando), UIX-16 (anclaje + botón de nuevos). Suites: JavaFX 79, Python 92, .NET 61. Verificado en app real (.NET publicada + Netwright): vacío inicial visible y layout de 3 columnas intacto.

Lote G (agente 2, sin solape): UIX-17/18/19/24 + DIAG-07/08. UIX-28 bloqueado (ver §5.3).

Lote E: UIX-01 (virtualizar lista Python: `QListView` + `MensajeListModel`/`MensajeDelegate` en `ui/lista_virtual.py`, `_MessageRow` eliminado; contrato visual idéntico — burbuja 65%/tope 580, rachas, separadores, resaltado, estados, descarga/reintentar/ampliar por coordenadas; doble-click = responder, click-derecho = copiar cuerpo). Suites: Python 100 (92 + 8 nuevos `test_lista_virtual.py`). Verificado en app real (MCP): burbujas, racha sin remitente, separador "Ayer", resaltado y tema claro.

Lote F: UIX-15 (.NET): nueve avisos/errores recuperables migrados a un banner inline accesible, con nivel visual y cierre manual; el aviso remoto de sesión cerrada aparece en el banner del login. Se mantienen las tres confirmaciones modales de salida. Añadidos tests xUnit para los estados visible/oculto, reemplazo y limpieza del aviso. Verificación: `dotnet test` 71/71; `dotnet build -c Release` sin warnings ni errores.

| ID | Asunto | Prioridad | Clientes | Estado |
|---|---|---|---|---|
| UIX-01 | **Virtualizar la lista de mensajes** | **P0** | Python | Hecho (Lote E) |
| UIX-15 | Avisos y errores recuperables inline; conservar confirmaciones | **P0** | .NET | Hecho (Lote F) |
| UIX-13 | Estados vacíos (conversación vacía, búsqueda sin resultados, sin contactos) | P1 | los 3 | Hecho (Lote D) |
| UIX-05 | Agrupar mensajes consecutivos (ocultar remitente en una racha) | P1 | los 3 | Hecho (Lote A) |
| UIX-04 | Separadores de día | P1 | los 3 | Hecho (Lote A) |
| UIX-03 | Formato de fecha unificado y relativo | P1 | los 3 | Hecho (Lote A) |
| UIX-02 | Distintivo de no leídos en los 3 + filtro "No leídos" | P1 | los 3 | Hecho (Lote A) |
| UIX-06 | **Buscar dentro del historial** con `Ctrl+F` | P1 | los 3 | Hecho (Lote B) |
| UIX-07 | Debounce en la búsqueda | P1 | los 3 | Hecho (Lote B) |
| UIX-08 | Zoom y pan en imagen (rueda / `+` / `-` / `F` / `0` / `Esc`) | P1 | los 3 | Hecho (Lote C) |
| UIX-09 | Cancelar la carga de imagen al salir de vista | P1 | los 3 | Hecho (Lote C) |
| UIX-17 | Corregir 4 contrastes que fallan WCAG AA en tema claro | P1 | Java, Python | Hecho (Lote G) |
| UIX-18 | Diferenciar visualmente "Enviado" y "Entregado" | P1 | .NET | Hecho (Lote G) |
| UIX-19 | Nombre accesible en botones de solo icono | P1 | Python, .NET | Hecho (Lote G) en .NET; Python pendiente |
| UIX-24 | Foco inicial en el campo de código del login | P1 | .NET | Hecho (Lote G) |
| UIX-14 | Indicador de carga del historial | P2 | los 3 | Hecho (Lote D) |
| UIX-16 | Anclaje de scroll al cargar anteriores | P2 | los 3 | Hecho (Lote D) |
| UIX-10 | Ancho de burbuja relativo al ancho de ventana | P2 | los 3 | Hecho (Lote D) |
| UIX-11 | Sidebar redimensionable en .NET | P2 | .NET | Hecho (Lote D) |
| UIX-12 | `Shift+Enter` = salto de línea | P2 | Python, .NET | Hecho (Lote D) |
| UIX-21 | Cuerpo del mensaje seleccionable y copiable | P2 | Java, .NET | Por hacer |
| UIX-22 | Mnemónicos / accelerator keys | P2 | los 3 | Por hacer |
| UIX-23 | Escala de tipografía / alta resolución | P2 | los 3 | Por hacer |
| UIX-15b | Login redimensionable | P2 | Java, .NET | Por hacer |
| — | Tooltip de por qué servidor/puerto no son editables | P2 | los 3 | Por hacer |
| — | Cuarto estado en el login: "usuario no registrado" | P2 | los 3 | Por hacer |

### 5.3 Errores de tematización (P1)

| ID | Bug | Estado |
|---|---|---|
| UIX-25 | `.sidebar-title` duplicado y contradictorio (`MUERT-33`) | ✅ Hecho en `MUERTE-4`; estilo de tema claro corregido |
| UIX-26 | Python adjunta QSS de **tema claro** en modo oscuro para los iconos de adjunto | ⬜ Por hacer |
| UIX-27 | Cambiar de tema **reconstruye toda la lista** de mensajes en .NET | ⬜ Por hacer |
| UIX-28 | .NET usa `StaticResource` pero **reasigna por reflexión** (el propio comentario lo admite) | 🔴 Bloqueado (Lote G): quitarlo exige añadir `AppSurfaceBrush`/`AppTextBrush` a `Themes/*.xaml` (fuera de alcance UIX-18) y tipar el `ThemesDictionary` de WPF-UI leyendo `App.xaml` (fuera de la lista permitida). Ver informe Lote G |
| UIX-29 | Python arranca en tema claro mientras `tema.py` dice oscuro por defecto | ✅ Hecho (Lote G): `main.py` arranca en `Theme.DARK` con acento `#7B61B8` (canónico oscuro) |

---

## 6. Patrones de diseño en los clientes

Detalle, riesgos y criterios medibles en [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) §3–6.

| ID | Patrón | Qué resuelve | Prioridad | Estado |
|---|---|---|---|---|
| **PAT-01** | Composition root real | Java: `FabricaCliente` compone runtime/adaptadores; los puertos de login y chat y la navegación se inyectan por constructor vía `FabricaControladores`; sin localizador estático. Python conserva 6 colaboradores con defaults `or`; .NET conserva la composición pendiente en UI | P0 (base de PAT-VM) | 🟡 Parcial: Java implementado y compilado 2026-10-06; Python/.NET pendientes |
| **PAT-ARQ-JAVA** | SOLID + límites limpios en cliente Java | Puertos por consumidor para login, chat y transporte; mapper JSON inyectado; evento de aplicación para UI; ejecutor inyectado; gestores de adjuntos y reconexión; delegación del controlador al ViewModel; reglas ArchUnit | P1 | 🟡 Parcial: 127 pruebas pasan, incluida reconexión automática; DTO confinados a adaptadores; faltan extracción de coordinación JavaFX y smoke visual |
| **PAT-03** | `EstadoConexion` observable (4 estados, 3 clientes) | Cerró la falta de notificación que originó `UIX-00a` y `UIX-00c`; las vistas se suscriben al estado publicado por la fachada | P0 | ✅ **Hecho 2026-10-05** (§5.1) |
| **PAT-02** | Guarda de autenticación obligatoria en comandos nuevos | El surrogate de `SEC-05`: la firma dice "puede responder errores" y el cuerpo dice lo contrario | P0 (con `SEC-05`) | ✅ **Hecho** con `SEC-05` (§2): default `true` + 2 tests |
| **PAT-VM** | MVVM: `ChatViewModel` en los 3 | Java ya tiene ViewModel que centraliza estado conversacional y sirve de frontera de operaciones, pero el controlador aún conserva coordinación JavaFX extensa (968 líneas). Python tiene 1 322; .NET va ⅓ (`MensajeVM` sí notifica, `UsuarioVM` no) | P1 | 🟡 Parcial en Java; extracción completa de coordinación JavaFX y MVVM Python/.NET pendientes |
| **PAT-CMD** | `Command` / `ICommand` para las acciones de UI | "Puedo enviar" está implementado **3 veces** y en .NET hay **0 `ICommand`**, así que ningún botón se habilita de forma declarativa | P1 | ⬜ Por hacer |
| **PAT-04** | Adapter del almacén local | Python **no tiene interfaz**: la vista la salta y accede a `fachada.historial`. El upsert `DELETE + INSERT` portable ya está documentado y solo se usa en Java | P2 | ⬜ Por hacer |
| **PAT-05** | Adapter de transporte TCP/TLS | 3 implementaciones con 3 estrategias, y **validación TLS inconsistente**: Python valida fail-fast, Java no valida, .NET sustituye el puerto en silencio | P2 (cierra `SEG-01`) | ⬜ Por hacer |
| **PAT-06** | Test de equivalencia del `schema-local.sql` en .NET | `test_schema_distribuible.py:34` protege la copia Python; **la .NET no tiene equivalente** y puede divergir en silencio | P2 | ✅ Cerrado **por otra vía** (en `MUERTE-5`): `Mensajeria.Tests.csproj` enlaza el `schema-local.sql` real y los 35 tests de store lo ejecutan |

### 6.1 `PAT-03` en detalle — qué se añadió

Un enum por cliente con **los mismos 4 estados y los mismos textos**, y un punto único de
cambio. La vista ya no deduce nada: se suscribe.

| | Java | Python | .NET |
|---|---|---|---|
| Definición | `client-interfaces/.../transversal/EstadoConexion.java` | `transversal/estado_conexion.py` | `Transversal/EstadoConexion.cs` |
| Se suscribe con | `fachada.alCambiarEstado(obs)` | `fachada.on_estado_ui` | `fachada.OnEstadoConexion +=` |
| Lo publica | `FachadaCliente.cambiarEstado` (único sitio) | `FachadaCliente._cambiar_estado` | `FachadaCliente.CambiarEstado` |

Los 4 estados: `Conectando` · `Conectado` · `SinRed` · `SesionCerrada`. Los dos degradados
muestran banner.

**Dos decisiones que no son obvias y conviene no volver a discutir:**

1. **`CLOSE_NOTICE` es `SesionCerrada`, no `SinRed`.** El servidor cerró la sesión a propósito;
   ofrecer "reconectar" ahí es Offering la acción equivocada. Los 3 clientes lo distinguen, y hay
   test en los tres.
2. **Un cierre voluntario no degrada el estado.** El hilo receptor también muere al cerrar a
   propósito, así que `alDesconexion` disparaba en un logout normal. En Java se resolvió en el
   transporte con un **contador de generación** (`ConexionCliente.generacion`): el lector solo
   reporta si sigue siendo el dueño del socket. Sin eso, el banner parpadearía en el login. En
   Python y .NET se resolvió con la guarda `if estado != SesionCerrada`.

> **Efecto colateral arreglado de paso.** El `finally` del hilo lector viejo también fallaba
> **todos los pendientes** de la conexión nueva, porque `pendientes` es compartido. Ahora un
> lector obsoleto no toca nada. No se tocó esa parte a propósito: cambiar la semántica de fallo
> de peticiones pendientes es un cambio de comportamiento que `SMOKE` debe medir.

### 6.2 `PAT-01` en detalle — composición e inyección Java

| Pieza | Responsabilidad comprobable |
|---|---|
| `ClienteMain` | Solo inicia `ClienteApp`; ya no construye ni almacena servicios |
| `FabricaCliente` | Lee configuración, prepara H2 y construye transporte, DAOs, autenticación, fachada y runtime |
| `InterfazFachadaCliente` | Contrato agregado conservado por compatibilidad interna; la UI recibe puertos más pequeños |
| `FabricaControladores` | Mapea tipos FXML a constructores con puertos de login/chat, ejecutor, configuración y navegación inyectados |
| `ClienteApp` | En `init()` compone el runtime; en `stop()` lo cierra. Conserva stage/navegación como estado de instancia |
| `ClienteRuntime` | Cierra conexión, fachada y almacén local de forma idempotente |

### 6.3 `PAT-ARQ-JAVA` en detalle — contratos por consumidor

| Frontera | Responsabilidad comprobable |
|---|---|
| `InterfazInicioSesion` | Login usa solo autenticación y desconexión; la fachada agregada la implementa sin perder sus operaciones existentes |
| `InterfazChatCliente` | La vista recibe los casos de uso de conversación/presencia/sesión que utiliza, sin depender de la fachada agregada |
| `InterfazArchivosConversacion` | La presentación de adjuntos solicita historial/bytes sin depender del DTO de descarga del protocolo |
| `GestorAdjuntosChat` | Mantiene paginación remota y ciclo de carga, reintento, cancelación y descarga de medios |
| `InterfazMapeadorDatosServidor` | Puerto que traduce directorio e historial JSON a records del cliente; `MapeadorDatosServidor` concentra Gson en el adaptador |
| `InterfazEventosCliente` / `EventoCliente` | Presentación recibe presencia, cierre, acuses y mensajes como eventos propios del cliente, no como DTOs del protocolo |
| `Executor` | El contenedor de composición entrega el ejecutor acotado a los controladores; se elimina el `new Thread` ad hoc del login |

La arquitectura sigue usando los módulos y paquetes actuales. No se añadieron capas Maven ni se
alteraron el contrato TCP/JSON, el servidor o los clientes Python/.NET. En la continuación del
2026-10-06, `InterfazConexionRed` y `ReceptorMensajes` pasaron a `client-interfaces`; conexión,
autenticación y fachada intercambian resultados neutrales, y ArchUnit impide que los servicios y
puertos dependan de `common-protocol`. El DTO/codec queda en `componentes.conexion` y el mapeo JSON
en `componentes.protocolo`. El ViewModel asumió estado conversacional y el controlador dejó de
retener una referencia directa a la fachada; aun así, el controlador conserva coordinación JavaFX.
Por eso `PAT-ARQ-JAVA`/`PAT-VM` siguen parciales y no se declara Clean Architecture completa.

La compilación de `servidor-app` pasó en el mismo reactor Maven y no se modificó código de `server/`.
En la continuación del 2026-10-06, una ejecución limpia pasó 127 pruebas del cliente (0 fallos y
0 errores), incluida la carga de `login.fxml` y `chat.fxml` con la fábrica real y la reconexión
automática tras una caída. También pasó `mvn -q -pl server/servidor-app,client/client-app -am
-DskipTests compile`; no se modificó código de `server/`. Sigue pendiente un smoke visual conectado
para validar tema, adjuntos y recorridos completos de sesión/reconexión.

**Criterios medibles de `PAT-VM`** (para que no sea un subjective "quedó más limpio"):

| Métrica | Antes | Objetivo |
|---|---|---|
| `ChatController.java` / `chat_window.py` / `ChatWindow.xaml.cs` | 774 / 1 322 / 790 | ≤ 350 / ≤ 550 / ≤ 400 |
| Métodos privados que los tests tienen que llamar | 3 | **0** |
| Bindings reales en el cliente Java | 1 | ≥ 8 |

---

## 7. Contrato visual compartido

| ID | Tarea | Estado |
|---|---|---|
| **VIS-01** | **Fijar una única paleta por tema.** El tema oscuro coincide en los 3 (11 colores idénticos); **el claro no coincide en ninguno**: burbuja propia `#E4F3EC` / `#E1F5FE` / `#D9FDD3` y acento `#2456A6` / `#147D73` / defecto de WPF-UI. Deriva real, no diferencia de toolkit | ⬜ Por hacer |
| **VIS-02** | **Centralizar los 73 literales compartidos.** Caso grave: la **máquina de estados de mensaje** está escrita como `CASE` SQL **3 veces** con la misma precedencia — si se añade un estado y se olvida una pista, el historial de un cliente discrepa del de otro. Ojo: `Respuesta a {identidad}: "…"` es **visible en el protocolo** y no se puede cambiar sin tocar los 3 a la vez | ⬜ Por hacer |

**Lo que NO entra:** i18n (resource files / `QTranslator` / `.resx`). Ninguno de los 3 tiene
mecanismo y el enunciado no lo pide. Lo que sí entra es centralizar literales (`VIS-02`), que es lo
que hace visible la deriva.

---

## 8. Diagramas

Fuentes Mermaid **renderizadas y revisadas** en `DIAGRAMAS/`; solo los mockups de DIAG-09 y la
reconciliación del legado siguen pendientes. Detalle y plan de consolidación en
[`DIAGRAMAS/README.md`](DIAGRAMAS/README.md).

| ID | Tarea | Estado | Aceptación |
|---|---|---|---|
| **DIAG-01** | **Casos de uso** — `casos/uc-cliente.mmd`, `uc-servidor.mmd`, `uc-sistema.mmd`. Antes **no existía ningún `usecaseDiagram`** en el repo | ✅ source | Render + revisión |
| **DIAG-02** | **Secuencia** — conexión/trama, Observer, Object Pool, tuberías. Antes solo había 3, en la carpeta vieja | ✅ source | Render + revisión |
| **DIAG-03** | **Actividad** — login, envío, archivo fragmentado, descarga, reconexión/cola. Antes no existía ningún árbol de decisión | ✅ source | Render + revisión |
| **DIAG-04** | **Estados** — mensaje, sesión, transferencia. Antes no existía ningún `stateDiagram`. `st-mensaje` pasa a ser la referencia contra la cual se comparan los 3 `CASE` SQL duplicados | ✅ source | Render + revisión |
| **DIAG-05** | **Despliegue** — 4 procesos en LAN, con la prohibición explícita de cliente → base del servidor | ✅ source | Render + revisión |
| **DIAG-06** | **Clases de Python y .NET** — no existía ninguno de los dos | ✅ source | Render + revisión |
| **DIAG-07** | **Consolidación** — migrar diagramas únicos a `DIAGRAMAS/` y revisar los legados | ✅ Fuentes/renderes canónicos migrados y catalogados en Lote G. **Las carpetas legadas siguen presentes y no se borran en esta revisión**; su inventario y criterios de retiro están en `DIAGRAMAS/README.md` §3 y `REVISION_DOCUMENTAL.md`. | Comparar los 10 `.mmd` legados y 5 mockups legado con sus equivalentes antes de archivarlos; registrar lo que no esté cubierto. |
| **DIAG-08** | **`render.ps1` sale siempre con código 0.** No hay forma de que CI detecte un render roto | ✅ Hecho (Lote G): contador `$script:fallos` + `exit 1`; verificado con roto temporal (exit 1) y sin rotos (exit 0). De paso, `Invoke-Nativo` evita que el stderr del shim npm aborte el bucle en PS 5.1 | Contar los `FALLO` y devolver exit code distinto de 0 |
| **DIAG-09** | **Mockups posteriores a las features**: estados de conexión/vacío, banner inline .NET y vista administrativa actual | ⬜ Por hacer; varias features existen, pero los diseños/renderes deben reflejar el código actual | Actualizar source `.excalidraw`, render y revisión visual. |
| **DIAG-10** | Toolchain Mermaid en el entorno documentado | ✅ Hecho 2026-10-06; `mmdc`/Node disponibles y `render.ps1 -Solo mermaid` se registró con exit 0 | Revalidar al cambiar de equipo/entorno. |

Los fuentes Mermaid canónicos tienen render en la carpeta `DIAGRAMAS/`; ver el conteo actual y las
excepciones históricas en `DIAGRAMAS/README.md` §0–§3. La falta actual de `DIAG-09` es de mockups
para nuevos estados de producto, no de instalación del renderizador Mermaid.

---

## 9. Seguridad (además de `SEC-05`)

| ID | Tarea | Estado | Nota |
|---|---|---|---|
| **SEC-05** | ~~`LISTAR_USUARIOS` sin autenticación~~ | ✅ **Cerrado** 2026-10-05 | §2. Guardas + fallo cerrado en el SPI + test parametrizado de 14 tipos + E2E |
| **SEC-06** | Guardar el surrogate de `SEC-05` | ✅ **Cerrado** 2026-10-05 | §2. Automatizado con `requiereAutenticacion()` por defecto `true` y 2 tests que fijan el conjunto abierto |
| SEG-01 | TLS en Python. Hoy solo Java y .NET. Implica cliente + operativa | ⬜ Por hacer | Aceptación: handshake + fail-fast como Java/.NET (`CAMBIAR`/vacía, almacén ausente, colisión de puertos). Se cierra junto con `PAT-05` |
| SEG-02 | Paridad `.webp` en validación de imagen | ⬜ Por hacer | Matriz 3 clientes × (png/jpg/webp válido/inválido) |
| SEG-03 | Rotación de secretos en producción | ✅ Hecho 2026-10-05 | El fail-fast se movió a `DataSourceAdapter.dataSource` porque en el `ApplicationRunner` corría **después** de que Spring creara el `DataSource`, y el síntoma era un `Access denied` que nunca nombraba la variable que faltaba |
| SEG-04 | Alerta NuGet `SQLitePCLRaw.lib.e_sqlite3 2.1.6` | 🔴 Riesgo | Versión fijada a propósito. Decidir antes de producción: actualizar o aceptar con justificación registrada |

---

## 10. Producto — requieren decisión, no se inician a medias

| ID | Tarea | Estado |
|---|---|---|
| **PROD-03** | **`difundir()` sin botón.** El servidor tiene difusión, pero es la del administrador; los clientes tienen el método y **ninguna UI**. Decidir: botón de difusión en los 3 clientes, o borrar el camino. Afecta a `MUERTE-6` | 🟠 Propuesto |
| PROD-01 | Notificación al minimizar | 🟠 Propuesto. Requiere decisión de UX + alcance por SO |
| PROD-02 | Salas temáticas | 🟠 Propuesto. Requiere modelo + enrutado + 3 UIs + cambio de protocolo |
| **PROD-04** | **`idioma=es` sin uso en los 3 clientes.** 0 lecturas de código; está en la lista blanca de `Config.save` y documentado en 2 README. Decidir: implementar i18n o quitar la clave | 🟠 Propuesto |
| **PROD-05** | **`pillow==10.4.0` sin un solo import** en el cliente Python | 🟠 Propuesto. Quitar o usar |

---

## 11. Fases ya cerradas (no reabrir sin motivo)

### DIST — distribución (P0 para entregar fuera de este PC) — ✅ 2026-10-05

- **DIST-01 ✅:** `docker-compose.prod.yml` + `.env.prod.example`, secretos por entorno,
  volúmenes de producción con nombre propio (`mensajeria-prod-*`).
- **DIST-02 ✅:** 4 instaladores nativos (`distribuir-servidor.cmd`, `dist-client.cmd`,
  `distribuir.cmd`, `dotnet publish`). Los 3 `.cmd` estaban rotos: Java usaba `package` donde
  hacía falta `install`, y buscaba los nativos JavaFX con un glob entrecomillado que nunca expande.
- **DIST-03 ✅:** checklist de actualización/backup en `DESPLIEGUE.md`, ejecutada extremo a extremo.

### PERF — rendimiento — ✅ 2026-10-05

- **PERF-01 ✅:** **95-98 tramas/s, ACK p50 ~690 ms, p95 ~1150 ms, 0 rechazos**, 100 conexiones
  vivas de 100. Base previa (50 sesiones × 2 textos, solo texto): 11,8 tramas/s.
- **PERF-02 ✅:** Hikari 10→30 y `filtros.hilos` 4→16 dan 79→96 tramas/s y p95 1524→1088 ms. El
  combo es lo que rinde; aislados dan 89 y 87.
- **PERF-03 ✅:** `poolEntrega` en difusión masiva — 99/99 destinatarios en 2-6 s con un cliente lento
  cuyo socket nunca lee.

---

## 12. Orden sugerido

1. **Revisión de seguridad previa a producción** — `SEC-05`/`SEC-06` ya están cerrados; atender los
   riesgos todavía abiertos de §9 (`SEG-01`, `SEG-02`, `SEG-04`) antes de publicar.
2. **`SMOKE-01` a `SMOKE-09`** — aceptación manual de lo ya construido, incluyendo la consola
   administrativa. Registrar entorno y resultado en `GUIA_ACEPTACION.md`.
3. **Riesgos de producción abiertos** — revisar `SEG-01`, `SEG-02` y `SEG-04` antes de publicar.
4. **`DIAG-09` y cierre de `DIAG-07`** — actualizar mockups de estados/avisos y conciliar el
   contenido de los diagramas legados; las recomendaciones están en `REVISION_DOCUMENTAL.md`.
5. Continuar los refactors aún abiertos: `PAT-01` en Python/.NET, `PAT-VM`, `PAT-CMD`, `PAT-04`
   y `PAT-05`, según dependencias y riesgos descritos en `PLAN_PATRONES_UI.md`.
6. Completar tareas de interfaz y accesibilidad todavía abiertas (`UIX-*`, `VIS-*`) según los
   criterios vigentes de §5 y `GUIA_ACEPTACION.md`.
7. Resolver `MUERTE-6` y `PROD-*` solo cuando las decisiones de producto indicadas en §9 estén
   registradas.

---

## 13. Tooling de UI (ya instalado — no es backlog)

Herramientas presentes y verificadas en este entorno al 2026-10-06; al cambiar de computador hay
que volver a comprobar versiones y disponibilidad:

| Herramienta | Versión/fuente | Para qué | Prueba superada |
|---|---|---|---|
| skill `excalidraw-skill` | `mcp-excalidraw-server` (CLI `render` headless) | mockups `.excalidraw` + PNG | uso diario |
| skill `x9-diagrams` | repo `xonika9/agent-skills` | elegir formato, convertir, revisar | uso diario |
| skill `diagramas-profesionales` | local + referencias por toolkit | mockups, clases, ER y revamp | 9/9 checks de estilos |
| skill `pyside6-mcp` | `com55/pyside6-mcp` v4.0.11, MIT | ver/manejar la app Qt en vivo | launch→tree→find→click→stop |
| MCP `pyside6` | local `uvx pyside6-mcp@0.1.0` (uv 0.12.23) + `timeout: 60000` | 22 tools de Qt | ✅ 2026-10-06: estaba caído (`server unavailable` en el log por resolución PyPI en arranque frío + `PATH` con segmento roto `hotspot\\bin`; ambos corregidos en `opencode.jsonc`). Como `launch_app` ejecuta `uv run` (exige `pyproject.toml`), se añadió `client-python/pyproject.toml` mínimo + grupo `dev` con pytest. Verificado: `launch_app` → screenshot del login → `stop_app` |
| LSP opencode | `"lsp": true` en `opencode.jsonc` + `pyright` global por npm | diagnósticos al agente en `.java`/`.cs`/`.py` | ✅ 2026-10-06: antes `all LSPs are disabled`. `jdtls` (JDK 25) y `csharp` (.NET 10) cumplen requisitos; opencode descarga esos servidores en el primer uso (tarda minutos la primera vez) |
| Workspace VS Code | `universidad-mensajeria/.vscode/` (`settings.json`, `extensions.json`, `tasks.json`) | JDK 25 + Maven 3.9.11, intérprete al `.venv` de client-python, 5 tasks con los comandos de AGENTS.md | ✅ 2026-10-06: las 9 extensiones ya estaban instaladas; solo faltaba el workspace |
| MCP `netwright` | `dotnet tool install -g Netwright` 2.0.0, MIT | 15 tools de la app WPF | launch + screenshot del login real |
| MCP remoto `drawio` | `https://mcp.draw.io/mcp` | `search_shapes` | conectado |
| MCP remoto `qt-docs` | `https://qt-docs-mcp.qt.io/mcp` | señales/slots/props Qt | handshake + catálogo OK |
| MCP remoto `mslearn` | `https://learn.microsoft.com/api/mcp` | docs oficiales XAML/WPF | handshake + catálogo OK |

**Pendiente documental:** completar los mockups `DIAG-09` y revisar/reconciliar los ficheros fuente
legados `DIAG-07`. Las carpetas legadas se conservan; no se eliminó ningún archivo en esta revisión.
No falta instalar `mmdc`/Node en el entorno descrito. Evaluación pendiente:
reevaluar `dotnet/skills` si hace falta `msbuild` para instaladores nativos.

Nada instalado sin URL verificada ni prueba de funcionamiento. OJO técnico: no usar `{env:VAR}` en
`opencode.jsonc` con el CLI `mcp add` (lo expande y rompe el JSON); editar el archivo a mano.

---

## 14. Hechos de despliegue (resumen; detalle en `DESPLIEGUE.md`)

- Servidor: JAR Spring Boot ejecutable, Java 21, directorios persistentes `server/uploads/` y
  `server/logs/`.
- Cliente JavaFX: carpeta de distribución con el JAR, librerías Maven, nativos JavaFX, launcher y
  `client.properties`.
- Python: carpeta independiente con `requirements.txt` y esquema SQLite; cada PC crea su `.venv`.
- .NET: salida `dotnet publish`, preferentemente self-contained para Windows x64.
- Clientes → servidor: TCP configurable, predeterminado `5000`. **Solo el servidor** → base de
  datos. Los clientes nunca tocan la base del servidor.

---

## Índice documental

- [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) — auditoría con evidencia (`archivo:línea`).
- [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) — fases, orden y criterios de aceptación.
- [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) — contrato funcional y no funcional.
- [`PLAN.md`](PLAN.md) — arquitectura y decisiones de diseño.
- [`PLAN_MEJORAS.md`](PLAN_MEJORAS.md) — diario de lo ya ejecutado.
- [`DESPLIEGUE.md`](DESPLIEGUE.md) — mapa de despliegue.
- [`GUIA_ACEPTACION.md`](GUIA_ACEPTACION.md) — pruebas automatizadas y matriz manual.
- [`DIAGRAMAS/README.md`](DIAGRAMAS/README.md) — catálogo de diagramas.
- Guías por componente:
  [servidor](universidad-mensajeria/server/servidor-app/README.md) ·
  [JavaFX](universidad-mensajeria/client/README.md) ·
  [Python](universidad-mensajeria/client-python/README.md) ·
  [.NET/WPF](universidad-mensajeria/client-dotnet/README.md)
- Contratos: [TCP/JSON](universidad-mensajeria/common-protocol/PROTOCOLO.md) ·
  [persistencia local](universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md)
