"""Nombres de compatibilidad para los tipos del contrato de puertos.

La unica declaracion de los 28 valores es ``puertos.contratos``. Este modulo
mantiene imports historicos de componentes/UI y los conjuntos auxiliares que
usa el historial SQLite. El valor JSON siempre es el nombre exacto del enum.
"""

from puertos.contratos import TipoMensajeCliente

# El nombre anterior sigue disponible; str-Enum conserva comparacion,
# serializacion JSON y binding SQLite compatibles con los literales existentes.
TipoMensaje = TipoMensajeCliente


#: Conjunto de tipos validos, derivado del enum. Frozen para evitar mutaciones.
TODOS = frozenset(valor.value for valor in TipoMensaje)

#: Tipos que el cliente persiste como mensaje de conversacion (los que llevan
#: burbuja). Usado por las consultas SQL del historial local.
CONVERSACION = frozenset({
    TipoMensaje.MENSAJE_TEXTO.value,
    TipoMensaje.MENSAJE_IMAGEN.value,
    TipoMensaje.MENSAJE_ARCHIVO.value,
    TipoMensaje.BROADCAST.value,
    TipoMensaje.SYNC_LOGIN.value,
})


def es_valido(tipo: object) -> bool:
    """True si `tipo` es uno de los 28 tipos del protocolo."""
    return isinstance(tipo, str) and tipo in TODOS
