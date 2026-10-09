import unittest

from ui.enviar_mensaje_comando import EnviarMensajeComando


class PuertoEnvio:
    def __init__(self, acepta_id=True, resultado=True):
        self.acepta_id = acepta_id
        self.resultado = resultado
        self.llamadas = []

    def _registrar(self, tipo, usuario, valor, id_msg=None):
        if not self.acepta_id and id_msg is not None:
            raise TypeError("firma antigua")
        self.llamadas.append((tipo, usuario, valor, id_msg))
        return self.resultado

    def enviar_texto(self, usuario, texto, id_msg=None):
        return self._registrar("texto", usuario, texto, id_msg)

    def enviar_imagen(self, usuario, ruta, id_msg=None):
        return self._registrar("imagen", usuario, ruta, id_msg)

    def enviar_archivo(self, usuario, ruta, id_msg=None):
        return self._registrar("archivo", usuario, ruta, id_msg)


class PruebasEnviarMensajeComando(unittest.TestCase):
    EXTENSIONES_IMAGEN = frozenset({".png", ".jpg"})

    def test_envia_texto_y_imagen_en_orden_con_ids(self):
        puerto = PuertoEnvio()
        comando = EnviarMensajeComando(
            "B002", "hola", "texto-1", "foto.PNG", "adjunto-1",
            self.EXTENSIONES_IMAGEN,
        )

        resultado = comando.ejecutar(puerto)

        self.assertEqual(("B002", True, ["texto-1", "adjunto-1"]), resultado)
        self.assertEqual([
            ("texto", "B002", "hola", "texto-1"),
            ("imagen", "B002", "foto.PNG", "adjunto-1"),
        ], puerto.llamadas)

    def test_archivo_no_soportado_como_imagen_usa_envio_archivo(self):
        puerto = PuertoEnvio(acepta_id=False, resultado=False)
        comando = EnviarMensajeComando(
            "B002", "", None, "archivo.webp", "archivo-1",
            self.EXTENSIONES_IMAGEN,
        )

        resultado = comando.ejecutar(puerto)

        self.assertEqual(("B002", False, ["archivo-1"]), resultado)
        self.assertEqual([("archivo", "B002", "archivo.webp", None)], puerto.llamadas)


if __name__ == "__main__":
    unittest.main()
