"""UIX-03: formato de fecha unificado y relativo en los 3 clientes.

Réplica exacta de `FormatoFecha.java` y `FormatoFecha.cs`. Antes la burbuja
pintaba la cadena cruda (`2026-01-01 10:00:00` o `...T10:00:00` mezclados) y un
mensaje de ayer parecía de hoy. Contrato compartido (VIS-02):
"Hoy 10:00", "Ayer 10:00", "02-01 10:00", "02-01-2025 10:00".

Acepta `yyyy-MM-ddTHH:mm:ss` (red), `yyyy-MM-dd HH:mm:ss` (SQLite) y cadenas
con zona horaria: la base SQLite la comparten los clientes por cuenta y el
cliente .NET escribe UTC con sufijo `Z` (`DateTime.ToString("O")`).
Lo ilegible devuelve "" en vez de romper la fila.
"""
import datetime


def _sin_zona(fecha):
    """Deja el datetime naive en hora local (como `TryLeer` de .NET).

    Las filas escritas por Python/Java son naive y las del cliente .NET llegan
    aware; sin esta normalización mezclar ambas rompe con
    `TypeError: can't subtract offset-naive and offset-aware datetimes`.
    """
    if fecha.tzinfo is None:
        return fecha
    return fecha.astimezone().replace(tzinfo=None)


def _leer(fecha_envio):
    if not fecha_envio or not str(fecha_envio).strip():
        return None
    texto = str(fecha_envio).strip()
    # fromisoformat acepta 'T' o espacio, microsegundos y zona horaria.
    try:
        return _sin_zona(datetime.datetime.fromisoformat(texto))
    except ValueError:
        pass
    for formato in ("%Y-%m-%dT%H:%M:%S", "%Y-%m-%dT%H:%M", "%Y-%m-%d"):
        try:
            return _sin_zona(datetime.datetime.strptime(texto[:19], formato))
        except ValueError:
            continue
    return None


def hora_corta(fecha_envio):
    """Hora corta para el pie de la burbuja."""
    fecha = _leer(fecha_envio)
    if fecha is None:
        return ""
    hoy = datetime.date.today()
    dia = fecha.date()
    hora = fecha.strftime("%H:%M")
    if dia == hoy:
        return hora
    if dia.year == hoy.year:
        return fecha.strftime("%d-%m") + " " + hora
    return fecha.strftime("%d-%m-%Y") + " " + hora


def etiqueta_dia(fecha_envio):
    """Etiqueta para el separador de día (UIX-04)."""
    fecha = _leer(fecha_envio)
    if fecha is None:
        return ""
    hoy = datetime.date.today()
    dia = fecha.date()
    if dia == hoy:
        return "Hoy"
    if dia == hoy - datetime.timedelta(days=1):
        return "Ayer"
    if dia.year == hoy.year:
        return fecha.strftime("%d-%m")
    return fecha.strftime("%d-%m-%Y")


def clave_dia(fecha_envio):
    """Clave de agrupación por día (UIX-04): "" si ilegible."""
    fecha = _leer(fecha_envio)
    return "" if fecha is None else fecha.date().isoformat()


VENTANA_RACHA_MINUTOS = 5


def agrupar(anterior, actual):
    """UIX-05: ¿el mensaje actual va en la racha del anterior (sin remitente)?

    Mismo contrato que `FilasChat.agrupar` (Java) y `DebeMostrarEncabezado`
    (.NET, negado): mismo `origen`, mismo día y menos de 5 minutos.
    `anterior` y `actual` son las filas crudas (`origen`, `fecha_envio`).
    Con fecha ilegible no se agrupa: mostrar el remitente es lo seguro.
    """
    if not anterior or not actual:
        return False
    if (anterior.get("origen") or "") != (actual.get("origen") or ""):
        return False
    fa = _leer(anterior.get("fecha_envio"))
    fb = _leer(actual.get("fecha_envio"))
    if fa is None or fb is None:
        return False
    if fa.date() != fb.date():
        return False
    return abs((fb - fa).total_seconds()) < VENTANA_RACHA_MINUTOS * 60
