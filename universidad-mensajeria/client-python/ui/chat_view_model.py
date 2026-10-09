"""Estado de presentación de la búsqueda en el historial del chat."""

from dataclasses import dataclass, field


@dataclass
class ChatViewModel:
    """Conserva coincidencias y saltos pendientes sin depender de Qt."""

    ids_coincidencia: list[str] = field(default_factory=list)
    indice_coincidencia: int = -1
    patron_busqueda: str = ""
    salto_pendiente: tuple[str, int] | None = None

    def limpiar_busqueda(self) -> None:
        self.ids_coincidencia.clear()
        self.indice_coincidencia = -1
        self.patron_busqueda = ""
        self.salto_pendiente = None

    def fijar_coincidencias(self, patron: str, identificadores: list[str]) -> str | None:
        self.patron_busqueda = patron
        self.ids_coincidencia = list(identificadores)
        self.indice_coincidencia = 0 if self.ids_coincidencia else -1
        return self.ids_coincidencia[0] if self.ids_coincidencia else None

    def mover_coincidencia(self, paso: int) -> str | None:
        if not self.ids_coincidencia:
            return None
        self.indice_coincidencia = (
            self.indice_coincidencia + paso) % len(self.ids_coincidencia)
        return self.ids_coincidencia[self.indice_coincidencia]

    def programar_salto(self, identificador: str, intentos: int) -> None:
        self.salto_pendiente = (identificador, intentos)

    def consumir_salto(self) -> tuple[str, int] | None:
        pendiente = self.salto_pendiente
        self.salto_pendiente = None
        return pendiente
