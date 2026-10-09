"""Comando independiente de UI para enviar texto y un adjunto."""

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class EnviarMensajeComando:
    usuario: str
    texto: str
    id_texto: str | None
    ruta_adjunto: str | None
    id_adjunto: str | None
    extensiones_imagen: frozenset[str]

    def ejecutar(self, puerto):
        """Envía las partes en orden y devuelve el contrato del worker de UI."""
        en_red = True
        ids_enviados = []
        if self.id_texto:
            ok = self._invocar(
                puerto.enviar_texto, self.usuario, self.texto, self.id_texto)
            en_red = (ok is not False) and en_red
            ids_enviados.append(self.id_texto)
        if self.id_adjunto and self.ruta_adjunto:
            extension = Path(self.ruta_adjunto).suffix.casefold()
            metodo = (puerto.enviar_imagen
                      if extension in self.extensiones_imagen
                      else puerto.enviar_archivo)
            ok = self._invocar(
                metodo, self.usuario, self.ruta_adjunto, self.id_adjunto)
            en_red = (ok is not False) and en_red
            ids_enviados.append(self.id_adjunto)
        return self.usuario, en_red, ids_enviados

    @staticmethod
    def _invocar(metodo, usuario, contenido, id_mensaje):
        try:
            return metodo(usuario, contenido, id_msg=id_mensaje)
        except TypeError:
            # Compatibilidad con fachadas de prueba o integraciones antiguas.
            return metodo(usuario, contenido)
