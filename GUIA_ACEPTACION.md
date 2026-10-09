# Guía de pruebas y aceptación

Esta guía cubre las comprobaciones compartidas del servidor y los tres clientes. Los pasos para
instalar cada proceso están en [`DESPLIEGUE.md`](DESPLIEGUE.md) y en el README dentro de cada
carpeta de despliegue.

## Pruebas automatizadas

Desde `universidad-mensajeria/`, con Docker disponible y MySQL de pruebas en `localhost:3307`:

```powershell
docker compose up -d mysql
mvn -pl server/servidor-app,client/client-app -am test
```

Pruebas de Python, desde `client-python/`:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests
```

Pruebas y build WPF, desde `universidad-mensajeria/` (los tests actuales apuntan a .NET 10):

```powershell
dotnet test client-dotnet.Tests/Mensajeria.Tests.csproj
dotnet build client-dotnet/Mensajeria.csproj -c Release
```

La aceptación automatizada no sustituye la prueba manual entre procesos y computadores.

## Preparación del smoke

1. Ejecuta el servidor y cada cliente en computadores separados, o valida primero en LAN con
   ventanas separadas. Asegura que el firewall permite cliente → servidor en `5000/TCP`.
2. Confirma conectividad desde cada cliente con `Test-NetConnection IP_SERVIDOR -Port 5000`.
3. Usa cuentas distintas. El alta de usuarios desde las UIs está desactivada; las cuentas de
   demostración están en el CSV de semilla del servidor.
4. Comprueba primero una dirección Java ↔ cliente remoto y luego todas las parejas de la matriz.

## Matriz de interoperabilidad

En **cada dirección** verifica mensaje recibido una sola vez, remitente/nombre correctos, burbuja
propia a la derecha y recibida a la izquierda, estado entregado/leído sobre el mensaje correcto:

| Pareja | Direcciones que se deben probar |
|---|---|
| Java ↔ Python | Java → Python; Python → Java |
| Java ↔ .NET | Java → .NET; .NET → Java |
| Python ↔ .NET | Python → .NET; .NET → Python |

Para cada dirección envía: texto; imagen sola; texto e imagen; respuesta a texto; respuesta a
imagen. Revisa que una cita llegue como texto ordinario y que un cliente no emita un segundo acuse
al recibir `MENSAJE_LEIDO` o `MENSAJE_ENTREGADO`.

## Historial, sesiones e imágenes

- Mantén dos procesos Java abiertos con cuentas distintas. Un mismo ID recibido debe quedar con la
  orientación relativa a cada cuenta; enviar en una cuenta no debe parecer un mensaje propio en la
  otra.
- Abre dos sesiones del mismo usuario. Cerrar una con `LOGOUT` no debe desconectar la otra; la
  presencia queda offline únicamente después de cerrar la última. Prueba explícitamente «Salir en
  todos».
- Envía una imagen nueva y comprueba en el servidor `uploads/Nombre Apellido [CODIGO]/<hash>/` el
  original y las cinco derivadas. En el otro cliente la imagen aparece en la conversación, también
  después de reiniciar; la carga es diferida, no bloquea la UI, y un fallo permite reintento.
- Cierra y vuelve a abrir cada cliente. Verifica historial, nombre/código, metadatos de imagen y
  acuses. No borres las bases legacy antes de comprobar la migración por cuenta.
- Desconecta temporalmente un cliente, vuelve a conectarlo y comprueba reintento sin duplicados.
- Comprueba que el botón de registro no aparece en ninguna UI.

## Informes y almacenamiento del servidor

- Inicia sesión y consulta `informe conexiones`: el conteo aumenta por login exitoso, no por enviar,
  listar o leer mensajes; las sesiones vivas cambian al abrir/cerrar sesiones.
- En informes 2–4 se muestra `Nombre Apellido [CODIGO]`; el filtro por código sigue funcionando.
- Inspecciona filas, rutas y auditoría con DBeaver siguiendo la sección de la [guía del servidor](universidad-mensajeria/server/servidor-app/README.md#inspección-con-dbeaver).
- La presencia es estado vivo en memoria del servidor; no concluyas que una persona está online
  basándote en una fila histórica de MySQL.

## Registro del resultado

Anota sistema operativo y versiones, commit/versión desplegada, IP y puerto (sin contraseñas),
pareja/dirección, caso probado, resultado, hora e ID del mensaje. Si falla, conserva el log
pertinente de cliente y servidor y revisa el mismo ID y tipo de trama en ambos extremos.

## Smoke de los cambios 2026-10-04 (cita, login, descarga, difusión)

- **Cita en tarjeta (3 clientes):** responder muestra la cita en tarjeta
  con ✕ y el campo queda limpio; Esc cancela cita y adjunto sin borrar el
  texto; el mensaje enviado lleva la cita como prefijo. Cancela con ✕ y
  verifica que el envío sale sin cita.
- **Enviar solo adjunto:** en cada cliente, adjunta solo imagen y luego
  solo archivo (sin texto) y verifica que Enviar habilita y llega.
  Prueba también adjunto mayor a 50 MB (debe avisar, no habilitar).
- **Login solo-lectura:** en los 3 clientes, servidor/puerto no editables
  y reflejan `client.properties`; en .NET, cerrar el login pregunta
  "¿Deseas cerrar el programa?".
- **Descarga con filtro (escritorio):** aplica Grises/Sepia/etc.,
  previsualiza y descarga: el archivo debe llamarse
  `<nombre>-<Filtro>.png` y diferir del original. El detalle muestra
  Nombre[codigo] (y `SERVIDOR (sistema)` para broadcasts).
- **Maestro-detalle (escritorio):** en Informes tipo mensajes, seleccionar
  fila muestra el mismo detalle que la pestaña Mensajes; en Usuarios,
  click muestra ficha + "Cerrar sesiones" + "Ver mensajes" (salta a
  Mensajes con el filtro puesto).
- **Difusión con progreso:** difunde desde consola y desde escritorio;
  verifica avance `Difusión N/total` y resumen final con fallos; con un
  cliente lento, el resto recibe sin bloqueo.
- **Reintento offline Python:** sin red, envía imagen/archivo (queda en
  pendientes), reconecta y verifica entrega sin duplicados (claves de
  contrato `nombreArchivo/tamanoArchivo/indiceParte/contenidoImagen/
  hashSha256/archivoId` en el reintento).

---

## Comprobaciones de seguridad (nuevas, 2026-10-05)

### `SEC-05` — tramas sin autenticación

Esta sección conserva un procedimiento de bajo nivel para reproducir el hallazgo histórico y
confirmar el comportamiento actual si se necesita una comprobación manual. `SEC-05` está cerrado;
la protección principal está cubierta por pruebas parametrizadas y E2E descritas en el
[`roadmap`](ESTADO_ROADMAP.md#seguridad-bloqueante).

1. Levanta el servidor. Abre un socket TCP crudo contra `IP_SERVIDOR:5000` **sin autenticar**.
   Puedes usar PowerShell:

   ```powershell
   $c = New-Object System.Net.Sockets.TcpClient("IP_SERVIDOR", 5000)
   $s = $c.GetStream()
   $trama = '{"tipo":"LISTAR_USUARIOS","id":"probe-1"}'
   $len = [BitConverter]::GetBytes([Text.Encoding]::UTF8.GetByteCount($trama))
   [array]::Reverse($len)
   $s.Write($len, 0, 4)
   $b = [Text.Encoding]::UTF8.GetBytes($trama)
   $s.Write($b, 0, $b.Length)
   $s.Flush()
   ```

2. Lee la respuesta. **Comportamiento correcto:** una trama `ERROR` con el motivo
   `autenticate primero con LOGIN`, y **ningún** dato de usuario.
3. Comportamiento anterior al arreglo: llegaba `LISTAR_USUARIOS_RESPUESTA` con datos del directorio.
   El comportamiento vigente debe cumplir el paso 2.
4. Repite con `LISTAR_CONECTADOS` y `LOGOUT`. Las 3 deben devolver `ERROR`.
5. Repite los 4 pasos con **TLS** en `5001` para confirmar que el cifrado no cambia el resultado.

### Después de cerrarlo

La prueba automática que lo fija es un test **parametrizado** que itera **todos** los tipos
registrados en `Despachador` y exige `ERROR` con sesión anónima. Si añades un comando nuevo y no lo
registras en esa prueba, el hueco vuelve: por eso la prueba debe recorrer el registro, no una lista
escrita a mano.

---

## Estados de conexión (`SMOKE-06`; `PAT-03` implementado)

Matriz de 4 estados × 3 clientes. Los 4 estados son `Conectando`, `Conectado`, `SinRed` y
`SesionCerrada`.

| Transición | Cómo se provoca | Qué se espera en los 3 clientes | Anotar |
|---|---|---|---|
| → `Conectando` | Login con el servidor apagado y luego encendiéndolo | Indicador de carga neutro mientras conecta; el error de transporte, si llega, se muestra separado. La corrección de .NET está en `UIX-00f`. | |
| → `Conectado` | Login correcto | El banner ámbar desaparece; el directorio carga | |
| → `SinRed` | Matar el proceso del servidor con el cliente abierto | Los 3 clientes reciben el estado observable y muestran el aviso/banner de desconexión. Medir la latencia real; no asumir el límite de 2 s a partir de la ida/vuelta de una herramienta remota. | |
| → `SesionCerrada` | `Salir`, o `Salir en todos` desde otra sesión, o esperar 10 min | Aviso con el **motivo** y vuelta al login. En WPF, el cierre remoto se comunica en el banner del login. | |
| `SinRed` → `Conectando` | Reencender el servidor | Comprobar reconexión y reintento de cola sin duplicados; anotar si el usuario debe accionar una actualización según el cliente. | |

`PAT-03` está implementado en Java, Python y .NET. `SMOKE-06` continúa pendiente como matriz
manual completa de cuatro estados por tres clientes, con versión del sistema, texto observado y
tiempos medidos.

---

## Capturas lado a lado (`SMOKE-07` y `SMOKE-08`)

Este registro se completa cuando se implemente `VIS-01`; mientras siga pendiente, las capturas
documentan la línea base, no una paridad ya alcanzada.

1. Con la **misma conversación** en los 3 clientes, captura el mismo intervalo en tema **oscuro**
   y en tema **claro**.
2. Compara contra `DIAGRAMAS/mockups/mockup-chat-telegram-{java,python,dotnet}.png`.
3. Los colores se comparan contra la paleta declarada en los recursos actuales, no contra valores
   anteriores de la auditoría. Anota los valores leídos en los 3 clientes y clasifica diferencias
   por tema/toolkit.
4. Contrastes a medir con el inspector del navegador de toolkit o una herramienta de contraste:
   `.chat-list-preview` en tema claro, el placeholder del campo de búsqueda, y la fecha de la
   burbuja. Todos deben llegar a **4.5:1**.
5. Anota los hex finales de los 2 tokens que divergían, y la ratio de los 4 contrastes.

---

## Búsqueda en el historial (`UIX-06`)

La búsqueda (`Ctrl+F`) y el debounce están implementados en los tres clientes; esta comprobación
verifica el recorrido de usuario y la paridad con datos comunes.

1. `Ctrl+F` en la conversación abierta.
2. Busca un texto que esté en un mensaje **cargado hace varias páginas** (scrollea hacia atrás).
3. Debe encontrarlo sin abrir el historial entero.
4. La búsqueda debe aplicar debounce y mostrar el resultado sin bloquear la interfaz.
5. Repetir en los 3 clientes con el mismo texto: los resultados deben ser los mismos.

---

## Cambio de tema y reconstrucción de la lista (`UIX-26`, `UIX-27`, `UIX-29`)

1. Cambia de oscuro a claro **con 100+ mensajes** en pantalla en cada cliente.
2. El cambio debe ser fluido y **no** recargar la red ni releer las imágenes desde el disco.
3. En Python, el arranque debe usar el tema oscuro por defecto (`UIX-29` ya está implementado) y
   respetar una configuración explícita si se admite en `client.properties`.
4. Comprueba que los iconos de adjunto en modo oscuro usan colores legibles (`UIX-26` sigue en el
   roadmap como pendiente).
5. Comprueba la reconstrucción sin recarga de datos al cambiar de tema en .NET (`UIX-27` sigue en
   el roadmap como pendiente).

## Avisos inline del cliente WPF (`UIX-15`)

1. Provoca avisos/errores recuperables de envío, descarga, adjunto y cierre de otras sesiones.
2. Comprueba que cada aviso aparece en el banner del chat con severidad, se puede cerrar y un aviso
   posterior reemplaza el anterior sin diálogos en cascada.
3. Simula un cierre remoto: el motivo debe aparecer en el banner ámbar del login.
4. Verifica las tres confirmaciones que se conservan: cerrar LoginWindow, salir del chat y cerrar
   todas las sesiones.
5. La cobertura xUnit de `EstadoAvisoInline` está en
   `universidad-mensajeria/client-dotnet.Tests/EstadoAvisoInlineTests.cs`; el último resultado
   registrado fue 71/71, pero esta guía no afirma una ejecución nueva en esta revisión.

## Consola administrativa del servidor (Sesión B 2026-10-07; cierre 2026-10-08)

1. En JavaFX, confirma que Estado presenta cada indicador verticalmente y que el texto no se
   solapa al redimensionar la ventana.
2. En Conectados, revisa el estado vacío «No hay usuarios activos» y, con sesiones abiertas, que
   lista y desplegable muestran `Nombre Apellidos [código]`; clic en la lista cambia el
   desplegable y viceversa. «Desconectar usuario» pide confirmación Sí/No con el nombre
   visible; con `No` no se cierra nada.
3. En Usuarios, confirma la tabla centrada con márgenes al redimensionar; abre un usuario con
   doble clic; la ficha debe mostrar resumen, conexiones, mensajes y archivos, con paginación
   de 50 registros y contacto principal. «Desconectar usuario» también confirma.
4. En la ficha, pestaña Mensajes: filtro por tipo (Todos/TEXTO/IMAGEN/ARCHIVO/BROADCAST),
   tabla + detalle con preview/descarga igual que en Mensajes, divisor movible; al cambiar de
   página conserva la selección por id.
5. En Mensajes, escribe un código y selecciónalo del desplegable; confirma caracteres, palabras,
   IP histórica del remitente y las IP vivas del destinatario (o «No disponible» si está
   offline). Envía una imagen y confirma en la consola JLine el bloque multilínea con hash y
   las 5 rutas derivadas; en texto confirma hash y conteos.
6. En Informes, verifica que el detalle de mensaje/imagen solo está visible para el informe de
   mensajes; pulsa `Actualizar` y confirma que no hay auto-refresh que mueva scroll, selección
   o detalle (el texto de ayuda lo indica); revisa la expansión de Logs, la presentación de
   Pool y el botón «Enviar difusión».
7. Cierra la ventana administrativa con X: debe preguntar si se cierra esa vista; confirmar no
   debe detener el servicio TCP ni JLine.
8. En JLine, recorre títulos, comandos, tablas y eventos; confirma que se conservaron los comandos.
