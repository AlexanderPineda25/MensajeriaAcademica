"""Framing del protocolo: cabecera de 4 bytes big-endian + JSON en UTF-8.

Es lo UNICO que los 3 clientes comparten literalmente (AGENTS.md invariante 1).
`contrato/framing.json` fija 6 vectores y 5 rechazos, y `tests/test_framing.py`
los ejecuta byte a byte contra este modulo.

El caso que mata es `multibyte_acentos`: 53 caracteres, 56 BYTES en UTF-8.
Medir en caracteres escribe 0x35 en vez de 0x38, la cabecera queda 3 bytes
corta, el lector se descuadra y se pierde todo lo que venga despues. Por eso
la longitud se mide SIEMPRE sobre los `bytes` ya serializados, nunca sobre el
`str` de Python. Una ida y vuelta con ese bug se cancela y no detecta nada.
"""
import json
import struct

#: 16 MiB (AGENTS.md invariante 2). Los archivos no caben: viajan fragmentados.
TOPE_TRAMA = 16 * 1024 * 1024

#: Clave obligatoria de toda trama. El servidor no distingue `null` de ausente,
#: asi que una trama sin `tipo` no es un mensaje: se rechaza.
CLAVE_TIPO = "tipo"


def serializar(obj) -> bytes:
    """Payload JSON UTF-8 compacto: sin espacios y sin escapes ASCII.

    Compacto para que los bytes coincidan con los de Gson (que es lo que
    escribe el servidor); `ensure_ascii=False` para no inflar un acento a
    seis bytes.
    """
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def empaquetar(obj) -> bytes:
    """`[4 bytes de longitud big-endian][payload UTF-8]` listo para el socket."""
    payload = serializar(obj)
    return struct.pack('>I', len(payload)) + payload


def enviar(sock, obj) -> None:
    """Escribe una trama completa."""
    sock.sendall(empaquetar(obj))


def recibir(sock) -> dict:
    """Lee una trama completa y la devuelve como `dict`.

    Eleva `ValueError` si la longitud es invalida, si el JSON no parsea o si
    falta la clave `tipo`; `ConnectionError` si el stream se acaba antes de
    tiempo. No hay respuestas parciales: o la trama entera o ninguna.
    """
    cabecera = _recv_exact(sock, 4)
    if not cabecera:
        raise ConnectionError('conexion cerrada')
    n = struct.unpack('>I', cabecera)[0]
    if n <= 0 or n > TOPE_TRAMA:
        raise ValueError(f'longitud invalida: {n}')
    data = _recv_exact(sock, n)
    mensaje = json.loads(data.decode('utf-8'))
    if not isinstance(mensaje, dict) or CLAVE_TIPO not in mensaje:
        raise ValueError(f'trama sin la clave "{CLAVE_TIPO}": no es un mensaje')
    return mensaje


def _recv_exact(sock, n):
    buf = b''
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError('conexion cerrada')
        buf += chunk
    return buf
