"""Fase 1.4 (base): las 6 excepciones del contrato con sus 3 familias.

Espejo 1:1 de `client-interfaces/transversal/excepciones` (Java) y de
`Transversal/Excepciones` (.NET). La fuente de verdad es
`contrato/puertos.json` -> `excepciones`, el mismo fichero que los records.

POR QUE NO SE COLAPSAN EN UNA SOLA
---------------------------------
La distincion de las 3 familias es lo que decide si un envio se REINTENTA o
queda marcado ERROR, asi que no es cosmetica. Python no distingue checked de
unchecked en el `try`, pero las familias se conservan en la jerarquia de
clases, que es lo que hace discriminable un `except` por tipo:

    Exception
    +-- ClienteError ................ checked (Java ExcepcionCliente)
    |   +-- HistorialError .......... fallo de la base local (reintentable)
    |   +-- PersistenciaError ....... fallo de esquema o de la conexion SQL
    +-- ValueError .................. runtime (Java IllegalArgumentException)
    |   +-- ExcepcionValidacion ..... argumento invalido
    +-- RuntimeError ............... runtime (Java IllegalStateException)
    |   +-- SesionError ............. rechazo del servidor o sesion cerrada
    +-- OSError .................... la familia IOException
        +-- OperacionNoSoportada .... puerto opcional sin adaptador

Que hacer con cada una:
- `OSError` / `ConnectionError` / `TimeoutError`: fallo TRANSITORIO. El envio
  se guarda en `pendientes_envio` y lo saca el flush periodico.
- `SesionError`: el servidor RECHAZO o la sesion esta cerrada. No se reintenta
  nunca: el mensaje pasa a estado ERROR.
- `ValueError` / `ExcepcionValidacion`: error del cliente (argumento malo). No
  se reintenta y no se encola: es un bug de la vista.
- `ClienteError` (y sus dos subfamilias): el puerto se respeto pero la base no
  pudo dar la operacion. Se propaga a la capa 3; no se reintenta a ciegas.
"""
from __future__ import annotations


class ClienteError(Exception):
    """Raiz de los fallos del cliente. Equivale a `ExcepcionCliente` en Java.

    En Java es *checked*: el llamante esta obligado a decidir. En Python el
    `except` es opcional, asi que la obligacion se traslada al contrato: quien
    llama a un puerto debe decidir y documentar que hace.
    """


class HistorialError(ClienteError):
    """Fallo al leer o escribir la base local por cuenta.

    Suele ser transitorio (SQLite bloqueado, disco lleno). El reintento de la
    cola offline tambien pasa por aqui, asi que el flush debe sobrevivir.
    """


class PersistenciaError(ClienteError):
    """Fallo de la persistencia de fondo: esquema, migracion o conexion SQL.

    No heredan las dos de un tipo comun a proposito: `HistorialError` son las
    filas de la conversacion, `PersistenciaError` es la base entera. Un catch
    por tipo no es intercambiable entre ellas.
    """


class ExcepcionValidacion(ValueError):
    """Argumento invalido: destinatario vacio, contenido en blanco, etc.

    Hereda de `ValueError` a proposito (el contrato pide `ValueError` en
    Python): quien hoy hace `except ValueError` sigue funcionando, que es
    justo el LSP que el contrato quiere conservar. No hereda de
    `ClienteError`: es runtime y no se reintenta ni se encola.
    """


class SesionError(RuntimeError):
    """Rechazo del servidor o sesion cerrada: no se reintenta nunca.

    Equivale a `ExcepcionSesion` (Java `IllegalStateException`). Deliberadamente
    NO hereda de `ClienteError`: el servidor dijo que no, y un reintento solo
    gastaria la cola. El mensaje se marca ERROR.
    """


class OperacionNoSoportada(OSError):
    """Un puerto opcional que el adaptador no implementa.

    Equivale a `ExcepcionOperacionNoSoportada`, que en Java extiende IOException
    a proposito. En Python hereda de `OSError` por el mismo motivo: es lo que
    permite que un puerto declare una operacion opcional (`enviarArchivo`,
    `enviarLectura`, `listarUsuarios`) sin romper el LSP de los llamantes que
    ya tratan `OSError` como "fallo de red, reintentar".
    """