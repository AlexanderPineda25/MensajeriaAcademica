"""Fase 1.6: paridad de framing con Java y .NET (contrato/framing.json).

El framing es lo UNICO que los 3 clientes comparten literalmente:
`[4 bytes de longitud big-endian][payload JSON en UTF-8]`. Este test ejecuta
los 6 vectores y los 5 rechazos del contrato contra `transversal/protocolo.py`.

Se compara contra `trama_hex` BYTE A BYTE, no solo que haya ida y vuelta: una
ida y vuelta con el mismo bug se cancela y no detecta nada. El vector
`multibyte_acentos` es el que mata (53 caracteres, 56 bytes): medir en
caracteres deja la cabecera 3 bytes corta.
"""
import json
import socket
import struct
import unittest
from functools import lru_cache
from pathlib import Path

from transversal.protocolo import (
    TOPE_TRAMA,
    empaquetar,
    enviar,
    recibir,
    serializar,
)

#: Raiz del monorepos: client-python/tests -> client-python -> universidad-mensajeria -> raiz
_RAIZ = Path(__file__).resolve().parents[3]
_FRAMING = _RAIZ / "contrato" / "framing.json"


@lru_cache(maxsize=1)
def _contrato() -> dict:
    return json.loads(_FRAMING.read_text(encoding="utf-8"))


def _vectores() -> list:
    return _contrato()["vectores"]


def _rechazos() -> list:
    return _contrato()["rechazos"]


def _leer(trama: bytes) -> dict:
    """Entrega `trama` cruda al codec del cliente y devuelve lo que responde."""
    escritor, lector = socket.socketpair()
    try:
        escritor.sendall(trama)
    finally:
        escritor.close()
    try:
        return recibir(lector)
    finally:
        lector.close()


class TestVectoresDeFraming(unittest.TestCase):
    def test_el_contrato_tiene_los_6_vectores_y_los_5_rechazos(self):
        self.assertEqual(len(_vectores()), 6)
        self.assertEqual(len(_rechazos()), 5)

    def test_longitudes_de_bytes_coinciden_con_el_contrato(self):
        for vector in _vectores():
            with self.subTest(vector["nombre"]):
                self.assertEqual(len(vector["payload"].encode("utf-8")),
                                 vector["longitud"])

    def test_trama_byte_a_byte_contra_el_contrato(self):
        # Comparacion EXACTA con `trama_hex`: si el codigo midiera caracteres
        # en vez de bytes, aqui falla; una ida y vuelta con ese bug se cancela.
        for vector in _vectores():
            with self.subTest(vector["nombre"]):
                trama = empaquetar(json.loads(vector["payload"]))
                self.assertEqual(trama.hex(), vector["trama_hex"])
                self.assertEqual(len(trama), 4 + vector["longitud"])

    def test_cabecera_big_endian_de_cuatro_bytes(self):
        for vector in _vectores():
            with self.subTest(vector["nombre"]):
                cabecera = empaquetar(json.loads(vector["payload"]))[:4]
                self.assertEqual(cabecera, struct.pack(">I", vector["longitud"]))

    def test_el_json_del_cliente_es_compacto(self):
        # Sin espacios separadores ni escapes ASCII: es lo que produce Gson en
        # Java y lo que hace comparables los bytes entre los 3 clientes. (Los
        # espacios DENTRO del contenido si se conservan.)
        crudo = serializar({"tipo": "MENSAJE_TEXTO", "contenido": "Señor café año"})
        self.assertEqual(crudo,
                         '{"tipo":"MENSAJE_TEXTO","contenido":"Señor café año"}'
                         .encode("utf-8"))
        self.assertNotIn(b'", "', crudo)
        self.assertNotIn(b'": "', crudo)

    def test_ida_y_vuelta_por_socket_real(self):
        for vector in _vectores():
            with self.subTest(vector["nombre"]):
                escritor, lector = socket.socketpair()
                try:
                    enviar(escritor, json.loads(vector["payload"]))
                    self.assertEqual(recibir(lector), json.loads(vector["payload"]))
                finally:
                    escritor.close()
                    lector.close()

    def test_lee_los_mismos_bytes_que_escribio_otro_lenguaje(self):
        # El lado lectura tambien, byte a byte: no basta con que lo nuestro
        # cuadre consigo mismo.
        for vector in _vectores():
            with self.subTest(vector["nombre"]):
                mensaje = _leer(bytes.fromhex(vector["trama_hex"]))
                self.assertEqual(mensaje, json.loads(vector["payload"]))


class TestMultibyteAcentos(unittest.TestCase):
    """EL CASO QUE MATA: 53 caracteres pero 56 BYTES en UTF-8."""

    def setUp(self):
        self.vector = next(v for v in _vectores()
                           if v["nombre"] == "multibyte_acentos")

    def test_el_contrato_declara_mas_bytes_que_caracteres(self):
        payload = self.vector["payload"]
        self.assertEqual(len(payload), self.vector["caracteres"])
        self.assertEqual(len(payload.encode("utf-8")), self.vector["longitud"])
        self.assertGreater(self.vector["longitud"], self.vector["caracteres"])

    def test_la_cabecera_no_usa_el_numero_de_caracteres(self):
        trama = empaquetar(json.loads(self.vector["payload"]))
        self.assertNotEqual(trama[:4],
                            struct.pack(">I", self.vector["caracteres"]))
        self.assertEqual(trama[:4], b"\x00\x00\x00\x38")

    def test_el_bug_de_caracteres_produciria_una_cabecera_corta(self):
        # Control negativo explicito: si alguien "optimiza" a len(str), la
        # trama queda con la cabecera del bug y este test lo canta.
        buggy = (struct.pack(">I", self.vector["caracteres"])
                 + self.vector["payload"].encode("utf-8"))
        self.assertNotEqual(buggy.hex(), self.vector["trama_hex"])
        # Solo cambian los 4 bytes de la cabecera: el payload es el mismo.
        self.assertEqual(buggy.hex()[8:], self.vector["trama_hex"][8:])
        self.assertEqual(buggy[:4], b"\x00\x00\x00\x35")


class TestRechazos(unittest.TestCase):
    def test_toda_trama_del_contrato_se_rechaza(self):
        for caso in _rechazos():
            with self.subTest(caso["nombre"]):
                self.assertTrue(caso["debe_fallar"])
                with self.assertRaises((ValueError, ConnectionError)):
                    _leer(bytes.fromhex(caso["trama_hex"]))

    def test_longitud_cero(self):
        with self.assertRaisesRegex(ValueError, "longitud invalida: 0"):
            _leer(bytes.fromhex("00000000"))

    def test_longitud_sobre_el_tope(self):
        # 32 MiB anunciados no pueden hacer que el cliente reserve esa memoria.
        with self.assertRaisesRegex(ValueError, "longitud invalida"):
            _leer(bytes.fromhex("02000000"))

    def test_trama_incompleta(self):
        # Anuncia 255 bytes y solo hay 2: el stream se acaba antes de tiempo.
        with self.assertRaises(ConnectionError):
            _leer(bytes.fromhex("000000ff4142"))

    def test_json_sin_tipo(self):
        with self.assertRaisesRegex(ValueError, "sin la clave"):
            _leer(bytes.fromhex("000000117b22746578746f223a22786f736861227d"))

    def test_json_roto_del_contrato(self):
        # El vector tal cual solo trae 2 de los 5 bytes que anuncia, asi que
        # el socket se acaba antes de que el parser opine. Se rechaza igual.
        with self.assertRaises((ValueError, ConnectionError)):
            _leer(bytes.fromhex("000000057b226e"))

    def test_json_truncado_con_la_longitud_correcta(self):
        # Mismo motivo del contrato ("JSON truncado") pero con los bytes
        # anunciados presentes: aqui el que eleva es el parser, no el socket.
        with self.assertRaises(ValueError):
            _leer(bytes.fromhex("000000027b226e"))

    def test_el_tope_es_de_16_mib(self):
        self.assertEqual(TOPE_TRAMA, 16777216)


if __name__ == '__main__':
    unittest.main()
