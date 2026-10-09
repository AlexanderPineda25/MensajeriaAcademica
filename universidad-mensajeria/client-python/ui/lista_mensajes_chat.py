"""Lista virtualizada con un fondo de conversación escalado a modo cover."""

from PySide6.QtCore import Qt
from PySide6.QtGui import QBrush, QImage, QPalette, QPixmap
from PySide6.QtWidgets import QListView


class ListaMensajesChat(QListView):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._imagen_fondo = QImage()
        self.viewport().setBackgroundRole(QPalette.ColorRole.Base)
        self.viewport().setAutoFillBackground(True)

    def fijar_fondo(self, ruta):
        self._imagen_fondo = QImage(str(ruta))
        self._actualizar_fondo()

    def resizeEvent(self, evento):
        super().resizeEvent(evento)
        self._actualizar_fondo()

    def _actualizar_fondo(self):
        tamano = self.viewport().size()
        if self._imagen_fondo.isNull() or tamano.isEmpty():
            return
        escalada = self._imagen_fondo.scaled(
            tamano,
            Qt.AspectRatioMode.KeepAspectRatioByExpanding,
            Qt.TransformationMode.SmoothTransformation,
        )
        x = max(0, (escalada.width() - tamano.width()) // 2)
        y = max(0, (escalada.height() - tamano.height()) // 2)
        recortada = escalada.copy(x, y, tamano.width(), tamano.height())
        paleta = self.viewport().palette()
        paleta.setBrush(QPalette.ColorRole.Base, QBrush(QPixmap.fromImage(recortada)))
        self.viewport().setPalette(paleta)
