"""Mapeo y presentación segura de la traza de filtros de una imagen."""
from __future__ import annotations

import json
import math
import re
from typing import Any


def mapear_etapas_filtrado(valor: Any) -> str | None:
    """Adapta `etapasFiltrado` de la trama al texto JSON persistible.

    El servidor envía este campo como una cadena JSON. Aceptar también listas
    y objetos conserva compatibilidad con transportes que ya hayan decodificado
    el JSON exterior e interior. Las cadenas existentes se conservan tal cual.
    """
    if valor is None:
        return None
    if isinstance(valor, str):
        return valor if valor.strip() else None
    if isinstance(valor, (list, dict)):
        try:
            return json.dumps(valor, ensure_ascii=False, separators=(",", ":"))
        except (TypeError, ValueError):
            return None
    return None


def resumir_etapas_filtrado(valor: Any) -> str:
    """Devuelve el texto de paridad para las etapas, sin incluir rutas."""
    if isinstance(valor, str):
        if not valor.strip():
            return ""
        try:
            etapas = json.loads(valor)
        except (TypeError, ValueError):
            return ""
    else:
        etapas = valor
    if not isinstance(etapas, list):
        return ""

    resumen = []
    for indice, etapa in enumerate(etapas, start=1):
        if not isinstance(etapa, dict):
            continue
        nombre = _nombre_etapa(etapa.get("etapa"), indice)
        ms = etapa.get("ms")
        duracion = ""
        if (not isinstance(ms, bool) and isinstance(ms, (int, float))
                and math.isfinite(ms) and ms >= 0):
            ms_texto = str(int(ms)) if float(ms).is_integer() else format(ms, ".15g")
            duracion = f" ({ms_texto} ms)"
        resumen.append(nombre + duracion)
    return "Filtros: " + " → ".join(resumen) if resumen else ""


def _nombre_etapa(valor: Any, indice: int) -> str:
    if not isinstance(valor, str) or not valor.strip():
        return f"Etapa {indice}"
    # Si `etapa` contiene una ruta heredada, conserva solo el nombre final.
    # El campo `ruta` nunca se consulta ni se muestra.
    nombre = valor.strip().replace("\\", "/").rsplit("/", 1)[-1]
    nombre = re.sub(r"\.[^.]*$", "", nombre)
    nombre = re.sub(r"^\d+[_\-\s.]*", "", nombre)
    clave = re.sub(r"[^a-z0-9áéíóúñ]", "", nombre.casefold())
    if clave in {"sha256", "sha256imagen"}:
        return "Verificación SHA-256"
    if clave in {"grises", "escalagris", "escalagrises", "escaladegrises"}:
        return "Grises"
    if clave == "sepia":
        return "Sepia"
    if clave in {"reduccion", "reducción"}:
        return "Reducción"
    if clave == "brillo":
        return "Brillo"
    giro = re.fullmatch(r"giro(\d+)", clave)
    if giro:
        return f"Giro {giro.group(1)}°"
    if clave == "giro":
        return "Giro"
    nombre = re.sub(r"[_\-]+", " ", nombre)
    nombre = re.sub(r"(?<=[a-záéíóúñ])(?=[A-Z])", " ", nombre)
    palabras = nombre.split()
    return " ".join(palabra[:1].upper() + palabra[1:] for palabra in palabras) \
        if palabras else f"Etapa {indice}"
