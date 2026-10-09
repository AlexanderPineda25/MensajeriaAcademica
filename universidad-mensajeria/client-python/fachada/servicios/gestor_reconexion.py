"""Gestiona el estado observable y la política de espera para reconexión."""

import datetime
import threading
from typing import Callable

from fachada.servicios.respuestas import lanzar_si_error
from transversal.estado_conexion import CONECTADO, CONECTANDO, SESION_CERRADA, SIN_RED
from transversal.tipo_mensaje import TipoMensaje


class GestorReconexionSesion:
    _ESPERA_RECONEXION_SEGUNDOS = (1, 2, 4, 8, 16, 30)

    def __init__(self, conexion, autenticacion, publicar_estado,
                 codigo_actual: Callable[[], str | None],
                 fijar_codigo: Callable[[str | None], None],
                 preparar_cuenta: Callable[[str], None],
                 exigir_sesion: Callable[[], None],
                 iniciar_flush: Callable[[], None],
                 detener_flush: Callable[[], None],
                 estado_inicial=SIN_RED):
        self.conexion = conexion
        self.autenticacion = autenticacion
        self._publicar_estado = publicar_estado
        self._codigo_actual = codigo_actual
        self._fijar_codigo = fijar_codigo
        self._preparar_cuenta = preparar_cuenta
        self._exigir_sesion = exigir_sesion
        self._iniciar_flush = iniciar_flush
        self._detener_flush = detener_flush
        self.estado = estado_inicial
        self._lock = threading.RLock()
        self._codigo_sesion = None
        self._contrasena_sesion = None
        self._cierre_intencional = True
        self._intentos_reconexion = 0
        self._reconexion_en_curso = False
        self._generacion_sesion = 0
        self._reintento_programado = None

    def cambiar_estado(self, nuevo):
        if nuevo == self.estado:
            return
        self.estado = nuevo
        try:
            self._publicar_estado(nuevo)
        except Exception:
            # Un observador de UI no puede romper el ciclo de red.
            pass

    def conectar(self):
        self.cambiar_estado(CONECTANDO)
        try:
            self.conexion.conectar()
            self.cambiar_estado(CONECTADO)
        except Exception:
            self.cambiar_estado(SIN_RED)
            raise

    def desconectar(self):
        self.olvidar_sesion()
        self._detener_flush()
        codigo = self._codigo_actual()
        if codigo:
            try:
                self.conexion.enviar_async({
                    "tipo": TipoMensaje.LOGOUT,
                    "codigo": codigo,
                    "fechaHora": datetime.datetime.now().isoformat(),
                })
            except Exception:
                pass
        self.conexion.desconectar()
        self._fijar_codigo(None)
        self.cambiar_estado(SIN_RED)

    def login(self, codigo, contrasena):
        if not self.conexion.conectado:
            self.conectar()
        respuesta = self.autenticacion.login(codigo, contrasena)
        if respuesta.get("exito"):
            self.guardar_sesion(codigo, contrasena)
            self.cambiar_estado(CONECTADO)
            self.fijar_cuenta(codigo)
        return respuesta

    def fijar_cuenta(self, codigo):
        self._fijar_codigo(codigo)
        self._preparar_cuenta(codigo)
        self._iniciar_flush()

    def expulsar_otras_sesiones(self):
        self._exigir_sesion()
        respuesta = self.conexion.pedir({
            "tipo": TipoMensaje.KICK,
            "codigo": self._codigo_actual(),
            "fechaHora": datetime.datetime.now().isoformat(),
        })
        lanzar_si_error(respuesta, "no se pudieron cerrar las otras sesiones")
        return True

    def guardar_sesion(self, codigo, contrasena):
        """Guarda credenciales solo en memoria para restaurar esta sesión."""
        with self._lock:
            self._generacion_sesion += 1
            self._codigo_sesion = codigo
            self._contrasena_sesion = contrasena
            self._cierre_intencional = False
            self._intentos_reconexion = 0
            self._cancelar_reintento()

    def olvidar_sesion(self):
        with self._lock:
            self._cierre_intencional = True
            self._generacion_sesion += 1
            self._codigo_sesion = None
            self._contrasena_sesion = None
            self._intentos_reconexion = 0
            self._cancelar_reintento()

    def programar_reconexion(self):
        with self._lock:
            if (self._cierre_intencional or not self._codigo_sesion
                    or self._contrasena_sesion is None
                    or self._reconexion_en_curso):
                return
            if self._reintento_programado is not None:
                return
            demora = self.espera_segundos(self._intentos_reconexion)
            self._intentos_reconexion += 1
            temporizador = threading.Timer(demora, self._intentar_reconexion)
            temporizador.daemon = True
            self._reintento_programado = temporizador
            temporizador.start()

    def sesion_cerrada_remotamente(self):
        self.olvidar_sesion()
        self._detener_flush()
        self._fijar_codigo(None)
        try:
            self.conexion.desconectar()
        finally:
            self.cambiar_estado(SESION_CERRADA)

    def cerrar(self):
        self.olvidar_sesion()
        self._detener_flush()

    def _intentar_reconexion(self):
        with self._lock:
            self._reintento_programado = None
            if (self._cierre_intencional or not self._codigo_sesion
                    or self._contrasena_sesion is None
                    or self._reconexion_en_curso):
                return
            self._reconexion_en_curso = True
            generacion = self._generacion_sesion
            codigo = self._codigo_sesion
            contrasena = self._contrasena_sesion
        reintentar = False
        try:
            self.cambiar_estado(CONECTANDO)
            self.conexion.desconectar()
            if not self._sesion_vigente(generacion):
                return
            self.conexion.conectar()
            if not self._sesion_vigente(generacion):
                self.conexion.desconectar()
                return
            respuesta = self.autenticacion.login(codigo, contrasena)
            if not respuesta.get("exito"):
                self.olvidar_sesion()
                self._fijar_codigo(None)
                self.conexion.desconectar()
                self.cambiar_estado(SESION_CERRADA)
                return
            if not self._sesion_vigente(generacion):
                self.conexion.desconectar()
                return
            with self._lock:
                self._intentos_reconexion = 0
            self._fijar_codigo(codigo)
            self._preparar_cuenta(codigo)
            self._iniciar_flush()
            self.cambiar_estado(CONECTADO)
        except Exception:
            self.conexion.desconectar()
            if self._sesion_vigente(generacion):
                self.cambiar_estado(SIN_RED)
                reintentar = True
        finally:
            with self._lock:
                self._reconexion_en_curso = False
        if reintentar:
            self.programar_reconexion()

    def _sesion_vigente(self, generacion):
        with self._lock:
            return (not self._cierre_intencional
                    and generacion == self._generacion_sesion)

    def _cancelar_reintento(self):
        if self._reintento_programado is not None:
            self._reintento_programado.cancel()
            self._reintento_programado = None

    @classmethod
    def espera_segundos(cls, intento):
        indice = min(max(0, intento), len(cls._ESPERA_RECONEXION_SEGUNDOS) - 1)
        return cls._ESPERA_RECONEXION_SEGUNDOS[indice]
