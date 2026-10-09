# Plan de paridad total de los tres clientes

**Estado:** en ejecución (actualizado 2026-10-08)
**Alcance:** `client/` (JavaFX), `client-python/` (PySide6), `client-dotnet/` (WPF)
**Fuera de alcance:** el servidor no se modifica, salvo que la Fase 0 demuestre que falta un comando.
**Objetivo:** que los tres clientes sean **intercambiables** — mismo servidor, mismo protocolo, mismas
funcionalidades, misma arquitectura por capas, mismo diseño visual verificado por capturas.

**Avance:** las fases 0.1–0.4 tienen sus cambios de código y contrato implementados. La verificación
combinada más reciente pasa: JavaFX **212/212**, Python **218 casos (213 correctos y 5 omitidos)**,
.NET **203/203** y build WPF con 0 advertencias/errores. Las diferencias conocidas de §3.1 y el
contrato de estados de §3.2 están cerrados. Siguen pendientes el smoke real de interoperabilidad con
archivos de 20 MiB, completar arquitectura Python/.NET y unificar banners/accesibilidad (§3.3),
capturas visuales y empaquetado/documentación (fases 4–6).

---

## 0. Conclusión previa: Java **no** puede ser la referencia literal

> **Actualizado 2026-10-08 — el bloqueante de §0.1 ya está resuelto.** Java envía por el protocolo
> fragmentado; el trabajo de contrato y tokens de las fases 0.2–0.4 también está implementado.
> La verificación actual pasa las tres suites y el build WPF. Aún falta el smoke real de 20 MiB y
> las fases 1–6 siguen abiertas según los criterios detallados abajo.

Mientras el contrato esté escrito a mano tres veces, cualquier fase posterior reintroduce deriva.
Esta sección crea **una sola fuente de verdad** y genera el resto.

### 0.1 El hallazgo bloqueante: adjuntos de más de ~12 MiB no funcionan en Java

| Dato | Valor | Fuente |
|---|---|---|
| Tope de trama del protocolo | **16 MiB** | `TramaCodec.TAMANO_MAXIMO_TRAMA`, invariante 2 de `AGENTS.md` §5 |
| Tope de adjunto que anuncia la UI de Java | **50 MiB** | `TextoCliente.TOPE_ADJUNTO_BYTES` |
| Base64 infla los bytes | **× 4/3** | — |
| Transferencia fragmentada en Java | **implementada** | `ConexionCliente.java`, `FachadaCliente.java`; falta el smoke real de aceptación |

El defecto inicial ya quedó corregido: Java fragmenta los archivos en bloques de 1 MiB y calcula el
SHA-256 del archivo completo. La suite cubre archivos de 13 MiB y los metadatos de cada fragmento;
queda por comprobar el envío real de 20 MiB a través del servidor.

Mientras tanto:

- **Python** (`fachada_cliente.py:286-351`) **sí** fragmenta: `ARCHIVO_INICIO` → N × `ARCHIVO_PARTE`
  (1 MiB) → `ARCHIVO_FIN`, con SHA-256.
- **.NET** (`FachadaCliente.cs:609-712`, `ColectorFragmentos`) **también**, con verificación de hash.
- El **servidor** soporta las tres fases (`ArchivoInicioCommand`, `ArchivoParteCommand`,
  `ArchivoFinCommand`, registrados en `Despachador`).
- `AGENTS.md` §5 invariante 2 dice literalmente: *"Los archivos viajan fragmentados"*.

**Conclusión:** el invariante correcto es el fragmentado. Java es el que incumple. Si el plan se
limita a replicar Java, se pierde una funcionalidad que dos clientes ya tienen y que el enunciado
del proyecto exige.

### 0.2 Lo que Java tiene y los otros dos no

- El **fondo de chat con imagen** (`fondos/fondo_chat_oscuro.jpg` 2 257 KB,
  `fondo_chat_claro.jpg` 1 087 KB, aplicados con `-fx-background-image: cover`).
- Una **capa de puertos real**: `client-interfaces` con 12 interfaces, 24 records, y **6 pruebas de
  ArchUnit** que impiden que la presentación toque el socket o la base.
- `ChatViewModel` + **7 colaboradores de presentación extraídos** del controlador.
- **8 métodos `default`** en `InterfazConexionRed` que documentan operaciones opcionales.
- Inyección por constructor real desde `FabricaCliente` (11 parámetros).

### 0.3 Lo que Python y .NET tienen y Java no

| Capacidad | Python | .NET | Java |
|---|---|---|---|
| Adjuntos fragmentados > 12 MiB | ✅ | ✅ | ❌ **defecto** |
| TLS con validación *fail-fast* | ✅ `transversal/tls.py` | ⚠️ parcial | ❌ |
| Flush periódico de cola (30 s) | ❌ **falta** | ✅ `IntervaloFlushSegundos = 30` | ✅ |
| Sidebar redimensionable y persistido | ❌ | ✅ UIX-11 | ❌ |

### 0.4 Decisión que hay que tomar antes de la Fase 1

> **El contrato es la especificación, no la implementación Java.**
> Se adopta el fragmentado como comportamiento canónico (invariante 2) y **se arregla Java**.
> La lista de funcionalidades, los tokens de diseño y los literales de UI **sí** se toman de Java,
> porque ahí están completos.

Propuesta de resolución asimétrica, que es la única que no pierde trabajo ya hecho:

| Eje | Fuente canónica | Los otros dos |
|---|---|---|
| Funcionalidades | JavaFX | se completa lo que falte |
| Literales de UI | JavaFX | se corrigen las derivas |
| Tokens de diseño | `chat.css` | se regeneran desde el archivo único |
| Arquitectura por capas | `client-interfaces` | se replica |
| Transferencia de adjuntos | **protocolo / Python / .NET** | **se arregla Java** |
| Validación TLS | **Python** | se lleva a Java y .NET |

---

## 1. Arquitectura objetivo

Los tres clientes implementan **la misma arquitectura de 4 capas**, con los mismos nombres y las
mismas responsabilidades. Solo cambia la tecnología de cada capa.

```
┌──────────────────────────────────────────────────────────────────────┐
│ CAPA 4 · PRESENTACIÓN        JavaFX      PySide6         WPF         │
│   LoginWindow · ChatWindow · VisorImagen                              │
│   Celdas: MensajeCell · UsuarioCell · VisorImagenChat                │
│   Controladores: ChatController (≤250) · LoginController             │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ depende solo de interfaces
┌───────────────────────────────▼──────────────────────────────────────┐
│ CAPA 3 · ESTADO Y CASOS DE USO                                         │
│   ChatViewModel (observable)                                          │
│   RefrescadorContactosChat · PaginadorHistorialChat                   │
│   BusquedaHistorialChat · GestorAdjuntosChat                          │
│   GestorSalidaSesionChat · CargadorAdjuntoEntradaChat                │
│   EnviarMensajeComando (Command)                                     │
│   FachadaCliente (≤250) → 7 colaboradores:                            │
│     CoordinadorMensajeria · LectorHistorialRemoto                    │
│     GestorReconexionSesion · ReintentadorPendientes                   │
│     SincronizadorDirectorio · ConstructorRecientes (puro)             │
│     PublicadorEventos · ReceptorEntrante                              │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ depende solo de interfaces
┌───────────────────────────────▼──────────────────────────────────────┐
│ CAPA 2 · PUERTOS  (= client-interfaces, 24 records + 12 interfaces)   │
│   InterfazConexionRed · ReceptorMensajes · InterfazHistorialLocal    │
│   InterfazCacheUsuarios · InterfazAutenticacion · InterfazMapeador   │
│   InterfazAlmacenLocal · InterfazChatViewModel · InterfazChatCliente │
│   InterfazFachadaCliente · InterfazNavegacionCliente · …              │
│   REGLA: cero dependencias de framework, de DTO o de SQL              │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ implementa
┌───────────────────────────────▼──────────────────────────────────────┐
│ CAPA 1 · ADAPTADORES                                                     │
│   Red:    ConexionCliente  · TramaCodec (4B big-endian + JSON UTF-8)  │
│   Datos:  LocalStore → HistorialLocal (SQLite) · CacheUsuarios        │
│   Mapa:   MapeadorDatosServidor                                        │
│   DI:     FabricaCliente (composition root único)                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.1 Por qué importa (SOLID, Clean Code)

| Principio | Cómo se cumple | Cómo se verifica |
|---|---|---|
| **S** — SRP | `FachadaCliente` delega; cada colaborador tiene una razón para cambiar | Ningún archivo de capa 3 > 350 líneas |
| **O** — OCP | 8 métodos `default` de `InterfazConexionRed` añaden operaciones sin tocar la interfaz | Test de contrato de `InterfazConexionRed` |
| **L** — LSP | `ExcepcionSesion`/`ExcepcionValidacion`/**3 familias** de jerarquía (Checked / Runtime / IO) | Test de jerarquía por lenguaje |
| **I** — ISP | `ReceptorMensajes` son 4 callbacks; `InterfazChatCliente` no expone lo que no usa | Test de_size de interfaz |
| **D** — DIP | Capa 2 sin imports de framework; todo se inyecta desde `FabricaCliente` | **6 reglas ArchUnit** en Java + equivalentes en pytest/xUnit |

### 1.2 Nombres de referencia (idénticos en los 3 lenguajes)

```
IInterfazConexionRed      /  IConexionRed       /  IConexionRed
IReceptorMensajes         /  ReceptorMensajes   /  IReceptorMensajes
IInterfazHistorialLocal   /  IHistorialLocal    /  IHistorialLocal
IInterfazCacheUsuarios    /  ICacheUsuarios     /  ICacheUsuarios
IInterfazAutenticacion    /  IAutenticacion     /  IAutenticacion
IInterfazMapeadorDatos    /  IMapeadorDatos     /  IMapeadorDatos
IInterfazAlmacenLocal     /  IAlmacenLocal      /  IAlmacenLocal
IInterfazChatViewModel    /  IChatViewModel     /  IChatViewModel
IInterfazFachadaCliente   /  IFachadaCliente    /  IFachadaCliente
ChatViewModel             /  ChatViewModel      /  ChatViewModel
FachadaCliente            /  FachadaCliente     /  FachadaCliente
MensajeCell               /  MensajeDelegate    /  ChatBubbleTemplate
```

---

## 2. Fases de ejecución

Estimaciones en **jornadas de 8 h** con 1 persona. El orden importa: las fases 0 y 1 bloquean a todas
las demás.

| # | Fase | Java | Python | .NET | Est. | Bloquea a |
|---|---|---|---|---|---|---|
| **0.1** | Fragmentado de adjuntos | ✅ **hecho** | ✅ ya | ✅ ya | **2 j** | fase 1 |
| **0.2** | Tokens de diseño generados | ✅ **hecho** | ✅ hecho | ✅ hecho | **3 j** | fase 4 |
| **0.3** | Máquina de estados del mensaje en un archivo | ✅ **hecho** | ✅ hecho | ✅ hecho | 1 j | fase 4 |
| **0.4** | Especificación normativa de los puertos | ✅ **hecho** | ✅ hecho | ✅ hecho | 1 j | fase 2 |
| **1** | Red y protocolo al 100 % | 4 j | 3 j | 3 j | **10 j** | fase 2 |
| **2** | Arquitectura por capas | 1 j | 9 j | 7 j | **17 j** | fase 3 |
| **3** | Paridad funcional | 1 j | 5 j | 4 j | **10 j** | fase 4 |
| **4** | Paridad visual con capturas | 3 j | 4 j | 4 j | **11 j** | fase 5 |
| **5** | Paridad de pruebas | 2 j | 4 j | 3 j | **9 j** | fase 6 |
| **6** | Empaquetado y documentación | 1 j | 2 j | 2 j | **5 j** | — |
| | **Total** | **14 j** | **30 j** | **25 j** | **69 j** | |

### 0.5 Lo que ya se corrigió — Fase 0.1 ejecutada el 2026-10-07

El defecto de los adjuntos quedó arreglado y verificado. Cambios:

| Archivo | Cambio |
|---|---|
| `InterfazConexionRed.java` | Nuevo `default enviarArchivoFragmentado(id, remitente, destinatario, nombre, mime, byte[])`. Recibe **bytes crudos** y **degrada a trama única** para que los dobles de prueba no se rompan |
| `ConexionCliente.java` | Implementa `ARCHIVO_INICIO` → N × `ARCHIVO_PARTE` (1 MiB, el mismo corte que Python y .NET) → `ARCHIVO_FIN` con SHA-256 del archivo completo |
| `ConexionCliente.java` | **Segundo defecto encontrado por las pruebas:** `clonarConId` reescribía cada solicitud copiando 27 de los 29 componentes y **descartaba `totalPartes`, `indiceParte` y `tipoOriginal`**. El servidor rechaza `ARCHIVO_INICIO`/`ARCHIVO_PARTE` si no llegan, así que la fragmentación no habría funcionado ni con el arreglo anterior. Corregido |
| `CoordinadorMensajeriaCliente.java` | Envía por la vía fragmentada y guarda `"[Archivo]"` en `contenido` en vez del Base64. Antes un adjunto de 50 MiB metía ~67 MiB en la columna `contenido` del H2 local **y el preview del sidebar salía con basura de Base64** (`SUBSTR(contenido,1,160)`). Coincide con el cliente Python |
| `ReintentadorPendientesCliente.java` | El reintento de cola también va fragmentado: un archivo grande que quedó pendiente sin red no debe fallar al reenviarse |
| `TransferenciaFragmentadaTest.java` | **Nuevo, 8 casos.** Incluye el que prueba el motivo: un archivo de 13 MiB cuyo Base64 (17,3 MiB) excede el tope de trama |

Comportamiento conservado a propósito: `enviarImagen` sigue en trama única (una imagen cabe de
sobra en 16 MiB), que es el camino que ejercita `GUIA_ACEPTACION.md`. Un rechazo del servidor en
cualquiera de las tres fases se devuelve al llamante y marca ERROR; solo una caída real de red
registra pendiente y reintenta.

**Verificación:** `mvn -pl client/client-app -am test` → **182/182** (174 previas + 8 nuevas),
ArchUnit 6/6 sin cambios. `mvn -pl server/servidor-app,client/client-app -am -DskipTests compile`
→ BUILD SUCCESS; el servidor no se tocó.

**Lo que falta de esta fase:** la prueba recorre un stub con el `TramaCodec` real, no el servidor
completo. Falta el smoke con un archivo de 20 MiB real de extremo a extremo (§0.1 paso 6).

### 0.6 Lo que ya se hizo — Fase 0.2 ejecutada el 2026-10-07

El diseño ahora **se genera**. `contrato/` es la fuente única y `verificar.py` corre los 5 pasos
(tokens → Java → Python → build WPF → .NET) con un solo comando.

| Fichero | Papel |
|---|---|
| `contrato/tokens-diseno.json` | La paleta, una vez |
| `contrato/generar_tokens.py` | Genera los 7 ficheros; falla si un contraste baja de 4.5:1 |
| `contrato/plantillas/*.in` | 1 plantilla por **pantalla**, no por tema |
| `contrato/verificar.py` | Los 5 pasos, exit 0 o 1 |
| `contrato/README.md` | Cómo se usa y por qué |

Cambios en los clientes:

- **`ui/tema.py` perdió su paleta.** Ahora deriva de `tema_tokens.py` (generado) y traduce a los
  nombres que `chat_window.py` ya usaba: **ese fichero no cambió ni una línea**. Un test falla si
  aparece un hex literal en `tema.py`.
- **`TemaOscuro.xaml` / `TemaClaro.xaml`** se generan. Se añadieron 7 claves nuevas (`SendBrush`,
  `InputBgBrush`, `InputFgBrush`, `SidebarBrush`, `DividerBrush` y 3 de aviso) para que WPF pueda
  dejar de usar colores sueltos.
- **Los 4 CSS** se generan. Al ser 1 plantilla por pantalla, `chat-claro.css` ya no puede quedarse
  corto: se le añadieron los 3 selectores que le faltaban.
- **Borrado `ui/registro_window.py`.** Era un stub de 3 líneas con `__all__ = []` que
  `test_registro_eliminado` venía rechazando desde antes de esta fase (fallo preexistente, no lo
  causó el trabajo de tokens).

Dos problemas reales que solo aparecieron al generar:

1. **WPF no compone `rgba()`.** `CitaBgBrush` salía como `Color="rgba(38, 125, 117, 0.08)"`, que
   compila pero **revienta en runtime** al cargar el diccionario. El generador ahora aplana los
   rgba sobre el fondo del tema y hay un test que exige `#AARRGGBB` en cada `Color`.
2. **Qt tampoco.** `transparent` y `rgba()` se resuelven a un color concreto para Python.

Correcciones de contraste aplicadas (el generador las detectó y se negaba a escribir):

| Token | Antes | Ahora | Motivo |
|---|---|---|---|
| `sender.fg` claro | `#267d75` | `#1F635C` | 3.79 → 5.40:1 sobre la burbuja ajena |
| `sender.out.fg` / `time.out.fg` oscuro | `#C4B5FD` | `#F7F5FF` | 2.68 → 4.59:1 sobre la burbuja propia morada |
| `input.prompt` oscuro | `#7A8A99` | `#9AAAB8` | 3.57 → 5.32:1 |
| `login.field.prompt` claro | `#7A8A99` | `#5A686E` | igual que el de `input.prompt` |

El token `sender.fg` **se partió en dos** (`sender.fg` y `sender.out.fg`): el lavanda no funciona
sobre la burbuja propia morada, pero sí sobre la ajena. Es un token que la revisión manual no habría
detectado.

**Verificación:** `contrato/verificar.py` → los 7 pasos en verde.
Tokens: 19 contrastes OK. JavaFX **182/182**. Python **114** (5 skip, +9 de contrato, y el
preexistente de registro resuelto). WPF build 0 errores 0 warnings, **79/79**.

### 0.7 Lo que ya se hizo — Fase 0.3 ejecutada el 2026-10-08

La máquina de estados del mensaje pasa a ser un contrato verificable.

| Fichero | Papel |
|---|---|
| `contrato/estado-mensaje.json` | Los 5 estados: orden, tick, detalle, color. Más las 5 reglas |
| `contrato/verificar_estado_mensaje.py` | Extrae lo escrito en cada cliente y lo compara |
| `contrato/test_estado_mensaje.py` | 7 tests que fallan si divergen |

**El bug que encontró.** Al comparar los glifos de los ticks, el verificador dijo que
`ERROR` no aparecía en Python ni en .NET. No era un problema del verificador: era real.

| Cliente | Un mensaje que el servidor rechaza |
|---|---|
| JavaFX | `! No enviado` |
| PySide6 | **nada** |
| WPF | **nada** |

La causa: Python y .NET tenían el tick en un `switch`/`dict` con solo 4 estados, y el caso por
defecto devolvía cadena vacía. `ERROR` **sí se escribía en la base** (los tres lo hacen) y **sí
se mostraba en Java**, así que el mismo rechazo se veía en un cliente y no en los otros dos.
Corregido en los dos.

**Un segundo defecto, ya en el verificador.** Buscaba la tabla de ticks solo en `client-app` y
daba un falso negativo en los 5 estados de Java. La tabla vive en `client-interfaces`; el cliente
Java está partido en dos módulos Maven. El verificador ahora recorre los dos, y excluye `dist/`
para que una copia empaquetada vieja no lo engañe.

**Lo que el verificador comprueba** (todo en verde):

| Regla | Por qué |
|---|---|
| `LEIDO` nunca se degrada | Un reintento de cola no debe rebajar un mensaje ya leído |
| `ENTREGADO` no baja a `PENDIENTE`/`ENVIADO` | Evita el eco del `ENVIADO` que se grabó antes de caer la red |
| `ENVIADO` no baja a `PENDIENTE` | Mismo motivo |
| `estado <> 'LEIDO'` en los 3 | Es la condición de "sin leer" del contador |
| `ERROR` no aparece como condición | Es terminal: un acuse tardío no debe resucitarlo |
| Los 5 ticks con su glifo | Lo ve el usuario; la lógica puede coincidir y la UI no |

**Verificación:** `contrato/verificar.py` → 7 pasos en verde. Python **114** → **121** (los 7
nuevos). JavaFX 182/182, .NET 79/79, WPF 0 errores.

### 0.8 Lo que ya se hizo — Fase 0.4 ejecutada el 2026-10-08

`contrato/puertos.json` es la especificación normativa, y `verificar_puertos.py` comprueba que el
código Java la cumple. Sin el verificador, el JSON sería documentación que nadie relee.

| Fichero | Papel |
|---|---|
| `contrato/puertos.json` | Los 23 puertos, 186 métodos, 8 records, 2 enums, 6 excepciones |
| `contrato/verificar_puertos.py` | Contrasta el contrato con el código Java |
| `contrato/test_puertos.py` | 12 tests que fallan si divergen |

**Lo que verifica**, todo en verde:

| Comprobación | Por qué importa |
|---|---|
| Los 23 puertos existen con las firmas declaradas | Es la superficie que copiarán Python y .NET |
| Ningún método público sin declarar en el contrato | Un método added por lasاهمas 4 es lo que se cuela |
| Los 28 valores de `TipoMensajeCliente` | Espejo del protocolo; perder uno rompe un comando |
| Los 4 de `EstadoConexion` **y sus textos** | Los textos van dentro del enum: si divergen, el usuario lo ve |
| Las 3 familias de excepción | `ExcepcionSesion` y `ExcepcionValidacion` **no** heredan de `ExcepcionCliente` |
| Las operaciones opcionales siguen siendo `default` | Si pasan a abstractas, rompen los dobles y el LSP |
| `client-interfaces/pom.xml` sin `<dependencies>` | Los puertos no pueden depender de framework |
| La tabla de backoff `{1,2,4,8,16,30}` | Python y .NET reconectan con esta cadencia |

**Tres cosas que el verificador tuvo que arreglar en sí mismo** (no en el código):

1. **Conteo de sobrecargas.** Comparar los métodos como conjunto plano hacía que
   `enviarTexto(String,String,String)` y `enviarTexto(String,String,String,String)` — dos
   métodos distintos — se contaran como el mismo, y el orden de los parámetros se perdía al
   ordenarlos. Ahora se comparan como multiconjunto y **en orden**.
2. **Cuerpos de los `default`.** El `guardar(u)` de dentro de
   `default void reemplazarDirectorio(...) { for (...) guardar(u); }` se contaba como un método
   más. Resuelto por profundidad de llaves: solo cuentan las declaraciones en el cuerpo de la
   interfaz.
3. **Prefijos de paquete.** `java.util.List<RecienteChat>` y `List<RecienteChat>` son el mismo
   tipo, pero no la misma cadena.

**Mutación probada:** borré `olvidarSesion` del JSON y el verificador lo detectó en las dos
interfaces donde existe, con exit 1. El verificador falla cuando debe.

**Verificación:** `contrato/verificar.py` → **8 pasos en verde**, exit 0. Python 121, JavaFX
182/182, .NET 79/79, WPF 0 errores en la verificación inicial de la fase.

**Con esto quedó lista la base contractual de la Fase 0** y se pudo iniciar la Fase 2: `puertos.json`
es la lista de trabajo, con la equivalencia exacta en Python y C# de cada puerto. El estado actual
de esa fase está resumido en §2.

Mientras el contrato esté escrito a mano tres veces, cualquier fase posterior reintroduce deriva.
Esta fase crea **una sola fuente de verdad** y genera el resto.

#### 0.1 Resolver el bloqueante de los adjuntos — **✅ hecho, falta el smoke** (ver §0.5)

Implementar en `ConexionCliente` + `FachadaCliente` el envío fragmentado que ya tienen Python y .NET:

1. `enviarArchivo(id, remitente, destino, nombre, mime, base64)` **deja** de enviar una sola trama.
2. Nuevo `enviarArchivoFragmentado(...)`: recorta en trozos de **1 MiB** de datos crudos (mismo
   tamaño que Python `_PARTE_MAX_BYTES`), emite `ARCHIVO_INICIO` → N × `ARCHIVO_PARTE` → `ARCHIVO_FIN`.
3. Calcular `hashSha256` del archivo completo y enviarlo en `ARCHIVO_INICIO`.
4. **Mantener** `enviarImagen` en trama única (una imagen cabe de sobra en 16 MiB) para no romper el
   camino que ya usa `GUIA_ACEPTACION.md`.
5. Tests: archivo de 12 MiB (falla hoy, debe pasar), 50 MiB exacto, y comprobación de que el
   servidor devuelve `ACK` con el mismo `archivoId`.
6. Ajustar `TextoCliente.TOPE_ADJUNTO_BYTES` al valor realmente soportado, o dejarlo en 50 MiB
   **solo cuando** el fragmentado esté implementado y probado.

**Criterio de salida:** un archivo de 20 MiB enviado desde Java llega en el otro cliente con sus 5
derivadas de filtro en `uploads/`, y el SHA-256 coincide.

#### 0.2 Tokens de diseño en un archivo único (3 j) — **✅ hecho** (ver §0.6)

La paleta vive **una vez** en `contrato/tokens-diseno.json`:

```json
{
  "version": 1,
  "tema": {
    "oscuro": {
      "root.bg":            "#0E1621",
      "sidebar.bg":         "#182533",
      "divider":            "#2A3B4D",
      "message.in.bg":      "#182533",
      "message.out.bg":     "#7B61B8",
      "message.text":       "#E8EEF2",
      "message.sender":     "#C4B5FD",
      "message.read":       "#5EB1FF",
      "message.unread":     "#A8B8C1",
      "send.button":        "#7B61B8",
      "send.button.hover":  "#6A4FA3",
      "input.bg":           "#243447",
      "input.focus.border": "#7B61B8",
      "unread.badge":       "#48A982",
      "offline.banner.bg":  "#FFFBEB",
      "offline.banner.fg":  "#B45309",
      "day.separator":      "#A8B8C1",
      "quote.bg":           "#243447",
      "quote.border":       "#A78BFA"
    },
    "claro": { "…": "…" }
  },
  "comunes": {
    "error.bg": "#FDECEA", "error.fg": "#B91C1C",
    "warn.bg":  "#FFFBEB", "warn.fg":  "#B45309",
    "radius": 12, "gap": 10, "font": "Segoe UI", "fontSize": 13
  }
}
```

**Generador** `contrato/generar_tokens.py` produce, desde ese archivo:

| Salida | Destino | Nota |
|---|---|---|
| `chat.css`, `chat-claro.css` | Java | conserva los selectores actuales |
| `login.css`, `login-claro.css` | Java | |
| `TemaOscuro.xaml`, `TemaClaro.xaml` | .NET | sustituye los 2 diccionarios a mano |
| `tema.qss` (o `tokens_tema.py`) | Python | **archivo nuevo**, cierra el hueco |

**Arreglo de paridad que entra aquí:** `chat-claro.css` **no** tiene `.message-out .message-time` ni
`.message-out .image-caption`, que sí están en `chat.css`. El generador elimina esa clase de bug.

El generador `contrato/generar_tokens.py` produce los 7 ficheros y **falla** (exit 1) si un
contraste baja de 4.5:1, si una plantilla usa un token inexistente o si un `.xaml` lleva un color
que WPF no entiende. `--check` verifica sin escribir, para CI.

#### 0.3 Máquina de estados del mensaje en un archivo único (1 j) — **✅ hecho** (ver §0.7)

Es la parte grave de `VIS-02`: la precedencia estaba escrita **5 veces** en 3 lenguajes con dos
mecanismos distintos, y añadir un estado en una sola copia hacía que ese cliente discrepara del
resto **sin que ninguna prueba fallara**.

`contrato/estado-mensaje.json` declara los 5 estados con su orden, tick, detalle y color, más las
5 reglas de precedencia. `contrato/verificar_estado_mensaje.py` extrae lo que hay escrito en cada
cliente y lo compara. **No se decide verificar — lo pendiente es el resto de los literales.**

De los ~73 conceptos que cataloga `DIAGRAMAS/textos-ui.md`, esta fase cubre los 5 estados del
mensaje (los que decides). Queda un paso menor, 1 j, para el resto de los literales si se quiere.

#### 0.4 Especificación normativa de los puertos (1 j) — **✅ hecho** (ver §0.8)

`contrato/puertos.json` es la especificación normativa y `verificar_puertos.py` comprueba que el
código Java la cumple. Cierra la Fase 0 y desbloquea la 2.

**No son 12 interfaces: son 23 puertos.** El recuento inicial solo miraba `client-interfaces`; hay
9 más repartidas por `client-app` y `presentacion`:

| Capa | Puertos | Cuántos |
|---|---|---|
| 1 · Adaptadores | `InterfazConexionRed`, `ReceptorMensajes`, `InterfazMapeadorDatosServidor`, `InterfazAlmacenLocal` | 4 |
| 2 · Datos | `InterfazHistorialLocal`, `InterfazCacheUsuarios`, `InterfazAutenticacion` | 3 |
| 3 · Casos de uso | `InterfazFachadaCliente`, `InterfazChatCliente`, `InterfazInicioSesion`, `InterfazArchivosConversacion`, `InterfazEventosCliente`, `InterfazCoordinadorMensajeria`, `InterfazCoordinadorHistorial`, `InterfazGestorReconexion`, `InterfazSincronizadorDirectorio`, `InterfazReintentadorPendientes`, `InterfazLectorHistorialRemoto` | 11 |
| 3 · Presentación | `InterfazChatViewModel`, `InterfazNavegacionCliente`, `InterfazGestorAdjuntos`, `InterfazFabricaAdjuntos`, `FlujoConversacion` | 5 |

**186 métodos declarados**, más 8 records, 2 enums y 6 excepciones.

---

### Fase 1 — Red y protocolo al 100 % (10 j)

| # | Tarea | Java | Python | .NET | j |
|---|---|---|---|---|---|
| 1.1 | Fragmentado de adjuntos | ✅ implementado; falta smoke | ✅ ya estaba | ✅ ya estaba | 2 |
| 1.2 | Flush periódico de cola 30 s | ✅ ya estaba | ✅ implementado | ✅ ya estaba | 1 |
| 1.3 | TLS *fail-fast* + almacén de confianza | ✅ implementado | ✅ implementado | ✅ implementado | 3 |
| 1.4 | Base aditiva de DTOs tipados | ✅ ya está | ✅ implementada | ✅ implementada | 2 |
| 1.5 | Constantes de tipos de trama centralizadas | ✅ enum | ✅ implementadas | ✅ enum | 0.5 |
| 1.6 | Test de paridad de framing (mismos bytes en los 3) | ✅ implementado | ✅ implementado | ✅ implementado | 1.5 |

**1.2 — flush de pendientes.** Python usa un worker daemon idempotente con `Event` cancelable,
iniciado al completar login o restaurar la sesión y detenido al desconectar. Los fallos se registran
y no terminan el hilo.

**1.3 — `SEG-01`.** Los tres clientes validan antes de abrir el socket: `tls.habilitado` debe ser
`true`/`false`, los puertos fijos no pueden colisionar y el almacén indicado debe existir. El valor
ausente/vacío mantiene TLS apagado o usa la confianza del sistema, según corresponda; ningún cliente
degrada TLS a texto claro. Java usa JKS y Python/.NET PEM, así que cada distribución requiere el
formato de almacén que admite su cliente.

**1.4 — DTO base.** Se añadieron los seis records indicados en el contrato y las seis excepciones
conservan sus tres familias. Es una entrega aditiva: las fachadas aún exponen los tipos previos;
reemplazarlos pertenece a la Fase 2.

**1.6 — framing.** `contrato/framing.json` contiene seis vectores byte a byte y cinco rechazos.
Las suites ejercitan el codec real en cada cliente; `multibyte_acentos` fija 53 caracteres y 56 bytes
UTF-8 para detectar cabeceras calculadas por caracteres.

**Verificación automatizada (2026-10-08):** `contrato/verificar.py` completó sus ocho pasos. JavaFX:
212/212; Python: 218 casos (213 correctos y 5 omitidos); .NET: 203/203; build WPF Release:
0 advertencias y 0 errores. La verificación combinada terminó en verde.

**Criterio de salida:** `GUIA_ACEPTACION.md` §"Matriz de interoperabilidad" pasa en las 6 direcciones,
incluido un adjunto de 20 MiB en las 6.

**Pendiente para cerrar la fase:** no se ejecutó el smoke de aceptación de extremo a extremo con el
servidor para la matriz de seis direcciones y los adjuntos de 20 MiB. Las suites validan los codecs,
la fragmentación contra dobles y el handshake TLS de .NET, pero no sustituyen esa matriz real.

---

### Fase 2 — Arquitectura por capas en Python y .NET (17 j)

Java ya está aquí (fases 3 y 4 de `PLAN_ARQUITECTURA_CLIENTE_JAVA.md`). El trabajo es replicar la
forma, no reescribirla.

#### 2.1 Python — capa de puertos (3 j)

Antes de esta fase, `client-python/` no tenía puertos formales: la vista saltaba la capa y llamaba
`fachada.historial` directamente (`chat_window.py:521`). Se formaliza la capa neutral:

```
puertos/            # espejo de client-interfaces, sin importar nada de Qt ni sqlite3
  contratos.py      #   RespuestaRed, TipoMensajeCliente (28 valores), EstadoConexion (4)
  datos.py          #   MensajeLocal, RecienteChat, PendienteEnvio, UsuarioLocal, EstadoMensaje
  excepciones.py    #   ExcepcionCliente + 4 submáquinas (3 familias, como Java)
  autenticacion.py  #   IAutenticacion
  cache.py          #   ICacheUsuarios
  conexion.py       #   IInterfazConexionRed + IReceptorMensajes   ← el más grande
  historial.py      #   IInterfazHistorialLocal (20 métodos del contrato)
  mapeador.py       #   IInterfazMapeadorDatos
  almacen.py        #   IInterfazAlmacenLocal
```

Regla dura, verificada por test: **`puertos/` no importa `PySide6`, `sqlite3` ni `json`** — igual que
el `pom.xml` de `client-interfaces` no tiene bloque `<dependencies>`.

**Estado 2026-10-08:** implementados los siete Protocols de las capas 1–2, los records neutrales
`RespuestaRed` y `EstadoMensaje`, y los enums `TipoMensajeCliente`/`EstadoConexion`. Los módulos
anteriores de `transversal/` conservan sus nombres públicos como fachadas de compatibilidad. El test
de arquitectura compara los métodos y campos con `contrato/puertos.json` y verifica que los módulos
de `puertos/` no dependan de UI, SQL ni JSON. El contrato fuente tiene **19** operaciones en
`InterfazHistorialLocal`; el contrato incluye 20 métodos, entre ellos la actualización de etapas
del filtrado de imágenes.

#### 2.2 Python — descomponer la fachada (2 j)

Antes de esta descomposición, `fachada/fachada_cliente.py` superaba las **700 líneas** y hacía
varias tareas. Se extraen los mismos 7 colaboradores que Java, con los mismos nombres:

| Collaborador de Java | LOC Java | Python | .NET |
|---|---|---|---|
| `CoordinadorMensajeriaCliente` | 165 | implementado | implementado |
| `LectorHistorialRemotoCliente` | 174 | implementado | crear |
| `GestorReconexionSesion` | 151 | implementado | crear |
| `ReintentadorPendientesCliente` | 128 | implementado | crear |
| `SincronizadorDirectorioCliente` | 106 | implementado | crear |
| `ConstructorRecientes` (puro) | 79 | implementado | crear |
| `PublicadorEventosCliente` | 69 | implementado | crear |
| `ReceptorEntranteCliente` | 189 | implementado | crear |

`FachadaCliente` queda en **≤ 250 líneas**: guarda de sesión, ciclo de vida y delegación.

**Estado 2026-10-08:** los ocho colaboradores Python están en `fachada/servicios/` y la fachada
quedó en **248 líneas**. `test_arquitectura.py` comprueba el límite y el suite Python cubre el
envío, historial, reintentos, reconexión, directorio y eventos. Falta replicar esta descomposición
en .NET.

#### 2.3 Python — descomponer la vista (3 j)

`ui/chat_window.py` tiene **1 759 líneas**: es el archivo más grande del repositorio. Se divide igual
que Java:

| Java | Python | .NET |
|---|---|---|
| `ChatController` (471) | `chat_window.py` → 183 líneas | `ChatWindow.xaml.cs` (1 149) → ≤ 400 |
| `ChatViewModel` (314) | `ui/chat_view_model.py` → parcial | pendiente; `UsuarioVM` y `MensajeVM` extraídos |
| `MensajeCell` (338) | `ui/celdas/mensaje_delegate.py` → extraído | ya existe `ResaltadoTextBlock.cs` |
| `UsuarioCell` (75) | `ui/celdas/usuario_cell.py` → extraído | `DataTemplate` de `ChatWindow.xaml` |
| `VisorImagenChat` (118) | `ui/visor_imagen_chat.py` → extraído | `ImagenViewerWindow` ya existe → **extraer** |
| `RefrescadorContactosChat` (325) | `ui/refrescador_contactos_chat.py` | crear |
| `PaginadorHistorialChat` (230) | `ui/paginador_historial_chat.py` | crear |
| `BusquedaHistorialChat` (217) | `ui/busqueda_historial_chat.py` | crear |
| `GestorAdjuntosChat` (209) | `ui/gestor_adjuntos_chat.py` | crear |
| `GestorSalidaSesionChat` (123) | `ui/gestor_salida_sesion_chat.py` | crear |
| `CargadorAdjuntoEntradaChat` (150) | `ui/cargador_adjunto_entrada_chat.py` → extraído | crear |
| `EnviarMensajeComando` (72) | pendiente; envío en `FlujoConversacionChat` | crear |
| `MiniaturasChat` (65) | `ui/miniaturas_chat.py` → extraído | crear |
| `EnviarMensajeComando` (72) | `ui/enviar_mensaje_comando.py` → extraído | crear |

**Estado 2026-10-08:** `chat_window.py` bajó a **183 líneas**. La construcción de UI, el visor,
los controles/señales, paginación, búsqueda, refresco de contactos, adjuntos, tareas, eventos,
flujo conversacional, cierre, renderizado de texto y delegado de mensajes se distribuyeron en
módulos de `ui/`; `chat_window.py` conserva las clases mixin y los nombres de importación previos
donde ya eran usados. `ChatViewModel` mantiene el estado de búsqueda y navegación pendiente sin
depender de Qt. La suite de UI (27 casos) y las pruebas del ViewModel (3 casos) pasan.

La fase sigue parcial: falta completar el ViewModel y la descomposición equivalente en .NET.
`MiniaturasChat` encapsula la caché acotada y el escalado de imágenes; `EnviarMensajeComando`
coordina el envío de texto y adjunto fuera del flujo de UI; `CargadorAdjuntoEntradaChat` resuelve
caché/base64/transporte sin depender de la vista, y `UsuarioCell` construye la fila con roles
estables para filtrar los no leídos. En .NET `UsuarioVM` y `MensajeVM` viven en `ViewModels/`, y el
code-behind sigue en 1.149 líneas.

#### 2.4 .NET — capa de puertos y composición (2 j)

Antes del trabajo de esta fase, `client-dotnet/` tenía **4 interfaces** (`IConexionRed`,
`IAutenticacion`, `IHistorialLocal`, `IFachadaCliente`); Java tiene 37. Se completa la capa con las que faltan
(`IInterfazCacheUsuarios`, `IInterfazMapeadorDatos`, `IInterfazAlmacenLocal`, `IInterfazChatViewModel`,
`IReceptorMensajes`, `InterfazGestorAdjuntos`, …), y se añade un **composition root** explícito
(`Composicion/FabricaCliente.cs`) equivalente a `FabricaCliente.java`: antes la construcción estaba
esparcida entre `App.xaml.cs` y el propio code-behind.

**Estado 2026-10-08:** añadidos `IMapeadorDatos`, `IReceptorMensajes`, `RespuestaRed`,
`TipoMensajeCliente` y `Composicion/FabricaCliente`; la conexión y la fachada ya entregan eventos al
receptor tipado mediante un adaptador que conserva compatibilidad con la UI actual. También se
extrajo `CoordinadorMensajeriaCliente` para texto, imagen y archivo; el proveedor del historial
resuelve el store vigente después del cambio de cuenta y los reintentos conservan la fragmentación.
xUnit pasó **203/203** y el build Release de WPF terminó sin advertencias ni errores. Sigue pendiente
migrar la UI a `RespuestaRed`, completar puertos/colaboradores y mover la composición de
red/almacenamiento fuera de `FachadaCliente`.

#### 2.5 Tests de arquitectura en los 3 (5 j)

Equivalente a las 6 reglas ArchUnit de `ArquitecturaClienteJavaTest.java`:

| Regla | pytest | xUnit |
|---|---|---|
| `puertos/` no importa framework, DTO ni SQL | `test_arquitectura.py::test_puertos_sin_dependencias` | `ArquitecturaTests.cs::PuertosSinDependencias` |
| Presentación no toca `sqlite3` ni el socket | `test_arquitectura.py::test_presentacion_no_importa_socket_ni_persistencia` | `…::PresentacionAislada` |
| Fachada no ve DTO de protocolo | `…::test_fachada_no_importa_codec_ni_dto_de_protocolo` | `…::FachadaSinProtocolo` |
| Ninguna vista importa la fachada concreta | `…::test_vistas_no_importan_la_fachada_concreta` | `…::VistaUsaPuerto` |
| Los 3 estados de `EstadoMensaje` comparten precedencia | `test_estado_mensaje.py` | `EstadoMensajeTests.cs` |
| Ningún archivo de capa 3 supera 350 líneas | `…::test_archivos_de_presentacion_y_fachada_no_superan_350_lineas` | `…::TamanioArchivos` |

**Criterio de salida:** `chat_window.py`, `ChatWindow.xaml.cs` y `fachada_cliente.py` /
`FachadaCliente.cs` por debajo de 400 líneas; los tres `puertos/` pasan los 6 tests.

**Estado 2026-10-08:** Python comprueba fronteras de presentación, fachada, vista y ViewModel,
y exige que sus archivos de `ui/` y `fachada/` no superen 350 líneas. Las 33 pruebas de
`test_arquitectura.py` pasan; la suite completa pasó **218 casos: 213 correctos y 5 omitidos**.
El verificador común terminó en verde (tokens, estado, contrato, JavaFX, Python, build WPF y suite
.NET). .NET tiene cuatro reglas xUnit nuevas y **203/203** pruebas; su build Release quedó con 0
advertencias y 0 errores. No se añadió todavía la regla .NET de tamaño porque `ChatWindow.xaml.cs`
(1.149 líneas) y `FachadaCliente.cs` (798) aún exceden el objetivo. La fase de arquitectura no está
cerrada en los tres clientes.

---

### Fase 3 — Paridad funcional (10 j)

Se cierra la matriz de las **66 capacidades** inventariadas en el cliente JavaFX.

#### 3.1 Cerrar la matriz de funcionalidades (6 j)

Estas diferencias conocidas de Python y .NET se completaron:

| # | Capacidad | Python | .NET | j |
|---|---|---|---|---|
| 1 | **Fondo de chat con imagen** (`cover`, detrás de burbujas opacas) | ✅ | ✅ | 2 |
| 2 | Sidebar redimensionable y persistido (UIX-11) | ✅ | ✅ | 1 |
| 3 | Flush periódico de pendientes (reiterado de 1.2) | ✅ | ✅ | 0.5 |
| 4 | Foco automático en el composer al responder | ✅ | ✅ | 0.5 |
| 5 | Recordar el último chat por cuenta | ✅ | ✅ | 0.5 |
| 6 | Anclaje de scroll al precargar página antigua (UIX-16) | ✅ | ✅ | 1 |
| 7 | `etapasFiltrado` visible y persistido | ✅ | ✅ | 0.5 |

Para el punto 1 se **copian los JPG** a `client-python/ui/fondos/` y `client-dotnet/Temas/fondos/` —
no se regeneran, para que la paridad sea de píxel. Python escala/corta el fondo al viewport; WPF lo
escala con `UniformToFill` detrás de la lista transparente, porque el template del `ListBox` podía
tapar un fondo aplicado directamente a la lista.

**Estado 2026-10-08:** al probar el entregable se detectó que `dist/MensajeriaPython` era una copia
del 6 de octubre y no traía los JPG, aunque el cliente fuente sí los cargaba. Se regeneró con
`distribuir.cmd`; el script ahora exige ambos fondos y la copia regenerada carga los dos. La prueba
`test_fondo_chat.py` renderiza el viewport en ambos temas y comprueba que no queda plano. En WPF, el
fondo se movió al `Grid` detrás de `lstMensajes`, que queda transparente; se elevó la opacidad a
0.62. `FondoChatWpfTests` comprueba la capa transparente y ambos JPG; el build Release pasó y la
publicación incluyó las imágenes en `Temas/fondos/`. También se encontró que el `.exe` de la carpeta
publicada anterior seguía siendo viejo aunque los JPG estuvieran allí; se regeneró
`dist/MensajeriaWPF` con el arreglo incluido.

Python y WPF conservan el anclaje del mensaje al anteponer páginas antiguas; ambos guardan el último
chat por cuenta. Java conserva esa selección y ahora persiste el ancho del sidebar por cuenta.
`etapasFiltrado` se almacena al enviar, recibir y sincronizar historial, y los tres clientes muestran
la misma leyenda en el orden recibido (por ejemplo, `Filtros: Grises (12 ms) → Sepia (11 ms)`),
normalizan los nombres de archivo y omiten rutas. La fila 4 ya tenía foco en el composer en los tres
clientes y ahora Python lo comprueba; el flush periódico de pendientes ya estaba implementado en
los tres.

Con la última verificación combinada (212 pruebas JavaFX, 222 Python con 5 omitidas y 205 xUnit;
build WPF sin advertencias ni errores), las siete diferencias de esta tabla están cerradas.
La Fase 3 sigue abierta por §3.3.

#### 3.2 Estado de mensaje: una sola fuente (2 j) — ✅ verificado

La fuente canónica es `contrato/estado-mensaje.json`, según `AGENTS.md` §5. La precedencia
`PENDIENTE → ENVIADO → ENTREGADO → LEIDO` + `ERROR` se comprueba contra las implementaciones de los
tres clientes con `contrato/verificar_estado_mensaje.py`; las suites también verifican que `LEIDO`
no retrocede, que `ENTREGADO` y `ENVIADO` conservan su orden y que `ERROR` es terminal. La última
verificación combinada pasó estos controles. Se conserva el `CASE` específico de cada base local y
el JSON como fuente normativa única.

#### 3.3 Accesibilidad y microcopia (2 j)

- Nombres accesibles: JavaFX ya expone 12 controles; Python usa `setAccessibleName` en las acciones
  equivalentes y WPF `AutomationProperties.Name`. La prueba de UI Python comprueba los nombres de
  las acciones de chat y login.
- Banners: cada cliente aún tiene un mecanismo distinto (`etiquetaAviso` en Java,
  `lbl_error/lbl_aviso` en Python, `bordeAvisoInline` en WPF). WPF y ahora los avisos de login usan
  `LiveSetting="Polite"`; falta unificar el banner inline con severidad (Información / Éxito / Error)
  y anunciar cambios dinámicos en Java y Python.
- `PROD-04`: `idioma=es` sigue en los 3 `client.properties`. Java lo carga y lo imprime en el
  diagnóstico de arranque; los clientes no cambian de idioma. Decisión: dejar la clave informativa
  y no implementar i18n, que el enunciado no pide.

**Estado 2026-10-08:** se completaron nombres accesibles para las acciones de búsqueda, adjuntos,
respuesta y login en las tres interfaces; WPF tiene regiones de aviso corteses. Falta unificar la
presentación y anuncio de banners entre JavaFX, Python y WPF. La Fase 3 permanece abierta hasta cerrar
esa tarea.

**Criterio de salida:** las 66 capacidades de JavaFX funcionan en los 3; ninguna regresión en la
matriz de 6 direcciones.

---

### Fase 4 — Paridad visual con capturas (11 j) · el requisito de los PNG

Esta fase convierte "que se parezca" en algo **medible**. El objetivo no es "parecido": es que la
diferencia de píxel entre renders equivalentes esté por debajo de un umbral acordado.

#### 4.1 Guion de capturas (1 j)

`contrato/guion-capturas.md` fija **12 escenarios**, cada uno en **2 temas**, en **3 clientes** =
**72 capturas**, más las de estados que solo aparecen por acción = **~90 PNG**.

| # | Escenario | Qué demuestra |
|---|---|---|
| K1 | Login vacío | tarjetas, campo código, botón |
| K2 | Login con error `✖` | banner de error |
| K3 | Login con aviso `⏳` | banner ámbar |
| K4 | Login conectando | spinner, botón deshabilitado |
| K5 | Chat sin contactos | estado vacío `Sin contactos todavía.` |
| K6 | Chat con búsqueda sin resultados | `Sin resultados para esta búsqueda.` |
| K7 | Conversación corta | burbujas propia/derecha y recibida/izquierda |
| K8 | Conversación larga (100+ mensajes) | agrupado, cabecera de día, scroll |
| K9 | Mensaje con imagen | miniatura, caption, estados |
| K10 | Mensaje con archivo | `📎`, bytes, botón `⬇ Descargar` |
| K11 | Cita activa | barra `Respondiendo a`, `↩` en la burbuja |
| K12 | Banner offline | ámbar `⛔ Sin conexión…` |

#### 4.2 Automatizar la captura (4 j)

No se captura a mano: se agrega un **modo de captura** a cada cliente, determinista y sin captura de
pantalla del SO.

| Cliente | Mecanismo | j |
|---|---|---|
| JavaFX | Flag `--capturar=DIR` en `ClienteMain`. Monta cada `Scene` con `Monocle` headless y llama a `node.snapshot(null, null)`. Se benefician las 6 pruebas de carga de FXML que ya existen | 1.5 |
| Python | El MCP `pyside6`/`pyside6-mcp` **ya instalado y verificado** (lanzar → captura → parar). O bien `widget.grab().save(...)` en modo `--capturar` | 1 |
| .NET | El MCP `netwright` **ya instalado y verificado** (lanzar + captura). O bien `RenderTargetBitmap` sobre un `VisualBrush` del `Window` | 1.5 |

Un `capturar.cmd` y un `capturar.ps1` en la raíz lanzan los 3 y dejan los PNG en
`DIAGRAMAS/capturas/<escenario>/<tema>/<cliente>.png`.

> **Nota de `AGENTS.md` §7:** un `.mmd` sin PNG no cuenta. Lo mismo aplica aquí — un PNG sin
> escenario declarado en el guion no cuenta como evidencia.

#### 4.3 Comparación automática (4 j)

`contrato/comparar_capturas.py`:

1. Alinea por tamaño; si difiere, **red de primero** (falta un selector CSS).
2. Calcula **diferencia pixel a pixel**; classifies por zona (burbuja, sidebar, composer, banner).
3. Muestrea el color del pixel donde debería estar cada token y lo compara con
   `tokens-diseno.json` → **esta comprobación es la que detecta que los tokens no se regeneraron**.
4. Calcula la ratio de contraste de los 4 puntos que exige `GUIA_ACEPTACION.md` §SMOKE-07
   (`.chat-list-preview` en claro, placeholder de búsqueda, fecha de burbuja) → todos ≥ 4.5:1.
5. Salida: `DIAGRAMAS/capturas/informe.html` con las 3 columnas y el delta por zona.

**Umbral acordado:** ≤ 2 % de píxeles distintos por escenario en cada tema, y 0 tokens fuera de
paleta. Lo que se salga del umbral se registra como tarea, no se explica.

#### 4.4 Actualizar mockups (2 j)

Los `.excalidraw` actuales (`mockup-chat-telegram-{java,python,dotnet}.png`) son de Telegram y
anteriores a varias features. Se **reemplazan por los PNG reales** de este guion y se regenera el
`.excalidraw` desde ellos. Cierra `DIAG-09` de `ESTADO_ROADMAP.md` §8.

**Criterio de salida:** `DIAGRAMAS/capturas/` con ~90 PNG, `informe.html` en verde, y las 4 capturas
canónicas (login, chat oscuro, chat claro, visor) colgando de `DIAGRAMAS/README.md`.

---

### Fase 5 — Paridad de pruebas (9 j)

| Suite | Actual | Objetivo | j |
|---|---|---|---|
| JavaFX | 174 casos | 174 | — |
| Python | 105 tests / 2 055 líneas | ≥ 174 | 4 |
| .NET | 79 casos / 1 424 líneas | ≥ 174 | 3 |
| Contrato compartido | — | ~40 (tokens, literales, estado, framing) | 2 |

**Tests que se agregan y hoy no existen en ningún cliente:**

- `test_contrato_tokens.py` / `TokensTests.cs` — el cliente aplica **todos** los tokens.
- `test_contrato_textos.py` / `TextosTests.cs` — los literales coinciden con `textos-ui.json`.
- `test_estado_mensaje.py` / `EstadoMensajeTests.cs` — precedencia idéntica a la de Java.
- `test_framing_paridad.py` / `TramaCodecTests.cs` — el mismo vector byte a byte.
- `test_arquitectura.py` / `ArquitecturaTests.cs` — las 6 reglas de la §2.5.
- `test_fondo_chat.py` / `FondoChatTests.cs` — el fondo se carga y no tapa el texto.

**Arreglo de tooling:** `client-dotnet.Tests` apunta a `net10.0` mientras la app apunta a
`net8.0-windows`. Mientras no se unifique, un test puede pasar en el TFM equivocado y no probar nada.
Unificar a `net8.0-windows` en la Fase 0.

---

### Fase 6 — Empaquetado y documentación (5 j)

1. Los 3 `distribuir*.cmd` / `dotnet publish` producen paquetes con los **mismos assets**: fondos,
   `schema-local.sql`, `client.properties`.
2. Un `distribuir-todos.cmd` que produce las 4 distribuciones (servidor + 3 clientes).
3. Actualizar `AGENTS.md` §2 y §6.4, `PLAN.md`, `ESTADO_ROADMAP.md` §6 y §7, `PLAN_PATRONES_UI.md`,
   `ANALISIS_TECNICO.md`, `DESPLIEGUE.md`, `GUIA_ACEPTACION.md`, `DIAGRAMAS/README.md`.
4. Los 3 `README.md` de cliente con la misma estructura de secciones.
5. Marcar `VIS-01`, `VIS-02`, `SEG-01`, `SEG-02`, `PAT-01`, `PAT-04`, `PAT-05`, `PAT-VM`, `PAT-CMD`,
   `PROD-03`, `PROD-04` según lo que este plan cierre.

---

## 3. Orden de ataque recomendado

```
Fase 0 ──────────────► Fase 1 ──────────────► Fase 2 ──────────────► Fase 3 ──────────────► Fase 4 ──► Fase 5 ──► Fase 6
    │                      │                      │                      │
    │ tokens/literales      │ fragmentado Java      │ puertos en Py y .NET  │ fondo de chat      │ 90 PNG
    │ una fuente de verdad  │ TLS igual en los 3    │ fachada ≤250 líneas    │ fondo en Py y .NET  │ umbral 2 %
    │ bloquea a todos       │ DTO tipados           │ 6 tests de arquitectura│ fondo en Py y .NET  │ contraste 4.5:1
    │                      │ bloquea a fase 2      │ bloquea a fase 3       │ banner inline
    ▼                      ▼                      ▼                      ▼
  (7 j)                  (10 j)                 (17 j)                 (10 j)              (11 j)    (9 j)   (5 j)
```

**Puntos de decisión para el usuario antes de empezar:**

| Decisión | Recomendación | Impacto si cambia |
|---|---|---|
| ¿Arreglar Java o degradar Python/.NET a trama única? | **Arreglar Java** | Perderíamos fragmentado, SHA-256 y archivos >12 MiB. El invariante 2 del repo lo prohíbe |
| ¿Generar el diseño o copiar el CSS a mano? | **Generar** | La copia manual reintroduce `chat-claro.css` incompleto y la deriva de `VIS-01` |
| ¿Umbral de paridad visual? | **2 % de píxeles + 0 tokens fuera de paleta** | Sin umbral medible, "se parece" no se puede aceptar ni rechazar |
| ¿`idioma=es`? | Conservarla como dato informativo, sin i18n | El idioma no altera la UI; borrarla exige cambiar el formato compartido |
| ¿Admin e informes en los clientes? | **No** | Están en `servidor-app`. Copiarlos multiplica el trabajo ×3 |

---

## 4. Riesgos y controles

| Riesgo | Prob. | Impacto | Control |
|---|---|---|---|
| Se replica el defecto de adjuntos de Java | Alta | Alto | Bloqueo por diseño: Fase 0.1 antes que nada |
| La deriva visual vuelve tras cada cambio de toolkit | Alta | Medio | Tokens generados + comparación de píxel en CI |
| Cambiar el protocolo rompe a un tercer cliente | Media | Muy alto | Test de framing con vector idéntico en los 3 + matriz de 6 direcciones |
| El CSS de JavaFX tiene semántica que Qt/WPF no expresan | Media | Medio | Catálogo de los 929 LOC de CSS: cada selector va a un equivalente o se documenta como excepción |
| Refactor grande en 2 clientes a la vez | Media | Alto | Una fase y un lenguaje por entrega; nunca los 3 en el mismo commit |
| Los 6 626 LOC de Java se copian sin entender | Media | Alto | Fase 2 empieza por los puertos, que es donde está el contrato |
| Las capturas se desactualizan | Media | Medio | El guion declara el escenario; PNG sin escenario no cuenta |
| Effort estimado se va al doble | Media | Medio | La Fase 0 es barata y preventiva: cristaliza el contrato antes de gastar 50 j |

---

## 5. Definición de "hecho"

El objetivo del proyecto se cumple cuando **todo** lo siguiente es cierto:

1. Los 3 clientes **conectan al mismo servidor** y pasan la matriz de 6 direcciones
   (`GUIA_ACEPTACION.md`), **incluida una imagen y un archivo de 20 MiB**.
2. Las **66 capacidades** del cliente JavaFX funcionan en los 3.
3. Los **~90 PNG** de `DIAGRAMAS/capturas/` cumplen el umbral: ≤ 2 % de píxeles distintos por
   escenario en cada tema, 0 tokens fuera de `tokens-diseno.json`, 4 contrastes ≥ 4.5:1.
4. Ningún archivo de capa 3 supera **350 líneas** en los 3 lenguajes; la fachada ≤ **250**.
5. Los **6 tests de arquitectura** pasan en los 3.
6. Ninguna vista importa la base de datos, el socket ni un DTO de protocolo.
7. Las 3 suites tienen **≥ 174 casos** cada una y están en verde.
8. Un literal de UI cambiado en `textos-ui.json` aparece en los 3 clientes; un color cambiado en
   `tokens-diseno.json` aparece en los 3.
9. `PLAN_ARQUITECTURA_CLIENTE_JAVA.md` pasa a "completo" con el smoke visual cerrado.

---

## 6. Lo que este plan NO hace

| No se hace | Motivo |
|---|---|
| Tocar `servidor-app` | Ninguna fase lo requiere: el fragmentado, los tokens y los literales se resuelven en los clientes. El servidor entra solo en la compilación de aceptación |
| Añadir vistas que el cliente JavaFX no tiene | La referencia es JavaFX. Si una pantalla no está en `client/`, no entra: el objetivo es paridad entre clientes, no alcance nuevo |
| Mover JavaFX a otro toolkit | El objetivo es llevar PySide6 y WPF al nivel de JavaFX, no migrar el cliente principal |
| Reescribir `common-protocol` | Invariante 1: el protocolo es neutro de lenguaje y ya es compartido. Solo se usa |
