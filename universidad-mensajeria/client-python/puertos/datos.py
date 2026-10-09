"""Fase 1.4 (base): los records del contrato como dataclasses frozen.

Espejo 1:1 de `client-interfaces/transversal/records` (Java) y de
`Transversal/Records` (.NET). La fuente de verdad es
`contrato/puertos.json` -> `records`; `tests/test_arquitectura.py` compara
componente a componente, asi que anadir o quitar un campo en el contrato rompe
la suite.

REGLA DURA (equivale a las 6 reglas ArchUnit del cliente Java): este modulo no
importa `PySide6`, `sqlite3` ni `json`. Es la capa 2 NEUTRA: describe datos,
no sabe de toolkits, de SQL ni de como se serializa el protocolo. Quien
mapea (el `InterfazMapeadorDatosServidor` de la Fase 2) es el unico que puede
importar `json`.

Convencion de nombres: los campos van en `snake_case` siguiendo las columnas
de `store/schema-local.sql`, que es la copia canonica de
`common-protocol/dto/.../schema-local.sql`, y en el MISMO orden que declara el
record Java. Asi el mapeo de la Fase 2 es mecanico y `tests/test_arquitectura.py`
puede comparar componente a componente contra `contrato/puertos.json`.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional


@dataclass(frozen=True)
class MensajeLocal:
    """Una fila de `historial_local`: 17 componentes.

    `destino == "*"` marca una difusion propia. `contenido` es NULL para
    imagenes y archivos: los bytes NO se guardan en la base local (§4.2), se
    descargan bajo demanda con `archivo_id` (AGENTS.md invariante 2).
    """

    id_mensaje: str
    origen: str
    destino: str
    tipo: str
    contenido: Optional[str]
    hash_sha256: Optional[str]
    num_caracteres: Optional[int]
    num_palabras: Optional[int]
    nombre_archivo: Optional[str]
    ruta_archivo: Optional[str]
    tamano_archivo: Optional[int]
    fecha_envio: str
    enviado: bool
    descargado: bool
    estado: str
    archivo_id: Optional[str]
    etapas_filtrado: Optional[str] = None


@dataclass(frozen=True)
class RecienteChat:
    """Una fila de la lista de contactos: quien es, que dijo y cuantos sin leer."""

    otro: str
    ultimo_contenido: Optional[str]
    ultimo_tipo: str
    fecha_ultimo: str
    no_leidos: int
    conectado: bool


@dataclass(frozen=True)
class PendienteEnvio:
    """La cola offline (`pendientes_envio`), en orden por `fecha_creado` ASC.

    `payload` son BYTES CRUDOS, jamas Base64 en `str`: al reintentar hay que
    volver a fragmentar el archivo en trozos de 1 MiB, y un Base64 no se puede
    cortar sin volver a codificar (AGENTS.md invariante 2). `payload` es None
    para texto y para los acuses de lectura.
    """

    id: str
    tipo: str
    origen: str
    destino: str
    contenido: Optional[str]
    nombre_archivo: Optional[str]
    payload: Optional[bytes]
    fecha_creado: str
    intentos: int
    ultimo_error: Optional[str]


@dataclass(frozen=True)
class UsuarioLocal:
    """Fila de `cache_usuarios`: el directorio local por cuenta."""

    codigo: str
    nombres: str
    apellidos: str
    programa: str
    conectado: bool
    fecha_registro: Optional[str]
    actualizado: Optional[str]


class EstadoMensaje(str, Enum):
    """Estados del mensaje definidos por ``contrato/estado-mensaje.json``."""

    PENDIENTE = "PENDIENTE"
    ENVIADO = "ENVIADO"
    ENTREGADO = "ENTREGADO"
    LEIDO = "LEIDO"
    ERROR = "ERROR"


@dataclass(frozen=True)
class PaginaHistorialRemota:
    """Respuesta paginada de `HISTORIAL_PAGE`: que pagina se pidio y cuanto hay."""

    recibidos: int
    pagina: int
    total_paginas: int


@dataclass(frozen=True)
class EventoCliente:
    """Evento de dominio ya adaptado: la presentacion nunca ve un `dict`."""

    class Tipo(Enum):
        """Los 5 eventos que el bus de la fachada publica a la UI."""

        PRESENCIA = "PRESENCIA"
        SESION_CERRADA = "SESION_CERRADA"
        ACUSE_ENTREGA = "ACUSE_ENTREGA"
        ACUSE_LECTURA = "ACUSE_LECTURA"
        MENSAJE = "MENSAJE"

    tipo: "EventoCliente.Tipo"
    remitente: str
    destinatario: str
