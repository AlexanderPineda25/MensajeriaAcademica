"""Puerto de conversion entre los DTO crudos del servidor y datos neutros."""
from __future__ import annotations

from typing import Protocol, runtime_checkable

from puertos.datos import MensajeLocal, UsuarioLocal


@runtime_checkable
class IMapeadorDatosServidor(Protocol):
    def mapear_directorio(self, json: str) -> list[UsuarioLocal]: ...

    def mapear_pagina_historial(self, json: str,
                                codigo_sesion: str) -> list[MensajeLocal]: ...

    def normalizar_tipo_mensaje(self, tipo: str) -> str: ...
