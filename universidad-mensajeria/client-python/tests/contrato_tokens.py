"""Acceso al contrato compartido desde las pruebas del cliente Python.

La paleta ya no vive en el cliente: vive en ``contrato/tokens-diseno.json`` y se
genera para los tres. Las pruebas deben leerla de ahi, no fijar hexes: asi un
cambio de diseno no obliga a tocar 100 tests y ningun test puede validar una
paleta que el cliente ya no usa.
"""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

# Raiz del monorepos: cliente-python/tests -> cliente-python -> universidad-mensajeria -> raiz
_RAIZ = Path(__file__).resolve().parents[3]
_TOKENS = _RAIZ / "contrato" / "tokens-diseno.json"


@lru_cache(maxsize=1)
def _contrato() -> dict:
    return json.loads(_TOKENS.read_text(encoding="utf-8"))


def TOKEN_CANONICO(tema: str, clave: str) -> str:
    """Valor de un token tal como esta en el JSON (sin generar)."""
    return _contrato()["temas"][tema][clave]


def TOKENS_CANONICOS(tema: str) -> dict[str, str]:
    """Todos los tokens del tema, con los comunes ya aplanados."""
    datos = _contrato()
    plano = dict(datos["comunes"])
    plano.update(datos["temas"][tema])
    return plano


def _canal(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminancia(color: str) -> float:
    h = color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    return 0.2126 * _canal(r) + 0.7152 * _canal(g) + 0.0722 * _canal(b)


def contraste(foreground: str, background: str) -> float:
    a, b = luminancia(foreground), luminancia(background)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def rutas_de_paleta() -> list[tuple[str, str]]:
    """Ficheros de los 3 clientes donde la paleta debe estar ya generada.

    Devuelve (etiqueta, contenido) para que cada test pueda afirmar que su
    cliente lleva los tokens del contrato y no una copia congelada.
    """
    monorepos = _RAIZ / "universidad-mensajeria"
    rutas = {
        "java/chat.css": monorepos / "client/client-app/src/main/resources/estilos/chat.css",
        "java/chat-claro.css": monorepos / "client/client-app/src/main/resources/estilos/chat-claro.css",
        "java/login.css": monorepos / "client/client-app/src/main/resources/estilos/login.css",
        "java/login-claro.css": monorepos / "client/client-app/src/main/resources/estilos/login-claro.css",
        "wpf/TemaOscuro.xaml": monorepos / "client-dotnet/Themes/TemaOscuro.xaml",
        "wpf/TemaClaro.xaml": monorepos / "client-dotnet/Themes/TemaClaro.xaml",
        "python/tema_tokens.py": monorepos / "client-python/ui/tema_tokens.py",
    }
    return [(etiqueta, ruta.read_text(encoding="utf-8")) for etiqueta, ruta in rutas.items()]


def color_wpf(valor: str) -> str:
    """El .xaml usa #AARRGGBB; el JSON guarda #RRGGBB."""
    h = valor.lstrip("#")
    return ("#" + h.upper()) if len(h) == 8 else "#FF" + h.upper()


def todos_los_tokens_en_contrato() -> set[str]:
    return set(_contrato()["temas"]["oscuro"]) | set(_contrato()["temas"]["claro"])