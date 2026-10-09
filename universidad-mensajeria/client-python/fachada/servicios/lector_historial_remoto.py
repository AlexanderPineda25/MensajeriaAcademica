"""Lectura del historial local/remoto y descargas bajo demanda."""
import base64
import binascii
import datetime
import json
import os

from fachada.servicios.respuestas import lanzar_si_error
from transversal.tipo_mensaje import TipoMensaje


class LectorHistorialRemotoCliente:
    def __init__(self, conexion, historial_actual, codigo_actual, exigir_sesion):
        self.conexion = conexion
        self._historial_actual = historial_actual
        self._codigo_actual = codigo_actual
        self._exigir_sesion = exigir_sesion

    @property
    def historial(self):
        return self._historial_actual()

    @property
    def codigo_actual(self):
        return self._codigo_actual()

    @staticmethod
    def _lanzar_si_error(resp, defecto, ok_tipos=(TipoMensaje.ACK,)):
        return lanzar_si_error(resp, defecto, ok_tipos)

    def descargar_archivo(self, archivo_id, ruta_destino, on_progreso=None):
            """DESCARGAR_ARCHIVO bajo demanda (Fase 6/§7.2): guarda bytes en disco.

            on_progreso(bytes_escritos, total_bytes) se llama al finalizar la descarga.
            Retorna ruta_destino escrita.
            """
            self._exigir_sesion()
            if archivo_id is None or str(archivo_id).strip() == "":
                raise RuntimeError("la descarga necesita el identificador del archivo")
            resp = self.conexion.pedir({
                "tipo": TipoMensaje.DESCARGAR_ARCHIVO,
                "archivoId": str(archivo_id),
                "fechaHora": datetime.datetime.now().isoformat()
            })
            self._lanzar_si_error(resp, "descarga rechazada",
                                  ok_tipos=(TipoMensaje.DESCARGAR_ARCHIVO_RESPUESTA,))
            b64 = resp.get("contenidoImagen") or ""
            datos = base64.b64decode(b64)
            with open(ruta_destino, "wb") as f:
                f.write(datos)
            if on_progreso:
                on_progreso(len(datos), len(datos))
            return ruta_destino

    def marcar_descargado(self, id_mensaje, ruta_archivo):
            self.historial.marcar_descargado(id_mensaje, ruta_archivo)

    def obtener_imagen(self, id_mensaje, ruta_destino):
            """Carga una imagen desde caché o la solicita por archivoId."""
            fila = self.historial.obtener_por_id(id_mensaje)
            if not fila:
                raise RuntimeError("imagen no encontrada en el historial")
            ruta_local = fila.get("ruta_archivo")
            if ruta_local and os.path.isfile(ruta_local):
                return ruta_local
            archivo_id = fila.get("archivo_id")
            if not archivo_id:
                contenido = fila.get("contenido") or ""
                try:
                    crudos = base64.b64decode(contenido, validate=True)
                except (binascii.Error, ValueError):
                    raise RuntimeError("el servidor no entregó el identificador de esta imagen")
                if not crudos:
                    raise RuntimeError("la imagen local está vacía")
                with open(ruta_destino, "wb") as imagen:
                    imagen.write(crudos)
                self.marcar_descargado(id_mensaje, ruta_destino)
                return ruta_destino
            ruta = self.descargar_archivo(archivo_id, ruta_destino)
            self.marcar_descargado(id_mensaje, ruta)
            return ruta

    def obtener_conversacion(self, destinatario, limit=50, offset=0):
            return self.historial.obtener_conversacion(self.codigo_actual, destinatario,
                                                       limit=limit, offset=offset)

    def contar_conversacion(self, destinatario):
            return self.historial.contar_conversacion(self.codigo_actual, destinatario)

    def buscar_en_historial(self, destinatario, patron, limite=200):
            """UIX-06: ids con el patrón (de más nuevo a más viejo)."""
            return [m["id_mensaje"]
                    for m in self.historial.buscar_en_conversacion(
                        self.codigo_actual, destinatario, patron, limite)]

    def traer_historial(self, destinatario, pagina):
            self._exigir_sesion()
            resp = self.conexion.pedir({
                "tipo": TipoMensaje.HISTORIAL_REQ, "remitente": self.codigo_actual,
                "destinatario": destinatario, "pagina": pagina,
                "fechaHora": datetime.datetime.now().isoformat()
            })
            self._lanzar_si_error(resp, "historial rechazado", ok_tipos=(TipoMensaje.HISTORIAL_PAGE,))
            contenido = resp.get("contenido") or "[]"
            try:
                filas = json.loads(contenido)
            except (TypeError, ValueError) as error:
                raise RuntimeError("pagina remota ilegible") from error
            for fila in filas:
                id_mensaje = fila.get("id")
                origen = fila.get("remitente")
                destino = fila.get("destinatario")
                tipo = fila.get("tipo")
                if not id_mensaje or not origen or not destino or not tipo:
                    continue
                propio = origen == self.codigo_actual
                es_img = (tipo == TipoMensaje.MENSAJE_IMAGEN)
                es_arch = (tipo == TipoMensaje.MENSAJE_ARCHIVO)
                contenido_hist = "[Imagen]" if es_img else ("[Archivo]" if es_arch else fila.get("contenido"))
                fecha_remota = fila.get("fechaEnvio") or datetime.datetime.now().isoformat()
                # La fila viva (UUID del remitente) y la remota (id numerico del
                # servidor) son el mismo mensaje: fusionar, no duplicar burbujas.
                conocido = self.historial.buscar_equivalente(
                    origen, destino, tipo, fila.get("hashSha256") or "",
                    fila.get("contenido") or "", fecha_remota)
                if conocido:
                    self.historial.actualizar_metadatos_remotos(
                        conocido, hash_sha256=fila.get("hashSha256") or None,
                        num_caracteres=fila.get("numCaracteres"),
                        num_palabras=fila.get("numPalabras"),
                        nombre_archivo=fila.get("nombreArchivo") or None,
                        tamano_archivo=fila.get("tamanoArchivo"),
                        archivo_id=fila.get("archivoId"),
                        etapas_filtrado=fila.get("etapasFiltrado"),
                        estado="ENVIADO" if propio else "ENTREGADO")
                    continue
                self.historial.guardar_mensaje(
                    id_mensaje, origen, destino, tipo,
                    contenido_hist,
                    fecha_remota,
                    hash_sha256=fila.get("hashSha256") or "",
                    num_caracteres=fila.get("numCaracteres") or 0,
                    num_palabras=fila.get("numPalabras") or 0,
                    nombre_archivo=fila.get("nombreArchivo") or "",
                    tamano_archivo=fila.get("tamanoArchivo") or 0,
                    enviado=1 if propio else 0,
                    archivo_id=fila.get("archivoId"),
                    etapas_filtrado=fila.get("etapasFiltrado"),
                    estado="ENVIADO" if propio else "ENTREGADO")
            return int(resp.get("totalPaginas") or 0)
