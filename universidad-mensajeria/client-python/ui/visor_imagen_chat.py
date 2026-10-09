"""Visor independiente para imágenes de una conversación."""

from PySide6.QtCore import Qt
from PySide6.QtGui import QShortcut
from PySide6.QtWidgets import QDialog, QHBoxLayout, QVBoxLayout
from qfluentwidgets import PushButton


class VisorImagenChat(QDialog):
    """Visor con zoom y pan: rueda anclada al ratón y atajos de teclado."""

    def __init__(self, pixmap, titulo, parent=None):
        from PySide6.QtWidgets import QGraphicsPixmapItem, QGraphicsScene, QGraphicsView
        super().__init__(parent)
        self.setWindowTitle(titulo or "Imagen")
        self.resize(940, 760)
        self.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, True)
        self._zoom = 1.0
        self._escena = QGraphicsScene(self)
        self._escena.addItem(QGraphicsPixmapItem(pixmap))
        self._vista = QGraphicsView(self._escena, self)
        self._vista.setTransformationAnchor(
            QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self._vista.setResizeAnchor(
            QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self._vista.setDragMode(QGraphicsView.DragMode.ScrollHandDrag)
        self._vista.setBackgroundBrush(Qt.GlobalColor.black)
        caja = QVBoxLayout(self)
        caja.setContentsMargins(0, 0, 0, 0)
        caja.addWidget(self._vista)
        fila = QHBoxLayout()
        fila.setContentsMargins(8, 4, 8, 8)
        menos = PushButton("−")
        menos.setFixedWidth(40)
        menos.setToolTip("Alejar")
        menos.clicked.connect(lambda: self._aplicar_zoom(1 / 1.25))
        cero = PushButton("0")
        cero.setFixedWidth(40)
        cero.setToolTip("Tamaño original (F)")
        cero.clicked.connect(lambda: self._aplicar_zoom(None))
        mas = PushButton("+")
        mas.setFixedWidth(40)
        mas.setToolTip("Acercar")
        mas.clicked.connect(lambda: self._aplicar_zoom(1.25))
        fila.addStretch(1)
        fila.addWidget(menos)
        fila.addWidget(cero)
        fila.addWidget(mas)
        caja.addLayout(fila)
        atajo_mas = QShortcut("Ctrl++", self)
        atajo_mas.activated.connect(lambda: self._aplicar_zoom(1.25))
        atajo_menos = QShortcut("Ctrl+-", self)
        atajo_menos.activated.connect(lambda: self._aplicar_zoom(1 / 1.25))
        atajo_cero = QShortcut("Ctrl+0", self)
        atajo_cero.activated.connect(lambda: self._aplicar_zoom(None))

    def _aplicar_zoom(self, factor):
        if factor is None:
            self._vista.resetTransform()
            self._zoom = 1.0
            return
        nuevo = min(8.0, max(0.2, self._zoom * factor))
        self._vista.scale(nuevo / self._zoom, nuevo / self._zoom)
        self._zoom = nuevo

    def wheelEvent(self, evento):
        self._aplicar_zoom(1.15 if evento.angleDelta().y() > 0 else 1 / 1.15)
        evento.accept()

    def keyPressEvent(self, evento):
        tecla = evento.key()
        if tecla in (Qt.Key.Key_Plus, Qt.Key.Key_Equal):
            self._aplicar_zoom(1.25)
        elif tecla == Qt.Key.Key_Minus:
            self._aplicar_zoom(1 / 1.25)
        elif tecla in (Qt.Key.Key_0, Qt.Key.Key_F):
            self._aplicar_zoom(None)
        else:
            super().keyPressEvent(evento)
