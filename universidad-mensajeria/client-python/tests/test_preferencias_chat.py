import tempfile
import unittest

from PySide6.QtCore import QSettings

from ui.preferencias_chat import PreferenciasChat


class PruebasPreferenciasChat(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.ajustes = QSettings(
            self.temp.name + "/preferencias.ini", QSettings.Format.IniFormat)
        self.ajustes.clear()
        self.preferencias = PreferenciasChat(self.ajustes)

    def test_divisor_se_persiste_y_se_aisla_por_cuenta(self):
        self.preferencias.guardar_divisor_lateral("A001", [310, 770])

        self.assertEqual((310, 770), self.preferencias.divisor_lateral("A001"))
        self.assertIsNone(self.preferencias.divisor_lateral("B002"))

    def test_ignora_datos_invalidos(self):
        self.ajustes.setValue(
            "cuentas/A001/panel-chat/divisor-lateral", [0, "incorrecto"])

        self.assertIsNone(self.preferencias.divisor_lateral("A001"))

    def test_ultimo_chat_se_aisla_por_cuenta(self):
        self.preferencias.guardar_ultimo_chat("A001", "B002")

        self.assertEqual("B002", self.preferencias.ultimo_chat("A001"))
        self.assertIsNone(self.preferencias.ultimo_chat("C003"))

    def test_no_persiste_una_conversacion_con_la_propia_cuenta(self):
        self.preferencias.guardar_ultimo_chat("A001", "A001")

        self.assertIsNone(self.preferencias.ultimo_chat("A001"))


if __name__ == "__main__":
    unittest.main()
