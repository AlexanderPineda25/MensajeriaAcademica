"""Equivalencia de client.properties entre los 3 clientes (MUERTE-5).

El mismo fichero canonico lo leen los tests de Java, Python y .NET. Cada uno
comprueba que su analizador resuelve los mismos valores; si uno se aparta del
formato compartido (PROTOCOLO.md 5.2), su test falla.
"""
import os
import unittest

from transversal.config import Config


RAIZ = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
FIXTURE = os.path.join(
    RAIZ, "common-protocol", "test-fixtures", "client-properties-fixture.properties"
)


class TestConfigCompartida(unittest.TestCase):
    def test_fixture_canonico_existe(self):
        self.assertTrue(os.path.isfile(FIXTURE), f"falta el fixture canonico: {FIXTURE}")

    def test_fixture_canonico_coincide_con_los_otros_clientes(self):
        cfg = Config(FIXTURE)

        self.assertEqual("10.0.0.25", cfg.get("host"))
        self.assertEqual("5012", cfg.get("puerto"))
        self.assertEqual("jdbc:h2:mem:prueba;OPTIONS=x=y", cfg.get("db.url"))
        self.assertEqual("true", cfg.get("tls.habilitado"))
        self.assertEqual("5443", cfg.get("tls.puerto"))
        self.assertEqual("C:/confianza/prueba.jks", cfg.get("tls.confianza"))
        self.assertEqual("es", cfg.get("idioma"))
        # clave repetida: gana la ultima linea
        self.assertEqual("oscuro", cfg.get("tema"))

    def test_lineas_vacias_y_comentarios_no_se_convierten_en_claves(self):
        cfg = Config(FIXTURE)

        self.assertNotIn("", cfg.props)
        self.assertNotIn("#", cfg.props)
        for clave in cfg.props:
            self.assertFalse(clave.startswith("#"), f"comentario leakage: {clave!r}")
            self.assertEqual(clave, clave.strip(), f"clave sin recortar: {clave!r}")

    def test_guardar_reescribe_solo_la_clave_y_conserva_el_resto(self):
        # El .gitignore no cubre el temporal: se limpia en el tearDown.
        import tempfile

        with tempfile.TemporaryDirectory() as temporal:
            ruta = os.path.join(temporal, "client.properties")
            with open(FIXTURE, encoding="utf-8") as origen:
                original = origen.read()
            with open(ruta, "w", encoding="utf-8") as destino:
                destino.write(original)

            cfg = Config(ruta)
            cfg.set("tema", "claro")
            cfg.save()

            with open(ruta, encoding="utf-8") as releido:
                lineas = [l.rstrip("\n") for l in releido]

        self.assertIn("tema=claro", lineas)
        self.assertNotIn("tema=oscuro", lineas)
        # El resto del fichero sobrevive a la reescritura.
        self.assertIn("host=10.0.0.25", lineas)
        self.assertIn("puerto=5012", lineas)


if __name__ == "__main__":
    unittest.main()