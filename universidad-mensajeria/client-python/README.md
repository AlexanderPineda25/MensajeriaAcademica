# Despliegue del cliente Python

Cliente de escritorio PySide6/QFluentWidgets. Esta carpeta contiene la aplicación Python y su
copia de `store/schema-local.sql`; no necesita el monorepo ni acceso a la base MySQL del servidor.

## Requisitos e instalación

Windows 10/11 de 64 bits y Python 3.13 recomendado (es el runtime de validación; el mínimo
declarado en `pyproject.toml` es 3.11). Las versiones de PySide6, QFluentWidgets y Pillow están
fijadas en `requirements.txt`.

Desde la carpeta `client-python`:

```powershell
py -3.13 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe main.py
```

Inicia siempre desde la carpeta de la aplicación: `client.properties` se busca relativo al
directorio de trabajo. Instala la carpeta bajo un directorio donde el usuario tenga permisos de
escritura; las imágenes recibidas se almacenan en la subcarpeta `imagenes`.

## Preparar y copiar la carpeta

La forma oficial es el script `distribuir.cmd`, ejecutado desde `client-python`:

```powershell
cmd distribuir.cmd
```

Genera `dist\MensajeriaPython\` sin `.venv`, cachés, pruebas, imágenes recibidas (`imagenes/`) ni
bases SQLite. **Sí incluye los recursos gráficos estáticos de la interfaz**, en particular
`ui\fondos\fondo_chat_claro.jpg` y `ui\fondos\fondo_chat_oscuro.jpg`. Verifica el contenido mínimo
(`main.py`, `requirements.txt`, `client.properties`, `store\schema-local.sql`, fachada y UI), compila
la copia con `compileall` para detectar errores de sintaxis y deja un `version.txt` con la fecha de
generación.

Si prefieres hacerlo a mano, copia el contenido de `client-python` excluyendo el entorno virtual,
las cachés y las pruebas. No copies `.venv`: contiene rutas y binarios ligados al equipo donde se
creó.

```powershell
$dist = "$env:USERPROFILE\Desktop\MensajeriaPython"
robocopy . $dist /E /XD .venv __pycache__ tests .pytest_cache imagenes dist /XF *.db *.db-shm *.db-wal *.pyc
```

`robocopy` puede devolver códigos `1` a `7` cuando la copia terminó correctamente. En el destino,
repite los pasos de instalación del entorno virtual y ejecuta `main.py`.

## Configuración

Edita `client.properties` en la carpeta del cliente:

```properties
host=192.168.1.20
puerto=5000
db.url=sqlite:///~/mensajeria_cliente.db
idioma=es
tema=oscuro
tls.habilitado=false
tls.puerto=5001
tls.confianza=
```

Reemplaza `192.168.1.20` por la IP LAN o el nombre DNS del servidor. `localhost` solo sirve cuando
Python y el servidor están en el mismo computador. `puerto` debe coincidir con `red.puerto` del
servidor. El cliente necesita salida TCP a ese puerto; no requiere puertos entrantes ni acceso a
MySQL.

**TLS opcional** (`transversal/tls.py`): con `tls.habilitado=true` el cliente conecta al puerto
`tls.puerto` (5001 por defecto) con TLS ≥ 1.2 y verificación de certificado y hostname siempre
activas. `tls.confianza` apunta a un bundle PEM con la CA propia; vacío, se usan las CA del
sistema. La validación es fail-fast antes de abrir el socket: un valor de `tls.habilitado` que no
sea `true`/`false`, puertos plano y TLS iguales, o una ruta de confianza indicada pero ausente
producen un error accionable en lugar de una conexión caída.

SQLite vive en el perfil del usuario y se deriva una ruta por código de cuenta. El primer acceso
migra de manera aditiva el archivo legado compartido si existe. No borres ese archivo antes de
iniciar por primera vez. `store/schema-local.sql` está incluido para que la migración y el primer
arranque también funcionen cuando solo se distribuye esta carpeta.

## Funcionalidad

- Acceso con código y contraseña (sin registro); la ventana muestra servidor
  y puerto con opción de recordarlos. Tema claro/oscuro persistente y aplicado
  también a los controles (campos, combo y botón cambian con el modo).
- El panel lateral es una lista de conversaciones estilo WhatsApp: tarjeta con
  punto de presencia, nombre, preview del último mensaje, hora relativa e
  insignia de no leídos; lo más nuevo arriba y la presencia no reordena (los
  contactos sin conversación van al final). Filtro Todos/Conectados/Con no
  leídos y buscador. La lista se pinta al instante desde la caché SQLite y la
  red la refresca por detrás; el directorio se escribe en lote (una sola
  transacción). El historial remoto se fusiona sin duplicar burbujas ya
  recibidas en vivo.
- Enviar admite texto, imagen o archivo genérico hasta 50 MB por el
  protocolo fragmentado (`ARCHIVO_INICIO/PARTE/FIN`); el `FIN` recibe `ACK`
  del servidor. Los archivos recibidos se descargan bajo demanda.
- La difusión del servidor aparece como mensaje de `SERVIDOR`.
- Sin red, los envíos quedan en una cola local y se reintentan solos cada 30 s
  (`INTERVALO_FLUSH_SEGUNDOS`): si la red vuelve, la cola se vacía sin que el
  usuario toque nada. El intento se para al cerrar sesión o desconectar.

## Uso y solución de problemas

- El registro por red está eliminado; solicita al administrador código y contraseña.
- Si no conecta, prueba `Test-NetConnection IP_SERVIDOR -Port 5000` y revisa firewall/IP/puerto.
- Si la UI dice que envió pero no aparece el mensaje, conserva los logs/estado local y verifica el
  acuse y la conexión; no edites SQLite para “forzar” un visto.
- Conserva juntos `main.py`, paquetes (`ui`, `fachada`, `componentes`, `store`, `transversal`,
  `puertos`), `requirements.txt`, `client.properties` y `store/schema-local.sql`.
- Para actualizar, reemplaza los archivos de aplicación pero conserva la base SQLite del usuario.

## Pruebas de desarrollo

Desde esta carpeta y con las dependencias instaladas (pytest está en el grupo `dev` de
`pyproject.toml`):

```powershell
.\.venv\Scripts\python.exe -m pip install pytest
.\.venv\Scripts\python.exe -m pytest tests
```

También funciona con unittest:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests
```

Última ejecución anotada (2026-10-08): 222 aprobados, 5 omitidos y 741 subtests con pytest.
