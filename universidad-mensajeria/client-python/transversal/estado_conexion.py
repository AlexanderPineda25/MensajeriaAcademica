"""PAT-03: los 4 estados de la conexión, observables por la vista.

Réplica exacta de `EstadoConexion.java` y `EstadoConexion.cs`. Antes Python solo
cambtaba un texto de paso dentro de `_procesar_desconexion` y el banner offline
se movía dentro del resultado de `directorio`, así que una caída sin refresco no
se veía. Los textos son los mismos en los 3 clientes (contrato compartido,
ver VIS-02).
"""

CONECTANDO = "conectando"
CONECTADO = "conectado"
SIN_RED = "sin_red"
SESION_CERRADA = "sesion_cerrada"

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