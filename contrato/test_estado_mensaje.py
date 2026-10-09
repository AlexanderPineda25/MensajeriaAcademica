"""Comprueba que los 3 clientes coinciden con la maquina de estados del contrato.

Es la version que corre dentro de la suite de Python, para que `pytest` /
`unittest` fellen si alguien divergen. La comprobacion de fondo es
`contrato/verificar_estado_mensaje.py`; aqui se invoca y se traduce su codigo
de salida a un fallo de test.

Que protege: `contrato/estado-mensaje.json` (cierra VIS-02). La precedencia
PENDIENTE -> ENVIADO -> ENTREGADO -> LEIDO, con ERROR terminal, estaba escrita a
mano 5 veces en 3 lenguajes con dos mecanismos distintos (CASE de enteros en Java,
ON CONFLICT en Python y .NET). Anadir un estado en una sola copia hacia que ese
cliente discrepe del resto sin que ninguna prueba lo notara.
"""
import json
import subprocess
import sys
import unittest
from pathlib import Path

CONTRATO = Path(__file__).resolve().parent.parent
VENV_PYTHON = CONTRATO / "universidad-mensajeria/client-python/.venv/Scripts/python.exe"
VERIFICADOR = CONTRATO / "contrato/verificar_estado_mensaje.py"
TOKENS_JSON = CONTRATO / "contrato/estado-mensaje.json"


class TestMaquinaDeEstados(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        ejecutable = VENV_PYTHON if VENV_PYTHON.exists() else Path(sys.executable)
        cls.resultado = subprocess.run(
            [str(ejecutable), str(VERIFICADOR)],
            capture_output=True, text=True, cwd=str(CONTRATO),
            encoding="utf-8", errors="replace",
        )

    def test_los_tres_clientes_coinciden_con_el_contrato(self):
        self.assertEqual(
            0, self.resultado.returncode,
            "La maquina de estados diverge del contrato:\n"
            f"{self.resultado.stdout}\n{self.resultado.stderr}",
        )

    def test_el_contrato_declara_los_cinco_estados(self):
        datos = json.loads(TOKENS_JSON.read_text(encoding="utf-8"))
        nombres = {e["nombre"] for e in datos["estados"]}
        self.assertEqual(
            {"PENDIENTE", "ENVIADO", "ENTREGADO", "LEIDO", "ERROR"}, nombres,
            f"faltan o sobran estados en el contrato: {nombres}",
        )

    def test_leido_es_el_de_mayor_orden(self):
        """Si esto cambia, la regla 'nada degrada un LEIDO' cambia de sentido."""
        datos = json.loads(TOKENS_JSON.read_text(encoding="utf-8"))
        orden = {e["nombre"]: e["orden"] for e in datos["estados"]}
        for nombre in ("PENDIENTE", "ENVIADO", "ENTREGADO"):
            self.assertLess(orden[nombre], orden["LEIDO"],
                            f"{nombre} no puede estar por encima de LEIDO")

    def test_error_es_terminal(self):
        """ERROR no debe poder ser upgrading por un acuse tardio."""
        datos = json.loads(TOKENS_JSON.read_text(encoding="utf-8"))
        error = next(e for e in datos["estados"] if e["nombre"] == "ERROR")
        self.assertTrue(error.get("terminal"),
                        "ERROR debe declararse terminal en el contrato")
        # Con orden 0, un PENDIENTE entrante no lo reanimaria.
        pendiente = next(e for e in datos["estados"] if e["nombre"] == "PENDIENTE")
        self.assertEqual(0, error["orden"],
                         "ERROR debe tener orden 0 para no competir con PENDIENTE")
        self.assertEqual(0, pendiente["orden"])

    def test_leido_es_el_unico_con_color_propio(self):
        """El azul del leido es la senal que distingue entregado de leido."""
        datos = json.loads(TOKENS_JSON.read_text(encoding="utf-8"))
        con_color = [e["nombre"] for e in datos["estados"]
                     if e["color"] == "tick.read"]
        self.assertEqual(["LEIDO"], con_color,
                         "solo LEIDO puede usar el color de 'leido'")

    def test_cada_estado_tiene_tick_y_detalle(self):
        datos = json.loads(TOKENS_JSON.read_text(encoding="utf-8"))
        for estado in datos["estados"]:
            self.assertTrue(estado["tick"].strip(),
                            f"{estado['nombre']} sin tick: el usuario veria nada")
            self.assertTrue(estado["detalle"].strip(),
                            f"{estado['nombre']} sin detalle para el tooltip")

    def test_las_reglas_de_precedencia_cubren_los_casos(self):
        """Sin una regla por cada combinacion, un estado nuevo abre un hueco."""
        datos = json.loads(TOKENS_JSON.read_text(encoding="utf-8"))
        ids = {r["id"] for r in datos["precedencia"]["reglas"]}
        for obligatorio in ("leido-absorbe", "nuevo-leido",
                            "entregado-no-retrcede", "enviado-no-retrcede",
                            "defecto-gana"):
            self.assertIn(obligatorio, ids,
                          f"falta la regla `{obligatorio}` en el contrato")


if __name__ == "__main__":
    unittest.main()