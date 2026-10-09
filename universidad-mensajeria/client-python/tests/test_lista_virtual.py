"""UIX-01: modelo virtualizado + delegado de la lista de mensajes.

Sin widgets por fila: el modelo guarda dicts y el delegado calcula el
layout (distribuir) y resuelve los clics por coordenadas.
"""
import os
import unittest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtWidgets import QApplication

from ui.lista_virtual import ROL_ITEM, MensajeDelegate, MensajeListModel
from ui.miniaturas_chat import MiniaturasChat
from ui.tema import MODO_CLARO, MODO_OSCURO, tokens


def _mensaje(**extras):
    base = {"id": "m1", "sender": "B002", "date": "2026-10-02 10:00:00",
            "text": "hola", "is_own": False, "is_image": False,
            "mostrar_remitente": True}
    base.update(extras)
    return base


class PruebasListaVirtual(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self.modelo = MensajeListModel()
        self.delegado = MensajeDelegate()

    def test_agregar_e_indice_por_id(self):
        self.modelo.agregar(_mensaje(id="a"))
        self.modelo.agregar(_mensaje(id="b"))
        self.assertEqual(2, self.modelo.rowCount())
        indice = self.modelo.indice_por_id("b")
        self.assertTrue(indice.isValid())
        self.assertEqual("b", self.modelo.data(indice, ROL_ITEM)["id"])
        self.assertFalse(self.modelo.indice_por_id("zzz").isValid())

    def test_separadores_se_quitan_sin_tocar_mensajes(self):
        self.modelo.agregar(_mensaje(id="a"))
        self.modelo.agregar({"es_separador": True, "etiqueta": "Hoy"})
        self.modelo.agregar(_mensaje(id="b"))
        self.modelo.quitar_separadores()
        self.assertEqual(["a", "b"], [m["id"] for m in self.modelo.mensajes()])
        self.assertEqual(2, self.modelo.rowCount())

    def test_refrescar_no_mueve_filas(self):
        self.modelo.agregar(_mensaje(id="a", text="uno"))
        self.modelo.agregar(_mensaje(id="b", text="dos"))
        self.modelo.data(self.modelo.indice_por_id("a"), ROL_ITEM)["text"] = "UNO"
        self.modelo.refrescar("a")
        self.assertEqual("UNO", self.modelo.mensajes()[0]["text"])
        self.assertEqual(2, self.modelo.rowCount())

    def test_distribuir_propia_derecha_y_tope(self):
        t = tokens(MODO_OSCURO)
        lay = self.delegado.distribuir(_mensaje(is_own=True), 1000, t)
        self.assertTrue(lay["propia"])
        x, _y, ancho, _h = lay["burbuja"]
        # UIX-10: 65% de 1000 = 650, topado a 580.
        self.assertEqual(580, ancho)
        self.assertGreater(x, 100)
        lay2 = self.delegado.distribuir(_mensaje(is_own=False), 1000, t)
        self.assertFalse(lay2["propia"])

    def test_distribuir_sin_remitente_en_racha(self):
        t = tokens(MODO_OSCURO)
        lay = self.delegado.distribuir(_mensaje(mostrar_remitente=False), 600, t)
        self.assertNotIn("remitente", lay)
        lay2 = self.delegado.distribuir(_mensaje(mostrar_remitente=True), 600, t)
        self.assertIn("remitente", lay2)

    def test_distribuir_botones_segun_dict(self):
        t = tokens(MODO_CLARO)
        lay = self.delegado.distribuir(_mensaje(is_image=True), 600, t)
        self.assertIn("descarga", lay)
        self.assertNotIn("retry", lay)
        lay2 = self.delegado.distribuir(
            _mensaje(is_image=True, image_error="falló"), 600, t)
        self.assertIn("retry", lay2)
        lay3 = self.delegado.distribuir(
            _mensaje(es_archivo=True, archivo_id="7"), 600, t)
        self.assertIn("descarga", lay3)
        lay4 = self.delegado.distribuir(_mensaje(), 600, t)
        self.assertNotIn("descarga", lay4)
        self.assertNotIn("retry", lay4)

    def test_resaltado_marca_coincidencias(self):
        self.delegado.set_patron("hola")
        t = tokens(MODO_OSCURO)
        lay = self.delegado.distribuir(_mensaje(text="hola mundo"), 600, t)
        self.assertIn("<span", lay["_doc"].toHtml())
        self.delegado.set_patron("")
        lay2 = self.delegado.distribuir(_mensaje(text="hola mundo"), 600, t)
        self.assertNotIn("<span", lay2["_doc"].toHtml())

    def test_pixmaps_con_tope(self):
        from PySide6.QtGui import QImage, QPixmap
        imagen = QImage(8, 8, QImage.Format.Format_ARGB32)
        for i in range(105):
            self.delegado.guardar_pixmap(f"id-{i}", QPixmap.fromImage(imagen))
        # El tope (100) expulsa las más viejas.
        self.assertIsNone(self.delegado.pixmap_de("id-0"))
        self.assertIsNotNone(self.delegado.pixmap_de("id-104"))

    def test_miniatura_escalada_conserva_proporcion(self):
        from PySide6.QtGui import QImage, QPixmap
        imagen = QImage(80, 40, QImage.Format.Format_ARGB32)
        miniaturas = MiniaturasChat()
        miniaturas.guardar("imagen", QPixmap.fromImage(imagen))

        miniatura = miniaturas.escalar("imagen", 20, 20)

        self.assertEqual((20, 10), (miniatura.width(), miniatura.height()))
        self.assertIsNone(miniaturas.escalar("ausente"))

    def test_leyenda_de_filtros_va_bajo_la_imagen_y_no_muestra_rutas(self):
        from PySide6.QtGui import QImage, QPixmap
        imagen = QImage(20, 20, QImage.Format.Format_ARGB32)
        self.delegado.guardar_pixmap("imagen-filtros", QPixmap.fromImage(imagen))
        item = _mensaje(
            id="imagen-filtros", is_image=True,
            etapas_filtrado='[{"etapa":"01_grises.png","ms":12,"ruta":"C:/srv/01_grises.png"},'
                            '{"etapa":"02_sepia.png","ms":4,"ruta":"C:/srv/02_sepia.png"}]')

        lay = self.delegado.distribuir(item, 600, tokens(MODO_OSCURO))

        self.assertIn("imagen", lay)
        self.assertIn("etapas_filtrado", lay)
        x_img, y_img, _w_img, h_img = lay["imagen"]
        x_txt, y_txt, _w_txt, _h_txt, resumen = lay["etapas_filtrado"]
        self.assertEqual(x_img, x_txt)
        self.assertGreaterEqual(y_txt, y_img + h_img)
        self.assertEqual("Filtros: Grises (12 ms) → Sepia (4 ms)", resumen)
        self.assertLess(resumen.index("Grises"), resumen.index("Sepia"))
        self.assertNotIn("C:/srv", resumen)


if __name__ == "__main__":
    unittest.main()
