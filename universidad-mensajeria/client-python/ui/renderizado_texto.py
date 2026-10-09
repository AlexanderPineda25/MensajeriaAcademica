"""Funciones de presentación de texto reutilizables por los delegados."""

import html


def resaltar_html(texto, patron):
    """Escapa el texto y marca coincidencias sin interpretar el patrón como regex."""
    texto = texto or ""
    patron = (patron or "").strip()
    if not patron:
        return html.escape(texto)
    minus, mp = texto.casefold(), patron.casefold()
    partes, pos = [], 0
    while True:
        indice = minus.find(mp, pos)
        if indice < 0:
            break
        partes.append(html.escape(texto[pos:indice]))
        partes.append('<span style="background:#7B61B8;color:#FFFFFF;">'
                      + html.escape(texto[indice:indice + len(mp)]) + "</span>")
        pos = indice + len(mp)
    partes.append(html.escape(texto[pos:]))
    return "".join(partes)
