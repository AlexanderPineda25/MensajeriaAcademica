"""TLS opcional del cliente Python (espejo de ConexionCliente Java, FASE 11.2).

Mismo framing sobre el socket envuelto; solo cambia el socket. Paridad de
fail-fast con Java/.NET y con el servidor (``validarTls``):
- TLS habilitado con puertos fijos iguales (plano == TLS) -> error accionable.
- Almacen de confianza indicado pero ausente -> error accionable.
- Sin ``tls.confianza`` se usan las CA del sistema (como el SSLSocketFactory
  por defecto de Java); el hostname SIEMPRE se verifica (sin downgrade
  silencioso a plano).
"""
import os
import ssl

TLS_PUERTO_DEFECTO = 5001


def interpretar_habilitado_tls(habilitado):
    """Convierte la propiedad TLS a bool sin ocultar valores mal escritos.

    La clave ausente y una propiedad vacia significan TLS deshabilitado; si
    contiene texto, solo se aceptan ``true`` y ``false``.
    """
    if isinstance(habilitado, bool):
        return habilitado
    if habilitado is None:
        return False
    if isinstance(habilitado, str):
        valor = habilitado.strip().lower()
        if not valor:
            return False
        if valor == "true":
            return True
        if valor == "false":
            return False
    raise ValueError("valor invalido en tls.habilitado: use true o false")


def validar_tls(habilitado, puerto_plano, puerto_tls, confianza):
    """Falla rapido ANTES de abrir ningun socket.

    Acepta bool/str para ``habilitado`` (los properties llegan como texto).
    ``puerto_tls == 0`` significa efimero/igual que el plano (.NET hace lo
    mismo): solo colisionan dos fijos iguales.
    """
    if not interpretar_habilitado_tls(habilitado):
        return
    try:
        plano = int(puerto_plano)
    except (TypeError, ValueError):
        plano = 0
    try:
        tls = int(puerto_tls)
    except (TypeError, ValueError):
        tls = TLS_PUERTO_DEFECTO
    if tls == plano and tls != 0:
        raise ValueError(
            f"TLS y plano comparten el puerto {plano}: cambie tls.puerto "
            "a uno distinto de puerto en client.properties")
    if confianza:
        if not os.path.isfile(confianza):
            raise ValueError(
                f"TLS habilitado pero el almacen de confianza no existe: {confianza} "
                "(revise tls.confianza: ruta a un bundle PEM con la CA; "
                "el almacen real nunca se versiona)")


def crear_contexto(confianza=""):
    """SSLContext cliente: TLS>=1.2, hostname y cadena SIEMPRE verificados."""
    contexto = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    contexto.check_hostname = True
    contexto.verify_mode = ssl.CERT_REQUIRED
    if hasattr(ssl, "TLSVersion"):
        contexto.minimum_version = ssl.TLSVersion.TLSv1_2
    if confianza:
        contexto.load_verify_locations(cafile=confianza)
    else:
        contexto.load_default_certs()
    return contexto
