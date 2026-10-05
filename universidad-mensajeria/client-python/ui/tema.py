"""Tokens de tema claro/oscuro (UI-09) + modo por defecto oscuro.

Los mismos tokens alimentan login y chat para que el cambio sea
instantáneo y persistente (clave ``tema`` en client.properties).
"""

MODO_OSCURO = "oscuro"
MODO_CLARO = "claro"
MODOS = (MODO_OSCURO, MODO_CLARO)


def normalizar(modo):
    return modo if modo in MODOS else MODO_OSCURO


TOKENS = {
    MODO_OSCURO: {
        "ventana": "#0E1621",
        "tarjeta": "#182533",
        "tarjeta_borde": "#2A3B4D",
        "cabecera": "#1F3A5F",
        "texto": "#E8EEF2",
        "texto_suave": "#A8B8C1",
        "prompt": "#7A8A99",
        "campo": "#243447",
        "lista": "#0E1621",
        "burbuja_propia": "#7B61B8",
        "burbuja_propia_borde": "#6A4FA3",
        "burbuja_ajena": "#182533",
        "burbuja_ajena_borde": "#2A3B4D",
        "remitente": "#C4B5FD",
        "cuerpo_propia": "#FFFFFF",
        "cuerpo_ajena": "#E8EEF2",
        "fecha": "#C4B5FD",
        "visto_leido": "#5EB1FF",
        "visto_pendiente": "#A8B8C1",
        "visto_otro": "#C4B5FD",
        "cita_fondo": "#243447",
        "cita_borde": "#A78BFA",
        "cita_texto": "#C4B5FD",
        "preview_fondo": "#243447",
        "preview_borde": "#2A3B4D",
        "preview_thumb_fondo": "#0E1621",
        "boton_enviar": "primary",
    },
    MODO_CLARO: {
        "ventana": "#EEF1F6",
        "tarjeta": "#FFFFFF",
        "tarjeta_borde": "#D7DEE8",
        "cabecera": "#1F3A5F",
        "texto": "#202124",
        "texto_suave": "#61717F",
        "prompt": "#7A8A99",
        "campo": "#FFFFFF",
        "lista": "#F4F7F7",
        "burbuja_propia": "#E1F5FE",
        "burbuja_propia_borde": "#B3E5FC",
        "burbuja_ajena": "#FFFFFF",
        "burbuja_ajena_borde": "#E1E8E9",
        "remitente": "#17635B",
        "cuerpo_propia": "#24383D",
        "cuerpo_ajena": "#24383D",
        "fecha": "#687A80",
        "visto_leido": "#1E88E5",
        "visto_pendiente": "#8A9BA8",
        "visto_otro": "#61717F",
        "cita_fondo": "#E6F4F2",
        "cita_borde": "#147D73",
        "cita_texto": "#147D73",
        "preview_fondo": "#F0F4F4",
        "preview_borde": "#DCE4E5",
        "preview_thumb_fondo": "#FFFFFF",
        "boton_enviar": "primary",
    },
}


def tokens(modo):
    return TOKENS[normalizar(modo)]


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
