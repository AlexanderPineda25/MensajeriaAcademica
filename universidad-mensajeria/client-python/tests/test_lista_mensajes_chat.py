import os
import tempfile
import unittest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtCore import Qt
from PySide6.QtGui import QColor, QImage
from PySide6.QtWidgets import QApplication

from ui.lista_mensajes_chat import ListaMensajesChat


class PruebasListaMensajesChat(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def test_fondo_se_escala_al_viewport_y_se_renderiza(self):
        imagen = QImage(2, 1, QImage.Format.Format_RGB32)
        imagen.fill(QColor("#d02030"))
        with tempfile.TemporaryDirectory() as directorio:
            ruta = os.path.join(directorio, "fondo.png")
            self.assertTrue(imagen.save(ruta))
            vista = ListaMensajesChat()
            vista.resize(160, 100)
            vista.fijar_fondo(ruta)
            vista.show()
            self.app.processEvents()
            self.addCleanup(vista.close)

            textura = vista.viewport().palette().brush(
                vista.viewport().backgroundRole()).texture()
            self.assertEqual(vista.viewport().size(), textura.size())
            render = vista.viewport().grab().toImage()
            self.assertEqual(QColor("#d02030"), render.pixelColor(30, 30))


if __name__ == "__main__":
    unittest.main()
