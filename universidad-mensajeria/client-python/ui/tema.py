"""Tokens de tema claro/oscuro (UI-09) + modo por defecto oscuro.

Los mismos tokens alimentan login y chat para que el cambio sea
instantáneo y persistente (clave ``tema`` en client.properties).

La paleta NO vive aquí: viene generada en :mod:`ui.tema_tokens` desde
``contrato/tokens-diseno.json``, la fuente única de los tres clientes.
Este módulo solo traduce los nombres canónicos a los que ya usaba el
cliente Python, de modo que ``chat_window.py`` no cambia ni una línea.
"""
from ui.tema_tokens import MAPA_LEGADO, TOKENS as _CANONICOS

MODO_OSCURO = "oscuro"
MODO_CLARO = "claro"
MODOS = (MODO_OSCURO, MODO_CLARO)


def normalizar(modo):
    return modo if modo in MODOS else MODO_OSCURO


#: Nombre antiguo -> token canónico del que depende.
_LEGADO_A_CANONICO = {viejo: nuevo for nuevo, viejo in MAPA_LEGADO.items()}

#: La paleta real del cliente, con los nombres canónicos.
TOKENS_CANONICOS = {
    modo: {clave: valor for clave, valor in _CANONICOS[modo].items()}
    for modo in MODOS
}

#: Diccionario con los nombres que ya usaba el cliente. Se mantiene porque
#: `chat_window.py` y `login_window.py` lo consumen directamente.
TOKENS = {
    modo: {viejo: TOKENS_CANONICOS[modo][canonico]
           for viejo, canonico in _LEGADO_A_CANONICO.items()
           if canonico in TOKENS_CANONICOS[modo]}
    for modo in MODOS
}


def tokens(modo):
    return TOKENS[normalizar(modo)]


def aplicar_tema_fluent(modo):
    """Sincroniza el tema global de QFluentWidgets con el modo elegido.

    Sin esto, `main.py` fijaba Theme.DARK al arrancar y los controles Fluent
    (LineEdit, PasswordLineEdit, ComboBox, PrimaryPushButton) se quedaban
    oscuros aunque la ventana pasara a claro. El acento sigue al token
    `login.button.bg` del modo, que ya viene distinto por tema del contrato.
    """
    from PySide6.QtGui import QColor
    from qfluentwidgets import Theme, setTheme, setThemeColor

    modo = normalizar(modo)
    setTheme(Theme.DARK if modo == MODO_OSCURO else Theme.LIGHT)
    setThemeColor(QColor(TOKENS_CANONICOS[modo]["login.button.bg"]))


def modo_por_indice(indice):
    """Indice del combo -> modo, con oscuro como defecto."""
    return MODOS[indice] if 0 <= indice < len(MODOS) else MODO_OSCURO


def modo_guardado(fachada):
    """Modo persistido en client.properties (oscuro si no se puede leer).

    La implementacion vivia duplicada byte a byte en ChatWindow y LoginWindow
    (MUERTE-5): un arreglo en una ventana no llegaba a la otra.
    """
    try:
        config = getattr(fachada, "config", None)
        if config is not None:
            return normalizar(config.get("tema", MODO_OSCURO))
    except Exception:
        pass
    return MODO_OSCURO


def guardar_modo(fachada, modo):
    """Persiste el modo; si el config no sabe escribir, se ignora."""
    try:
        config = getattr(fachada, "config", None)
        if config is not None and hasattr(config, "set"):
            config.set("tema", normalizar(modo))
            if hasattr(config, "save"):
                config.save()
    except Exception:
        pass


def estilo_tarjeta(t):
    return (
        f"background: {t['tarjeta']}; border: 1px solid {t['tarjeta_borde']};"
        " border-radius: 12px;"
    )


def estilo_cabecera(t):
    return f"background: {t['cabecera']}; border-radius: 8px;"


def estilo_burbuja(t, propia):
    fondo = t["burbuja_propia"] if propia else t["burbuja_ajena"]
    borde = t["burbuja_propia_borde"] if propia else t["burbuja_ajena_borde"]
    return (
        "QFrame#bubble {"
        f" background: {fondo}; border: 1px solid {borde}; border-radius: 8px;"
        " } QLabel { border: 0; background: transparent; }"
    )


def estilo_cita(t):
    return (
        "QFrame#citaContainer {"
        f" background: {t['cita_fondo']}; border: 1px solid {t['cita_borde']};"
        f" border-left: 3px solid {t['cita_borde']}; border-radius: 6px;"
        " }"
    )


def estilo_preview(t):
    return (
        "QFrame#previewContainer {"
        f" background: {t['preview_fondo']}; border: 1px solid {t['preview_borde']};"
        " border-radius: 6px; }"
    )
