"""Cache acotada de imágenes y escalado para las filas del chat."""

from collections import OrderedDict

from PySide6.QtCore import Qt


MAX_CACHE_PIXMAP = 100


class MiniaturasChat:
    """Conserva imágenes recientes y produce su miniatura de presentación."""

    def __init__(self, limite=MAX_CACHE_PIXMAP):
        self._limite = limite
        self._pixmaps = OrderedDict()

    def guardar(self, id_mensaje, pixmap):
        self._pixmaps[id_mensaje] = pixmap
        while len(self._pixmaps) > self._limite:
            self._pixmaps.popitem(last=False)

    def obtener(self, id_mensaje):
        return self._pixmaps.get(id_mensaje)

    def escalar(self, id_mensaje, ancho=420, alto=280):
        pixmap = self.obtener(id_mensaje)
        if pixmap is None or pixmap.isNull():
            return None
        return pixmap.scaled(
            ancho,
            alto,
            Qt.AspectRatioMode.KeepAspectRatio,
            Qt.TransformationMode.SmoothTransformation,
        )
