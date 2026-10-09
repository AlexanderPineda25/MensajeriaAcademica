"""Controles pequeños y señales de coordinación para la vista de chat."""

from PySide6.QtCore import QObject, QRunnable, Qt, Signal
from qfluentwidgets import TextEdit


class CampoMensaje(TextEdit):
    """Editor multilínea: Enter envía y Mayús+Enter inserta un salto."""

    enviar_pedido = Signal()

    def keyPressEvent(self, evento):
        if evento.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter) \
                and not evento.modifiers() & Qt.KeyboardModifier.ShiftModifier:
            self.enviar_pedido.emit()
            evento.accept()
            return
        super().keyPressEvent(evento)

    def texto(self):
        return self.toPlainText()


class EventosChat(QObject):
    message = Signal(object)
    closure = Signal(object)
    presence = Signal(object)
    disconnected = Signal()
    state = Signal(str)
    task = Signal(str, object, object)


class TareaChat(QRunnable):
    def __init__(self, tipo, accion, eventos):
        super().__init__()
        self.tipo = tipo
        self.accion = accion
        self.eventos = eventos

    def run(self):
        try:
            self.eventos.task.emit(self.tipo, self.accion(), None)
        except Exception as error:
            self.eventos.task.emit(self.tipo, None, error)
