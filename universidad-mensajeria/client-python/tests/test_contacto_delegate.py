"""Tarjeta de contacto: delegado de la lista de conversaciones."""
import os
import unittest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QListWidget, QStyleOptionViewItem

from ui.celdas.contacto_delegate import ALTURA_FILA, ContactoDelegate
from ui.celdas.usuario_cell import ROL_NO_LEIDOS, UsuarioCell
from ui.tema import MODO_CLARO, MODO_OSCURO


class PruebasContactoDelegate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self.lista = QListWidget()
        self.delegado = ContactoDelegate(self.lista, MODO_OSCURO)
        self.lista.setItemDelegate(self.delegado)

    def test_altura_fija_de_tarjeta(self):
        item = UsuarioCell.crear({"codigo": "B002", "conectado": 1},
                                 "Ana Pérez [B002]", 2, preview="hola")
        self.lista.addItem(item)

        pista = self.delegado.sizeHint(
            QStyleOptionViewItem(), self.lista.indexFromItem(item))

        self.assertEqual(ALTURA_FILA, pista.height())

    def test_limpieza_de_insignia_repite_sin_repintar_texto_viejo(self):
        item = UsuarioCell.crear({"codigo": "B002"}, "B002", 3, preview="hola")
        self.lista.addItem(item)

        item.setData(ROL_NO_LEIDOS, 0)

        self.assertEqual(0, item.data(ROL_NO_LEIDOS))

    def test_cambio_de_tema_relee_los_tokens(self):
        self.delegado.actualizar_tema(MODO_CLARO)
        color_claro = self.delegado._nombre
        self.delegado.actualizar_tema(MODO_OSCURO)

        self.assertNotEqual(color_claro, self.delegado._nombre)


if __name__ == "__main__":
    unittest.main()
