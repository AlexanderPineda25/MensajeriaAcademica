"""Fase 1.5: las constantes de trama del cliente Python son las del contrato.

`transversal/tipo_mensaje.py` declara los 28 tipos a mano. Este test lo compara
con `contrato/puertos.json` -> `enums` -> `TipoMensajeCliente` -> `valores`, que
es la fuente de verdad compartida por los 3 clientes. Si alguien anade un tipo
al contrato y olvida el cliente (o al reves), esta suite falla.
"""
import json
import unittest
from pathlib import Path

from transversal.tipo_mensaje import (
    CONVERSACION,
    TODOS,
    TipoMensaje,
    es_valido,
)

#: Raiz del monorepos: client-python/tests -> client-python -> universidad-mensajeria -> raiz
_RAIZ = Path(__file__).resolve().parents[3]
_PUERTOS = _RAIZ / "contrato" / "puertos.json"


def tipos_del_contrato():
    """Los 28 valores de `TipoMensajeCliente` tal cual estan en el JSON."""
    contrato = json.loads(_PUERTOS.read_text(encoding="utf-8"))
    for enum in contrato["enums"]:
        if enum["nombre"] == "TipoMensajeCliente":
            return list(enum["valores"])
    raise AssertionError("contrato/puertos.json ya no declara TipoMensajeCliente")


def constantes_de_la_clase():
    """Los atributos publicos en MAYUSCULAS de `TipoMensaje`."""
    return {nombre: valor for nombre, valor in vars(TipoMensaje).items()
            if nombre.isupper()}


class TestTipoMensaje(unittest.TestCase):
    def test_el_contrato_declara_los_28_tipos(self):
        # Guarda del propio contrato: si el JSON pierde un tipo, este test lo
        # avisa en vez de dar por bueno un cliente con 27.
        self.assertEqual(len(tipos_del_contrato()), 28)

    def test_las_constantes_son_exactamente_las_del_contrato(self):
        self.assertEqual(set(constantes_de_la_clase().values()),
                         set(tipos_del_contrato()))

    def test_el_conjunto_publico_tiene_los_28_valores(self):
        self.assertEqual(TODOS, set(tipos_del_contrato()))
        self.assertEqual(len(TODOS), 28)

    def test_el_mapeo_es_por_nombre_exacto(self):
        # Java hace `TipoMensajeCliente.valueOf(mensaje.tipo().name())`: el
        # nombre de la constante debe ser el valor. Asi una trama desconocida
        # del servidor no se puede "acercar" por posicion.
        for nombre, valor in constantes_de_la_clase().items():
            self.assertEqual(nombre, valor)

    def test_es_valido_acepta_y_rechaza(self):
        self.assertTrue(es_valido(TipoMensaje.ACK))
        self.assertTrue(es_valido("MENSAJE_TEXTO"))
        self.assertFalse(es_valido("TIPO_INVENTADO"))
        self.assertFalse(es_valido(None))
        self.assertFalse(es_valido(7))

    def test_los_cinco_tipos_de_conversacion(self):
        self.assertEqual(CONVERSACION, {
            "MENSAJE_TEXTO", "MENSAJE_IMAGEN", "MENSAJE_ARCHIVO",
            "BROADCAST", "SYNC_LOGIN"})

    def test_el_contrato_no_declara_tipos_que_falten_aqui(self):
        # Simula la deriva: un tipo nuevo en el contrato que el cliente no tiene.
        self.assertEqual(set(tipos_del_contrato()) - set(TODOS), set())


if __name__ == '__main__':
    unittest.main()