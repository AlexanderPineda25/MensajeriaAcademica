# Plan de arquitectura del cliente Java

**Estado:** en curso; fases 3 y 4 con extracciones cohesivas verificadas (resta el smoke visual)  
**Alcance:** cliente JavaFX; el servidor se compila como verificación, pero no se modifica.  
**Objetivo:** mejorar límites arquitectónicos, SOLID y mantenibilidad sin alterar el comportamiento visible, la base local ni el contrato TCP/JSON.

## Estado de ejecución — 2026-10-07

`ChatController` pasó de 1024 a 470 líneas y `FachadaCliente` de 868 a 572, sin cambiar
comportamiento observable (suite 174/174 en verde, ArchUnit 6/6):

- Presentación: `BusquedaHistorialChat` (búsqueda UIX-06 + salto diferido),
  `PaginadorHistorialChat` (paginación + anclaje UIX-16 + aviso de nuevos),
  `RefrescadorContactosChat` (filtro, selección, refresco estrangulado) con puerto
  `FlujoConversacion` hacia el controlador, `GestorSalidaSesionChat` (kick/salir/cierre
  remoto) y `CargadorAdjuntoEntradaChat` (elegir/previsualizar/quitar). El controlador
  conserva solo cableado FXML, composición, envío vía `EnviarMensajeComando` y avisos
  de conexión (PAT-03). Se eliminó la constante `TOPE_BUSQUEDA` sin uso y los imports
  y métodos muertos resultantes (RNF-16).
- Fachada: `ReintentadorPendientesCliente`, `SincronizadorDirectorioCliente`,
  `LectorHistorialRemotoCliente`, `ConstructorRecientes` (puro), `PublicadorEventosCliente`
  (Observer) y `ReceptorEntranteCliente` (antes clase interna); la fachada conserva guarda
  de sesión, ciclo de vida y delegación, con inyección total desde `FabricaCliente`
  (constructor de 11 parámetros con valores por defecto para compatibilidad).
- Cada extracción tiene interfaz propia (DIP) y pruebas propias: 47 casos nuevos
  (`HistorialChatTest`, `RefrescadorContactosTest`, `SalidaSesionChatTest`,
  `AdjuntoEntradaChatTest`, `ReintentadorPendientesTest`, `ConstructorRecientesTest`,
  `ReceptorEntranteTest`, `SincronizadorDirectorioTest`, `LectorHistorialRemotoTest`,
  `PublicadorEventosTest`); `RedParaPruebas` es el doble de red compartido.
  ArchUnit cubre nominalmente a los 5 colaboradores nuevos de presentación.

## Estado de ejecución — 2026-10-06 (histórico)

La fase 3 avanzó: `ChatController` ahora delega el acceso a la fachada al `ChatViewModel`, y el
ViewModel es dueño del estado de conversación compartido (adjunto, respuesta, offset, directorio,
recientes y propiedades observables). En esta ronda, `InterfazConexionRed` y `ReceptorMensajes`
pasaron a `client-interfaces`; las respuestas se adaptan a `RespuestaRed`/`TipoMensajeCliente` en
`ConexionCliente`, antes de llegar a autenticación y fachada. La fachada dejó de exponer al
observador legado basado en `Mensaje`; la UI mantiene el contrato de eventos neutrales. ArchUnit
ahora protege la ausencia de dependencias protocolarias en esos puertos y servicios.

Validación ejecutada: `mvn -q -pl client/client-app -am clean test` pasó (127 pruebas, sin fallos
ni errores), incluida la prueba de reconexión automática tras la caída del servidor. También pasó
`mvn -q -pl server/servidor-app,client/client-app -am -DskipTests compile`; no se modificó código
del servidor. Se validó la carga de ambos FXML con la fábrica real. Aún falta ejecutar el smoke
visual conectado a un servidor real. Esto no completa el plan: el controlador todavía coordina
varios flujos de UI y queda la extracción cohesiva de casos de uso y la validación visual.

## Diagnóstico de partida

- `client-interfaces` define puertos y records neutrales y su `pom.xml` no depende de `dto`. El contrato `InterfazConexionRed`/`ReceptorMensajes` también vive allí; `FachadaCliente`, autenticación y coordinadores usan `RespuestaRed`, mientras que los DTO del protocolo quedan en los paquetes adaptadores `componentes.conexion` y `componentes.protocolo`.
- La presentación usa puertos y no accede directamente al socket ni a la base local. `ChatController` ya delega al `ChatViewModel`, pero todavía coordina eventos, selección, flujo de UI, paginación y refresco.
- Medición 2026-10-07: `ChatController` 470 líneas (era 1024) y `FachadaCliente` 572 (era 868).
  El controlador es adaptador FXML + `FlujoConversacion`; la fachada es guarda de sesión,
  ciclo de vida y delegación a 6 servicios con nombre.
- Línea base histórica: 83 pruebas pasaban antes de esta continuación. La ejecución limpia más reciente pasa 127 pruebas del cliente (0 fallos/errores), incluida carga automatizada de ambos FXML; todavía no hay smoke visual conectado al servidor.

## Principios e invariantes

1. Mantener los módulos Maven y paquetes existentes; no crear otro módulo como parte de este plan. Se añadirán paquetes internos solo donde separen responsabilidades y permitan comprobar la dirección de dependencias.
2. No cambiar el framing, JSON, comandos, nombres de campos ni semántica TCP; tampoco migrar la base local.
3. No modificar fuentes del servidor, Python ni .NET. El servidor solo se incluye en la compilación de aceptación.
4. Migrar con una fachada de compatibilidad: primero se añaden puertos neutrales y adaptadores; consumidores y firmas existentes se mantienen hasta que haya cobertura y todos los usos internos estén migrados.
5. Ninguna extracción debe cambiar orden de mensajes, acuses, paginación, reintentos, identidad de usuario, selección de conversación ni ciclo de vida de JavaFX.

## Fases de implementación

### 1. Caracterizar el comportamiento existente — parcial

Conservar la suite base histórica de 83 pruebas y añadir regresiones para los recorridos que hoy no cubre la presentación:

- inicio de sesión aceptado/rechazado y comunicación de errores sin filtrar excepciones técnicas;
- orden de conversaciones recientes, selección, presencia y mensajes entrantes/propios;
- envío de texto, imagen o archivo sin texto, acuses y prevención de duplicados al reintentar;
- paginación, búsqueda con resultados y sin resultados, cambio de contacto durante una consulta;
- reconexión, cierre voluntario, cierre remoto y expulsión de otras sesiones;
- [x] carga de ambos FXML con `FabricaControladores` e inyección de sus dependencias.
- [ ] cubrir todos los demás recorridos de presentación enumerados arriba mediante regresiones automatizadas.

Estas pruebas fijan los resultados observables antes de mover responsabilidades.

### 2. Aislar los casos de uso de TCP/JSON — completada en el cliente Java

- [x] Definir en `client-interfaces` puertos y records de aplicación sin dependencia de `Mensaje`, Gson, JavaFX o JDBC.
- [x] Mantener `ConexionCliente` como adaptador TCP que traduce contratos neutrales a los DTO existentes de `common-protocol`; el JSON transmitido sigue idéntico.
- [x] Mover `InterfazConexionRed` y `ReceptorMensajes` a `client-interfaces` con contratos neutrales.
- [x] Migrar autenticación, coordinador de mensajería y fachada a resultados/eventos neutrales; retirar la suscripción protocolaria legada sin consumidores de producción.
- [x] `client-interfaces` no tiene dependencia Maven de `dto` ni referencias a DTO protocolarios.

### 3. Separar presentación, estado y casos de uso del chat — en curso

- [x] Introducir `ChatViewModel` para estado observable y delegación de operaciones; no contiene acceso a socket, protocolo ni persistencia concretos.
- [x] El controlador usa el ViewModel como único acceso a los casos de uso/puerto de chat y no conserva una referencia directa a la fachada.
- [x] Completar la extracción cohesiva de coordinación de eventos, contactos/presencia, historial/búsqueda y acciones del chat; mantener el controlador como adaptador JavaFX.
- [x] Extraer contactos/presencia (`RefrescadorContactosChat` + `FlujoConversacion`), historial/búsqueda (`PaginadorHistorialChat`, `BusquedaHistorialChat`, `SaltoBusquedaPendiente`), salida de sesión (`GestorSalidaSesionChat`) y adjunto de entrada (`CargadorAdjuntoEntradaChat`); el envío ya iba por comando (`EnviarMensajeComando`).
- Mantener `ChatController` como adaptador JavaFX: conectar controles FXML con propiedades, comandos y navegación, sin ordenar directamente consultas, reintentos o reglas de conversación.
- Separar de forma cohesiva los flujos de contactos/presencia, historial/búsqueda y envío/adjuntos. Reutilizar `GestorAdjuntosChat` donde ya cubre carga y descarga; no duplicar sus responsabilidades.
- Inyectar ViewModel y casos de uso desde `FabricaControladores` y `ClienteApp`. Mantener nombres `fx:id`, vistas FXML, navegación, tema y textos actuales.

### 4. Reducir la fachada y conservar compatibilidad — parcial

- [x] Extraer `CoordinadorMensajeriaCliente` y `CoordinadorHistorialCliente`.
- [x] Extraer el ciclo de sesión/reconexión a `GestorReconexionSesion`, incluida la custodia temporal de credenciales, el backoff, la reautenticación y la restauración posterior a conexión.
- [x] Extraer reintentos (`ReintentadorPendientesCliente`), directorio/presencia (`SincronizadorDirectorioCliente`), lectura remota y descargas (`LectorHistorialRemotoCliente`), construcción de recientes (`ConstructorRecientes`, puro), bus de eventos (`PublicadorEventosCliente`) y receptor entrante (`ReceptorEntranteCliente`). `FachadaCliente` queda en 572 líneas como fachada fina con inyección total desde `FabricaCliente`.
- Conservar la fachada agregada y sus operaciones usadas hasta que las pruebas de referencias confirmen que ningún flujo interno necesita la forma antigua. El adaptador de compatibilidad será el único sitio permitido para traducir la API antigua a los contratos nuevos.
- No dividir clases solo para reducir líneas: cada extracción debe tener una responsabilidad verificable y pruebas propias.

### 5. Hacer cumplir las capas y cerrar documentación — parcial

- [x] Añadir pruebas ArchUnit para los contratos neutrales reconocidos, la presentación frente a
  persistencia/transporte y los controladores/ViewModel frente a implementaciones concretas
  (los 5 colaboradores nuevos de presentación están cubiertos nominalmente).
- [x] Proteger con ArchUnit que fachada, autenticación, coordinadores y puertos neutrales no dependan de DTO/codec; los paquetes adaptadores conservan esas dependencias (los 6 servicios nuevos de fachada están cubiertos nominalmente).
- [x] Sincronizar `PLAN_PATRONES_UI.md`, `ESTADO_ROADMAP.md`, `PLAN.md` y `AGENTS.md` con el estado parcial y las medidas observadas en esta continuación.
- [ ] Actualizar esos documentos con medidas finales después de completar los criterios restantes.
- [x] Añadir prueba automatizada de carga de login/chat FXML con la fábrica real y toolkit JavaFX.
- [ ] Hacer smoke visual del login y chat: carga FXML, tema, historial, adjuntos, conversación reciente, caída/reinicio del servidor y cierre de sesión.

## Criterios de aceptación

- La suite actual del cliente pasa: 174 pruebas, sin fallos/errores (127 base + 47 de los
  servicios y colaboradores extraídos), incluida la regresión de caída y reconexión; faltan
  regresiones automatizadas para los demás recorridos de presentación que enumera la fase 1.
- [ ] El FXML existente abre con la fábrica real; se pueden iniciar sesión, ver conversaciones recientes y enviar texto, imagen y archivo sin alterar resultados observables.
- [ ] Buscar/paginar historial, recibir mensajes y acuses, reconectar sin duplicar mensajes y salir/cerrar sesiones conservan su comportamiento en el smoke automatizado/visual.
- TCP/JSON y esquema local no cambian; el módulo `server/` y los clientes Python/.NET no reciben cambios de código.
- Las reglas ArchUnit existentes pasan; los DTO de protocolo están confinados en los adaptadores de conexión/protocolo.
- El estado conversacional está en `ChatViewModel`, el `ChatController` (470 líneas) es adaptador
  JavaFX + `FlujoConversacion` y la `FachadaCliente` (572 líneas) es guarda de sesión, ciclo de
  vida y delegación; queda pendiente el smoke visual conectado a un servidor real.
- [x] Pasan `mvn -q -pl client/client-app -am clean test` y `mvn -q -pl server/servidor-app,client/client-app -am -DskipTests compile` tras la extracción de reconexión.

## Riesgos y controles

| Riesgo | Control |
|---|---|
| Cambiar accidentalmente eventos, acuses u orden de mensajes | Caracterizar los flujos antes de extraer; ejecutar pruebas de fachada y regresión después de cada fase |
| Desincronizar el adaptador neutral del protocolo | Pruebas de contrato que comparen cada operación con el JSON/trama existente; no editar `common-protocol` |
| Alterar ciclo JavaFX/FXML al introducir ViewModel | Mantener `fx:id` y controladores FXML; prueba de carga con la fábrica de producción y smoke visual |
| Romper consumidores de la fachada agregada | Migración aditiva; adaptador legado hasta demostrar cero consumidores internos |
| Refactor grande difícil de revisar | Entregar y verificar una fase a la vez; no mezclar cambios de UI funcional ni servidor |

## Supuestos

- El contrato TCP/JSON es inmutable durante este trabajo; solo cambia el mapeo dentro del cliente Java.
- El alcance es arquitectura y mantenibilidad del cliente Java. Las correcciones funcionales visuales de otras solicitudes no se amplían aquí.
- La cifra de 83 pruebas es la línea base observada el 2026-10-06; se actualizará si cambia antes de iniciar la implementación.
