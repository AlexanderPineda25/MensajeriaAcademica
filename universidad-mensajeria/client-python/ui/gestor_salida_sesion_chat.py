"""Confirmaciones y cierre explícito de sesiones desde la UI."""

from PySide6.QtWidgets import QMessageBox


class GestorSalidaSesionChat:
    def salir_en_todos(self):
        respuesta = QMessageBox.question(
            self, "Confirmar salida", "¿Cerrar todas las sesiones de esta cuenta?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No)
        if respuesta != QMessageBox.StandardButton.Yes:
            return
        self._ejecutar("salir_todos", self.fachada.expulsar_otras_sesiones)

    def closeEvent(self, evento):
        if self._cerrando:
            evento.accept()
            return
        respuesta = QMessageBox.question(
            self, "Confirmar salida", "¿Deseas cerrar esta sesión y volver al inicio?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No)
        if respuesta != QMessageBox.StandardButton.Yes:
            evento.ignore()
            return
        self._cerrando = True
        self.fachada.on_mensaje_recibido_ui = None
        self.fachada.on_cierre_ui = None
        self.fachada.on_presencia_ui = None
        self.fachada.on_desconexion_ui = None
        self.fachada.desconectar()
        self.on_logout()
        evento.accept()
