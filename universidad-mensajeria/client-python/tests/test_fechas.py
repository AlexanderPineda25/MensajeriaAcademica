"""UIX-03: el mismo contrato que FormatoFecha (Java) y FormatoFecha (.NET)."""
import datetime
import unittest

from transversal.fechas import clave_dia, etiqueta_dia, hora_corta


class TestFechas(unittest.TestCase):
    def test_hoy_muestra_solo_la_hora(self):
        hoy = datetime.datetime.now().replace(hour=10, minute=5, second=0)
        texto = hoy.strftime("%Y-%m-%d %H:%M:%S")

        self.assertEqual("10:05", hora_corta(texto))
        self.assertEqual("Hoy", etiqueta_dia(texto))

    def test_ayer_se_marca_como_ayer(self):
        ayer = datetime.datetime.now() - datetime.timedelta(days=1)
        texto = ayer.replace(hour=9, minute=30).strftime("%Y-%m-%dT%H:%M:%S")

        self.assertEqual("Ayer", etiqueta_dia(texto))
        self.assertIn("09:30", hora_corta(texto))

    def test_hace_una_semana_muestra_dia_y_mes(self):
        hace = datetime.datetime.now() - datetime.timedelta(days=7)
        texto = hace.replace(hour=8, minute=0).strftime("%Y-%m-%d %H:%M:%S")

        self.assertRegex(hora_corta(texto), r"\d{2}-\d{2} 08:00")
        self.assertRegex(etiqueta_dia(texto), r"\d{2}-\d{2}")

    def test_acepta_los_dos_formatos_de_origen(self):
        hoy = datetime.date.today().isoformat()
        self.assertEqual("10:00", hora_corta(f"{hoy}T10:00:00"))
        self.assertEqual("10:00", hora_corta(f"{hoy} 10:00:00"))

    def test_lo_ilegible_no_rompe_la_fila(self):
        self.assertEqual("", hora_corta(None))
        self.assertEqual("", hora_corta(""))
        self.assertEqual("", hora_corta("ayer a las tres"))
        self.assertEqual("", etiqueta_dia("basura"))
        self.assertEqual("", clave_dia(None))

    def test_clave_dia_agrupa_por_fecha(self):
        hoy = datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
        ayer = (datetime.datetime.now() - datetime.timedelta(days=1)
                ).strftime("%Y-%m-%d %H:%M:%S")

        self.assertEqual(datetime.date.today().isoformat(), clave_dia(hoy))
        self.assertEqual(
            (datetime.date.today() - datetime.timedelta(days=1)).isoformat(),
            clave_dia(ayer))


if __name__ == "__main__":
    unittest.main()
