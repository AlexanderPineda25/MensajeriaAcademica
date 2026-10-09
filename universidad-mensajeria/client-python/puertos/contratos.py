"""Contratos neutros compartidos por el cliente Python.

Este modulo no conoce sockets, JSON, SQLite ni Qt. Los valores se mantienen
alineados con ``contrato/puertos.json``; las implementaciones concretas viven
en las capas de componentes, persistencia y presentacion.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional


class TipoMensajeCliente(str, Enum):
    """Tipo de trama interoperable con JavaFX y WPF (mapeo por nombre)."""

    LOGIN = "LOGIN"
    LOGIN_RESPUESTA = "LOGIN_RESPUESTA"
    REGISTRO = "REGISTRO"
    LOGOUT = "LOGOUT"
    CLOSE_NOTICE = "CLOSE_NOTICE"
    KICK = "KICK"
    MENSAJE_TEXTO = "MENSAJE_TEXTO"
    MENSAJE_IMAGEN = "MENSAJE_IMAGEN"
    MENSAJE_ARCHIVO = "MENSAJE_ARCHIVO"
    ARCHIVO_INICIO = "ARCHIVO_INICIO"
    ARCHIVO_PARTE = "ARCHIVO_PARTE"
    ARCHIVO_FIN = "ARCHIVO_FIN"
    IMAGE_FILTERED_RESULT = "IMAGE_FILTERED_RESULT"
    BROADCAST = "BROADCAST"
    MENSAJE_ENTREGADO = "MENSAJE_ENTREGADO"
    MENSAJE_LEIDO = "MENSAJE_LEIDO"
    PRESENCIA = "PRESENCIA"
    LISTAR_CONECTADOS = "LISTAR_CONECTADOS"
    LISTAR_CONECTADOS_RESPUESTA = "LISTAR_CONECTADOS_RESPUESTA"
    LISTAR_USUARIOS = "LISTAR_USUARIOS"
    LISTAR_USUARIOS_RESPUESTA = "LISTAR_USUARIOS_RESPUESTA"
    HISTORIAL_REQ = "HISTORIAL_REQ"
    HISTORIAL_PAGE = "HISTORIAL_PAGE"
    DESCARGAR_ARCHIVO = "DESCARGAR_ARCHIVO"
    DESCARGAR_ARCHIVO_RESPUESTA = "DESCARGAR_ARCHIVO_RESPUESTA"
    SYNC_LOGIN = "SYNC_LOGIN"
    ACK = "ACK"
    ERROR = "ERROR"


class EstadoConexion(str, Enum):
    """Estados observables y textos iguales a los de los otros clientes."""

    CONECTANDO = "conectando"
    CONECTADO = "conectado"
    SIN_RED = "sin_red"
    SESION_CERRADA = "sesion_cerrada"

    @property
    def texto(self) -> str:
        return {
            EstadoConexion.CONECTANDO: "Conectando…",
            EstadoConexion.CONECTADO: "En línea",
            EstadoConexion.SIN_RED: "Sin conexión — mensajes en cola local, se reintentan solos",
            EstadoConexion.SESION_CERRADA: "Sesión cerrada",
        }[self]

    @property
    def degradado(self) -> bool:
        return self in (EstadoConexion.SIN_RED, EstadoConexion.SESION_CERRADA)


@dataclass(frozen=True)
class RespuestaRed:
    """Respuesta de red ya adaptada; la capa de casos de uso no ve dicts."""

    tipo: TipoMensajeCliente
    id: Optional[str] = None
    fecha_hora: Optional[str] = None
    codigo: Optional[str] = None
    nombres: Optional[str] = None
    apellidos: Optional[str] = None
    programa: Optional[str] = None
    remitente: Optional[str] = None
    destinatario: Optional[str] = None
    contenido: Optional[str] = None
    hash_sha256: Optional[str] = None
    num_caracteres: Optional[int] = None
    num_palabras: Optional[int] = None
    etapas_filtrado: Optional[str] = None
    nombre_archivo: Optional[str] = None
    tamano_archivo: Optional[int] = None
    archivo_id: Optional[str] = None
    mime: Optional[str] = None
    contenido_imagen: Optional[str] = None
    pagina: Optional[int] = None
    total_paginas: Optional[int] = None
    exito: Optional[bool] = None
    mensaje_error: Optional[str] = None
    usuarios_conectados: Optional[list[str]] = None
    tipo_original: Optional[str] = None
