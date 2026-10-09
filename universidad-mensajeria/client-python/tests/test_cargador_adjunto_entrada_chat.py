import base64
import tempfile
import unittest
from pathlib import Path

from PySide6.QtCore import QBuffer, QIODevice
from PySide6.QtGui import QImage

from ui.cargador_adjunto_entrada_chat import CargadorAdjuntoEntradaChat


def _png_bytes():
    imagen = QImage(3, 2, QImage.Format.Format_ARGB32)
    buffer = QBuffer()
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    imagen.save(buffer, "PNG")
    return bytes(buffer.data())


class PruebasCargadorAdjuntoEntradaChat(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.cargador = CargadorAdjuntoEntradaChat()

    def test_carga_contenido_base64_a_caché(self):
        mensaje = {
            "id": "imagen-1", "filename": "foto.png",
            "image_base64": base64.b64encode(_png_bytes()).decode("ascii"),
        }

        id_cargado, imagen, ruta = self.cargador.cargar(
            mensaje, self.temp.name, lambda *_: self.fail("no debe ir a red"))

        self.assertEqual("imagen-1", id_cargado)
        self.assertTrue(Path(ruta).is_file())
        self.assertFalse(imagen.isNull())

    def test_reutiliza_ruta_local_existente(self):
        ruta = Path(self.temp.name) / "existente.png"
        ruta.write_bytes(_png_bytes())
        mensaje = {"id": "imagen-2", "path": str(ruta)}

        id_cargado, imagen, ruta_cargada = self.cargador.cargar(
            mensaje, self.temp.name, lambda *_: self.fail("no debe descargar"))

        self.assertEqual(("imagen-2", str(ruta)), (id_cargado, ruta_cargada))
        self.assertFalse(imagen.isNull())

    def test_descarga_si_no_hay_copia_local_ni_base64(self):
        llamadas = []

        def obtener_imagen(id_mensaje, destino):
            llamadas.append(id_mensaje)
            Path(destino).write_bytes(_png_bytes())

        id_cargado, imagen, ruta = self.cargador.cargar(
            {"id": "imagen-3", "filename": "foto.png"}, self.temp.name,
            obtener_imagen)

        self.assertEqual(["imagen-3"], llamadas)
        self.assertEqual("imagen-3", id_cargado)
        self.assertTrue(Path(ruta).is_file())
        self.assertFalse(imagen.isNull())

    def test_rechaza_contenido_que_no_es_imagen(self):
        with self.assertRaisesRegex(RuntimeError, "no es una imagen válida"):
            self.cargador.cargar(
                {"id": "imagen-mala", "image_base64": "bm90IGFuIGltYWdl"},
                self.temp.name, lambda *_: None)


if __name__ == "__main__":
    unittest.main()
