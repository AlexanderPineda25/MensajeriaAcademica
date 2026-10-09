"""PAT-03: los 4 estados de la conexión, observables por la vista.

Réplica exacta de `EstadoConexion.java` y `EstadoConexion.cs`. Antes Python solo
cambtaba un texto de paso dentro de `_procesar_desconexion` y el banner offline
se movía dentro del resultado de `directorio`, así que una caída sin refresco no
se veía. Los textos son los mismos en los 3 clientes (contrato compartido,
ver VIS-02).
"""

from puertos.contratos import EstadoConexion

CONECTANDO = EstadoConexion.CONECTANDO.value
CONECTADO = EstadoConexion.CONECTADO.value
SIN_RED = EstadoConexion.SIN_RED.value
SESION_CERRADA = EstadoConexion.SESION_CERRADA.value

TEXTOS = {
    CONECTANDO: "Conectando…",
    CONECTADO: "En línea",
    SIN_RED: "Sin conexión — mensajes en cola local, se reintentan solos",
    SESION_CERRADA: "Sesión cerrada",
}

#: Estados en los que no se puede trabajar en red (banner ambar + aviso).
DEGRADADOS = frozenset({SIN_RED, SESION_CERRADA})


def texto(estado):
    """Texto para la etiqueta de estado de la cabecera."""
    return TEXTOS.get(estado, TEXTOS[CONECTADO])


def muestra_banner(estado):
    """El banner de aviso se muestra solo en estados degradados."""
    return estado in DEGRADADOS
