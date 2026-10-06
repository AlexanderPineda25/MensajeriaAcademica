import sys
import os

from PySide6.QtGui import QColor, QFont, QFontDatabase
from PySide6.QtWidgets import QApplication
from qfluentwidgets import setTheme, setThemeColor, Theme

from fachada.fachada_cliente import FachadaCliente
from ui.chat_window import ChatWindow
from ui.login_window import LoginWindow


def iniciar():
    app = QApplication(sys.argv)
    app.setApplicationName("Mensajeria Academica")
    if sys.platform == "win32":
        fuente = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", "segoeui.ttf")
        if os.path.exists(fuente) and QFontDatabase.addApplicationFont(fuente) >= 0:
            app.setFont(QFont("Segoe UI", 10))
    # UIX-29: el canonico es oscuro (tema.py: normalizar/modo_guardado
    # devuelven MODO_OSCURO por defecto). Antes arrancaba en claro.
    setTheme(Theme.DARK)
    setThemeColor(QColor("#7B61B8"))

    fachada = FachadaCliente()
    login = LoginWindow(fachada)
    ventana_chat = None

    def volver_a_login():
        nonlocal ventana_chat
        ventana_chat = None
        login.show()

    def abrir_chat(codigo):
        nonlocal ventana_chat
        if ventana_chat is not None:
            ventana_chat.activateWindow()
            ventana_chat.raise_()
            return
        login.hide()
        ventana_chat = ChatWindow(fachada, codigo, volver_a_login)
        ventana_chat.show()

    login.login_succeeded.connect(abrir_chat)
    app.aboutToQuit.connect(fachada.desconectar)
    login.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(iniciar())
