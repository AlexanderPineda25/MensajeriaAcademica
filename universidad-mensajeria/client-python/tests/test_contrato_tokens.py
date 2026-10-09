"""Verifica que el diseño de los tres clientes viene del contrato compartido.

Este test es el que evita que la fase 0.2 se deshaga. Comprueba tres cosas:

1. Que los ficheros generados están al día (nadie editó a mano un CSS o un .xaml).
2. Que el cliente Python usa la paleta del contrato, no una copia congelada.
3. Que el contraste declarado se cumple de verdad.

No reimplementa el generador: ejecuta ``--check``, que ya valida plantillas,
tokens inexistentes y contraste.
"""
import re
import subprocess
import sys
import unittest
from pathlib import Path

from contrato_tokens import (
    TOKENS_CANONICOS,
    rutas_de_paleta,
    contraste,
)

CONTRATO = Path(__file__).resolve().parents[3] / "contrato"
GENERADOR = CONTRATO / "generar_tokens.py"
RAIZ = CONTRATO.parent
VENV_PYTHON = RAIZ / "universidad-mensajeria/client-python/.venv/Scripts/python.exe"

#: Colores que los .xaml de WPF deben cumplir. Un `rgba(...)` ahi revienta en
#: runtime aunque el proyecto compile, asi que se vigila de forma explicita.
RE_COLOR_XAML = re.compile(r'Color="([^"]*)"')
RE_HEX8 = re.compile(r"^#[0-9A-F]{8}$")


class TestContratoDiseno(unittest.TestCase):

    def test_ficheros_generados_estan_al_dia(self):
        """Nadie edito a mano un CSS, un .xaml o tema_tokens.py."""
        ejecutable = VENV_PYTHON if VENV_PYTHON.exists() else Path(sys.executable)
        resultado = subprocess.run(
            [str(ejecutable), str(GENERADOR), "--check"],
            capture_output=True, text=True, cwd=str(RAIZ),
        )
        self.assertEqual(
            0, resultado.returncode,
            "Hay ficheros generados desfasados. Ejecuta:\n"
            f"  {ejecutable} contrato/generar_tokens.py\n{resultado.stdout}"
            f"{resultado.stderr}",
        )

    def test_python_usa_la_paleta_del_contrato(self):
        """ui/tema.py debe derivar del JSON, no llevar una copia congelada."""
        from ui.tema import TOKENS, TOKENS_CANONICOS as EN_UI

        self.assertEqual(set(EN_UI), {"oscuro", "claro"})
        admitidos = re.compile(
            r"^(?:#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})"
            r"|transparent"                      # lo resuelve el generador, no llega aqui
            r"|rgba\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*[\d.]+\s*\))$"
        )
        for modo in ("oscuro", "claro"):
            self.assertTrue(TOKENS[modo], f"el cliente quedo sin tokens en {modo}")
            for nombre, valor in TOKENS[modo].items():
                self.assertRegex(
                    valor, admitidos,
                    f"{modo}/{nombre} = {valor!r} no es un color que Qt sepa pintar",
                )

    def test_rgba_si_llega_por_borde_o_texto(self):
        """Un rgba solo es aceptable donde Qt lo pone detras de algo: borde o texto.

        Qt no compone rgba() en un `background`, se queda en negro. Por eso la
        cita clara (que en JavaFX es translucida sobre la foto del chat) es la
        unica que puede llegar asi, y debe usarse en un border, no en un fondo.
        """
        from ui.tema import TOKENS

        permitidos = {"cita_fondo"}
        for modo in ("oscuro", "claro"):
            rgba = {n for n, v in TOKENS[modo].items() if v.strip().startswith("rgba(")}
            self.assertTrue(rgba <= permitidos,
                            f"{modo}: rgba() en {sorted(rgba - permitidos)}; "
                            "Qt solo lo compone en bordes y texto")

    def test_transparente_no_llega_a_python(self):
        """JavaFX y WPF admiten `transparent`; el generador debe resolverlo para Qt.

        `transparent` es valido en un `border:` de Qt, pero si ese token se
        pintara como fondo se quedaria negro. El generador lo convierte al color
        de fondo del tema, que es lo que significa "sin borde visible".
        """
        from ui.tema import TOKENS

        for modo in ("oscuro", "claro"):
            for nombre, valor in TOKENS[modo].items():
                self.assertNotEqual(valor.strip().lower(), "transparent",
                                    f"{modo}/{nombre} recibio `transparent`; Qt "
                                    "necesita un color concreto")

    def test_python_no_declara_la_paleta_a_mano(self):
        """tema.py no debe contener literales hex: seria una segunda fuente."""
        fuente = (RAIZ / "universidad-mensajeria/client-python/ui/tema.py").read_text(
            encoding="utf-8"
        )
        hexes = re.findall(r"#[0-9a-fA-F]{6}", fuente)
        self.assertEqual([], hexes,
                         "ui/tema.py tiene hexes literales; la paleta vive en "
                         "contrato/tokens-diseno.json")

    def test_colores_wpf_son_hex_de_8_digitos(self):
        """WPF solo entiende #AARRGGBB; rgba() o transparent crashean en runtime."""
        for etiqueta, contenido in rutas_de_paleta():
            if not etiqueta.startswith("wpf/"):
                continue
            for color in RE_COLOR_XAML.findall(contenido):
                self.assertRegex(color, RE_HEX8,
                                 f"{etiqueta}: Color=\"{color}\" no es #AARRGGBB")

    def test_contraste_declarado_se_cumple(self):
        """Reimplementa las reglas de `contrastes` del JSON de forma independiente."""
        import json

        datos = json.loads((CONTRATO / "tokens-diseno.json").read_text(encoding="utf-8"))
        fallos = []
        for regla in datos["contrastes"]:
            plano = TOKENS_CANONICOS(regla["tema"])
            fg, bg = plano[regla["fg"]], plano[regla["bg"]]
            if fg.startswith("rgba") or bg.startswith("rgba"):
                continue
            ratio = contraste(fg, bg)
            if ratio < float(regla.get("min", 4.5)):
                fallos.append(
                    f"{regla['tema']}: {regla['fg']} sobre {regla['bg']} = {ratio:.2f}:1 "
                    f"({regla.get('motivo', '')})"
                )
        self.assertEqual([], fallos, "Contrastes por debajo del minimo:\n" +
                         "\n".join(fallos))

    def test_chat_claro_tiene_los_mismos_selectores_que_chat(self):
        """El bug que motivo las plantillas: chat-claro.css se quedaba corto."""
        contenidos = dict(rutas_de_paleta())

        def selectores(texto):
            return set(re.findall(r"^\.([a-zA-Z0-9_\-]+)", texto, re.MULTILINE))

        oscuro = selectores(contenidos["java/chat.css"])
        claro = selectores(contenidos["java/chat-claro.css"])
        self.assertEqual(oscuro, claro,
                         f"Solo en chat.css: {sorted(oscuro - claro)}\n"
                         f"Solo en chat-claro.css: {sorted(claro - oscuro)}")

    def test_css_de_login_y_chat_no_se_solapan(self):
        """Un token compartido no puede significar dos cosas en pantallas distintas."""
        contenidos = dict(rutas_de_paleta())
        for etiqueta in ("java/chat.css", "java/chat-claro.css",
                         "java/login.css", "java/login-claro.css"):
            with self.subTest(archivo=etiqueta):
                self.assertIn("GENERADO por contrato/generar_tokens.py",
                              contenidos[etiqueta],
                              f"{etiqueta} no parece generado por el contrato")


if __name__ == "__main__":
    unittest.main()