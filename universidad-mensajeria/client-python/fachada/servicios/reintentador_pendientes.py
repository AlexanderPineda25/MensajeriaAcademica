"""Reenvía la cola offline en orden y conserva el primer fallo para reintento."""
import base64
import hashlib
import logging
import mimetypes
import threading
from threading import Event

from fachada.servicios.respuestas import lanzar_si_error
from transversal.tipo_mensaje import TipoMensaje

_PARTE_MAX_BYTES = 1 * 1024 * 1024
INTERVALO_FLUSH_SEGUNDOS = 30
INTERVALO_FLUSH_MINIMO_SEGUNDOS = 5
_REGISTRO = logging.getLogger(__name__)


class ReintentadorPendientesCliente:
    def __init__(self, conexion, historial_actual, codigo_actual,
                 reintentar_callback=None):
        self.conexion = conexion
        self._historial_actual = historial_actual
        self._codigo_actual = codigo_actual
        self._reintentar_callback = reintentar_callback
        self._flush_lock = threading.Lock()
        self._flush_hilo = None
        self._flush_parar = None

    def iniciar_flush_pendientes(self, periodo_segundos=INTERVALO_FLUSH_SEGUNDOS):
        """Arranca un único worker daemon, con un mínimo de cinco segundos."""
        with self._flush_lock:
            if self._flush_hilo is not None and self._flush_hilo.is_alive():
                return
            parar = threading.Event()
            periodo = max(INTERVALO_FLUSH_MINIMO_SEGUNDOS, periodo_segundos)
            self._flush_hilo = threading.Thread(
                target=self._bucle_flush, args=(periodo, parar),
                name="cliente-flush", daemon=True)
            self._flush_parar = parar
            self._flush_hilo.start()

    def detener_flush_pendientes(self):
        """Detiene el worker de forma idempotente."""
        with self._flush_lock:
            parar, self._flush_parar = self._flush_parar, None
            self._flush_hilo = None
        if parar is not None:
            parar.set()

    def flush_activo(self):
        return self._flush_hilo is not None and self._flush_hilo.is_alive()

    def _bucle_flush(self, periodo_segundos, parar: Event):
        while not parar.wait(periodo_segundos):
            try:
                callback = self._reintentar_callback or self.reintentar_pendientes
                callback()
            except Exception as error:  # noqa: BLE001 - el worker debe sobrevivir
                _REGISTRO.info("[flush] reintento omitido: %s", error)

    @property
    def historial(self):
        return self._historial_actual()

    @property
    def codigo_actual(self):
        return self._codigo_actual()

    @staticmethod
    def _lanzar_si_error(resp, defecto, ok_tipos=(TipoMensaje.ACK,)):
        return lanzar_si_error(resp, defecto, ok_tipos)

    def pendientes(self):
            return self.historial.obtener_pendientes()

    def reintentar_pendientes(self):
            """Reenvia pendientes en orden con ACK; devuelve cuantos salieron."""
            if not self.codigo_actual or not self.conexion.conectado:
                return 0
            enviados = 0
            for p in self.historial.obtener_pendientes():
                if p["tipo"] == TipoMensaje.MENSAJE_LEIDO:
                    req = {
                        "id": p["id"], "tipo": TipoMensaje.MENSAJE_LEIDO,
                        "remitente": p["origen"], "destinatario": p["destino"],
                        "contenido": p["contenido"],
                        "fechaHora": p["fecha_creado"]
                    }
                    try:
                        self.conexion.enviar_async(req)
                        self.historial.eliminar_pendiente(p["id"])
                        enviados += 1
                    except (ConnectionError, TimeoutError, OSError) as e:
                        self.historial.incrementar_intento(p["id"], str(e))
                        break
                    continue

                if p["tipo"] == TipoMensaje.MENSAJE_ARCHIVO:
                    datos = p["payload"] or b""
                    sha256 = hashlib.sha256(datos).hexdigest()
                    nombre = p["nombre_archivo"] or "archivo"
                    mime = mimetypes.guess_type(nombre)[0] or "application/octet-stream"
                    partes = [datos[i:i + _PARTE_MAX_BYTES] for i in range(0, len(datos), _PARTE_MAX_BYTES)]
                    total_partes = max(1, len(partes))
                    try:
                        self.conexion.pedir({
                            "id": p["id"],
                            "tipo": TipoMensaje.ARCHIVO_INICIO,
                            "remitente": p["origen"],
                            "destinatario": p["destino"],
                            "nombreArchivo": nombre,
                            "mime": mime,
                            "tamanoArchivo": len(datos),
                            "totalPartes": total_partes,
                            "fechaHora": p["fecha_creado"],
                        })
                        for i, parte in enumerate(partes):
                            self.conexion.pedir({
                                "id": p["id"],
                                "tipo": TipoMensaje.ARCHIVO_PARTE,
                                "remitente": p["origen"],
                                "archivoId": p["id"],
                                "indiceParte": i,
                                "contenidoImagen": base64.b64encode(parte).decode("utf-8"),
                                "fechaHora": p["fecha_creado"],
                            })
                        resp = self.conexion.pedir({
                            "id": p["id"],
                            "tipo": TipoMensaje.ARCHIVO_FIN,
                            "remitente": p["origen"],
                            "archivoId": p["id"],
                            "hashSha256": sha256,
                            "fechaHora": p["fecha_creado"],
                        })
                        self._lanzar_si_error(resp, "reintento archivo rechazado")
                        if resp.get("archivoId"):
                            self.historial.actualizar_archivo_id(p["id"], resp["archivoId"])
                        self.historial.marcar_estado(p["id"], "ENVIADO")
                        self.historial.eliminar_pendiente(p["id"])
                        enviados += 1
                    except (ConnectionError, TimeoutError) as e:
                        self.historial.incrementar_intento(p["id"], str(e))
                        break
                    except RuntimeError as e:
                        self.historial.incrementar_intento(p["id"], str(e))
                        break
                    continue

                req = {
                    "id": p["id"],
                    "tipo": p["tipo"],
                    "remitente": p["origen"],
                    "destinatario": p["destino"],
                    "fechaHora": p["fecha_creado"]
                }
                if p["tipo"] == TipoMensaje.MENSAJE_TEXTO:
                    req["contenido"] = p["contenido"]
                elif p["tipo"] == TipoMensaje.MENSAJE_IMAGEN:
                    payload = p["payload"] or b""
                    req["contenidoImagen"] = base64.b64encode(payload).decode('utf-8')
                    req["nombreArchivo"] = p["nombre_archivo"]
                try:
                    resp = self.conexion.pedir(req)
                    self._lanzar_si_error(resp, "reintento rechazado")
                    if p["tipo"] == TipoMensaje.MENSAJE_IMAGEN:
                        self.historial.actualizar_metadatos_remotos(
                            p["id"], archivo_id=resp.get("archivoId"),
                            hash_sha256=resp.get("hashSha256"),
                            etapas_filtrado=resp.get("etapasFiltrado"))
                    if p["tipo"] in (TipoMensaje.MENSAJE_IMAGEN, TipoMensaje.MENSAJE_TEXTO):
                        self.historial.marcar_estado(p["id"], "ENVIADO")
                    self.historial.eliminar_pendiente(p["id"])
                    enviados += 1
                except (ConnectionError, TimeoutError) as e:
                    self.historial.incrementar_intento(p["id"], str(e))
                    break
                except RuntimeError as e:
                    self.historial.incrementar_intento(p["id"], str(e))
                    break
            return enviados
