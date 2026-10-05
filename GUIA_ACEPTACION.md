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

Comprobación **manual y de bajo nivel**, porque ninguna suite la cubre hoy. Es la aceptación de
[`SEC-05`](ESTADO_ROADMAP.md#seguridad-bloqueante).

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
3. **Comportamiento defectuoso (el que hay hoy):** llega `LISTAR_USUARIOS_RESPUESTA` con el
   contenido de `nombres`, `apellidos`, `programa` y `conectado` de toda la comunidad.
4. Repite con `LISTAR_CONECTADOS` y `LOGOUT`. Las 3 deben devolver `ERROR`.
5. Repite los 4 pasos con **TLS** en `5001` para confirmar que el cifrado no cambia el resultado.

### Después de cerrarlo

La prueba automática que lo fija es un test **parametrizado** que itera **todos** los tipos
registrados en `Despachador` y exige `ERROR` con sesión anónima. Si añades un comando nuevo y no lo
registras en esa prueba, el hueco vuelve: por eso la prueba debe recorrer el registro, no una lista
escrita a mano.

---

## Estados de conexión (`SMOKE-06`, espera a `PAT-03`)

Matriz de 4 estados × 3 clientes. Los 4 estados son `Conectando`, `Conectado`, `SinRed` y
`SesionCerrada`.

| Transición | Cómo se provoca | Qué se espera en los 3 clientes | Anotar |
|---|---|---|---|
| → `Conectando` | Login con el servidor apagado y luego encendiéndolo | Indicador de carga, **no** texto de error. .NET hoy falla aquí: pinta "Conectando…" en la caja roja (`UIX-00f`) | |
| → `Conectado` | Login correcto | El banner ámbar desaparece; el directorio carga | |
| → `SinRed` | Matar el proceso del servidor con el cliente abierto | Banner ámbar en ≤ 2 s. **.NET hoy no muestra nada**: `OnDesconectado` no tiene suscriptores y `bannerOffline` nunca se activa (`UIX-00a`). Python solo lo cambia de paso al refrescar el directorio | |
| → `SesionCerrada` | `Salir`, o `Salir en todos` desde otra sesión, o esperar 10 min | Aviso con el **motivo** y vuelta al login | |
| `SinRed` → `Conectando` | Reencender el servidor | Reconexión **sin** que el usuario toque nada; la cola se reintenta sin duplicar | |

---

## Capturas lado a lado (`SMOKE-07` y `SMOKE-08`)

Objetivo: comprobar que `VIS-01` unificó la paleta y que los contrastes pasan.

1. Con la **misma conversación** en los 3 clientes, captura el mismo intervalo en tema **oscuro**
   y en tema **claro**.
2. Compara contra `DIAGRAMAS/mockups/mockup-chat-telegram-{java,python,dotnet}.png`.
3. Los **11 colores del tema oscuro** deben coincidir en los 3. Antes de `VIS-01` el tema **claro**
   no coincidía en ninguno: la burbuja propia era `#E4F3EC` / `#E1F5FE` / `#D9FDD3` y el acento
   `#2456A6` / `#147D73` / el de WPF-UI.
4. Contrastes a medir con el inspector del navegador de toolkit o una herramienta de contraste:
   `.chat-list-preview` en tema claro, el placeholder del campo de búsqueda, y la fecha de la
   burbuja. Todos deben llegar a **4.5:1**.
5. Anota los hex finales de los 2 tokens que divergían, y la ratio de los 4 contrastes.

---

## Búsqueda en el historial (`UIX-06`, cuando exista)

No existe hoy en ninguno de los 3 clientes, así que esta comprobación **no aplica** hasta que se
implemente.

1. `Ctrl+F` en la conversación abierta.
2. Busca un texto que esté en un mensaje **cargado hace varias páginas** (scrollea hacia atrás).
3. Debe encontrarlo sin abrir el historial entero.
4. La búsqueda debe tener retardo perceptible al teclear (debounce), no un refiltrado por tecla.
5. Repetir en los 3 clientes con el mismo texto: los resultados deben ser los mismos.

---

## Cambio de tema y reconstruction de la lista (`UIX-27`, `UIX-29`)

1. Cambia de oscuro a claro **con 100+ mensajes** en pantalla en cada cliente.
2. El cambio debe ser fluido y **no** recargar la red ni releer las imágenes desde el disco.
3. En Python el arranque debe respetar `tema=` de `client.properties` (hoy `main.py:20` fuerza tema
   claro).
4. En modo oscuro, los iconos de adjunto **sin imagen** deben usar el color del tema oscuro (hoy
   Python pone los de tema claro).
