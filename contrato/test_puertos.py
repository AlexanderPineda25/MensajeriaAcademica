"""Comprueba el contrato de puertos desde la suite de Python.

Invoca `contrato/verificar_puertos.py` y traduce su codigo de salida a un fallo
de test, para que un puerto nuevo que no se anada al contrato rompa la suite y no
solo un script que nadie ejecuta.

Por que importa: `contrato/puertos.json` es la especificacion normativa que van a
copiar Python y .NET en la Fase 2. Si el codigo Java se aparta del contrato, la
especificacion que se copie sera la equivocada.
"""
import json
import re
import subprocess
import sys
import unittest
from pathlib import Path

CONTRATO = Path(__file__).resolve().parent.parent
VENV_PYTHON = CONTRATO / "universidad-mensajeria/client-python/.venv/Scripts/python.exe"
VERIFICADOR = CONTRATO / "contrato/verificar_puertos.py"
PUERTOS = CONTRATO / "contrato/puertos.json"


def _cargar():
    return json.loads(PUERTOS.read_text(encoding="utf-8"))


class TestContratoDePuertos(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        ejecutable = VENV_PYTHON if VENV_PYTHON.exists() else Path(sys.executable)
        cls.resultado = subprocess.run(
            [str(ejecutable), str(VERIFICADOR)],
            capture_output=True, text=True, cwd=str(CONTRATO),
            encoding="utf-8", errors="replace",
        )

    def test_el_codigo_java_cumple_el_contrato(self):
        self.assertEqual(
            0, self.resultado.returncode,
            "El codigo Java se aparta del contrato de puertos:\n"
            f"{self.resultado.stdout}\n{self.resultado.stderr}",
        )

    def test_hay_todos_los_puertos_declarados(self):
        puertos = _cargar()["puertos"]
        self.assertGreaterEqual(len(puertos), 20,
                                f"solo hay {len(puertos)} puertos declarados; "
                                "el cliente Java tiene mas")

    def test_todo_puerto_declara_su_equivalencia_en_los_tres_lenguajes(self):
        """Cada puerto dice como se llama en Python y en C#.

        Sin esta columna, la Fase 2 seria adivinar. Es lo que hace que el JSON
        sirva de plan y no solo de inventario.
        """
        for puerto in _cargar()["puertos"]:
            with self.subTest(puerto=puerto["nombre"]):
                self.assertIn("python", puerto,
                              f"{puerto['nombre']} sin equivalente en Python")
                self.assertIn("csharp", puerto,
                              f"{puerto['nombre']} sin equivalente en C#")

    def test_los_puertos_pendientes_declaran_como_y_en_que_fase(self):
        """Un puerto que aun no existe en Python/.NET debe decirlo.

        Hay dos situaciones distintas y conviene no mezclarlas:
          - `a crear`: la ruta apunta a un fichero que NO existe. Falta la capa.
          - `a tipar`: el fichero existe pero recibe `dict`/`JsonElement` sueltos
            en vez de records tipados (Fase 1.4).

        El fallo que se quiere evitar: una ruta que ya existe pero no se marca,
        de modo que la Fase 2 no sepa si tiene que crearla o solo tiparla.
        """
        monorepos = CONTRATO / "universidad-mensajeria"
        for puerto in _cargar()["puertos"]:
            for lenguaje in ("python", "csharp"):
                with self.subTest(puerto=puerto["nombre"], lenguaje=lenguaje):
                    valor = puerto[lenguaje]
                    # Se queda con el nombre de fichero, sin `::` ni anotaciones.
                    ruta = re.sub(r"\(.*?\)", "", valor).split("::")[0].strip()
                    ruta = ruta.split(" ")[0]
                    if not ruta.endswith((".py", ".cs")):
                        continue
                    # Busca por nombre de fichero, ignorando el arbol de carpetas.
                    existentes = [p for p in monorepos.rglob(Path(ruta).name)
                                  if "dist" not in p.parts]
                    if not existentes:
                        self.assertIn(
                            "a crear", valor,
                            f"{puerto['nombre']}.{lenguaje} apunta a {ruta}, que no "
                            "existe, pero el contrato no lo marca como 'a crear'")
                    elif "a tipar" in valor:
                        self.assertTrue(existentes,
                                        f"{puerto['nombre']}.{lenguaje} marcado "
                                        f"'a tipar' pero {ruta} no existe")

    def test_ningun_puerto_declara_la_jerarquia_equivocada_de_excepciones(self):
        """3 familias: checked, runtime e IOException. No se mezclan."""
        datos = _cargar()
        bases = {e["java"]: e["base"] for e in datos["excepciones"]}
        self.assertEqual("IllegalArgumentException", bases["ExcepcionValidacion"])
        self.assertEqual("IllegalStateException", bases["ExcepcionSesion"])
        self.assertEqual("IOException", bases["ExcepcionOperacionNoSoportada"])
        # Estas dos NO heredan de ExcepcionCliente: es deliberado.
        self.assertNotEqual("ExcepcionCliente", bases["ExcepcionValidacion"])
        self.assertNotEqual("ExcepcionCliente", bases["ExcepcionSesion"])

    def test_las_operaciones_opcionales_siguen_siendo_default(self):
        """Si pasan a abstractas, rompen los dobles de prueba y el LSP."""
        resultado = self.resultado.stdout
        for nombre in ("enviarArchivo", "enviarLectura", "listarUsuarios"):
            self.assertIn(f"InterfazConexionRed.{nombre}", resultado,
                          f"{nombre} dejo de ser opcional sin avisar")

    def test_la_tabla_de_backoff_no_cambio(self):
        """Python y .NET reconectan con la tabla del contrato."""
        resultado = self.resultado.stdout
        self.assertIn("{1,2,4,8,16,30}", resultado,
                      "la tabla de backoff cambio: los otros dos clientes "
                      "reconectarian con otra cadencia")

    def test_tipo_mensaje_cliente_tiene_los_28_valores(self):
        """Es el espejo de common-protocol. Perder uno rompe un comando entero."""
        enums = {e["nombre"]: e for e in _cargar()["enums"]}
        valores = enums["TipoMensajeCliente"]["valores"]
        self.assertEqual(28, len(valores))
        for obligatorio in ("LOGIN", "MENSAJE_TEXTO", "ARCHIVO_INICIO", "ARCHIVO_PARTE",
                           "ARCHIVO_FIN", "HISTORIAL_REQ", "ACK", "ERROR"):
            self.assertIn(obligatorio, valores)

    def test_los_componentes_de_respuesta_red_no_cambiaron(self):
        """Es la adaptacion del DTO del protocolo. Un componente de mas o menos
        significa que ese cliente no puede leer una trama concreta."""
        records = {r["nombre"]: r for r in _cargar()["records"]}
        componentes = records["RespuestaRed"]["componentes"]
        self.assertEqual(25, len(componentes),
                         f"RespuestaRed tiene {len(componentes)} componentes, "
                         "el contrato declara 25")
        for obligatorio in ("tipo", "id", "remitente", "destinatario", "contenido",
                           "hashSha256", "archivoId", "exito", "mensajeError"):
            self.assertTrue(any(obligatorio in c for c in componentes),
                            f"RespuestaRed perdio `{obligatorio}`")

    def test_mensaje_local_conserva_los_17_componentes(self):
        records = {r["nombre"]: r for r in _cargar()["records"]}
        self.assertEqual(17, len(records["MensajeLocal"]["componentes"]))

    def test_pendiente_envio_guarda_bytes_crudos(self):
        """El contrato dice `byte[]`, no Base64.

        Si alguien lo cambia a String porque es mas comodo en Python, el reintento
        de un archivo grande dejaria de fragmentarse y volveria el defecto de 16 MiB.
        """
        records = {r["nombre"]: r for r in _cargar()["records"]}
        componentes = records["PendienteEnvio"]["componentes"]
        payload = [c for c in componentes if "payload" in c]
        self.assertEqual(1, len(payload))
        self.assertIn("byte[]", payload[0],
                      "payload debe ser byte[] crudo, no Base64")

    def test_cada_puerto_declara_su_capa(self):
        """1 adaptadores · 2 puertos · 3 casos de uso y presentacion."""
        capas = {p["nombre"]: p["capa"] for p in _cargar()["puertos"]}
        self.assertEqual(1, capas["InterfazConexionRed"], "la red es un adaptador")
        self.assertEqual(2, capas["InterfazHistorialLocal"],
                         "el historial es un puerto de datos")
        self.assertEqual(3, capas["InterfazChatViewModel"],
                         "el ViewModel es caso de uso")
        for nombre, capa in capas.items():
            with self.subTest(puerto=nombre):
                self.assertIn(capa, (1, 2, 3), f"capa {capa} no valida")


if __name__ == "__main__":
    unittest.main()
