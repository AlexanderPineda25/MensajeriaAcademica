"""Puerto del directorio local en memoria/cache por cuenta."""
from __future__ import annotations

from typing import Protocol, Sequence, runtime_checkable

from puertos.datos import UsuarioLocal


@runtime_checkable
class ICacheUsuarios(Protocol):
    def guardar(self, usuario: UsuarioLocal) -> None: ...

    def reemplazar_directorio(self, usuarios: Sequence[UsuarioLocal]) -> None: ...

    def listar(self) -> list[UsuarioLocal]: ...

    def por_codigo(self, codigo: str) -> UsuarioLocal | None: ...

    def marcar_conectados(self, codigos: Sequence[str]) -> None: ...
