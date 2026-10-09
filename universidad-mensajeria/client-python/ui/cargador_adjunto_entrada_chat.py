"""Carga imágenes entrantes desde caché, contenido recibido o transporte."""

import base64
from pathlib import Path

from PySide6.QtGui import QImage


class CargadorAdjuntoEntradaChat:
    def cargar(self, mensaje, directorio, obtener_imagen):
        carpeta = Path(directorio)
        carpeta.mkdir(parents=True, exist_ok=True)
        extension = Path(mensaje.get("filename") or "").suffix or ".img"
        ruta = mensaje.get("path")
        if not ruta or not Path(ruta).is_file():
            ruta = str(carpeta / ((mensaje.get("id") or "imagen") + extension))
            imagen_b64 = mensaje.get("image_base64")
            if imagen_b64:
                Path(ruta).write_bytes(base64.b64decode(imagen_b64))
            else:
                obtener_imagen(mensaje["id"], ruta)
        imagen = QImage(ruta)
        if imagen.isNull():
            raise RuntimeError("El archivo descargado no es una imagen válida")
        return mensaje.get("id"), imagen, ruta
