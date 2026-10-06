"""UIX-04/UIX-05: mismo contrato que FilasChat (Java) y Agrupar (.NET)."""
import datetime
import unittest

from transversal.fechas import agrupar, clave_dia, etiqueta_dia
from ui.chat_window import resaltar_html


def fila(origen, fecha):
    return {"origen": origen, "fecha_envio": fecha}


class TestFilas(unittest.TestCase):
    def test_la_racha_del_mismo_autor_agrupa(self):
        hoy = datetime.datetime.now().replace(hour=10, minute=0, second=0)
        base = hoy.strftime("%Y-%m-%d %H:%M:%S")
        dos_min = (hoy + datetime.timedelta(minutes=2)).strftime("%Y-%m-%dT%H:%M:%S")

        self.assertTrue(agrupar(fila("B", base), fila("B", dos_min)))

    def test_cambio_de_autor_rompe_la_racha(self):
        base = datetime.datetime.now().replace(hour=10, minute=0).strftime("%Y-%m-%d %H:%M:%S")
        otro = datetime.datetime.now().replace(hour=10, minute=2).strftime("%Y-%m-%d %H:%M:%S")

        self.assertFalse(agrupar(fila("B", base), fila("C", otro)))
        self.assertFalse(agrupar(None, fila("B", otro)))

    def test_corte_de_dia_y_ventana_rompen_la_racha(self):
        base = datetime.datetime.now().replace(hour=10, minute=0).strftime("%Y-%m-%d %H:%M:%S")
        ayer = (datetime.datetime.now() - datetime.timedelta(days=1)
                ).replace(hour=10, minute=2).strftime("%Y-%m-%d %H:%M:%S")
        una_hora = (datetime.datetime.now().replace(hour=10, minute=0)
                    + datetime.timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S")

        self.assertFalse(agrupar(fila("B", base), fila("B", ayer)))
        self.assertFalse(agrupar(fila("B", base), fila("B", una_hora)))

    def test_fecha_ilegible_no_agrupa(self):
        self.assertFalse(agrupar(fila("B", "basura"), fila("B", "basura")))
        self.assertEqual("", clave_dia("basura"))
        self.assertEqual("", etiqueta_dia(None))


class TestResaltar(unittest.TestCase):
    """UIX-06: mismo contrato que textoResaltado (Java)."""

    def test_envuelve_coincidencias_ignorando_mayusculas(self):
        html = resaltar_html("Hola mundo, hola", "HOLA")

        self.assertEqual(2, html.count("<span"))
        self.assertIn("Hola", html)
        self.assertIn("hola", html)
        self.assertNotIn("<span> </span>", html)

    def test_escapa_html_y_no_interpreta_regex(self):
        html = resaltar_html("<b>100% (x)</b>", "100% (x)")

        self.assertIn("&lt;b&gt;", html)
        self.assertIn("<span", html)
        # ".*" literal no casa con nada: no hay spans.
        self.assertNotIn("<span", resaltar_html("hola", ".*"))

    def test_vacio_devuelve_escapado_sin_marcas(self):
        self.assertEqual("hola", resaltar_html("hola", ""))
        self.assertEqual("hola", resaltar_html("hola", "   "))
        self.assertEqual("", resaltar_html("", "x"))


if __name__ == "__main__":
    unittest.main()
