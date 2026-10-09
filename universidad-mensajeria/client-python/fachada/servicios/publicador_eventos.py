"""Publica eventos de aplicación hacia callbacks de la UI sin mezclar casos de uso."""
import logging

_LOG = logging.getLogger(__name__)


class PublicadorEventosCliente:
    def __init__(self, on_mensaje=None, on_presencia=None, on_cierre=None,
                 on_desconexion=None):
        self._callbacks = {
            "mensaje": on_mensaje,
            "presencia": on_presencia,
            "cierre": on_cierre,
            "desconexion": on_desconexion,
        }

    def _publicar(self, evento, *argumentos):
        callback = self._callbacks[evento]
        if callback is None:
            return
        try:
            callback(*argumentos)
        except Exception:
            _LOG.exception("observador UI de %s falló", evento)

    def mensaje(self, mensaje):
        self._publicar("mensaje", mensaje)

    def presencia(self, evento):
        self._publicar("presencia", evento)

    def cierre(self, aviso):
        self._publicar("cierre", aviso)

    def desconexion(self):
        self._publicar("desconexion")
