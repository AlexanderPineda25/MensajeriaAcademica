"""Reglas compartidas al interpretar respuestas síncronas del servidor."""


def lanzar_si_error(respuesta, defecto, ok_tipos=("ACK",)):
    """Eleva rechazos explícitos; tolera respuestas exitosas de tipo nuevo."""
    tipo = respuesta.get("tipo")
    if tipo in ok_tipos and respuesta.get("exito", True):
        return
    if tipo == "ERROR" or not respuesta.get("exito", True):
        raise RuntimeError(respuesta.get("mensajeError") or defecto)

