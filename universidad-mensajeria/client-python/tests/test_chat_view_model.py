import unittest

from ui.chat_view_model import ChatViewModel


class TestChatViewModel(unittest.TestCase):
    def test_busqueda_envuelve_en_ambas_direcciones(self):
        modelo = ChatViewModel()

        self.assertEqual(modelo.fijar_coincidencias("hola", ["m1", "m2"]), "m1")
        self.assertEqual(modelo.mover_coincidencia(-1), "m2")
        self.assertEqual(modelo.mover_coincidencia(1), "m1")
        self.assertEqual(modelo.mover_coincidencia(1), "m2")

    def test_busqueda_vacia_y_limpieza(self):
        modelo = ChatViewModel()
        modelo.fijar_coincidencias("ausente", [])
        self.assertIsNone(modelo.mover_coincidencia(1))
        self.assertEqual(modelo.indice_coincidencia, -1)

        modelo.programar_salto("m9", 3)
        modelo.limpiar_busqueda()
        self.assertEqual(modelo.patron_busqueda, "")
        self.assertEqual(modelo.ids_coincidencia, [])
        self.assertIsNone(modelo.consumir_salto())

    def test_salto_pendiente_se_consumo_una_sola_vez(self):
        modelo = ChatViewModel()
        modelo.programar_salto("m4", 2)

        self.assertEqual(modelo.consumir_salto(), ("m4", 2))
        self.assertIsNone(modelo.consumir_salto())


if __name__ == "__main__":
    unittest.main()
