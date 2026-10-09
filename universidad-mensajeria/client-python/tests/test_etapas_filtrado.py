import unittest

from transversal.etapas_filtrado import (
    mapear_etapas_filtrado,
    resumir_etapas_filtrado,
)


class PruebasEtapasFiltrado(unittest.TestCase):
    def test_mapeador_conserva_cadena_json_y_acepta_lista(self):
        crudo = '[{"etapa":"01_grises.png","ms":12,"ruta":"srv/a.png"}]'
        self.assertEqual(mapear_etapas_filtrado(crudo), crudo)
        self.assertEqual(
            mapear_etapas_filtrado([{"etapa": "02_sepia.png", "ms": 3}]),
            '[{"etapa":"02_sepia.png","ms":3}]')
        self.assertIsNone(mapear_etapas_filtrado("  "))
        self.assertIsNone(mapear_etapas_filtrado(42))

    def test_resumen_preserva_orden_y_omite_rutas(self):
        etapas = (
            '[{"etapa":"01_grises.png","ms":12,"ruta":"C:/srv/01_grises.png"},'
            '{"etapa":"02_sepia.png","ms":3.5,"ruta":"C:/srv/02_sepia.png"},'
            '{"etapa":"C:/srv/03_giro180.png","ms":4,"ruta":"C:/srv/03_giro180.png"}]')

        resumen = resumir_etapas_filtrado(etapas)

        self.assertEqual(
            resumen,
            "Filtros: Grises (12 ms) → Sepia (3.5 ms) → Giro 180° (4 ms)")
        self.assertNotIn("ruta", resumen.casefold())
        self.assertNotIn("C:/srv", resumen)
        self.assertLess(resumen.index("Grises"), resumen.index("Sepia"))
        self.assertLess(resumen.index("Sepia"), resumen.index("Giro 180°"))

    def test_resumen_ignora_trazas_malformadas_y_mediciones_invalidas(self):
        self.assertEqual("", resumir_etapas_filtrado("no-json"))
        self.assertEqual("", resumir_etapas_filtrado("[]"))
        resumen = resumir_etapas_filtrado(
            '[{"etapa":"invalida","ms":-1,"ruta":"secreto"}]')
        self.assertEqual("Filtros: Invalida", resumen)
        self.assertNotIn("secreto", resumen)


if __name__ == "__main__":
    unittest.main()
