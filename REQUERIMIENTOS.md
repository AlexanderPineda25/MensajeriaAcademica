# Especificación de Requerimientos
## Aplicación de mensajería para la comunidad académica de la Universidad

---

## 1. Introducción

### 1.1 Propósito
Este documento define los requerimientos funcionales y no funcionales de una aplicación cliente/servidor que permite enviar y recibir mensajes (texto o imágenes) entre los miembros de la comunidad académica de la Universidad.

### 1.2 Alcance
El sistema se compone de dos aplicaciones:

- **Servidor**: valida usuarios, gestiona conexiones, procesa y almacena mensajes, registra logs y genera informes.
- **Cliente**: permite a cada usuario autenticarse, ver los usuarios conectados e intercambiar mensajes con un usuario específico.

### 1.3 Convención de prioridades

| Etiqueta | Significado |
|---|---|
| **Obligatorio** | Requerimiento del enunciado original o decisión ya tomada. Debe cumplirse. |
| **Ajuste** | Decisión de diseño que precisa o modifica el enunciado. Debe cumplirse. |
| **Extra** | Valor agregado opcional. Se implementa si hay tiempo, sin comprometer lo obligatorio. |

### 1.4 Definiciones y acrónimos

| Término | Descripción |
|---|---|
| **Usuario** | Miembro de la comunidad académica registrado en el sistema. |
| **Mensaje** | Unidad de información enviada entre usuarios; puede ser de tipo *texto* o *imagen*. |
| **Log** | Registro de eventos o de mensajes enviados entre usuarios. |
| **SHA-256** | Función hash criptográfica utilizada para obtener la huella de un mensaje. |
| **DTO** | *Data Transfer Object*, objeto para transportar datos entre capas. |
| **TCP/IP** | Protocolo de comunicación entre cliente y servidor. |
| **H2** | Base de datos relacional embebida utilizada por el cliente. |
| **Object Pool** | Patrón que mantiene un conjunto de objetos reutilizables (aquí, hilos/manejadores de conexión) que se prestan y se devuelven en lugar de crearse y destruirse. |
| **Sesión** | Conexión activa de un usuario con el servidor. Un usuario puede tener varias. |

---

## 2. Descripción general

### 2.1 Usuarios del sistema
- **Usuario (cliente)**: se autentica, consulta usuarios conectados y envía/recibe mensajes. **No puede registrarse desde el cliente.**
- **Administrador (servidor)**: opera el servidor, envía mensajes a todos, cierra conexiones, consulta informes y mantiene el archivo plano de usuarios.

### 2.2 Restricciones generales
- Las aplicaciones cliente y servidor pueden ser **aplicaciones de escritorio**.
- Deben construirse con **capas y componentes**.
- Los clientes pueden programarse en **lenguajes distintos de Java**.
- La comunicación cliente/servidor se realiza mediante **TCP/IP**.
- El servidor debe seguir la **arquitectura de paquetes y componentes** descrita en la sección 5.

---

## 3. Requerimientos funcionales del servidor

### 3.1 Gestión de usuarios

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S01** | Cada usuario debe tener registrados: **código, nombres, apellidos, contraseña y programa académico**. | Obligatorio |
| **RF-S02** | El servidor debe validar a los usuarios por **código y contraseña**. | Obligatorio |
| **RF-S03** | Los usuarios están registrados en un **archivo plano**, que es la **única fuente de registro**. El alta, la baja y la modificación de usuarios se hacen editando ese archivo; el servidor lo carga al iniciar. | Ajuste |
| **RF-S04** | Un mismo usuario **puede conectarse más de una vez** (múltiples sesiones simultáneas). Los mensajes y difusiones se distribuyen a todas sus sesiones activas. | Obligatorio |
| **RF-S27** | El servidor **no debe exponer ninguna operación de registro** a los clientes. Un intento de registro por red se rechaza con un mensaje de error explícito. | Ajuste |
| **RF-S28** | Las contraseñas del archivo plano se almacenan como **hash** (no en texto claro) y la validación compara hashes. | Extra |
| **RF-S34** | **Toda trama que requiera sesión autenticada debe rechazarse con `ERROR` cuando llega de una sesión anónima.** La comprobación es responsabilidad de cada comando, porque el despachador no filtra. Añadido 2026-10-05 tras el hallazgo `SEC-05`. | **Obligatorio** |
| **RF-S35** | Ningún comando puede declarar en su firma una capacidad de respuesta (`SalidaRespuestas`) que no use: la firma debe reflejar el cuerpo. Añadido 2026-10-05 (`SEC-06`). | Ajuste |

### 3.2 Gestión de conexiones

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S05** | El servidor debe **restringir el número de usuarios conectados** a la red; el límite se define en un **archivo de configuración**. | Obligatorio |
| **RF-S06** | El servidor debe permitir **cerrar las conexiones** de los usuarios. | Obligatorio |
| **RF-S29** | El servidor cierra automáticamente toda sesión **inactiva durante 10 minutos**. Se considera actividad cualquier trama recibida del cliente (mensaje, solicitud de lista de usuarios, señal de vida). El valor por defecto es **10 minutos** y se define en el archivo de configuración. | Ajuste |
| **RF-S30** | Antes de cerrar por inactividad, el servidor **notifica al cliente** el motivo del cierre; el cierre queda registrado en el log. | Ajuste |
| **RF-S31** | El cliente puede cerrar su sesión de forma voluntaria (*logout*) y el servidor libera los recursos asociados. | Extra |
| **RF-S32** | Se limita el número de **sesiones simultáneas por usuario**, parametrizable en el archivo de configuración. | Extra |

### 3.3 Mensajería

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S07** | El servidor debe recibir mensajes de tipo **texto** o **imagen**. | Obligatorio |
| **RF-S08** | El servidor debe permitir **enviar mensajes a todos los usuarios** conectados. | Obligatorio |
| **RF-S09** | Todos los mensajes deben **almacenarse en una o más estructuras de datos**, y desde allí se debe consultar su información. | Obligatorio |
| **RF-S10** | El servidor debe **mostrar por consola** la información de cada mensaje que llega (ver sección 3.7 para el formato). | Obligatorio |

### 3.4 Procesamiento de mensajes

**Mensajes de texto**

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S11** | Obtener el **hash SHA-256** del texto. | Obligatorio |
| **RF-S12** | **Contar los caracteres** del texto. | Obligatorio |
| **RF-S13** | **Contar las palabras** del texto. | Obligatorio |

**Mensajes de imagen**

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S14** | Obtener el **hash SHA-256** de la imagen. | Obligatorio |
| **RF-S15** | Aplicar los siguientes filtros: **escala de grises, sepia, giro de 180 grados, brillo y reducción de tamaño**. | Obligatorio |
| **RF-S16** | Cada filtro genera una **nueva imagen**, que debe **almacenarse**. | Obligatorio |
| **RF-S17** | Los filtros se ejecutan **secuencialmente**. | Obligatorio |
| **RF-S33** | El procesamiento de texto e imagen se implementa como una **tubería de filtros (*Pipes & Filters*)**, con etapas independientes y reutilizables. | Extra |

### 3.5 Persistencia y logs

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S18** | El servidor debe **registrar logs** de los mensajes enviados entre los usuarios. | Obligatorio |
| **RF-S19** | Los logs deben almacenarse en una **base de datos relacional**. | Obligatorio |
| **RF-S20** | El servidor debe guardar cada imagen aceptada y sus cinco derivadas en `uploads/<Nombre Apellido [CODIGO]>/<SHA-256>/`; el segmento del usuario se sanea y las rutas históricas siguen siendo descargables. | Obligatorio |
| **RF-S21** | La información se almacenará en una **base de datos relacional (MySQL)**. | Obligatorio |
| **RF-S22** | La conexión a la base de datos debe configurarse mediante **archivos de configuración**. | Obligatorio |

### 3.6 Informes

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S23** | Informe de **usuarios registrados**. | Obligatorio |
| **RF-S24** | Informe de usuarios con accesos acumulados (un incremento por cada **login exitoso**) y sesiones vivas. Las acciones de chat no incrementan accesos; presencia significa una o más sesiones activas y se apaga al cerrar la última. | Obligatorio |
| **RF-S25** | Informe con el **listado de mensajes enviados**, con el **detalle de cada mensaje**. | Obligatorio |
| **RF-S26** | Informe de **logs generados**. | Obligatorio |

### 3.7 Interfaz del servidor: consola y vista de escritorio

**Mejora de la consola**

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S34** | La consola presenta una **interfaz mejorada y legible**: encabezado con nombre, puerto y estado del servidor; **menú numerado** de opciones; separadores visuales entre secciones. | Ajuste |
| **RF-S35** | Los informes y listados se muestran en **tablas alineadas por columnas** (con paginación o truncado de campos largos). | Ajuste |
| **RF-S36** | Cada mensaje entrante se muestra en un **bloque con formato uniforme**: fecha y hora, remitente, destinatario, tipo (texto/imagen), hash SHA-256 y resultado del procesamiento (conteo de caracteres/palabras, o rutas de las imágenes generadas). | Ajuste |
| **RF-S37** | Los eventos se distinguen por **categoría y color** (información, advertencia, error, conexión, desconexión) cuando la terminal soporte colores ANSI; si no, se usan etiquetas de texto (`[INFO]`, `[WARN]`, `[ERROR]`). | Ajuste |
| **RF-S38** | La consola muestra un **resumen de estado** (usuarios conectados / límite, sesiones activas, mensajes procesados) y valida las entradas del administrador mostrando mensajes de error claros. | Ajuste |

**Refresco automático de las vistas**

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S39** | Las **vistas del servidor (consola y escritorio)** se actualizan **automáticamente** cuando ocurre un evento: conexión o desconexión de un usuario, llegada de un mensaje, cierre por inactividad, nuevo log. | Ajuste |
| **RF-S40** | El refresco automático **no depende de que el administrador pulse "refrescar"**. Si se conserva ese botón u opción, es solo un complemento. | Ajuste |
| **RF-S41** | En la consola, los eventos nuevos se **imprimen en el momento** en que ocurren sin interrumpir ni corromper el menú o la línea de entrada del administrador. Las listas e informes abiertos (usuarios conectados, mensajes, logs) se redibujan al cambiar los datos o a intervalos configurables. | Ajuste |
| **RF-S42** | El mecanismo de notificación a las vistas se implementa con el patrón **Observador (*Observer*)**: las vistas se suscriben a eventos publicados por la lógica del servidor, sin que esta dependa de aquellas. Las actualizaciones de la interfaz gráfica se ejecutan en su hilo de UI. | Ajuste |

### 3.8 Concurrencia: Object Pool

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S43** | El servidor aplica el patrón **Object Pool** para gestionar los **múltiples hilos** que atienden a los clientes: existe un conjunto de objetos trabajadores (hilos/manejadores de sesión) **preinstanciados y reutilizables**. | Ajuste |
| **RF-S44** | Al aceptar una conexión, el servidor **toma (*acquire*) un trabajador del pool**; al cerrarse la sesión, lo **devuelve (*release*)** al pool, reiniciando su estado. No se crea un hilo nuevo por cada conexión. | Ajuste |
| **RF-S45** | El **tamaño del pool** se define en el archivo de configuración y se **alinea con el límite máximo de usuarios conectados** (RF-S05). | Ajuste |
| **RF-S46** | Cuando el pool está agotado, el servidor **rechaza la conexión** con un mensaje explícito de "servidor lleno", o la encola con un tiempo máximo de espera configurable. El evento se registra en el log. | Ajuste |
| **RF-S47** | El pool es **seguro para concurrencia** (adquisición y devolución *thread-safe*), no pierde trabajadores ante errores o desconexiones abruptas y se **cierra ordenadamente** al detener el servidor. | Ajuste |
| **RF-S48** | El pool se expone mediante una **interfaz** (por ejemplo `PoolDeConexiones`) y se crea mediante una **fábrica**, cumpliendo los principios de inversión de dependencias e inyección de dependencias. | Ajuste |
| **RF-S49** | La consola y la vista de escritorio muestran el **estado del pool** (trabajadores en uso / disponibles / tamaño total). | Extra |

### 3.9 Usabilidad de la vista administrativa del servidor (2026-10-06)

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-S50** | La vista administrativa organiza los indicadores de Estado en una lista vertical adaptable, sin solapamiento al cambiar el tamaño de ventana. | Ajuste |
| **RF-S51** | La pestaña Conectados ofrece la acción `Desconectar usuario` y un selector que contiene los códigos con sesiones activas; cuando la lista está vacía muestra `No hay usuarios activos` y deshabilita la acción. | Ajuste |
| **RF-S52** | Al hacer doble clic en un usuario se abre una ficha reutilizable con secciones Resumen, Conexiones, Mensajes y Archivos. Incluye totales de mensajes/archivos, conexiones registradas, sesiones activas y contacto principal; mensajes y archivos se consultan en páginas de 50. El contacto principal cuenta mensajes enviados y recibidos y excluye `SERVIDOR`. | Ajuste |
| **RF-S53** | La pestaña Mensajes permite ingresar un código o elegirlo de una lista; muestra caracteres y palabras en filas separadas, la IP histórica del remitente y las IP de sesiones activas del destinatario. Si no existe sesión activa para el destinatario muestra `No disponible`; no se persiste una IP destinataria histórica. | Ajuste |
| **RF-S54** | El panel de contenido/imágenes de Informes solo aparece al consultar el informe de mensajes. | Ajuste |
| **RF-S55** | Logs usa el espacio disponible de la pestaña. Pool presenta sus métricas en secciones visuales claras sin cambiar sus valores ni semántica. | Ajuste |
| **RF-S56** | La acción de difusión administrativa conserva el envío a todos los usuarios y usa el rótulo `Enviar difusión`. | Ajuste |
| **RF-S57** | Cerrar con X solicita confirmación para cerrar la ventana administrativa; cerrarla no detiene el listener TCP ni la consola JLine. JLine mejora títulos, comandos, tablas y eventos sin alterar los comandos existentes. | Ajuste |

---

## 4. Requerimientos funcionales del cliente

### 4.1 Autenticación y presencia

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-C01** | El cliente debe permitir **autenticarse** ante el servidor con código y contraseña. | Obligatorio |
| **RF-C06** | El cliente **solo permite autenticarse**. **No existe opción, botón ni pantalla de registro**; los usuarios se dan de alta en el archivo plano del servidor (RF-S03). Ante credenciales inválidas se muestra un mensaje de error indicando que el usuario no está registrado o que los datos son incorrectos. | Ajuste |
| **RF-C02** | El cliente debe permitir **ver qué usuarios están conectados**. Todos los usuarios son visibles a la comunidad. | Obligatorio |
| **RF-C07** | La lista de usuarios conectados se **actualiza automáticamente** cuando alguien se conecta o desconecta. | Extra |
| **RF-C08** | Si el servidor cierra la sesión por inactividad (RF-S29), el cliente **muestra el aviso** y vuelve a la pantalla de autenticación. | Ajuste |

### 4.2 Mensajería

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-C03** | El cliente debe permitir **intercambiar mensajes (texto o archivos)** con un **usuario específico**. | Obligatorio |
| **RF-C09** | La interfaz de envío tiene **un único botón "Enviar"**. **No existe un botón independiente de "Enviar imagen"**. | Ajuste |
| **RF-C10** | La caja de redacción incluye un **campo de texto** y una **acción para adjuntar una imagen** (con vista previa y opción de quitarla). Adjuntar no envía por sí mismo; el envío siempre lo hace el botón "Enviar". | Ajuste |
| **RF-C11** | El botón "Enviar" permite enviar **solo texto**, **solo imagen** (sin texto) o **texto e imagen** juntos. Si hay texto e imagen, se envían como dos mensajes consecutivos al mismo destinatario (uno de tipo texto y otro de tipo imagen). | Ajuste |
| **RF-C12** | El botón "Enviar" permanece **deshabilitado** cuando no hay texto ni imagen adjunta, y no se envían mensajes vacíos. | Ajuste |
| **RF-C13** | Tras el envío, el campo de texto y la imagen adjunta se **limpian**. El mensaje aparece en la conversación con su estado (enviado / error). | Ajuste |
| **RF-C14** | El cliente permite emitir un **mensaje a toda la comunidad** conectada. | Extra |
| **RF-C17** | El directorio identifica a cada usuario por **nombre y apellidos**, manteniendo el código entre corchetes (`Nombre Apellido [CÓDIGO]`); si no hay datos nominales, muestra el código. La búsqueda encuentra por nombre o código. | Ajuste |
| **RF-C18** | Las imágenes del historial se visualizan dentro de la conversación. Se cargan al hacerse visibles, con descargas y decodificación fuera del hilo de interfaz, caché y deduplicación; los listados y refrescos no leen blobs innecesarios. | Ajuste |
| **RF-C19** | Se puede responder a un mensaje concreto. La respuesta incluye una cita textual portable (`Respuesta a Nombre [CÓDIGO]: "…"`); para imágenes cita el nombre del archivo. La cita se envía como texto ordinario, sin vínculo estructurado ni cambio de protocolo. | Ajuste |
| **RF-C20** | Los estados de entrega y lectura actualizan el mensaje local correcto. Los acuses pendientes se conservan y reintentan tras recuperar la conexión, sin realimentar otros acuses. | Ajuste |
| **RF-C21** | La carga de directorio, historial, imágenes y envíos no bloquea el hilo de presentación. El refresco habitual es local y paginado; la red se consulta por acciones explícitas o eventos push, no por sondeo periódico. | Ajuste |

### 4.3 Persistencia local

| ID | Requerimiento | Prioridad |
|---|---|---|
| **RF-C04** | La información del cliente se almacenará en una **base de datos relacional local (H2 Database)**. | Obligatorio |
| **RF-C05** | La conexión a la base de datos H2 debe configurarse mediante **archivos de configuración**. | Obligatorio |
| **RF-C15** | Todo mensaje enviado o recibido se guarda en H2 y el **historial** de cada conversación se puede consultar localmente. | Extra |
| **RF-C16** | Si la red falla, los mensajes se **encolan localmente** y se reintentan al restablecer la conexión. | Extra |
| **RF-C22** | El historial y las colas locales se aíslan por cuenta. Instancias del mismo código pueden compartir su archivo; cuentas distintas no comparten filas cuya orientación/estado sea relativo al usuario. La migración desde archivos compartidos es aditiva e idempotente. | Ajuste |
| **RF-C23** | El cliente **comunica su estado de conexión** (`Conectando`, `Conectado`, `SinRed`, `SesionCerrada`) de forma observable y reactiva, no por sondeo periódico del servidor. Añadido 2026-10-05 (`PAT-03`). | Ajuste |
| **RF-C24** | La vista **no alcanza el interior de la fachada**: no usa localizadores de servicio estáticos, no consulta atributos privados ni parchea la fachada en runtime. La lógica de presentación se alcanza por una costura pública y comprobable. Añadido 2026-10-05 (`PAT-01`). | Ajuste |
| **RF-C25** | **Los errores de red asíncronos son visibles para el usuario**: no se descartan en silencio. Añadido 2026-10-05 (`UIX-00c`, `UIX-00d`). | Ajuste |
| **RF-C26** | Una acción de reintento **reintenta de verdad** en los 3 clientes. Añadido 2026-10-05 (`UIX-00b`). | Obligatorio |
| **RF-C27** | El cliente muestra **estados vacíos** en toda pantalla susceptible de estar vacía (sin conversación, sin mensajes, sin resultados de búsqueda, sin contactos) y **distinguibles entre sí**. Añadido 2026-10-05 (`UIX-13`). | Ajuste |
| **RF-C28** | El usuario puede **buscar dentro del historial de una conversación** (`Ctrl+F`). Añadido 2026-10-05 (`UIX-06`). | Extra |
| **RF-C29** | Las imágenes del historial se pueden **ampliar con zoom y desplazamiento**, y se pueden **cancelar** las cargas que salen de vista. Añadido 2026-10-05 (`UIX-08`, `UIX-09`). | Ajuste |
| **RF-C30** | Los clientes ofrecen **agrupación de mensajes consecutivos** (el remitente no se repite en una racha) y **separadores de día**. Añadido 2026-10-05 (`UIX-05`, `UIX-04`). | Extra |
| **RF-C31** | Las listas largas se muestran **virtualizadas**. Añadido 2026-10-05 (`UIX-01`). | Ajuste |

El contrato anterior es independiente de lenguaje y toolkit: cada UI implementa las mismas
capacidades mediante su fachada y conserva el protocolo TCP común. Java usa H2 por cuenta; Python y
.NET usan SQLite por cuenta, con migraciones aditivas y el esquema documentado en
`universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md`. `LOGOUT` cierra solo la sesión solicitante; `KICK` cierra las
otras sesiones del mismo usuario. Para instalar cada cliente y el servidor, consulta
`DESPLIEGUE.md` y los README de sus carpetas; la matriz manual común está en `GUIA_ACEPTACION.md`.

---

## 5. Arquitectura del servidor

El servidor debe seguir la arquitectura de paquetes y componentes del diagrama *"Arquitectura de paquetes y componentes del servidor"*.

### 5.1 Capa de presentación (vistas e inicio)

| Paquete | Responsabilidad |
|---|---|
| **InicioServidor** | Punto de arranque; construye e inyecta las dependencias (incluido el pool) y lanza las vistas. |
| **Vista Consola** | Interfaz de consola del servidor (mejorada, ver 3.7); se comunica con `FachadaDeServicios` y **se suscribe a los eventos** para refrescarse sola. |
| **Vista Escritorio** | Interfaz gráfica de escritorio del servidor; se comunica con `FachadaDeServicios` y **se suscribe a los eventos** para refrescarse sola. |

### 5.2 Capa de negocio (lógica del servidor)

| Elemento | Tipo | Responsabilidad |
|---|---|---|
| **FachadaDeServicios** | Paquete | Punto único de acceso a la lógica del servidor para las vistas; coordina los componentes y publica los eventos a los observadores. |
| **InterfazProtocoloComunicacion** / **ProtocoloComunicacion** | Componente (interfaz / implementación) | Define e implementa el protocolo de comunicación. |
| **InterfazClienteServidor** / **ProtocoloClienteServidor** | Componente (interfaz / implementación) | Define e implementa el protocolo de interacción entre cliente y servidor. |
| **InterfazServiciosDisponibles** / **Servicios** | Componente (interfaz / implementación) | Expone los servicios disponibles del servidor. |
| **InterfazUsuariosDisponibles** / **Usuarios** | Componente (interfaz / implementación) | Gestiona la validación y consulta de usuarios cargados del archivo plano (sin registro por red). |
| **InterfazGestorMensajes** / **GestorColasDeMensajes** | Componente (interfaz / implementación) | Gestiona las colas y estructuras de datos de mensajes. |
| **Mensajes** | Paquete | Modelo y procesamiento de mensajes (texto e imagen). |
| **LogDeEventos** | Paquete | Registro de logs de eventos y mensajes. |
| **ConexionesClientes** | Paquete | Administración de las conexiones de los clientes: **Object Pool** de hilos/manejadores de sesión y control de inactividad (10 min). |
| **AlmacenarInformación** | Paquete | Recibe datos de `Mensajes` y `LogDeEventos` y los envía a la capa de persistencia. |

### 5.3 Capa de persistencia

| Paquete | Responsabilidad |
|---|---|
| **Persistencia** | Acceso a la **base de datos (MySQL)** y a los datos almacenados. |

### 5.4 Paquetes transversales

| Paquete | Responsabilidad |
|---|---|
| **DTO-Records** | Objetos de transferencia de datos y *records* compartidos entre capas. |
| **UtileriasVarias** | Utilidades generales (hash, lectura de configuración, manejo de archivos, formato de consola, etc.). |
| **Fachada** | Interfaces/contratos de fachada compartidos entre capas. |

### 5.5 Relaciones principales

- `InicioServidor` → `Vista Consola` y `Vista Escritorio`.
- `Vista Consola` y `Vista Escritorio` → `FachadaDeServicios`.
- `FachadaDeServicios` → componentes de interfaz (protocolo de comunicación, cliente-servidor, servicios, usuarios, gestor de mensajes) y paquetes `Mensajes`, `LogDeEventos` y `ConexionesClientes`.
- `Mensajes` → `AlmacenarInformación`.
- `LogDeEventos` → `AlmacenarInformación`.
- `AlmacenarInformación` → `Persistencia` → **Base de datos**.

---

## 6. Requerimientos no funcionales

| ID | Categoría | Requerimiento | Prioridad |
|---|---|---|---|
| **RNF-01** | Arquitectura | Las aplicaciones deben diseñarse **por capas y por componentes**. | Obligatorio |
| **RNF-02** | Arquitectura | El servidor debe respetar la arquitectura definida en la sección 5. | Obligatorio |
| **RNF-03** | Comunicación | La comunicación entre cliente y servidor debe usar **TCP/IP**. | Obligatorio |
| **RNF-04** | Interoperabilidad | El protocolo debe ser independiente del lenguaje, para permitir **clientes en lenguajes distintos de Java**. | Obligatorio |
| **RNF-05** | Plataforma | Cliente y servidor pueden ser **aplicaciones de escritorio**. | Obligatorio |
| **RNF-06** | Configurabilidad | Deben usarse **archivos de configuración** para: límite de usuarios conectados, tamaño del pool, tiempo de inactividad (10 min por defecto), conexión a MySQL (servidor) y conexión a H2 (cliente). | Obligatorio |
| **RNF-07** | Persistencia | Servidor: **MySQL**. Cliente: **H2 Database**. | Obligatorio |
| **RNF-08** | Seguridad | La integridad de los mensajes se verifica con **SHA-256**. | Obligatorio |
| **RNF-09** | Concurrencia | El servidor debe atender **múltiples clientes simultáneamente**, incluidas varias sesiones de un mismo usuario, mediante un **Object Pool** (RF-S43 a RF-S48). | Obligatorio |
| **RNF-10** | Mantenibilidad | Debe aplicarse bajo acoplamiento y alta cohesión entre componentes, de modo que puedan sustituirse mediante sus interfaces. | Obligatorio |
| **RNF-11** | Usabilidad | La consola del servidor debe ser legible y consistente; las vistas deben reflejar el estado real sin intervención manual. | Ajuste |
| **RNF-12** | Rendimiento | El refresco automático no debe bloquear el procesamiento de mensajes ni saturar la consola (se agrupan o limitan las actualizaciones frecuentes). | Ajuste |
| **RNF-13** | Formato de trama | Trama neutra: cabecera de 4 bytes (longitud del contenido, *Big-Endian*) seguida de contenido **JSON UTF-8**. Se evita la serialización nativa de Java para garantizar la interoperabilidad. | Extra |
| **RNF-14** | Clientes heterogéneos | Clientes espejo en otros lenguajes (por ejemplo Python y C#) que usen el mismo contrato de red. | Extra |
| **RNF-15** | Migraciones | Versionado del esquema de MySQL con una herramienta de migraciones (por ejemplo Flyway). | Extra |
| **RNF-16** | Mantenibilidad | **No se acumula código muerto.** Un símbolo sin referencias, sin anotación de framework/reflexión/SPI/convención de toolkit y sin cobertura de test, se borra. Lo que tiene 0 referencias pero vive por framework **no se toca**. Añadido 2026-10-05. | Ajuste |
| **RNF-17** | Paridad visual | Los tres clientes comparten **una** paleta por tema y **una** máquina de estados de mensaje. Si divergen, el historial de un cliente puede discrepar del de otro. Añadido 2026-10-05 (`VIS-01`, `VIS-02`). | Ajuste |
| **RNF-18** | Accesibilidad | Todo texto informativo cumple **WCAG AA (4.5:1)** en ambos temas; todo control de solo icono tiene nombre accesible; el estado de presencia no depende **solo** del color. Añadido 2026-10-05 (`UIX-17`, `UIX-19`, `UIX-20`). | Ajuste |
| **RNF-19** | Pruebas de UI | La lógica de presentación se alcanza por una costura **pública**: los tests no necesitan llamar métodos privados. Añadido 2026-10-05 (criterio medible de `PAT-VM`). | Ajuste |
| **RNF-20** | Documentación de diagramas | Existe diagrama de **casos de uso, secuencia, actividad, estados y despliegue**, más clases por componente de los **tres** clientes, cada uno derivado del código y con render revisado. Añadido 2026-10-05. | Ajuste |

---

## 7. Principios y prácticas de diseño obligatorios

| ID | Principio / Patrón | Aplicación esperada | Prioridad |
|---|---|---|---|
| **PD-01** | **Inversión de dependencias (DIP)** | Los módulos de alto nivel dependen de abstracciones (las interfaces `Interfaz...`), no de implementaciones concretas. | Obligatorio |
| **PD-02** | **Inyección de dependencias** | Las dependencias se entregan desde fuera (por ejemplo, desde `InicioServidor`) y no se instancian dentro de las clases. | Obligatorio |
| **PD-03** | **Fábricas de objetos** | La creación de componentes y objetos complejos (incluido el pool) se delega en fábricas. | Obligatorio |
| **PD-04** | **Otros principios** | Se recomienda aplicar además SOLID, el patrón Fachada y la separación de responsabilidades. | Obligatorio |
| **PD-05** | **Object Pool** | Gestión reutilizable de hilos/manejadores de sesión (RF-S43 a RF-S48). | Ajuste |
| **PD-06** | **Observer** | Notificación de eventos del servidor a las vistas para el refresco automático (RF-S42). | Ajuste |
| **PD-07** | **Pipes & Filters** | Procesamiento de texto e imagen en etapas (RF-S33). | Extra |
| **PD-08** | **Command, Strategy, Adapter** | Despacho de tramas a manejadores, intercambio de algoritmos (por ejemplo filtros) y adaptación de motores de BD. | Extra |
| **PD-09** | **MVVM en la capa cliente** | La presentación se separa del estado con un ViewModel observable: el controlador o la ventana no acumulan estado de negocio ni logreachat-array de red. Añadido 2026-10-05 (`PAT-VM`). | Ajuste |
| **PD-10** | **Command en las acciones de UI** | Las acciones del usuario (enviar, adjuntar, responder, cargar anteriores, salir) se encapsulan en comandos con condición de habilitación, en lugar de estar cableadas a mano. Añadido 2026-10-05 (`PAT-CMD`). | Ajuste |
| **PD-11** | **Composition root sin localizador estático** | Las dependencias se inyectan en las vistas; no se leen de un `static` ni de un atributo interno de otro objeto. Añadido 2026-10-05 (`PAT-01`). | Obligatorio |
| **PD-12** | **Strategy/Adapter de transporte** | TCP y TLS se seleccionan por configuración detrás de un mismo contrato, con **validación de configuración homogénea en los 3 clientes**. Añadido 2026-10-05 (`PAT-05`, `SEG-01`). | Ajuste |
| **PD-13** | **Repository con puerto declarado** | Todo acceso a la base local pasa por un puerto explícito e intercambiable, y el upsert usa la forma **portable** (`DELETE + INSERT`) sin sintaxis de motor. Añadido 2026-10-05 (`PAT-04`). | Ajuste |

---

## 8. Resumen de tecnologías y configuración

| Aspecto | Servidor | Cliente |
|---|---|---|
| Tipo de aplicación | Escritorio (consola mejorada + gráfica, ambas con refresco automático) | Escritorio |
| Lenguaje | Java | Libre (puede ser distinto de Java) |
| Base de datos | MySQL | H2 Database (local) |
| Configuración | Máximo de usuarios, tamaño del pool, inactividad (10 min) y conexión a BD | Conexión a BD |
| Comunicación | TCP/IP | TCP/IP |
| Registro de usuarios | Solo en archivo plano (sin registro desde el cliente) | Solo autenticación |
| Concurrencia | Object Pool de hilos/manejadores | — |
| Envío de mensajes | — | Un único botón "Enviar" (texto, imagen o ambos) |
| Almacenamiento de archivos | Directorio del servidor | — |

---

## 9. Trazabilidad (resumen)

| Funcionalidad | Requerimientos |
|---|---|
| Autenticación y usuarios | RF-S01 a RF-S04, RF-S27, RF-S28, RF-S34, RF-S35, RF-C01, RF-C06 |
| Control de conexiones e inactividad | RF-S05, RF-S06, RF-S29 a RF-S32, RF-C08, RF-C23 |
| Mensajería | RF-S07 a RF-S10, RF-C02, RF-C03, RF-C09 a RF-C14, RF-C17 a RF-C21 |
| Procesamiento de texto e imágenes | RF-S11 a RF-S17, RF-S33 |
| Persistencia y logs | RF-S18 a RF-S22, RF-C04, RF-C05, RF-C15, RF-C16 |
| Informes | RF-S23 a RF-S26 |
| Consola y refresco automático | RF-S39 a RF-S42, RNF-11, RNF-12 |
| Object Pool y concurrencia | RF-S43 a RF-S49, RNF-09, PD-05 |
| Arquitectura y diseño | RNF-01, RNF-02, RNF-10, PD-01 a PD-13 |
| **Autorización de tramas** | **RF-S34, RF-S35, PD-02** |
| **Calidad de la interfaz** | RF-C23 a RF-C31, RNF-17, RNF-18, RNF-19, PD-09, PD-10, PD-11 |
| **Persistencia local intercambiable** | PD-13, RNF-04 |
| **Transporte TCP/TLS** | RNF-03, RNF-13, PD-12 |
| **Higiene del código** | **RNF-16** |
| **Documentación de diagramas** | **RNF-20** |

> Los IDs en **negrita** corresponden a cambios recientes: los puntos 11–15 se añadieron el
> **2026-10-05** en la ronda de auditoría y el punto 16 el **2026-10-06** para la consola
> administrativa ([`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md)). Nota sobre numeración:
> `RF-S34` y `RF-S35` son
> **nuevos** y se solapan con el rango histórico `RF-S34..RF-S38` de §3.7 (interfaz de consola).
> Conviene renumerar en la siguiente versión del documento; mientras tanto, la desambiguación es
> `RF-S34/S35` = autorización de tramas y `RF-S36..S42` = consola y refresco.

---

## 10. Control de cambios respecto a la versión anterior

| # | Cambio | Requerimientos |
|---|---|---|
| 1 | Mejora de la interfaz de la consola del servidor | RF-S36 a RF-S40 (ver nota de numeración) |
| 2 | Los usuarios no se registran desde el cliente; el registro se hace solo en el archivo plano | RF-S03, RF-S27, RF-C06 |
| 3 | Cierre de sesión por inactividad a los 10 minutos | RF-S29, RF-S30, RF-C08 |
| 4 | Patrón Object Pool para los múltiples hilos | RF-S43 a RF-S49, PD-05 |
| 5 | Un solo botón "Enviar" que también envía solo la imagen, sin texto | RF-C09 a RF-C13 |
| 6 | Refresco automático de las vistas del servidor (consola y escritorio) | RF-S39 a RF-S42, PD-06 |
| 7 | Valor agregado tomado del segundo documento (marcado como *Extra*) | RF-S28, RF-S31, RF-S32, RF-S33, RF-S49, RF-C07, RF-C14 a RF-C16, RNF-13 a RNF-15, PD-07, PD-08 |
| 8 | Experiencia Java de chat: identidad legible, imágenes diferidas, respuestas como texto compatible y acuses persistentes; plantilla neutral para futuras vistas | RF-C17 a RF-C21 |
| 9 | Paridad de capacidades Python/.NET con Java, esquema local aditivo y cierre aislado de sesiones; se conserva cada toolkit y el protocolo TCP | RF-C02 a RF-C21, RNF-04, RNF-14 |
| 10 | Reparación de defectos de pruebas manuales: aislamiento por cuenta, informes con nombres y semántica de login/presencia, paquetes de imagen por propietario y paridad interoperable comprobada | RF-S14–S20, RF-S23–S26, RF-C02–C22 |
| **11** | **Auditoría 2026-10-05 — autorización de tramas.** Se vuelve **obligatorio** rechazar con `ERROR` toda trama que exija sesión autenticada si llega de una sesión anónima. Es el requerimiento que traduce el hallazgo `SEC-05`: `LISTAR_USUARIOS` respondía el directorio completo (código, nombres, apellidos, programa) **sin código ni contraseña**. Se añade además `RF-S35` para que la firma de un comando no pueda prometer una capacidad de respuesta que su cuerpo no tiene | **RF-S34, RF-S35, PD-02** |
| **12** | **Auditoría 2026-10-05 — calidad de la interfaz.** Estado de conexión reactivo en vez de sondeado, vista que no alcanza el interior de la fachada, errores asíncronos visibles, reintento que reintenta, estados vacíos, búsqueda dentro del historial, zoom de imagen, agrupación de mensajes, separadores de día y listas virtualizadas | **RF-C23 a RF-C31, RNF-19, PD-09, PD-10, PD-11** |
| **13** | **Auditoría 2026-10-05 — higiene y contrato visual.** No acumular código muerto (con la salvedad de lo que vive por framework, reflexión o SPI), paridad de paleta y de máquina de estados de mensaje entre los 3 clientes, y accesibilidad WCAG AA | **RNF-16, RNF-17, RNF-18** |
| **14** | **Auditoría 2026-10-05 — documentación de diagramas.** Exigencia de que existan diagramas de casos de uso, secuencia, actividad, estados y despliegue, más clases por componente de los **tres** clientes, todos derivados del código y con render revisado | **RNF-20** |
| **15** | **Auditoría 2026-10-05 — persistencia local y transporte.** El upsert local debe ser portable y pasar por un puerto declarado; TCP y TLS se eligen por configuración detrás de un contrato único, con validación de configuración homogénea en los 3 clientes | **PD-12, PD-13** |
| **16** | **Consola administrativa 2026-10-06.** Ficha de usuario con páginas de mensajes/archivos, estados vacíos explícitos, metadatos/IP disponibles, vista adaptativa, difusión y cierre aislado de la ventana administrativa | **RF-S50 a RF-S57** |
