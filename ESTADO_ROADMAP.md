# Estado y roadmap

Actualizado: **2026-10-05**. Este es el tablero de estado; la arquitectura está en
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

> **Resumen en 30 segundos.** El código de Fase 17 + Fase 18 + fixes 2026-10-04/05 está ✅ en las
> 3 pistas, y las suites están en verde. La auditoría del 2026-10-05 encontró **1 hueco de
> seguridad real y bloqueante** (`SEC-05`: `LISTAR_USUARIOS` responde sin autenticación),
> **38 items de código muerto**, **6 bugs de UI que son funciones rotas** y **6 diagramas que
> faltaban por completo** (incluidos todos los de casos de uso). Todo está planificado y
> **nada aplicado todavía**.

## Índice de familias

| Familia | Qué agrupa | Cantidad | Bloquea producción |
|---|---|---|---|
| `SMOKE-*` | Aceptación manual pendiente | 5 | **Sí** |
| `SEC-*` | Seguridad | 5 | **`SEC-05` sí** |
| `MUERTE-*` | Limpieza de código muerto | 6 lotes | No |
| `UIX-*` | Bugs y mejoras de interfaz | 29 | Los 6 P0, sí |
| `PAT-*` | Patrones de diseño en clientes | 6 | No |
| `VIS-*` | Contrato visual compartido | 2 | No |
| `SEG-*` / `DIST-*` / `PERF-*` / `PROD-*` | Fases previas | 15 | — |
| `DIAG-*` | Diagramas | 6 | No |
| `DOC-*` | Documentación | 4 | No |

---

## 1. Estado actual por componente

| Componente | Estado | Última verificación (este entorno, 2026-10-05) |
|---|---|---|
| Servidor + JavaFX | ✅ Hecho | `mvn -pl server/servidor-app,client/client-app -am test` → BUILD SUCCESS (servidor 90, cliente 58) |
| JavaFX solo | ✅ Hecho | `mvn -pl client/client-app -am test` → 58 correctas (55 + 3 `TemaTest` UI-09) |
| Python | ✅ Hecho | `.venv/Scripts/python.exe -m pytest tests` → 60 correctas + 5 omitidas |
| .NET tests | ✅ Hecho | `dotnet test client-dotnet.Tests/Mensajeria.Tests.csproj` → 35 correctas |
| WPF build | ✅ Hecho | `dotnet build client-dotnet/Mensajeria.csproj -c Release` → BUILD SUCCESS |
| WPF distribuible | ✅ Hecho | `dotnet publish -r win-x64 --self-contained true` → 474 archivos, 168 MB |
| Contenedor servidor | ✅ Hecho | imagen `mensajeria-servidor:1.0.0` construida; stack de producción arrancado `healthy` |
| Distribuible cliente Java | ✅ Hecho | `client\dist-client.cmd` → carpeta arrancada desde `%TEMP%` |
| Carga mixta | ✅ Medido | `CargaMixtaTest` 100 sesiones × 4 tramas → 95-98 tramas/s, p95 ACK ~1150 ms, 0 rechazos |
| Difusión masiva | ✅ Medido | 99/99 destinatarios con 1 cliente lento que nunca lee |

> Nota de entorno: Maven en `C:\Tools\apache-maven-3.9.9`, SDK 10 de .NET en
> `%LOCALAPPDATA%\Microsoft\dotnet10`, `.venv` de Python recreado. **No hay `mmdc`, ni
> `plantuml`, ni `node` instalados**, así que los 16 diagramas nuevos están sin render (§1.1).

Versiones vigentes: JDK 21+ (probado en 25), JavaFX 25.0.1, Flyway 11.14.1,
`Enable-Native-Access` en el manifest del JAR. Los tests E2E usan semilla propia
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

---

<a id="seguridad-bloqueante"></a>

## 2. 🔴 Lo primero: un hueco de seguridad bloqueante

### `SEC-05` — `LISTAR_USUARIOS` responde sin autenticación · **P0**

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

- [ ] **`SEC-05` — Cerrar el hueco.** ⬜ Por hacer. Alcance:
  1. Añadir `Comandos.exigirAutenticacion(...)` a `ListarUsuariosCommand`, `ListarCommand` y
     `LogoutCommand`.
  2. **Añadir el test parametrizado que itera TODOS los tipos registrados en `Despachador` y exige
     `ERROR` con sesión anónima.** Sin este test el hueco vuelve el próximo comando nuevo.
  3. Aceptación: `MensajeriaE2ETest` gana un caso `tramaNoAutenticadaSeRechaza`; abrir un socket
     crudo y mandar `LISTAR_USUARIOS` devuelve `ERROR "autenticate primero con LOGIN"` y ningún
     dato de usuario.
- [ ] **`SEC-06` — Cerrar el surrogate de `SEC-05`.** ⬜ Por hacer. Que `respuestas` sea un
  parámetro **no usado** es justo lo que hace indetectable el fallo: la firma dice "puede responder
  errores", el cuerpo dice lo contrario. Opciones: (a) quitar el parámetro de los 3 (como el resto
  de `MUERTE-13`), o (b) un revisor obligatorio de comandos nuevos en el checklist de PR.

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
| **SMOKE-06** | **Matriz de transición de conexión × 4 estados × 3 clientes** (`Conectando`/`Conectado`/`SinRed`/`SesionCerrada`), con el texto del aviso y el tiempo de reacción anotados. Es la aceptación de `PAT-03`. | ⬜ Por hacer (espera a `PAT-03`) | `GUIA_ACEPTACION.md` § Estados de conexión |
| **SMOKE-07** | **Capturas lado a lado de la misma conversación** en los 3 clientes, tema oscuro y claro, para comprobar que `VIS-01` unificó la paleta | ⬜ Por hacer | `DIAGRAMAS/mockups/` |
| **SMOKE-08** | **Comprobación de contrastes WCAG AA** de los tokens de tema claro en los 3 clientes | ⬜ Por hacer | `PLAN_PATRONES_UI.md` §7.4 |

Criterio para dar por aceptada la paridad: SMOKE-01 a SMOKE-08 en verde con SO/versión/commit
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
| MUERTE-1 | Java: 13 símbolos + 3 parámetros `respuestas` + 12 imports | `MUERT-01..13`. Incluye `SEC-05`/`SEC-06` | ⬜ Por hacer | `mvn test` verde; el E2E de login sigue pasando |
| MUERTE-2 | .NET: 9 pinceles, 3 constantes, 1 propiedad, 1 variable, 12 `using` | `MUERT-25..30` | ⬜ Por hacer | `dotnet test` + `build -c Release`; comparación visual de ambos temas contra el mockup |
| MUERTE-3 | Python: 6 símbolos + imports + campos de test | `MUERT-16..24` | ⬜ Por hacer | `pytest tests` verde. **`_tareas_imagen` es un leak**: su borrado es corrección |
| MUERTE-4 | CSS y XAML muertos | `MUERT-31..38` | ⬜ Por hacer | **Medio riesgo**: arrancar la app y confirmar que cada control conserva su estilo antes de borrar (regla B2) |
| MUERTE-5 | Deduplicación | 3 parsers de `client.properties` → 1; `fallbackSchema` muerto; 2 migradores de esquema duplicados; `_leer_modo`/`_guardar_modo` unificados; tabla MIME unificada | ⬜ Por hacer | Tests existentes + test nuevo de equivalencia de `client.properties` en los 3 |
| MUERTE-6 | Decisiones de producto, no limpieza | `difundir()` sin botón en Python y .NET; clave `idioma=` sin uso; dependencia `pillow` sin imports; `ui/registro_window.py` | 🟠 Propuesto | Ver §9 |

**Lo que estos lotes arreglan de paso:** `MUERT-33` (el título del sidebar sale del color
equivocado en tema claro), `MUERT-28` (un `_ =` que anula el aviso del compilador y esconde los
MUERT reales), `MUERT-19` (leak de memoria), `MUERT-25` (handler que sugiere un botón inexistente),
y el Javadoc obsoleto de `LoginConteoDTO.java:5` que afirma una agrupación de tipos que el código no
hace.

---

## 5. Bugs y mejoras de interfaz

Plan por fases en [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md). Evidencia completa en
[`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) §3.

### 5.1 Fase A — 6 bugs P0 (funciones rotas, no estilo)

Ninguno requiere refactor. Cada uno es un cambio pequeño y verificable por separado.

| ID | Cliente | Qué está roto | Aceptación | Estado |
|---|---|---|---|---|
| **UIX-00a** | .NET | **La pérdida de conexión es invisible.** `OnDesconectado` existe en el transporte y en la fachada, y `bannerOffline` existe en el XAML, pero **ninguna vista se suscribe** y nadie cambia el `Visibility` | Matar el servidor con el cliente abierto → el banner ámbar aparece en los 3 en ≤ 2 s, con el mismo texto | ⬜ Por hacer |
| **UIX-00b** | Python | **"Reintentar imagen" nunca reintenta**: el `_ejecutar` está después del `raise` | Romper red → enviar imagen → reconectar → pulsar Reintentar → la imagen aparece sin recargar | ⬜ Por hacer |
| **UIX-00c** | Java | `alError()` y `alDesconexion()` **están vacíos**: son un comentario, no código | El servidor cae durante un envío → el usuario ve un mensaje, no la nada | ⬜ Por hacer |
| **UIX-00d** | Java | El fallo de persistencia **se traga** (`catch (Exception e) { }`) | BD local en solo lectura → llega un mensaje → aparece en pantalla y el log lo dice | ⬜ Por hacer |
| **UIX-00e** | Python | **`login()` corre en el hilo de la GUI**: bloquea hasta 15 s | Servidor inalcanzable → la ventana responde los 15 s (se mueve, el botón cambia) | ⬜ Por hacer |
| **UIX-00f** | .NET | **"Conectando…" se pinta dentro de la caja roja de error** | Con latencia artificial: "Conectando…" en gris neutro; el rojo solo para error | ⬜ Por hacer |

> `UIX-00a` y `UIX-00c` son el mismo defecto en dos idiomas. Arreglarlo "a mano" en .NET sería la
> cuarta reimplementación de la misma idea — por eso el estado de conexión unificado es `PAT-03` y
> se hace en la Fase C, no antes.

### 5.2 Fase G — Mejoras por pantalla (P1/P2)

| ID | Asunto | Prioridad | Clientes | Estado |
|---|---|---|---|---|
| UIX-01 | **Virtualizar la lista de mensajes** | **P0** | Python | ⬜ Por hacer |
| UIX-15 | Quitar los `MessageBox` modales de .NET (**10, incluido "sin red"**) | **P0** | .NET | ⬜ Por hacer |
| UIX-13 | Estados vacíos (conversación vacía, búsqueda sin resultados, sin contactos) | P1 | los 3 | ⬜ Por hacer |
| UIX-05 | Agrupar mensajes consecutivos (ocultar remitente en una racha) | P1 | los 3 | ⬜ Por hacer |
| UIX-04 | Separadores de día | P1 | los 3 | ⬜ Por hacer |
| UIX-03 | Formato de fecha unificado y relativo | P1 | los 3 | ⬜ Por hacer |
| UIX-02 | Distintivo de no leídos en los 3 + filtro "No leídos" | P1 | los 3 | ⬜ Por hacer |
| UIX-06 | **Buscar dentro del historial** con `Ctrl+F` — **no existe en ninguno de los 3** | P1 | los 3 | ⬜ Por hacer |
| UIX-07 | Debounce en la búsqueda | P1 | los 3 | ⬜ Por hacer |
| UIX-08 | Zoom y pan en imagen (rueda / `+` / `-` / `F` / `0` / `Esc`) | P1 | los 3 | ⬜ Por hacer |
| UIX-09 | Cancelar la carga de imagen al salir de vista | P1 | los 3 | ⬜ Por hacer |
| UIX-17 | Corregir 4 contrastes que fallan WCAG AA en tema claro | P1 | Java, Python | ⬜ Por hacer |
| UIX-18 | Diferenciar visualmente "Enviado" y "Entregado" (comparten color) | P1 | .NET | ⬜ Por hacer |
| UIX-19 | Nombre accesible en botones de solo icono | P1 | Python, .NET | ⬜ Por hacer |
| UIX-24 | Foco inicial en el campo de código del login | P1 | .NET | ⬜ Por hacer |
| UIX-14 | Indicador de carga del historial | P2 | los 3 | ⬜ Por hacer |
| UIX-16 | Anclaje de scroll al cargar anteriores | P2 | los 3 | ⬜ Por hacer |
| UIX-10 | Ancho de burbuja relativo al ancho de ventana | P2 | los 3 | ⬜ Por hacer |
| UIX-11 | Sidebar redimensionable en .NET | P2 | .NET | ⬜ Por hacer |
| UIX-12 | `Shift+Enter` = salto de línea (imposible hoy) | P2 | Python, .NET | ⬜ Por hacer |
| UIX-21 | Cuerpo del mensaje seleccionable y copiable | P2 | Java, .NET | ⬜ Por hacer |
| UIX-22 | Mnemónicos / accelerator keys | P2 | los 3 | ⬜ Por hacer |
| UIX-23 | Escala de tipografía / alta resolución | P2 | los 3 | ⬜ Por hacer |
| UIX-15b | Login redimensionable (`.NET` `SizeToContent`, `Java` tamaño fijo) | P2 | Java, .NET | ⬜ Por hacer |
| — | Tooltip de por qué servidor/puerto no son editables | P2 | los 3 | ⬜ Por hacer |
| — | Cuarto estado en el login: "usuario no registrado" con qué hacer | P2 | los 3 | ⬜ Por hacer |

### 5.3 Errores de tematización (P1)

| ID | Bug | Estado |
|---|---|---|
| UIX-25 | `.sidebar-title` duplicado y contradictorio (`MUERT-33`) | ⬜ (se cierra en `MUERTE-4`) |
| UIX-26 | Python adjunta QSS de **tema claro** en modo oscuro para los iconos de adjunto | ⬜ Por hacer |
| UIX-27 | Cambiar de tema **reconstruye toda la lista** de mensajes en .NET | ⬜ Por hacer |
| UIX-28 | .NET usa `StaticResource` pero **reasigna por reflexión** (el propio comentario lo admite) | ⬜ Por hacer |
| UIX-29 | Python arranca en tema claro mientras `tema.py` dice oscuro por defecto | ⬜ Por hacer |

---

## 6. Patrones de diseño en los clientes

Detalle, riesgos y criterios medibles en [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) §3–6.

| ID | Patrón | Qué resuelve | Prioridad | Estado |
|---|---|---|---|---|
| **PAT-01** | Composition root real | Java: `ClienteApp.fachada()` es un localizador estático → **`ChatController` no se puede construir en un test**. Python: 6 colaboradores con defaults `or` y grafo que cambia de forma en runtime. .NET: el DI existe y solo lo usan los tests | P0 (base de PAT-VM) | ⬜ Por hacer |
| **PAT-03** | `EstadoConexion` observable (4 estados, 3 clientes) | La causa directa de `UIX-00a` y `UIX-00c`. Hoy Java **sondea** cada 30 s, Python solo cambia el banner de paso, .NET no se entera | P0 | ⬜ Por hacer |
| **PAT-02** | Guarda de autenticación obligatoria en comandos nuevos | El surrogate de `SEC-05`: la firma dice "puede responder errores" y el cuerpo dice lo contrario | P0 (con `SEC-05`) | ⬜ Por hacer |
| **PAT-VM** | MVVM: `ChatViewModel` en los 3 | `ChatController.java` son 774 líneas y `chat_window.py` 1 322, mezclando vista + estado + red. .NET va ⅓ (`MensajeVM` sí notifica, `UsuarioVM` no) | P1 | ⬜ Por hacer |
| **PAT-CMD** | `Command` / `ICommand` para las acciones de UI | "Puedo enviar" está implementado **3 veces** y en .NET hay **0 `ICommand`**, así que ningún botón se habilita de forma declarativa | P1 | ⬜ Por hacer |
| **PAT-04** | Adapter del almacén local | Python **no tiene interfaz**: la vista la salta y accede a `fachada.historial`. El upsert `DELETE + INSERT` portable ya está documentado y solo se usa en Java | P2 | ⬜ Por hacer |
| **PAT-05** | Adapter de transporte TCP/TLS | 3 implementaciones con 3 estrategias, y **validación TLS inconsistente**: Python valida fail-fast, Java no valida, .NET sustituye el puerto en silencio | P2 (cierra `SEG-01`) | ⬜ Por hacer |
| **PAT-06** | Test de equivalencia del `schema-local.sql` en .NET | `test_schema_distribuible.py:34` protege la copia Python; **la .NET no tiene equivalente** y puede divergir en silencio | P2 | ⬜ Por hacer |

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

Fuentes nuevas **escritas**, renders **pendientes** (§1.1). Detalle y plan de consolidación en
[`DIAGRAMAS/README.md`](DIAGRAMAS/README.md).

| ID | Tarea | Estado | Aceptación |
|---|---|---|---|
| **DIAG-01** | **Casos de uso** — `casos/uc-cliente.mmd`, `uc-servidor.mmd`, `uc-sistema.mmd`. Antes **no existía ningún `usecaseDiagram`** en el repo | ✅ source | Render + revisión |
| **DIAG-02** | **Secuencia** — conexión/trama, Observer, Object Pool, tuberías. Antes solo había 3, en la carpeta vieja | ✅ source | Render + revisión |
| **DIAG-03** | **Actividad** — login, envío, archivo fragmentado, descarga, reconexión/cola. Antes no existía ningún árbol de decisión | ✅ source | Render + revisión |
| **DIAG-04** | **Estados** — mensaje, sesión, transferencia. Antes no existía ningún `stateDiagram`. `st-mensaje` pasa a ser la referencia contra la cual se comparan los 3 `CASE` SQL duplicados | ✅ source | Render + revisión |
| **DIAG-05** | **Despliegue** — 4 procesos en LAN, con la prohibición explícita de cliente → base del servidor | ✅ source | Render + revisión |
| **DIAG-06** | **Clases de Python y .NET** — no existía ninguno de los dos | ✅ source | Render + revisión |
| **DIAG-07** | **Consolidación** — migrar a `DIAGRAMAS/` los 14 diagramas únicos de `DIAGRAMAS_ENTREGA` + `DIAGRAMAS_FINALES` y borrar las 2 carpetas vacías | ⬜ Por hacer | Los 6 pasos de `DIAGRAMAS/README.md` §3.3, en orden. **Copiar antes de borrar.** Añadir los `BaseName` nuevos al mapa `$anchos` de `render.ps1` |
| **DIAG-08** | **`render.ps1` sale siempre con código 0.** No hay forma de que CI detecte un render roto | ⬜ Por hacer | Contar los `FALLO` y devolver exit code distinto de 0 |
| **DIAG-09** | **Diagramas posteriores a la feature**: mockups de los 4 estados de conexión y de los estados vacíos / diálogos de error | ⬜ Por hacer (esperan a `PAT-03` y `UIX-13`) | — |
| **DIAG-10** | Instalar el toolchain de render en la máquina: `npm i -g @mermaid-js/mermaid-cli` | ⬜ Por hacer | `.\render.ps1` sin `Write-Warning` de herramienta ausente |

**Los 16 `.mmd` nuevos están sin render.** Sin `mmdc` no hay PNG y, por la regla de los 3 pasos de
`DIAGRAMAS/README.md` §1, **un diagrama sin render revisado no cuenta como terminado**. Por eso
`DIAG-10` bloquea a `DIAG-01..06` en la práctica.

---

## 9. Seguridad (además de `SEC-05`)

| ID | Tarea | Estado | Nota |
|---|---|---|---|
| **SEC-05** | `LISTAR_USUARIOS` sin autenticación | 🔴 **Bloqueante** | §2 |
| **SEC-06** | Guardar el surrogate de `SEC-05` | ⬜ Por hacer | §2 |
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

1. **`SEC-05` + `SEC-06`** — es lo único que bloquea producción. Empieza por el test parametrizado,
   no por el fix.
2. **`SMOKE-01` a `SMOKE-08`** — aceptación de lo ya construido. Nada de lo demás se considera
   aceptado antes.
3. **Fase A (`UIX-00a` a `UIX-00f`)** — 6 bugs de funciones rotas, sin refactor.
4. **`MUERTE-1` a `MUERTE-5`** — antes de partir los archivos, no después: `MUERT-19` y `MUERT-37`
   están dentro de los archivos que MVV va a tocar.
5. **`DIAG-10` → `DIAG-01` a `DIAG-06`** (render + revisión) y `DIAG-07` (consolidación).
6. **Fase C** (`PAT-01`, `PAT-03`) — cimientos.
7. **Fase D** (`PAT-VM`) y **Fase E** (`PAT-CMD`).
8. **Fase G** (`UIX-01` a `UIX-29`) y `VIS-01` / `VIS-02`.
9. **Fase F** (`PAT-04`, `PAT-05`, `PAT-06`) — cierra `SEG-01` y `SEG-02`.
10. **Decisiones de producto** (`PROD-03`, `PROD-04`, `PROD-05`) solo con decisión registrada.

---

## 13. Tooling de UI (ya instalado — no es backlog)

Base instalada y verificada el 2026-10-05, para no reinstalar ni reevaluar sin motivo:

| Herramienta | Versión/fuente | Para qué | Prueba superada |
|---|---|---|---|
| skill `excalidraw-skill` | `mcp-excalidraw-server` (CLI `render` headless) | mockups `.excalidraw` + PNG | uso diario |
| skill `x9-diagrams` | repo `xonika9/agent-skills` | elegir formato, convertir, revisar | uso diario |
| skill `diagramas-profesionales` | local + referencias por toolkit | mockups, clases, ER y revamp | 9/9 checks de estilos |
| skill `pyside6-mcp` | `com55/pyside6-mcp` v4.0.11, MIT | ver/manejar la app Qt en vivo | launch→tree→find→click→stop |
| MCP `pyside6` | local `uvx pyside6-mcp` (uv 0.12.23) | 22 tools de Qt | 22 tools listadas; E2E real OK |
| MCP `netwright` | `dotnet tool install -g Netwright` 2.0.0, MIT | 15 tools de la app WPF | launch + screenshot del login real |
| MCP remoto `drawio` | `https://mcp.draw.io/mcp` | `search_shapes` | conectado |
| MCP remoto `qt-docs` | `https://qt-docs-mcp.qt.io/mcp` | señales/slots/props Qt | handshake + catálogo OK |
| MCP remoto `mslearn` | `https://learn.microsoft.com/api/mcp` | docs oficiales XAML/WPF | handshake + catálogo OK |

**Lo que falta para esta ronda:** `mmdc` y `node` (`DIAG-10`). Evaluación pendiente: reevaluar
`dotnet/skills` si hace falta `msbuild` para instaladores nativos.

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
