"""Puerto de autenticacion, libre de detalles de UI y transporte."""
from __future__ import annotations

from typing import Protocol, runtime_checkable


@runtime_checkable
class IAutenticacion(Protocol):
    def login(self, codigo: str, contrasena: str) -> bool: ...

    def logout(self) -> None: ...

    def olvidar_sesion(self) -> None: ...

    def codigo_actual(self) -> str | None: ...

    def sesion_abierta(self) -> bool: ...
