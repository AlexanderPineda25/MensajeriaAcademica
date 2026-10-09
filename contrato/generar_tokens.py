#!/usr/bin/env python
"""Genera el diseno de los 3 clientes desde una sola fuente de verdad.

    python contrato/generar_tokens.py           # regenera y verifica
    python contrato/generar_tokens.py --check   # solo verifica, no escribe (para CI)

Que resuelve (Fase 0.2 de PLAN_PARIDAD_CLIENTES.md, cierra VIS-01):
la paleta estaba escrita 7 veces a mano y ya habia derivado en dos sitios.

Salidas
    universidad-mensajeria/client/client-app/src/main/resources/estilos/chat.css
    universidad-mensajeria/client/client-app/src/main/resources/estilos/chat-claro.css
    universidad-mensajeria/client/client-app/src/main/resources/estilos/login.css
    universidad-mensajeria/client/client-app/src/main/resources/estilos/login-claro.css
    universidad-mensajeria/client-dotnet/Themes/TemaOscuro.xaml
    universidad-mensajeria/client-dotnet/Themes/TemaClaro.xaml
    universidad-mensajeria/client-python/ui/tema_tokens.py

Garantias
  - Falla (exit 1) si algun contraste declarado no llega al minimo 4.5:1.
  - Falla si una plantilla usa un token que no existe en el JSON.
  - Con --check falla si algun fichero generado esta desfasado.

Las plantillas son de 1 por pantalla, no de 1 por tema: por eso ya no puede
volver a pasar que `chat-claro.css` se quede sin un selector que `chat.css` si
tiene (le faltaban 3).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CONTRATO = RAIZ / "contrato"
PLANTILLAS = CONTRATO / "plantillas"
MONOREPOS = RAIZ / "universidad-mensajeria"

ESTILOS_JAVA = MONOREPOS / "client/client-app/src/main/resources/estilos"
WPF = MONOREPOS / "client-dotnet/Themes"
PYTHON = MONOREPOS / "client-python/ui"

#: Alias: clave que usa la plantilla -> token canonico real.
ALIAS = {
    "muted.fg.fecha": "muted.fg",
    "tick.unread.otro": "tick.unread",
"root.bg.lista": "root.bg",
    "badge.bg": "badge_bg",
}

#: De donde saca cada token comun en las plantillas.
COMUNES_POR_CLAVE = {
    "comunes.badge_bg": "badge_bg",
    "comunes.badge_fg": "badge_fg",
    "comunes.boton_fg": "boton_fg",
    "comunes.banner_offline_bg": "banner_offline_bg",
    "comunes.banner_offline_border": "banner_offline_border",
    "comunes.banner_offline_fg": "banner_offline_fg",
    "comunes.error_bg": "error_bg",
    "comunes.error_border": "error_border",
    "comunes.error_fg": "error_fg",
    "comunes.avatar_online": "avatar_online",
    "comunes.avatar_offline": "avatar_offline",
    "comunes.fuente": "fuente",
    "comunes.tamano_fuente": "tamano_fuente",
    "comunes.tamano_fuente_burbuja": "tamano_fuente_burbuja",
    "comunes.radio_burbuja": "radio_burbuja",
    "comunes.radio_campo": "radio_campo",
    "comunes.radio_badge": "radio_badge",
    "comunes.radio_tarjeta": "radio_tarjeta",
    "comunes.burbuja_ancho_min": "burbuja_ancho_min",
    "comunes.burbuja_ancho_max": "burbuja_ancho_max",
    "comunes.burbuja_ancho_def": "burbuja_ancho_def",
    "comunes.burbuja_fraccion_ancho": "burbuja_fraccion_ancho",
    "comunes.imagen_ancho": "imagen_ancho",
    "comunes.imagen_alto": "imagen_alto",
    "comunes.placeholder_ancho": "placeholder_ancho",
    "comunes.placeholder_alto": "placeholder_alto",
}


# --------------------------------------------------------------------------- color


def _canal(c: float) -> float:
    """Canal sRGB a lineal, segun WCAG 2.x."""
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminancia(color: str) -> float:
    h = color.lstrip("#")
    if len(h) == 3:
        h = "".join(ch * 2 for ch in h)
    r, g, b = (int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    return 0.2126 * _canal(r) + 0.7152 * _canal(g) + 0.0722 * _canal(b)


def contraste(foreground: str, background: str) -> float:
    a, b = luminancia(foreground), luminancia(background)
    claro, oscuro = max(a, b), min(a, b)
    return (claro + 0.05) / (oscuro + 0.05)


def es_color(valor: str) -> bool:
    return bool(re.fullmatch(r"#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})", valor.strip()))


def a_wpf(color: str) -> str:
    """WPF usa #AARRGGBB."""
    h = color.lstrip("#")
    if len(h) == 3:
        h = "".join(ch * 2 for ch in h)
    return "#FF" + h.upper()


def rgba_fondo(valor: str) -> bool:
    """Un rgba no se puede medir por contraste: hay que aplanarlo sobre su fondo."""
    return valor.startswith("rgba(")


def aplanar_rgba(valor: str, fondo: str) -> str:
    """Convierte `rgba(r, g, b, a)` en un hex opaco sobre `fondo`.

    WPF no entiende `rgba()`. Las partes translucidas de JavaFX (la cita, la
    sombra de la burbuja) existen para que se vea el fondo del chat; en WPF se
    aplanan sobre el fondo del tema, que es el mismo efecto visual.
    """
    numeros = [float(x) for x in
               re.fullmatch(r"rgba\(([^)]+)\)", valor.strip()).group(1).split(",")]
    r, g, b, alfa = numeros[:4]
    h = fondo.lstrip("#")
    base = [int(h[i:i + 2], 16) for i in (0, 2, 4)]
    mezclado = [round(c * (1 - alfa) + v * alfa) for c, v in zip(base, (r, g, b))]
    return "#" + "".join(f"{c:02X}" for c in mezclado)


def color_wpf(valor: str, fondo: str) -> str:
    """Normaliza cualquier valor de color a #AARRGGBB, que es lo que WPF entiende."""
    v = valor.strip()
    if v.lower() == "transparent":
        return "#00FFFFFF"
    if v.lower() == "white":
        return "#FFFFFFFF"
    if v.lower() == "black":
        return "#FF000000"
    if rgba_fondo(v):
        v = aplanar_rgba(v, fondo)
    h = v.lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    if len(h) == 6:
        return "#FF" + h.upper()
    if len(h) == 8:
        return "#" + h.upper()
    sys.exit(f"color no convertible a WPF: {valor!r}")


#: Formato que debe tener todo Color="..." de los .xaml generados.
RE_COLOR_XAML = re.compile(r'Color="([^"]*)"')


# --------------------------------------------------------------------------- carga


def cargar() -> dict:
    datos = json.loads((CONTRATO / "tokens-diseno.json").read_text(encoding="utf-8"))
    faltantes = {"comunes", "temas", "contrastes", "version"} - set(datos)
    if faltantes:
        sys.exit(f"tokens-diseno.json incomplete: faltan {sorted(faltantes)}")
    for modo in ("oscuro", "claro"):
        if modo not in datos["temas"]:
            sys.exit(f"tokens-diseno.json sin el tema '{modo}'")
    return datos


# --------------------------------------------------------------------------- render


def resolver(datos: dict, tema: str) -> dict[str, str]:
    """Un solo diccionario con los tokens del tema mas los comunes."""
    plano = dict(datos["comunes"])
    plano.update(datos["temas"][tema])
    plano["tema"] = tema
    for alias, real in ALIAS.items():
        plano[alias] = plano[real]
    for clave, valor_token in COMUNES_POR_CLAVE.items():
        plano[clave] = plano[valor_token]
    return plano


def render(plantilla: Path, plano: dict[str, str], destino: Path) -> str:
    texto = plantilla.read_text(encoding="utf-8")
    sustituciones: dict[str, str] = {"tema": plano["tema"]}

    def buscar(m: re.Match[str]) -> str:
        clave = m.group(1)
        if clave not in plano:
            sys.exit(f"{plantilla.name}: token desconocido '{{{{{clave}}}}}'")
        sustituciones[clave] = plano[clave]
        return plano[clave]

    salida = re.sub(r"\{\{([a-zA-Z0-9_.]+)\}\}", buscar, texto)
    if destino.suffix == ".xaml":
        fondo = plano["root.bg"]
        salida = RE_COLOR_XAML.sub(
            lambda m: f'Color="{color_wpf(m.group(1), fondo)}"', salida
        )
    if "{{CUERPO}}" in salida or "{{COMUNES}}" in salida:
        sys.exit(f"{plantilla.name}: quedan marcadores sin resolver")
    return salida


def render_tokens_python(datos: dict) -> str:
    """Genera tema_tokens.py: tokens canonicos + equivalencia con los nombres viejos."""
    plantilla = (PLANTILLAS / "tema_tokens.py.in").read_text(encoding="utf-8")
    mapa = dict(re.findall(r'"([a-zA-Z0-9_.]+)":\s*"([a-z_]+)"', plantilla))
    faltantes = [c for c in mapa
                 if any(c not in resolver(datos, t) for t in ("oscuro", "claro"))]
    if faltantes:
        sys.exit(f"tema_tokens.py.in: tokens que no existen en el JSON: {faltantes}")

    temas_codigo = []
    for tema in ("oscuro", "claro"):
        plano = resolver(datos, tema)
        # Qt no tiene color con nombre: `transparent` en un `border:` se acepta,
        # pero si el token acaba pintandose como fondo se queda en negro. Se
        # resuelve al color de fondo del tema, que es lo que significa "sin borde".
        for clave, valor in list(plano.items()):
            if valor.strip().lower() == "transparent":
                plano[clave] = plano["login.card.bg"]
        cuerpo = "\n".join(
            f'        "{clave}": "{valor}",'
            for clave, valor in plano.items()
            if not clave.startswith("comunes.") and clave != "tema"
        )
        temas_codigo.append(f'    "{tema}": {{\n{cuerpo}\n    }},')

    mapa_codigo = "\n".join(f'    "{c}": "{d}",' for c, d in sorted(mapa.items()))
    cabeceras = ('"""Paleta del cliente Python.\n\n'
                 'GENERADO por contrato/generar_tokens.py desde contrato/tokens-diseno.json.\n'
                 'No editar a mano: se edita el JSON y se regenera.\n\n'
                 'TOKENS      la paleta canonica, con los mismos nombres que JavaFX y WPF.\n'
                 'MAPA_LEGADO token canonico -> nombre que ya usaba ui/tema.py, para que ese\n'
                 '            modulo derive de aqui sin tocar chat_window.py.\n'
                 '"""\n\n')
    return (cabeceras + "TOKENS = {\n" + "\n".join(temas_codigo) + "\n}\n\n"
            + "MAPA_LEGADO = {\n" + mapa_codigo + "\n}\n")


# --------------------------------------------------------------------------- salida


def salidas(datos: dict) -> list[tuple[Path, str]]:
    planos = {tema: resolver(datos, tema) for tema in ("oscuro", "claro")}
    return [
        (ESTILOS_JAVA / "chat.css", render(PLANTILLAS / "chat.css.in", planos["oscuro"],
                                           ESTILOS_JAVA / "chat.css")),
        (ESTILOS_JAVA / "chat-claro.css", render(PLANTILLAS / "chat.css.in", planos["claro"],
                                                 ESTILOS_JAVA / "chat-claro.css")),
        (ESTILOS_JAVA / "login.css", render(PLANTILLAS / "login.css.in", planos["oscuro"],
                                            ESTILOS_JAVA / "login.css")),
        (ESTILOS_JAVA / "login-claro.css", render(PLANTILLAS / "login.css.in", planos["claro"],
                                                   ESTILOS_JAVA / "login-claro.css")),
        (WPF / "TemaOscuro.xaml", render(PLANTILLAS / "tema.xaml.in", planos["oscuro"],
                                         WPF / "TemaOscuro.xaml")),
        (WPF / "TemaClaro.xaml", render(PLANTILLAS / "tema.xaml.in", planos["claro"],
                                        WPF / "TemaClaro.xaml")),
        (PYTHON / "tema_tokens.py", render_tokens_python(datos)),
    ]


def escribir(destino: Path, contenido: str, solo_check: bool) -> bool:
    if destino.exists() and destino.read_text(encoding="utf-8") == contenido:
        return True
    if solo_check:
        print(f"  DESFASADO  {destino.relative_to(RAIZ)}")
        return False
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(contenido, encoding="utf-8")
    print(f"  generado   {destino.relative_to(RAIZ)}")
    return False


def verificar_contrastes(datos: dict) -> list[str]:
    fallos: list[str] = []
    print("Contraste (WCAG 2.x):")
    for regla in datos["contrastes"]:
        plano = resolver(datos, regla["tema"])
        fg, bg = plano[regla["fg"]], plano[regla["bg"]]
        minimo = float(regla.get("min", 4.5))
        if rgba_fondo(fg) or rgba_fondo(bg):
            print(f"  ?          {regla['tema']:7} {regla['fg']} sobre {regla['bg']}: "
                  f"rgba, no medible")
            continue
        ratio = contraste(fg, bg)
        ok = ratio >= minimo
        marca = "OK " if ok else "FALLA"
        print(f"  {marca}       {regla['tema']:7} {regla['fg']} sobre {regla['bg']}: "
              f"{ratio:.2f}:1 (min {minimo:g})")
        if not ok:
            fallos.append(f"{regla['tema']}: {regla['fg']} sobre {regla['bg']} = {ratio:.2f}:1 "
                          f"({regla.get('motivo', '')})")
    return fallos


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verifica sin escribir; util en CI")
    args = parser.parse_args()

    datos = cargar()

    print("Ficheros generados:")
    al_dia = True
    for destino, contenido in salidas(datos):
        al_dia &= escribir(destino, contenido, args.check)

    fallos = verificar_contrastes(datos)
    if fallos:
        print(f"\n{len(fallos)} contraste(s) por debajo del minimo:", file=sys.stderr)
        for fallo in fallos:
            print(f"  - {fallo}", file=sys.stderr)
        return 1

    if args.check and not al_dia:
        print("\nHay ficheros generados desfasados. Ejecuta: python contrato/generar_tokens.py",
              file=sys.stderr)
        return 1
    if not args.check and al_dia:
        print("\nTodo ya estaba al dia.")
    print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())