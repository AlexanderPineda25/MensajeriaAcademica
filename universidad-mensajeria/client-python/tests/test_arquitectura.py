"""Fase 1.4 (base): reglas de arquitectura de `puertos/` y paridad de los records.

Equivalente a `ArquitecturaClienteJavaTest` (las 6 reglas ArchUnit del cliente
Java) reducido a lo que esta fase entrega: la capa de datos neutra.

1. `puertos/` no importa PySide6, qfluentwidgets, sqlite3 ni json. Es la capa 2
   del contrato: describe datos, no sabe de toolkits, de SQL ni de como se
   serializa el protocolo. Es el mismo motivo por el que el `pom.xml` de
   `client-interfaces` no tiene bloque `<dependencies>`.
2. Los dataclasses tienen los componentes EXACTOS de `contrato/puertos.json`.
"""
import ast
import json
import re
import unittest
from dataclasses import fields, is_dataclass
from functools import lru_cache
from pathlib import Path
from typing import get_args, get_type_hints

from puertos import contratos, datos
from puertos.almacen import IAlmacenLocal
from puertos.autenticacion import IAutenticacion
from puertos.cache import ICacheUsuarios
from puertos.conexion import IInterfazConexionRed, ReceptorMensajes
from puertos.historial import IInterfazHistorialLocal
from puertos.mapeador import IMapeadorDatosServidor
from fachada.servicios.gestor_reconexion import GestorReconexionSesion
from puertos.excepciones import (
    ClienteError,
    ExcepcionValidacion,
    HistorialError,
    OperacionNoSoportada,
    PersistenciaError,
    SesionError,
)

_RAIZ_CLIENTE = Path(__file__).resolve().parents[1]
_PUERTOS_PY = _RAIZ_CLIENTE / "puertos"

#: Raiz del monorepos: client-python/tests -> client-python -> universidad-mensajeria -> raiz
_RAIZ = Path(__file__).resolve().parents[3]
_PUERTOS_JSON = _RAIZ / "contrato" / "puertos.json"

#: Modulos que la capa 2 no puede conocer. `json` esta por el DTO del
#: protocolo: parsear a mano es trabajo del mapeador, no de los datos.
IMPORTACIONES_PROHIBIDAS = ("PySide6", "qfluentwidgets", "sqlite3", "json")

#: Paquetes del propio cliente de los que `puertos/` tampoco puede depender:
#: la capa neutral no sabe de vistas, de fachada ni de adaptadores.
PAQUETES_PROHIBIDOS = ("ui", "fachada", "componentes", "store", "main")

#: Records que esta fase crea. `RespuestaRed` y `ResultadoOperacion` tambien
#: estan en el contrato; el primero es la adaptacion del DTO del protocolo
#: (Fase 2) y el segundo no lo consume nadie.
RECORDS_DE_ESTA_FASE = ("MensajeLocal", "RecienteChat", "PendienteEnvio",
                        "UsuarioLocal", "PaginaHistorialRemota", "EventoCliente")

_CAMEL = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")


@lru_cache(maxsize=1)
def _contrato() -> dict:
    return json.loads(_PUERTOS_JSON.read_text(encoding="utf-8"))


def _records_del_contrato():
    return {r["nombre"]: r for r in _contrato()["records"]}


def _a_snake(nombre_java: str) -> str:
    """`idMensaje` -> `id_mensaje`. El mapeo es mecanico y sin excepciones."""
    return _CAMEL.sub("_", nombre_java).lower()


def _modulos_de_puertos():
    return sorted(_PUERTOS_PY.glob("*.py"))


def _archivos_python(paquete: str):
    return sorted((_RAIZ_CLIENTE / paquete).rglob("*.py"))


def _importaciones(ruta: Path):
    """`(modulo, linea, nombre_importado)` de todos los `import`/`from`."""
    arbol = ast.parse(ruta.read_text(encoding="utf-8"), filename=str(ruta))
    for nodo in ast.walk(arbol):
        if isinstance(nodo, ast.Import):
            for alias in nodo.names:
                yield alias.name, nodo.lineno, alias.name
        elif isinstance(nodo, ast.ImportFrom):
            # `from __future__ import ...` y relatives no dependen de nadie.
            if nodo.level:
                continue
            for alias in nodo.names:
                yield (nodo.module or ""), nodo.lineno, alias.name


class TestPuertosSinDependencias(unittest.TestCase):
    """Regla dura: la capa 2 no conoce toolkits, SQL ni el protocolo."""

    def test_hay_al_menos_dos_modulos_en_puertos(self):
        # Si `puertos/` se vacia, este test pasaria porinercia: no.
        self.assertGreaterEqual(len(_modulos_de_puertos()), 3)

    def test_no_importa_pyside6_ni_qfluentwidgets(self):
        for ruta in _modulos_de_puertos():
            for modulo, linea, _ in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertFalse(modulo.startswith("PySide6"),
                                     f"{ruta.name}:{linea} importa PySide6")
                    self.assertFalse(modulo.startswith("qfluentwidgets"),
                                     f"{ruta.name}:{linea} importa qfluentwidgets")

    def test_no_importa_sqlite3(self):
        for ruta in _modulos_de_puertos():
            for modulo, linea, _ in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertNotEqual(modulo.split(".")[0], "sqlite3",
                                        f"{ruta.name}:{linea} importa sqlite3")

    def test_no_importa_json(self):
        # El unico que puede parsear el JSON crudo es el mapeador (capa 1).
        for ruta in _modulos_de_puertos():
            for modulo, linea, nombre in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertNotEqual(modulo.split(".")[0], "json",
                                        f"{ruta.name}:{linea} importa json")
                    self.assertNotEqual(nombre, "json",
                                        f"{ruta.name}:{linea} importa json")

    def test_no_importa_capas_internas_del_cliente(self):
        for ruta in _modulos_de_puertos():
            for modulo, linea, _ in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertNotIn(
                        modulo.split(".")[0], PAQUETES_PROHIBIDOS,
                        f"{ruta.name}:{linea} importa {modulo}")

    def test_datos_no_declara_ninguna_dependencia_ni_siquiera_opcional(self):
        # `get_type_hints` no debe necesitar nada para resolver los campos.
        for nombre in RECORDS_DE_ESTA_FASE:
            with self.subTest(record=nombre):
                pistas = get_type_hints(getattr(datos, nombre))
                self.assertTrue(pistas)


class TestRecordsIgualesAlContrato(unittest.TestCase):
    def test_los_6_records_de_esta_fase_existen(self):
        for nombre in RECORDS_DE_ESTA_FASE:
            with self.subTest(record=nombre):
                self.assertTrue(is_dataclass(getattr(datos, nombre)))

    def test_componentes_en_el_mismo_orden_que_el_contrato(self):
        contrato = _records_del_contrato()
        for nombre in RECORDS_DE_ESTA_FASE:
            with self.subTest(record=nombre):
                esperados = [_a_snake(c.split()[1])
                             for c in contrato[nombre]["componentes"]]
                reales = [f.name for f in fields(getattr(datos, nombre))]
                self.assertEqual(reales, esperados)

    def test_cuentas_de_componentes_del_contrato(self):
        contrato = _records_del_contrato()
        esperadas = {"MensajeLocal": 17, "RecienteChat": 6,
                     "PendienteEnvio": 10, "UsuarioLocal": 7,
                     "PaginaHistorialRemota": 3, "EventoCliente": 3}
        for nombre, cuenta in esperadas.items():
            with self.subTest(record=nombre):
                self.assertEqual(len(contrato[nombre]["componentes"]), cuenta)
                self.assertEqual(len(fields(getattr(datos, nombre))), cuenta)

    def test_payload_de_la_cola_offline_son_bytes_crudos(self):
        # AGENTS.md invariante 2: si esto fuera Base64 en `str`, el reintento
        # no podria volver a fragmentar el archivo en trozos de 1 MiB.
        pistas = get_type_hints(datos.PendienteEnvio)
        self.assertEqual(get_args(pistas["payload"]), (bytes, type(None)))

    def test_los_conteos_del_contrato_no_pueden_doblarse(self):
        # Guarda de que el mapeo camelCase -> snake_case no este inventando
        # campos: cada componente Java debe dar exactamente un campo Python.
        contrato = _records_del_contrato()
        for nombre in RECORDS_DE_ESTA_FASE:
            with self.subTest(record=nombre):
                componentes = contrato[nombre]["componentes"]
                self.assertEqual(len(componentes),
                                 len(set(componentes)))
                self.assertEqual(len({_a_snake(c.split()[1]) for c in componentes}),
                                 len(componentes))

    def test_todos_los_records_son_inmutables(self):
        for nombre in RECORDS_DE_ESTA_FASE:
            with self.subTest(record=nombre):
                self.assertTrue(getattr(datos, nombre).__dataclass_params__.frozen)

    def test_el_enum_de_evento_tiene_los_5_valores_del_contrato(self):
        esperados = _records_del_contrato()["EventoCliente"]["enums"]["Tipo"]
        reales = [t.name for t in datos.EventoCliente.Tipo]
        self.assertEqual(reales, list(esperados))
        # El mapeo es por NOMBRE: el valor es el nombre.
        for valor in datos.EventoCliente.Tipo:
            self.assertEqual(valor.name, valor.value)


class TestFamiliasDeExcepciones(unittest.TestCase):
    """3 familias: un `catch` por tipo NO es intercambiable entre ellas."""

    def test_las_6_excepciones_existen(self):
        self.assertEqual(len({ClienteError, HistorialError, PersistenciaError,
                              ExcepcionValidacion, SesionError,
                              OperacionNoSoportada}), 6)

    def test_familia_checked_debajo_de_cliente_error(self):
        for subclase in (HistorialError, PersistenciaError):
            with self.subTest(excepcion=subclase.__name__):
                self.assertTrue(issubclass(subclase, ClienteError))
        # Y NO se confunden con las otras dos familias.
        self.assertFalse(issubclass(HistorialError, RuntimeError))
        self.assertFalse(issubclass(HistorialError, OSError))

    def test_historial_y_persistencia_no_se_confunden(self):
        self.assertFalse(issubclass(HistorialError, PersistenciaError))
        self.assertFalse(issubclass(PersistenciaError, HistorialError))

    def test_sesion_es_runtime_y_no_checked(self):
        # Java `IllegalStateException`: rechazo del servidor, no se reintenta.
        self.assertTrue(issubclass(SesionError, RuntimeError))
        self.assertFalse(issubclass(SesionError, ClienteError))

    def test_validacion_es_value_error(self):
        # Java `IllegalArgumentException`; el contrato pide `ValueError` en
        # Python, y heredar de el es lo que conserva el LSP.
        self.assertTrue(issubclass(ExcepcionValidacion, ValueError))
        self.assertFalse(issubclass(ExcepcionValidacion, ClienteError))

    def test_operacion_no_soportada_es_io_error(self):
        # Java extiende IOException a proposito: un puerto opcional no rompe el
        # LSP de quien ya trata IOException como fallo de red.
        self.assertTrue(issubclass(OperacionNoSoportada, OSError))
        self.assertFalse(issubclass(OperacionNoSoportada, ClienteError))

    def test_las_3_familias_se_distinguen_con_except_por_tipo(self):
        # Lo que decide si un rechazo se reintenta: OSError se reintenta,
        # SesionError marca ERROR.
        self.assertTrue(issubclass(ConnectionError, OSError))
        self.assertFalse(issubclass(SesionError, OSError))
        with self.assertRaises(SesionError):
            try:
                raise SesionError("el servidor rechazo el destinatario")
            except OSError:
                self.fail("SesionError no debe caer en la familia IOException")
        with self.assertRaises(OSError):
            raise OperacionNoSoportada("el adaptador no lista usuarios")


class TestPuertosDeLaFaseDos(unittest.TestCase):
    """La capa declara contratos estructurales para transporte y persistencia."""

    def test_los_siete_protocolos_de_fase_2_1_existen(self):
        protocolos = (IInterfazConexionRed, ReceptorMensajes,
                      IInterfazHistorialLocal, ICacheUsuarios,
                      IAutenticacion, IMapeadorDatosServidor, IAlmacenLocal)
        for protocolo in protocolos:
            with self.subTest(protocolo=protocolo.__name__):
                self.assertTrue(getattr(protocolo, "_is_protocol", False))

    def test_nombres_de_metodo_coinciden_con_el_contrato_compartido(self):
        contrato = _contrato()
        clases = {
            "InterfazConexionRed": IInterfazConexionRed,
            "ReceptorMensajes": ReceptorMensajes,
            "InterfazHistorialLocal": IInterfazHistorialLocal,
            "InterfazCacheUsuarios": ICacheUsuarios,
            "InterfazAutenticacion": IAutenticacion,
            "InterfazMapeadorDatosServidor": IMapeadorDatosServidor,
            "InterfazAlmacenLocal": IAlmacenLocal,
        }
        for puerto in contrato["puertos"]:
            nombre = puerto["nombre"]
            if nombre not in clases:
                continue
            esperados = {
                _a_snake(re.search(r"([A-Za-z_]\w*)\s*\(", metodo["firma"]).group(1))
                for metodo in puerto["metodos"]
            }
            reales = {
                miembro for miembro, valor in vars(clases[nombre]).items()
                if callable(valor) and not miembro.startswith("_")
            }
            with self.subTest(puerto=nombre):
                self.assertEqual(reales, esperados)

    def test_enum_de_tramas_y_respuesta_red_coinciden_con_el_contrato(self):
        contrato = _contrato()
        enum = next(item for item in contrato["enums"]
                    if item["nombre"] == "TipoMensajeCliente")
        self.assertEqual([valor.value for valor in contratos.TipoMensajeCliente],
                         enum["valores"])
        record = _records_del_contrato()["RespuestaRed"]
        esperados = [_a_snake(componente.split()[1])
                     for componente in record["componentes"]]
        self.assertEqual([campo.name for campo in fields(contratos.RespuestaRed)],
                         esperados)

    def test_estado_de_conexion_conserva_textos_y_banner_del_contrato(self):
        contrato = _contrato()
        enum = next(item for item in contrato["enums"]
                    if item["nombre"] == "EstadoConexion")
        self.assertEqual([estado.name for estado in contratos.EstadoConexion],
                         [estado["nombre"] for estado in enum["valores"]])
        for esperado in enum["valores"]:
            actual = contratos.EstadoConexion[esperado["nombre"]]
            with self.subTest(estado=actual.name):
                self.assertEqual(actual.texto, esperado["texto"])
                self.assertEqual(actual.degradado, esperado["degradado"])

    def test_estado_mensaje_espeja_la_fuente_canonica(self):
        fuente = json.loads((_RAIZ / "contrato" / "estado-mensaje.json")
                            .read_text(encoding="utf-8"))
        self.assertEqual([estado.value for estado in datos.EstadoMensaje],
                         [estado["nombre"] for estado in fuente["estados"]])

    def test_puerto_de_historial_tiene_20_metodos_en_el_contrato(self):
        fuente = _contrato()
        puerto = next(p for p in fuente["puertos"]
                      if p["nombre"] == "InterfazHistorialLocal")
        self.assertEqual(len(puerto["metodos"]), 20)

    def test_backoff_de_reconexion_es_exponencial_y_acotado(self):
        self.assertEqual(
            [GestorReconexionSesion.espera_segundos(i) for i in range(8)],
            [1, 2, 4, 8, 16, 30, 30, 30])

    def test_fachada_python_no_supera_250_lineas(self):
        ruta = _RAIZ_CLIENTE / "fachada" / "fachada_cliente.py"
        self.assertLessEqual(len(ruta.read_text(encoding="utf-8").splitlines()), 250)


class TestFronterasDeLaFaseDos(unittest.TestCase):
    """Reglas de separación que ya se pueden hacer cumplir en Python."""

    def test_presentacion_no_importa_socket_ni_persistencia(self):
        prohibidos = {"sqlite3", "socket", "store", "componentes"}
        for ruta in _archivos_python("ui"):
            for modulo, linea, _ in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertNotIn(modulo.split(".")[0], prohibidos,
                                     f"{ruta.relative_to(_RAIZ_CLIENTE)} importa {modulo}")

    def test_fachada_no_importa_codec_ni_dto_de_protocolo(self):
        prohibidos = ("transversal.protocolo", "common_protocol.codec",
                      "common_protocol.tipos")
        nombres_prohibidos = {"DTOComando", "DTORespuesta", "RespuestaProtocolo"}
        for ruta in _archivos_python("fachada"):
            for modulo, linea, nombre in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertFalse(modulo.startswith(prohibidos),
                                     f"{ruta.relative_to(_RAIZ_CLIENTE)} importa {modulo}")
                    self.assertNotIn(nombre, nombres_prohibidos)

    def test_vistas_no_importan_la_fachada_concreta(self):
        for ruta in _archivos_python("ui"):
            for modulo, linea, nombre in _importaciones(ruta):
                with self.subTest(fichero=ruta.name, linea=linea):
                    self.assertNotEqual(modulo, "fachada.fachada_cliente",
                                        f"{ruta.relative_to(_RAIZ_CLIENTE)} importa la fachada concreta")
                    self.assertNotEqual(nombre, "FachadaCliente")

    def test_view_model_no_depende_de_framework_ni_adaptadores(self):
        ruta = _RAIZ_CLIENTE / "ui" / "chat_view_model.py"
        prohibidos = ("PySide6", "qfluentwidgets", "sqlite3", "socket",
                      "fachada", "componentes", "store", "transversal")
        for modulo, linea, _ in _importaciones(ruta):
            with self.subTest(linea=linea):
                self.assertFalse(modulo.startswith(prohibidos),
                                 f"chat_view_model.py importa {modulo}")

    def test_archivos_de_presentacion_y_fachada_no_superan_350_lineas(self):
        for paquete in ("ui", "fachada"):
            for ruta in _archivos_python(paquete):
                lineas = len(ruta.read_text(encoding="utf-8").splitlines())
                with self.subTest(fichero=ruta.relative_to(_RAIZ_CLIENTE)):
                    self.assertLessEqual(lineas, 350,
                                         f"{ruta.relative_to(_RAIZ_CLIENTE)} tiene {lineas} líneas")


if __name__ == '__main__':
    unittest.main()
