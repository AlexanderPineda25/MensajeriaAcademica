"""Comprueba que los fondos de chat se cargan y se pintan en el viewport."""
import os
import unittest
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtGui import QImage
from PySide6.QtWidgets import QApplication

from ui.lista_mensajes_chat import ListaMensajesChat


class PruebasFondoChat(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])
        cls.directorio_fondos = Path(__file__).resolve().parents[1] / "ui" / "fondos"

    def test_fondos_de_ambos_temas_se_pintan_en_el_viewport(self):
        for nombre in ("fondo_chat_oscuro.jpg", "fondo_chat_claro.jpg"):
            with self.subTest(tema=nombre):
                lista = ListaMensajesChat()
                lista.setStyleSheet("QListView { border: 0; padding: 9px; }")
                lista.resize(480, 320)
                lista.fijar_fondo(self.directorio_fondos / nombre)
                lista.show()
                self.app.processEvents()

                self.assertFalse(lista._imagen_fondo.isNull())
                brush = lista.viewport().palette().brush(
                    lista.viewport().backgroundRole())
                self.assertEqual(brush.style().name, "TexturePattern")

                captura = QImage(lista.size(), QImage.Format.Format_ARGB32)
                lista.render(captura)
                viewport = lista.viewport().geometry()
                colores = set()
                for y in range(8, viewport.height() - 8, 7):
                    for x in range(8, viewport.width() - 8, 7):
                        color = captura.pixelColor(viewport.x() + x, viewport.y() + y)
                        colores.add((color.red(), color.green(), color.blue()))
                self.assertGreater(len(colores), 15, "El viewport quedó con un fondo plano")
                lista.close()


if __name__ == "__main__":
    unittest.main()
