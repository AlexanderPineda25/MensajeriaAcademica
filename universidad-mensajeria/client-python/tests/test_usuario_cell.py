import os
import unittest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication

from ui.celdas.usuario_cell import (
    ROL_CODIGO,
    ROL_CONECTADO,
    ROL_FECHA,
    ROL_NOMBRE,
    ROL_NO_LEIDOS,
    ROL_PREVIEW,
    UsuarioCell,
)


class PruebasUsuarioCell(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def test_construye_tarjeta_con_preview_fecha_e_insignia(self):
        usuario = {"codigo": "B002", "conectado": 1}

        item = UsuarioCell.crear(usuario, "Ana Pérez [B002]", 2,
                                 preview="hola, ¿cómo va?", fecha="2026-10-08 10:05:00")

        self.assertEqual("●  Ana Pérez [B002]  (2)\n    hola, ¿cómo va?",
                         item.text())
        self.assertEqual("B002", item.data(ROL_CODIGO))
        self.assertEqual("Ana Pérez [B002]", item.data(ROL_NOMBRE))
        self.assertEqual(2, item.data(ROL_NO_LEIDOS))
        self.assertEqual("hola, ¿cómo va?", item.data(ROL_PREVIEW))
        self.assertTrue(item.data(ROL_CONECTADO))
        self.assertEqual("2026-10-08 10:05:00", item.data(ROL_FECHA))
        # Qt.ItemDataRole.UserRole + 3: texto sin insignia (lo lee
        # `_limpiar_insignia` para restaurar sin regex).
        self.assertEqual("●  Ana Pérez [B002]\n    hola, ¿cómo va?",
                         item.data(Qt.ItemDataRole.UserRole + 3))

    def test_sin_conversacion_usa_el_literal_compartido(self):
        item = UsuarioCell.crear({"codigo": "C003"}, "C003")

        self.assertEqual("○  C003\n    Sin mensajes aún", item.text())
        self.assertFalse(item.data(ROL_CONECTADO))
        self.assertEqual("", item.data(ROL_FECHA))


if __name__ == "__main__":
    unittest.main()
