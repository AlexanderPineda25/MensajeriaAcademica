"""Historial local por cuenta: CRUD, no leidos, blobs y la cola offline (capa 2).

Los tipos de trama NO se escriben a mano ni aqui ni en el SQL: salen de
`transversal.tipo_mensaje`, que a su vez se compara con `contrato/puertos.json`.
"""
from transversal.tipo_mensaje import CONVERSACION, TipoMensaje
from transversal.etapas_filtrado import mapear_etapas_filtrado
from puertos.datos import RecienteChat

#: Marcadores `?` en el mismo orden que `CONVERSACION` (el SQL los lleva
#: parametrizados para que anadir un tipo no obligue a editar tres consultas).
_CONVERSACION_SQL = ",".join("?" * len(CONVERSACION))
_TIPOS_CONVERSACION = tuple(sorted(CONVERSACION))


class HistorialLocal:
    def __init__(self, db):
        self.db = db

    def guardar_mensaje(self, id_mensaje, origen, destino, tipo, contenido,
                        fecha_envio, hash_sha256="", num_caracteres=0,
                        num_palabras=0, nombre_archivo="", ruta_archivo="",
                        tamano_archivo=0, enviado=1, descargado=0,
                        archivo_id=None, estado="ENVIADO", etapas_filtrado=None):
        etapas_filtrado = mapear_etapas_filtrado(etapas_filtrado)
        with self.db.conexion() as conn:
            conn.execute("""
                INSERT INTO historial_local 
                (id_mensaje, origen, destino, tipo, contenido, hash_sha256,
                 num_caracteres, num_palabras, nombre_archivo, archivo_id,
                 ruta_archivo, tamano_archivo, fecha_envio, enviado, descargado, estado,
                 etapas_filtrado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id_mensaje) DO UPDATE SET
                    contenido = excluded.contenido,
                    hash_sha256 = COALESCE(excluded.hash_sha256, historial_local.hash_sha256),
                    num_caracteres = COALESCE(excluded.num_caracteres, historial_local.num_caracteres),
                    num_palabras = COALESCE(excluded.num_palabras, historial_local.num_palabras),
                    nombre_archivo = COALESCE(excluded.nombre_archivo, historial_local.nombre_archivo),
                    archivo_id = COALESCE(excluded.archivo_id, historial_local.archivo_id),
                    ruta_archivo = COALESCE(excluded.ruta_archivo, historial_local.ruta_archivo),
                    tamano_archivo = COALESCE(excluded.tamano_archivo, historial_local.tamano_archivo),
                    etapas_filtrado = COALESCE(excluded.etapas_filtrado, historial_local.etapas_filtrado),
                    descargado = MAX(excluded.descargado, historial_local.descargado),
                    estado = CASE
                        WHEN historial_local.estado = 'LEIDO' OR excluded.estado = 'LEIDO' THEN 'LEIDO'
                        WHEN historial_local.estado = 'ENTREGADO' AND excluded.estado IN ('PENDIENTE', 'ENVIADO') THEN 'ENTREGADO'
                        WHEN historial_local.estado = 'ENVIADO' AND excluded.estado = 'PENDIENTE' THEN 'ENVIADO'
                        ELSE excluded.estado END
            """, (id_mensaje, origen, destino, tipo, contenido, hash_sha256,
                  num_caracteres, num_palabras, nombre_archivo, archivo_id,
                  ruta_archivo, tamano_archivo, fecha_envio, enviado, descargado, estado,
                  etapas_filtrado))
            
    def obtener_conversacion(self, user1, user2, limit=50, offset=0):
        with self.db.conexion() as conn:
            cursor = conn.execute("""
                SELECT id_mensaje, origen, destino, tipo,
                       CASE WHEN tipo = ? THEN NULL ELSE contenido END AS contenido,
                       hash_sha256, num_caracteres, num_palabras, nombre_archivo,
                       archivo_id, ruta_archivo, tamano_archivo, fecha_envio,
                       enviado, descargado, estado, etapas_filtrado
                FROM historial_local
                WHERE (origen = ? AND destino = ?) OR (origen = ? AND destino = ?)
                ORDER BY fecha_envio DESC
                LIMIT ? OFFSET ?
            """, (TipoMensaje.MENSAJE_IMAGEN, user1, user2, user2, user1,
                  limit, offset))
            return [dict(row) for row in cursor.fetchall()]

    def obtener_por_id(self, id_mensaje):
        with self.db.conexion() as conn:
            fila = conn.execute(
                "SELECT * FROM historial_local WHERE id_mensaje = ?", (id_mensaje,)
            ).fetchone()
            return dict(fila) if fila else None

    def ids_no_leidos(self, yo, otro):
        with self.db.conexion() as conn:
            filas = conn.execute(f"""
                SELECT id_mensaje FROM historial_local
                WHERE origen = ? AND destino = ? AND estado <> 'LEIDO'
                  AND tipo IN ({_CONVERSACION_SQL})
                ORDER BY fecha_envio ASC
            """, (otro, yo, *_TIPOS_CONVERSACION)).fetchall()
            return [fila[0] for fila in filas]

    def contar_no_leidos_por_contacto(self, yo):
        """Badge mockup §3: no leídos agrupados por remitente en una sola query."""
        with self.db.conexion() as conn:
            filas = conn.execute(f"""
                SELECT origen AS codigo, COUNT(*) AS n
                FROM historial_local
                WHERE destino = ? AND estado <> 'LEIDO'
                  AND tipo IN ({_CONVERSACION_SQL})
                GROUP BY origen
            """, (yo, *_TIPOS_CONVERSACION)).fetchall()
            return {fila[0]: fila[1] for fila in filas}

    def marcar_estado_de_conversacion(self, yo, otro, estado):
        with self.db.conexion() as conn:
            conn.execute(f"""
                UPDATE historial_local SET estado = ?
                WHERE origen = ? AND destino = ? AND tipo IN ({_CONVERSACION_SQL})
            """, (estado, otro, yo, *_TIPOS_CONVERSACION))

    def buscar_en_conversacion(self, user1, user2, patron, limite=200):
        """UIX-06: mensajes con el patrón en texto o nombre de archivo.

        Mismo contrato que `buscar` (Java) y `BuscarEnConversacion` (.NET):
        insensible a mayúsculas, `%`/`_`/`\\` literales, de más nuevo a más
        viejo. La UI salta entre los ids.
        """
        if not patron or not patron.strip():
            return []
        like = "%" + patron.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        with self.db.conexion() as conn:
            return [dict(row) for row in conn.execute(
                """SELECT id_mensaje, origen, destino, tipo, contenido,
                          fecha_envio, estado, nombre_archivo, archivo_id
                   FROM historial_local
                   WHERE ((origen = ? AND destino = ?)
                       OR (origen = ? AND destino = ?))
                     AND (LOWER(contenido) LIKE LOWER(?) ESCAPE '\\'
                       OR LOWER(nombre_archivo) LIKE LOWER(?) ESCAPE '\\')
                   ORDER BY fecha_envio DESC
                   LIMIT ?""",
                (user1, user2, user2, user1, like, like, limite)).fetchall()]

    def buscar_equivalente(self, origen, destino, tipo, hash_sha256="",
                             contenido="", fecha_envio="", ventana_seg=300):
        """Id local de un mensaje ya conocido (vivo) igual al remoto.

        La entrega en vivo usa el UUID del remitente y el historial remoto
        el id numerico del servidor: sin esto cada fetch remoto duplicaba
        las burbujas. Para TEXTO/BROADCAST/SYNC se compara contenido exacto
        con ventana de tiempo; para IMAGEN/ARCHIVO solo hash (los
        placeholders "[Imagen]"/"[Archivo]" no identifican nada).
        Retorna id_mensaje o None.
        """
        if tipo in (TipoMensaje.MENSAJE_IMAGEN, TipoMensaje.MENSAJE_ARCHIVO):
            if not hash_sha256:
                return None
            criterio = "hash_sha256 = ?"
            params = [origen, destino, origen, destino, tipo, hash_sha256]
        else:
            texto = contenido or ""
            if not texto or texto.startswith("[Imagen]") \
                    or texto.startswith("[Archivo]"):
                return None
            criterio = ("contenido = ? AND ABS(strftime('%s', fecha_envio)"
                        " - strftime('%s', ?)) < ?")
            params = [origen, destino, origen, destino, tipo,
                      texto, fecha_envio or "", ventana_seg]
        with self.db.conexion() as conn:
            fila = conn.execute(
                f"""SELECT id_mensaje FROM historial_local
                    WHERE ((origen = ? AND destino = ?)
                        OR (origen = ? AND destino = ?))
                      AND tipo = ? AND {criterio}
                    ORDER BY fecha_envio DESC LIMIT 1""",
                params).fetchone()
            return fila[0] if fila else None

    def actualizar_metadatos_remotos(self, id_mensaje, hash_sha256=None,
                                     num_caracteres=None, num_palabras=None,
                                     nombre_archivo=None, tamano_archivo=None,
                                     archivo_id=None, estado=None,
                                     etapas_filtrado=None):
        """Completa la fila viva con metadatos del historial remoto (sin duplicar)."""
        etapas_filtrado = mapear_etapas_filtrado(etapas_filtrado)
        with self.db.conexion() as conn:
            conn.execute("""
                UPDATE historial_local SET
                    hash_sha256 = COALESCE(?, hash_sha256),
                    num_caracteres = COALESCE(?, num_caracteres),
                    num_palabras = COALESCE(?, num_palabras),
                    nombre_archivo = COALESCE(?, nombre_archivo),
                    tamano_archivo = COALESCE(?, tamano_archivo),
                    archivo_id = COALESCE(?, archivo_id),
                    etapas_filtrado = COALESCE(?, etapas_filtrado),
                    estado = CASE
                        WHEN historial_local.estado = 'LEIDO' OR ? = 'LEIDO' THEN 'LEIDO'
                        WHEN historial_local.estado = 'ENTREGADO'
                             AND ? IN ('PENDIENTE', 'ENVIADO') THEN 'ENTREGADO'
                        WHEN historial_local.estado = 'ENVIADO' AND ? = 'PENDIENTE' THEN 'ENVIADO'
                        ELSE COALESCE(?, historial_local.estado) END
                WHERE id_mensaje = ?
            """, (hash_sha256 or None, num_caracteres, num_palabras,
                  nombre_archivo or None, tamano_archivo,
                  archivo_id or None, etapas_filtrado, estado, estado, estado, estado,
                  id_mensaje))

    def contar_conversacion(self, user1, user2):
        """HISTORIAL_LOCAL.md §2: conteo para totalPaginas."""
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT COUNT(*)
                FROM historial_local
                WHERE (origen = ? AND destino = ?)
                   OR (origen = ? AND destino = ?)
            """, (user1, user2, user2, user1))
            return cursor.fetchone()[0]

    def recientes(self, yo):
        """Último mensaje y no leídos por contacto; excluye broadcasts propios."""
        with self.db.conexion() as conn:
            filas = conn.execute(f"""
                WITH ordenados AS (
                    SELECT CASE WHEN origen = ? THEN destino ELSE origen END AS otro,
                           contenido, tipo, fecha_envio,
                           ROW_NUMBER() OVER (
                               PARTITION BY CASE WHEN origen = ? THEN destino ELSE origen END
                               ORDER BY fecha_envio DESC, id_mensaje DESC
                           ) AS numero
                    FROM historial_local
                    WHERE ((origen = ? AND destino <> ? AND destino <> '*')
                        OR (destino = ? AND origen <> ? AND origen <> '*'))
                      AND tipo IN ({_CONVERSACION_SQL})
                )
                SELECT actual.otro, actual.contenido, actual.tipo, actual.fecha_envio,
                       (SELECT COUNT(*) FROM historial_local AS no_leidos
                        WHERE no_leidos.origen = actual.otro
                          AND no_leidos.destino = ?
                          AND no_leidos.estado <> 'LEIDO'
                          AND no_leidos.tipo IN ({_CONVERSACION_SQL})) AS no_leidos
                FROM ordenados AS actual
                WHERE actual.numero = 1
                ORDER BY actual.fecha_envio DESC
            """, (yo, yo, yo, yo, yo, yo,
                  *_TIPOS_CONVERSACION, yo, *_TIPOS_CONVERSACION)).fetchall()
        return [RecienteChat(
            fila["otro"], fila["contenido"], fila["tipo"], fila["fecha_envio"],
            int(fila["no_leidos"]), False)
            for fila in filas]

    def marcar_descargado(self, id_mensaje, ruta_archivo):
        """HISTORIAL_LOCAL.md §3: marca archivo descargado y guarda su ruta."""
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE historial_local
                SET descargado = 1, ruta_archivo = ?
                WHERE id_mensaje = ?
            """, (ruta_archivo, id_mensaje))
            conn.commit()

    def marcar_estado(self, id_mensaje, estado):
        with self.db.conexion() as conn:
            conn.execute("""
                UPDATE historial_local SET estado = CASE
                    WHEN estado = 'LEIDO' OR ? = 'LEIDO' THEN 'LEIDO'
                    WHEN estado = 'ENTREGADO' AND ? IN ('PENDIENTE', 'ENVIADO') THEN estado
                    WHEN estado = 'ENVIADO' AND ? = 'PENDIENTE' THEN estado
                    ELSE ? END
                WHERE id_mensaje = ?
            """, (estado, estado, estado, estado, id_mensaje))

    def actualizar_archivo_id(self, id_mensaje, archivo_id):
        with self.db.conexion() as conn:
            conn.execute("UPDATE historial_local SET archivo_id = ? WHERE id_mensaje = ?",
                         (archivo_id, id_mensaje))

    def actualizar_etapas_filtrado(self, id_mensaje, etapas_filtrado):
        etapas_filtrado = mapear_etapas_filtrado(etapas_filtrado)
        with self.db.conexion() as conn:
            conn.execute(
                "UPDATE historial_local SET etapas_filtrado = ? WHERE id_mensaje = ?",
                (etapas_filtrado, id_mensaje))

    def actualizar_hash(self, id_mensaje, hash_sha256):
        with self.db.conexion() as conn:
            conn.execute("UPDATE historial_local SET hash_sha256 = ? WHERE id_mensaje = ?",
                         (hash_sha256, id_mensaje))

    def guardar_pendiente(self, id_pend, tipo, origen, destino, contenido, nombre_archivo, payload, fecha_creado):
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            # Upsert portable del contrato: pre-DELETE para re-envios del mismo id.
            cursor.execute("DELETE FROM pendientes_envio WHERE id = ?", (id_pend,))
            cursor.execute("""
                INSERT INTO pendientes_envio (id, tipo, origen, destino, contenido, nombre_archivo, payload, fecha_creado, intentos)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """, (id_pend, tipo, origen, destino, contenido, nombre_archivo, payload, fecha_creado))
            conn.commit()

    def obtener_pendientes(self):
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM pendientes_envio ORDER BY fecha_creado ASC")
            return [dict(row) for row in cursor.fetchall()]

    def eliminar_pendiente(self, id_pend):
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM pendientes_envio WHERE id = ?", (id_pend,))
            conn.commit()

    def incrementar_intento(self, id_pend, ultimo_error):
        """HISTORIAL_LOCAL.md §5: cuenta reintentos del pendiente."""
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE pendientes_envio
                SET intentos = intentos + 1, ultimo_error = ?
                WHERE id = ?
            """, (ultimo_error, id_pend))
            conn.commit()
            
    def actualizar_cache_usuario(self, codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado):
        self.actualizar_cache_usuarios(
            [(codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado)])

    def actualizar_cache_usuarios(self, usuarios):
        """Lote en UNA conexión y UN commit.

        Antes `listar_usuarios` hacía DELETE+INSERT por usuario abriendo cada
        vez la base (N conexiones = N fsyncs en WAL): con cientos de contactos
        el directorio tardaba segundos en cargar. Mismo upsert portable
        DELETE+INSERT, una sola transacción.
        """
        filas = list(usuarios)
        if not filas:
            return
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.executemany("DELETE FROM cache_usuarios WHERE codigo = ?",
                               ((fila[0],) for fila in filas))
            cursor.executemany("""
                INSERT INTO cache_usuarios (codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, filas)
            conn.commit()

    def listar_cache_usuarios(self):
        """HISTORIAL_LOCAL.md §7: directorio local, conectados primero."""
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado
                FROM cache_usuarios
                ORDER BY conectado DESC, apellidos ASC
            """)
            return [dict(row) for row in cursor.fetchall()]

    def marcar_conectados(self, codigos):
        """Refresca banderas de conexion con el listado del servidor."""
        with self.db.conexion() as conn:
            cursor = conn.cursor()
            cursor.execute("UPDATE cache_usuarios SET conectado = 0")
            for codigo in codigos:
                cursor.execute("UPDATE cache_usuarios SET conectado = 1 WHERE codigo = ?", (codigo,))
            conn.commit()
