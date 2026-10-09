"""Puerto de apertura y preparacion del almacenamiento local por cuenta."""
from __future__ import annotations

from contextlib import AbstractContextManager
from typing import Any, Protocol, runtime_checkable


@runtime_checkable
class IAlmacenLocal(Protocol):
    def abrir(self) -> AbstractContextManager[Any]: ...

    def preparar_cuenta(self, codigo: str) -> None: ...

    def inicializar(self) -> int: ...

    def cerrar(self) -> None: ...
