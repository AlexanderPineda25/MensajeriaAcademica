# Catálogo de textos de UI — 3 clientes (punto 10, diario archivado en docs/archivo/PLAN_MEJORAS-historico.md)

Congela el lenguaje visual común tras los puntos 1–9 (cita en tarjeta,
login solo-lectura). Regla: los literales de la columna **Canónico** son los
que deben mostrar los 3 clientes; la columna de cada cliente indica su texto
actual cuando difiere en mecanismo pero no en concepto (ver §6).

Convenciones: `→` en el botón de entrar, `✕` para quitar cita/adjunto,
`…` (elipsis única, no `...`), tildes donde el español las lleva.

## 1. Login

| Concepto | Java | Python | .NET | Canónico |
|---|---|---|---|---|
| Título ventana | `Mensajería Académica \| Iniciar sesión` | `Mensajería Académica \| Iniciar sesión` | `Mensajería Académica \| Iniciar sesión` | `Mensajería Académica \| Iniciar sesión` |
| Eyebrow | `UNIVERSIDAD` | `UNIVERSIDAD` | `UNIVERSIDAD` | `UNIVERSIDAD` |
| Título tarjeta | `Mensajería Académica` | `Mensajería Académica` | `Mensajería Académica` | `Mensajería Académica` |
| Subtítulo | `Iniciar sesión` | = | = | = (sin coletillas) |
| Etiqueta código | `Código universitario` | = | = | = |
| Placeholder código | `A001234567` | = | = | = |
| Etiqueta contraseña | `Contraseña` | = | = | = |
| Placeholder contraseña | `••••••••••` | = | = | = |
| Etiqueta host / puerto | `Servidor` / `Puerto` | = | = | = (solo lectura, valores de `client.properties`; sin spinners en ningún cliente) |
| Selector de tema | `Oscuro` / `Claro` (`ComboBox`) | = | = | = (UI-09; persiste `tema=` en `client.properties`, oscuro por defecto) |
| Botón entrar | `Iniciar sesión →` (centrado) | = | = | = (centrado, ancho completo o fijo centrado) |
| Fallback sin properties | Campos `localhost` / `5000`, sin línea extra | = | = | Sin línea `Servidor actual`; las cajas muestran el fallback |
| Checkbox recordar | — (eliminado pto. 6) | — (eliminado pto. 6) | — (eliminado pto. 6; era decorativo, nunca se leía) | Sin checkbox |
| Botón entrar | `Iniciar sesión →` | = | = | = |
| Error vacío | `✖ Ingresa el código y la contraseña.` (prefijo `✖ `) | `✖ Ingresa el código y la contraseña.` | `✖ Ingresa el código y la contraseña.` | `✖ Ingresa el código y la contraseña.` |
| Estado conectando | spinner (sin texto) | `Conectando…` (en el botón) | `Conectando…` (en la tarjeta de error) | `Conectando…` donde el toolkit no tenga spinner |
| Error credenciales | `✖ Usuario no registrado o datos incorrectos.` | `✖ Usuario no registrado o datos incorrectos.` | `✖ Usuario no registrado o datos incorrectos.` | = (string exacto, sin detalle del servidor; el detalle va al log) |
| Error de red | `✖ No se pudo conectar ({Tipo}): {mensaje}` | `✖ No se pudo conectar ({Tipo}): {mensaje}` | `✖ No se pudo conectar ({Tipo}): {mensaje}` | = (mismo formato en los 3; Java distingue red de credencial mala) |
| Aviso ámbar (p. ej. cierre por inactividad) | `⏳ {aviso}` | `⏳ {aviso}` | `⏳ {aviso}` | `⏳ {aviso}` |
| Confirmar cierre del login | pregunta `¿Deseas cerrar el programa?`, título `Confirmar salida`, `Sí/No` (defecto `No`) | Sin confirmación (el login es la ventana raíz; cerrar = salir) | pregunta `¿Deseas cerrar el programa?`, título `Confirmar salida`, `Yes/No` (defecto `No`) | `¿Deseas cerrar el programa?` / `Confirmar salida` donde exista confirmación; Python documenta la ausencia (sin cambio de conducta) |

## 2. Chat — panel lateral

| Concepto | Java | Python | .NET | Canónico |
|---|---|---|---|---|
| Título sección | `Contactos` | `Contactos` | `Contactos` | `Contactos` |
| Botón refrescar | `↻` (icono, texto accesible `Actualizar usuarios`) | `Actualizar` | `Actualizar` | `Actualizar` en texto; en Java se admite el icono `↻` con el mismo texto accesible |
| Fila de acciones (pie del sidebar) | `KICK otras` / `Salir` / `Salir en todos` | `Salir` / `Salir en todos` | `Salir` / `Salir en todos` | Al pie del sidebar en los 3, orden Java (sin KICK fuera de Java); nada de salir en cabecera ni composer |
| Selector de tema (pie del sidebar) | `Oscuro` / `Claro` | = | = | = (debajo de la fila de acciones) |
| Etiqueta sesión | `Sesión activa · {Nombre [código]}` | `Mensajería⏎Sesión activa · {Nombre [código]}` (dos líneas) | `Sesión activa · {Nombre [código]}` | `Sesión activa · {Nombre [código]}` (Python antepone la línea `Mensajería` por su layout de dos líneas) |
| Placeholder buscar | `Buscar nombre o código` | `Buscar nombre o código` | `Buscar nombre o código` | = |
| Filtro | `Todos` / `Conectados` / `No leídos` | `Todos` / `Conectados` / `Con no leídos` | `Todos` / `Conectados` / `Con no leídos` | El filtro de no leídos existe en los 3; difiere solo el literal (`No leídos` Java / `Con no leídos` Python y .NET) |
| Presencia en fila | `En línea` / `Desconectado` (tooltip del avatar) | punto de color `●`/`○` en la tarjeta (sin texto) | `en línea` / `desconectado` | Desde 2026-10-08 la tarjeta Python es la de Java (punto + nombre + preview + insignia + hora relativa) sin el texto de presencia; `en línea`/`desconectado` sigue en la cabecera |
| Fila sin mensajes | `Sin mensajes aún` | `Sin mensajes aún` | — | Literal compartido Java/Python (preview de `RecienteChat` vacío); .NET no muestra preview |
| Contador no leídos | insignia numérica | insignia numérica (píldora verde a la derecha) | insignia/trazo propio | El número mostrado es el mismo dato en los 3; Python ya no lleva `({N})` en el texto de la fila |
| Orden de la lista | recientes por fecha (presencia solo desempata) | recientes por fecha (presencia solo desempata) | recientes por fecha (presencia desempata) | Tipo WhatsApp en los 3 desde 2026-10-08: la presencia no reordena; contactos sin conversación al final |
| Banner offline | `⛔ Sin conexión — mensajes en cola local, se reintentan solos` | = | = | = (idéntico en los 3) |
| Estado directorio | `Actualizando directorio y presencia…` / `Recientes: {N}` | `{N} contactos en línea` / `Sin conexión: directorio local · cola activa` / `Conectando...` (inicial) | `{N} contactos en línea` | Formatos propios por cliente (distinta fuente de datos local); el conteo `{N} contactos en línea` es idéntico en Python/.NET |

## 3. Chat — cabecera de conversación

| Concepto | Java | Python | .NET | Canónico |
|---|---|---|---|---|
| Sin selección | `Selecciona una conversación` | `Selecciona una conversación` | `Selecciona una conversación` | = (tuteo; se unificó el `Seleccione` de .NET) |
| Con selección | `Nombre Apellidos [código]` (vía `FormatoUsuario`) | = | = | `Nombre Apellidos [código]`, fallback al código solo |
| Presencia cabecera | `en línea` / `desconectado` | = | = | = |
| Botón historial | `↑ Cargar mensajes anteriores` (en cabecera, derecha) | `↑ Cargar mensajes anteriores` (en cabecera, derecha) | `↑ Cargar mensajes anteriores` (en cabecera, derecha) | = (en la cabecera a la derecha en los 3; Java conserva la flecha `↑` de affordance) |

## 4. Chat — composer, cita y adjunto

| Concepto | Java | Python | .NET | Canónico |
|---|---|---|---|---|
| Placeholder entrada | `Escribe un mensaje (Enter para enviar)` | `Escribe un mensaje (Enter para enviar)` | `Escribe un mensaje (Enter para enviar)` | = (documenta la regla Enter=enviar) |
| Botón enviar | `Enviar` | `Enviar` | `Enviar` | = |
| Botón adjuntar | `＋` a la izquierda del campo (abre `Todos *.*`, decide por extensión) | `＋` a la izquierda del campo (abre `Todos *.*`, decide por extensión) | `＋` a la izquierda del campo (abre `Todos *.*`, decide por extensión) | Botón único `＋` en los 3, **a la izquierda** del campo; abre siempre `Todos los archivos (*.*)` |
| Tarjeta de cita | cabecera `Respondiendo a` + `Respuesta a {autor}: "{extracto}"` (150 car. máx.) + `✕` (`Cancelar respuesta`) | `Respuesta a {autor}: "{extracto}"` (120 car. + `…`) + `✕` | `Respuesta a {autor}: "{extracto}"` (120 car. + `…`) + `✕` | Patrón `Respuesta a {autor}: "{extracto}"` + botón `✕`; la cabecera `Respondiendo a` solo existe en Java (se conserva) |
| Cita de imagen/archivo | `[Imagen: {nombre}]` / nombre de archivo | `[Imagen: {nombre}]` | nombre de archivo | `Respuesta a …` con el nombre entre `[Imagen: …]` para imágenes; para archivos, el nombre tal cual |
| Barra adjunto | `{nombre}[ (archivo generico)]` + miniatura + `✕` (`Quitar imagen adjunta`) | `{nombre} ({tamaño})` + miniatura + `✕ Quitar` | imagen: `Adjunta: {nombre} (arrastra otra para reemplazar)` + vista previa + `Quitar`; archivo: `Archivo: {nombre} ({bytes} bytes)` + `Quitar` | Botón `Quitar` (Python antepone `✕` por su set de botones); el texto descriptivo del adjunto es libre por cliente pero siempre incluye el nombre |
| Límite 50 MiB | `Máximo 50 MiB` (en estado) | — (sin tope local; el servidor rechaza) | aviso inline `Atención`: `El archivo supera el máximo de 50 MB ({bytes} bytes).` | El número `50 MB/MiB` es el mismo límite; cada cliente conserva su vía de aviso |
| Diálogo abrir | `Elegir archivo (imagen, PDF, documento...)` | `Seleccionar archivo` | Sin título (filtros `Imágenes\|…` / `Todos los archivos\|*.*`) | Título libre por cliente; los filtros deben ofrecer imágenes + todos los archivos |
| Sin conversación al adjuntar | — (el botón existe sin destino; el envío exige destino) | título `Seleccionar conversación`, `Selecciona una conversación primero.` | Silencio (retorno temprano; punto 2) | `Selecciona una conversación primero.` donde haya aviso; no se unifica el mecanismo |

## 5. Chat — burbuja y estados

| Concepto | Java | Python | .NET | Canónico |
|---|---|---|---|---|
| Botón responder | `↩` (tooltip `Responder a este mensaje`) + doble-click en burbuja/fila | `Responder` + doble-click en la fila (`itemDoubleClicked`) | `Responder` + doble-click en la lista (`MouseDoubleClick`) | Doble-click en cualquier mensaje = responder (Telegram Web); el botón se conserva en los 3; un click en imagen abre grande |
| Botón descargar | `⬇ Descargar` | `⬇ Descargar` | `⬇ Descargar` | = |
| Botón reintentar imagen | placeholder `No se pudo cargar · reintentar` (clic) | `Reintentar imagen` | `Reintentar imagen` | `Reintentar imagen` en texto; en Java el placeholder clicable cumple la función |
| Placeholder imagen | `Imagen` / nombre de archivo; `Cargando imagen…` | `Imagen` / nombre de archivo; `Cargando imagen…` | nombre de archivo; `Cargando imagen…` | `Cargando imagen…` durante la carga; después, el nombre del archivo |
| Tick propio | `⏳ Pendiente` / `✓ Enviado` / `✓✓ Entregado` / `✓✓ Leído` (leído en azul `#5EB1FF` sobre violeta); tooltip `Enviado` / `Entregado` / `Visto por {nombre}` / `Pendiente de envío` / `No enviado` | `⏳ Pendiente` / `✓ Enviado` / `✓✓ Entregado` / `✓✓ Leído` (leído en azul `#5EB1FF`) | `⏳ Pendiente` / `✓ Enviado` / `✓✓ Entregado` / `✓✓ Leído` (leído en azul `#5EB1FF`) | Visto literal con palabra en los 3, igual que Python; `✓✓` para entregado/leído y azul solo en leído; hora + visto dentro de la burbuja |
| Fecha burbuja | `HH:mm` del día | `aaaa-MM-dd HH:mm` | `aaaa-MM-dd HH:mm` | Formato corto local; Java muestra solo hora en la burbuja (lista distinta, se conserva) |
| Archivo en burbuja | `📎 {nombre} ({bytes} bytes)` | `📎 {nombre} ({tamaño})` | contenido/archivo local o remoto | Prefijo `📎` + nombre; la unidad de tamaño es libre (`bytes`/`KB`/`MB`) |

## 6. Diálogos y avisos transversales

| Concepto | Java | Python | .NET | Canónico |
|---|---|---|---|---|
| Cerrar chat (volver al inicio) | pregunta `¿Deseas cerrar esta sesión y volver al inicio?`, título `Confirmar salida`, defecto `No` | = (título `Confirmar salida`, defecto `No`) | = (título `Confirmar salida`, defecto `No`) | `¿Deseas cerrar esta sesión y volver al inicio?` / `Confirmar salida` |
| Salir en todos | Sin confirmación propia: ejecuta y vuelve al inicio con estado `Se solicitó cerrar tus otras sesiones` | pregunta `¿Cerrar todas las sesiones de esta cuenta?`, título `Confirmar salida`, defecto `No` | pregunta `¿Cerrar todas las sesiones de esta cuenta?`, título `Confirmar salida`, defecto `No` | `¿Cerrar todas las sesiones de esta cuenta?` / `Confirmar salida` donde exista confirmación; en Java no se añade diálogo (sería cambio de conducta) |
| Sesión cerrada por el servidor | estado `Sesión cerrada: {motivo}` + aviso al próximo login | título `Sesión cerrada`, `{motivo}` (defecto `Sesión cerrada por el servidor.`) | banner inline ámbar en el login: `⏳ {motivo}` (defecto `sesión cerrada por el servidor`) | Motivo en un aviso inline del login; defecto `Sesión cerrada por el servidor.` |
| Envío sin red | estado `Sin red: guardado para reintentar` | banner `Sin red: guardado para reintentar` | banner inline `Información`: `Sin red: guardado para reintentar.` | `Sin red: guardado para reintentar` |
| Error de envío | estado `No se completó el envío: {causa}` | banner `No se pudo enviar: {error}` | banner inline `Error`: detalle de la excepción | Base `No se pudo enviar`; el detalle anexo es libre |
| Guardar archivo | título `Guardar archivo` | título `Guardar archivo` | diálogo sin título (se conserva; el concepto es el mismo) | `Guardar archivo` |
| Descarga OK | estado `Archivo guardado: {nombre}` | título `Descarga completa`, `Archivo guardado exitosamente en:\n{ruta}` | banner inline `Listo`: `Archivo guardado en {ruta}.` | Mensaje de éxito con la ruta |
| Error de descarga | estado `No se pudo descargar: {causa}` | título `No se pudo descargar`, `El identificador del archivo no está disponible.` | banner inline `Error`: detalle de la excepción, incluido el caso sin identificador | El mensaje explica el motivo de la descarga fallida |
| Error de adjunto | estado propio | aviso inline | banner inline `Error` o `Atención`, con el detalle de validación | El aviso aparece en el chat y puede cerrarse manualmente |
| Error al cerrar otras sesiones | estado propio | estado propio | banner inline `Error`: detalle de la excepción | No se abre un modal de error |
| Descargando… | estado `Descargando {nombre}…` | botón `Descargando…` → `✓ Descargado` | botón deshabilitado durante la descarga | `Descargando…` como estado transitorio; botón deshabilitado mientras dura |
| Cierre remoto inactividad | aviso al próximo login `Sesión cerrada por inactividad (10 min), inicie de nuevo. {motivo}` | — | — | Solo Java (RF-C08); se conserva |

## 7. Paleta Telegram oscuro liso (3.1, fondo `#0E1621`) + modo Normal (UI-09)

Conmutables en login y chat (selector `Oscuro`/`Claro`), persiste `tema=`
en `client.properties`, **oscuro por defecto**.

Oscuro: fondo app/lista `#0E1621`, panel/cabecera/composer `#182533`, borde `#2A3B4D`,
burbuja propia `#7B61B8` (borde `#6A4FA3`, texto `#FFFFFF`), burbuja ajena
`#182533` (texto `#E8EEF2`), remitente/autor `#C4B5FD`, cita sobre `#243447`
con barra de acento `#A78BFA`, fecha dentro de burbuja `#C4B5FD`, visto
leído azul `#5EB1FF` y resto `#E8EEF2`/`#A8B8C1`, campo/buscador `#243447`
(texto `#E8EEF2`, prompt `#7A8A99`), enviar `#7B61B8` (hover `#6A4FA3`).
Banner offline ámbar y presencia verde/gris se conservan. Radios: tarjeta 12,
errores/avisos 8, burbuja 8, cita 6 (Python/.NET) / 4 (Java), enviar 5–8.

Paleta clara anterior (documentada, sustituida por el tema oscuro 3.1): fondo
`#EEF1F6`, burbuja propia `#E1F5FE`/`#D9FDD3`/`#e4f3ec` según cliente, cita
`#147D73` sobre `#E6F4F2`, leído `#1E88E5`/`#1D9BF0`/`#238bb2`.

Normal/claro (UI-09, alternativa): ventana `#EEF1F6`, tarjeta `#FFFFFF`,
lista `#F4F7F7`, burbuja propia `#E1F5FE` (Python) / `#D9FDD3` (.NET) /
`#e4f3ec` (Java), ajena `#FFFFFF`, remitente teal, cita teal sobre
`#E6F4F2`, leído azul `#1E88E5`/`#1D9BF0`. Login claro: tarjeta blanca,
campos `#F7F9FC`, botón `#2456A6`.

## 8. Teclado y ratón (3.1)

- `Enter` = enviar en los 3 (Python `returnPressed`, .NET `KeyDown` sin
  `Shift`, Java `ENTER` sin `Shift` + `defaultButton`).
- `Shift+Enter` = salto de línea donde el control es multilínea (.NET/Java).
- `Esc` = cancelar cita/adjunto en los 3 (Java `alTeclaEntrada`, .NET
  `TxtMensaje_KeyDown`, Python `QShortcut Esc → _cancelar_composicion`).
- Doble-click en cualquier burbuja/fila = responder (Telegram Web): Java
  `fila.setOnMouseClicked (2 clicks)`, Python `itemDoubleClicked`, .NET
  `MouseDoubleClick`. El botón Responder/`↩` se conserva.
- Un click en imagen = ampliar (Java `abrirGrande`, Python diálogo no modal,
  .NET ventana `AbrirImagenGrande`); el doble-click en imagen responde.

## 9. Checklist de recorrido guiado (misma en los 3)

1. Login muestra tarjeta §1 (sin nota de alta, sin línea de servidor, con
   selector `Oscuro`/`Claro`) con servidor de solo lectura y botón
   `Iniciar sesión →` centrado; vacío → `✖ Ingresa el código y la contraseña.`;
   credencial mala → `✖ Usuario no registrado o datos incorrectos.` (exacto).
2. Chat abre con `Sesión activa · Nombre [código]`, sección `Contactos`,
   `Buscar nombre o código`, `Todos`/`Conectados`, banner offline §2.
3. Sin selección: `Selecciona una conversación`; con selección: identidad +
   `en línea`/`desconectado`; `↑/Cargar mensajes anteriores` según §3.
4. Cita: tarjeta `Respuesta a …` + `✕`; enviar antepone la cita; cancelar no
   deja rastro (matriz pto. 1).
5. Composer: placeholder §4, `Enter` envía, `Enviar` se habilita con texto o
   adjunto (matriz pto. 2).
6. Salir: `¿Deseas cerrar esta sesión y volver al inicio?` /
   `Confirmar salida`; `¿Cerrar todas las sesiones de esta cuenta?` donde
   aplique (§6); descarga OK muestra la ruta en un aviso inline.

En .NET se conservan las tres confirmaciones modales existentes. Los avisos de chat usan un banner
inline con severidad `Error`, `Atención`, `Información` o `Listo`, permanece hasta cerrarlo o
reemplazarlo por otro aviso, y el cierre remoto muestra el motivo en el banner ámbar del login.

## 10. Consola administrativa del servidor

Esta sección fija etiquetas y estados de la vista JavaFX administrativa; no cambia el protocolo TCP
ni los textos que intercambian los clientes.

| Ubicación | Texto/estado | Comportamiento |
|---|---|---|
| Pestaña Conectados, selector vacío | `No hay usuarios activos` | Acción `Desconectar usuario` deshabilitada si no existen sesiones activas |
| Pestaña Conectados, acción | `Desconectar usuario` | Selector con códigos de usuarios que tienen sesiones activas |
| Pestaña Mensajes | `Caracteres` y `Palabras` | Valores presentados como filas separadas |
| Pestaña Mensajes, IP destino | `No disponible` | Solo cuando el destinatario no tiene sesión activa; las IP actuales no se presentan como históricas |
| Pestaña Difundir, botón | `Enviar difusión` | Envía a todos los usuarios, incluidos los desconectados para entrega posterior |
| Cierre de ventana administrativa con X | `¿Deseas cerrar la vista administrativa del servidor? El servicio y la consola JLine seguirán activos.` | Título `Cerrar vista del servidor`; encabezado `Salir de la vista administrativa`; confirmar solo cierra la ventana |
| Usuarios, detalle | `Resumen`, `Conexiones`, `Mensajes`, `Archivos` | Ficha abre al hacer doble clic y pagina mensajes/archivos de 50 en 50 |

Las demás etiquetas de pestaña son `Estado`, `Conectados`, `Usuarios`, `Mensajes`, `Informes`,
`Logs`, `Pool` y `Difundir`. Los literales de cierre se cotejaron con `VistaEscritorio.java`.
