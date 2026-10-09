"""Adapta avisos asíncronos entrantes y los deriva a historial y UI."""
import logging
from typing import Callable

from fachada.servicios.publicador_eventos import PublicadorEventosCliente
from transversal.estado_conexion import SESION_CERRADA, SIN_RED
from transversal.tipo_mensaje import CONVERSACION, TipoMensaje

_LOG = logging.getLogger(__name__)


class ReceptorEntranteCliente:
    def __init__(self, historial_actual: Callable[[], object],
                 codigo_actual: Callable[[], str | None],
                 estado_actual: Callable[[], str], cambiar_estado: Callable[[str], None],
                 sincronizador, publicador: PublicadorEventosCliente,
                 gestor_reconexion):
        self._historial_actual = historial_actual
        self._codigo_actual = codigo_actual
        self._estado_actual = estado_actual
        self._cambiar_estado = cambiar_estado
        self._sincronizador = sincronizador
        self._publicador = publicador
        self._gestor_reconexion = gestor_reconexion

    @property
    def historial(self):
        return self._historial_actual()

    @property
    def codigo_actual(self):
        return self._codigo_actual()

    def al_recibir(self, mensaje):
        tipo = mensaje.get("tipo")
        if tipo in (TipoMensaje.MENSAJE_ENTREGADO, TipoMensaje.MENSAJE_LEIDO):
            id_mensaje = mensaje.get("contenido")
            if id_mensaje:
                estado = "ENTREGADO" if tipo == TipoMensaje.MENSAJE_ENTREGADO else "LEIDO"
                self.historial.marcar_estado(id_mensaje, estado)
            self._publicador.mensaje(mensaje)
            return
        if tipo == TipoMensaje.PRESENCIA:
            conectados = mensaje.get("usuariosConectados") or []
            self.historial.marcar_conectados(conectados)
            for codigo in conectados:
                self._sincronizador._asegurar_en_cache(codigo)
            self._publicador.presencia(mensaje)
            return
        if tipo not in CONVERSACION:
            return

        # En mensajes entrantes, el destino es siempre la cuenta local. Así
        # los broadcasts aparecen en el chat del remitente y los propios no
        # se confunden con mensajes enviados por el otro usuario.
        es_imagen = tipo == TipoMensaje.MENSAJE_IMAGEN
        es_archivo = tipo == TipoMensaje.MENSAJE_ARCHIVO
        self.historial.guardar_mensaje(
            id_mensaje=mensaje.get("id"),
            origen=mensaje.get("remitente"),
            destino=self.codigo_actual,
            tipo=tipo,
            contenido=("[Imagen]" if es_imagen else "[Archivo]" if es_archivo
                       else mensaje.get("contenido")),
            fecha_envio=mensaje.get("fechaHora"),
            hash_sha256=mensaje.get("hashSha256") or "",
            num_caracteres=mensaje.get("numCaracteres") or 0,
            num_palabras=mensaje.get("numPalabras") or 0,
            nombre_archivo=mensaje.get("nombreArchivo") or mensaje.get("nombre") or "",
            tamano_archivo=mensaje.get("tamanoArchivo") or mensaje.get("tamano") or 0,
            descargado=1 if tipo == TipoMensaje.MENSAJE_TEXTO else 0,
            archivo_id=mensaje.get("archivoId"), estado="ENVIADO",
            etapas_filtrado=mensaje.get("etapasFiltrado"),
        )
        if mensaje.get("remitente"):
            self._sincronizador._asegurar_en_cache(mensaje["remitente"])
        self._publicador.mensaje(mensaje)

    def al_cierre(self, aviso):
        self._gestor_reconexion.sesion_cerrada_remotamente()
        self._publicador.cierre(aviso)

    def al_error(self, motivo):
        _LOG.warning("error asíncrono del servidor: %s", motivo)

    def al_desconexion(self):
        if self._estado_actual() != SESION_CERRADA:
            self._cambiar_estado(SIN_RED)
        self._gestor_reconexion.programar_reconexion()
        self._publicador.desconexion()
