"""Fachada del cliente Python (unico punto de entrada de la UI, §7.2).

Espejo de FachadaCliente Java (Fase 5):
- Enviado con red → pedir() + ACK; guarda local con metadatos del servidor.
- Sin red → guarda local + pendiente (reintento con ACK al reconectar).
- Recibido (texto/imagen/broadcast) → persiste con destino = codigo propio.
- Errores del servidor (destinatario invalido, limites) se elevan, no se
  tragan: el fire-and-forget quedo reservado solo para LOGOUT.
"""
from componentes.conexion_red import ConexionRed
from componentes.autenticacion import Autenticacion
from componentes.historial_local import HistorialLocal
from fachada.servicios.coordinador_mensajeria import CoordinadorMensajeriaCliente
from fachada.servicios.coordinador_mensajeria import (
    ARCHIVO_MAX_BYTES as _ARCHIVO_MAX_BYTES,
    PARTE_MAX_BYTES as _PARTE_MAX_BYTES,
)
from fachada.servicios.lector_historial_remoto import LectorHistorialRemotoCliente
from fachada.servicios.reintentador_pendientes import (
    INTERVALO_FLUSH_MINIMO_SEGUNDOS,
    INTERVALO_FLUSH_SEGUNDOS,
    ReintentadorPendientesCliente,
)
from fachada.servicios.sincronizador_directorio import SincronizadorDirectorioCliente
from fachada.servicios.publicador_eventos import PublicadorEventosCliente
from fachada.servicios.receptor_entrante import ReceptorEntranteCliente
from fachada.servicios.gestor_reconexion import GestorReconexionSesion
from store.database import Database
from transversal.config import Config
DESTINO_BROADCAST = "*"

class FachadaCliente:
    def __init__(self, on_mensaje_recibido_ui=None, on_cierre_ui=None,
                 on_desconexion_ui=None, config=None, db=None, conexion=None,
                 autenticacion=None, historial=None, on_presencia_ui=None):
        self.config = config or Config()
        self.db = db or Database(self.config.get("db.url", "sqlite:///~/mensajeria_cliente.db"))
        self.historial = historial or HistorialLocal(self.db)
        self.on_mensaje_recibido_ui = on_mensaje_recibido_ui
        self.on_cierre_ui = on_cierre_ui
        self.on_desconexion_ui = on_desconexion_ui
        self.on_presencia_ui = on_presencia_ui
        self.on_estado_ui = None
        try:
            tls_puerto = int(self.config.get("tls.puerto", 5001) or 5001)
        except (TypeError, ValueError):
            tls_puerto = 5001
        self.conexion = conexion or ConexionRed(
            self.config.get("host", "localhost"),
            self.config.get("puerto", 5000),
            self._handle_unsolicited_message,
            on_cierre=self._handle_cierre,
            on_desconexion=self._handle_desconexion,
            tls_habilitado=self.config.get("tls.habilitado", False),
            tls_puerto=tls_puerto,
            tls_confianza=self.config.get("tls.confianza", "") or "",
        )
        self.autenticacion = autenticacion or Autenticacion(self.conexion)
        self.codigo_actual = None
        self._coordinador_mensajeria = CoordinadorMensajeriaCliente(
            self.conexion,
            historial_actual=lambda: self.historial,
            codigo_actual=lambda: self.codigo_actual,
            exigir_sesion=self._exigir_sesion,
            reintentar_pendientes=lambda: self.reintentar_pendientes(),
        )
        self._lector_historial = LectorHistorialRemotoCliente(
            self.conexion,
            historial_actual=lambda: self.historial,
            codigo_actual=lambda: self.codigo_actual,
            exigir_sesion=self._exigir_sesion,
        )
        self._reintentador_pendientes = ReintentadorPendientesCliente(
            self.conexion,
            historial_actual=lambda: self.historial,
            codigo_actual=lambda: self.codigo_actual,
            reintentar_callback=lambda: self.reintentar_pendientes(),
        )
        self._sincronizador_directorio = SincronizadorDirectorioCliente(
            self.conexion,
            historial_actual=lambda: self.historial,
            codigo_actual=lambda: self.codigo_actual,
            exigir_sesion=self._exigir_sesion,
        )
        self._gestor_reconexion = GestorReconexionSesion(
            conexion=self.conexion,
            autenticacion=self.autenticacion,
            publicar_estado=lambda estado: self.on_estado_ui(estado)
            if self.on_estado_ui else None,
            codigo_actual=lambda: self.codigo_actual,
            fijar_codigo=lambda codigo: setattr(self, "codigo_actual", codigo),
            preparar_cuenta=self._preparar_cuenta,
            exigir_sesion=self._exigir_sesion,
            iniciar_flush=self.iniciar_flush_pendientes,
            detener_flush=self.detener_flush_pendientes,
        )
        self._publicador_eventos = PublicadorEventosCliente(
            on_mensaje=self.on_mensaje_recibido_ui,
            on_presencia=self.on_presencia_ui,
            on_cierre=self.on_cierre_ui,
            on_desconexion=self.on_desconexion_ui,
        )
        self._receptor_entrante = ReceptorEntranteCliente(
            historial_actual=lambda: self.historial,
            codigo_actual=lambda: self.codigo_actual,
            estado_actual=lambda: self.estado_conexion,
            cambiar_estado=self._cambiar_estado,
            sincronizador=self._sincronizador_directorio,
            publicador=self._publicador_eventos,
            gestor_reconexion=self._gestor_reconexion,
        )

    # ---------------------------------------------------------- ciclo de vida
    @property
    def estado_conexion(self):
        return self._gestor_reconexion.estado

    @property
    def _flush_hilo(self):
        """Hilo del flush, expuesto para conservar la API de pruebas existente."""
        return self._reintentador_pendientes._flush_hilo

    def _cambiar_estado(self, nuevo):
        return self._gestor_reconexion.cambiar_estado(nuevo)

    def iniciar_flush_pendientes(self, periodo_segundos=INTERVALO_FLUSH_SEGUNDOS):
        return self._reintentador_pendientes.iniciar_flush_pendientes(periodo_segundos)

    def detener_flush_pendientes(self):
        return self._reintentador_pendientes.detener_flush_pendientes()

    def flush_activo(self):
        return self._reintentador_pendientes.flush_activo()

    def _bucle_flush(self, periodo_segundos, parar):
        return self._reintentador_pendientes._bucle_flush(periodo_segundos, parar)

    def conectar(self):
        return self._gestor_reconexion.conectar()

    def desconectar(self):
        return self._gestor_reconexion.desconectar()

    def login(self, codigo, contrasena):
        return self._gestor_reconexion.login(codigo, contrasena)

    def fijar_cuenta(self, codigo):
        return self._gestor_reconexion.fijar_cuenta(codigo)

    def _preparar_cuenta(self, codigo):
        if hasattr(self.db, "para_cuenta"):
            self.db = self.db.para_cuenta(codigo)
            self.historial = HistorialLocal(self.db)
        self._asegurar_en_cache(codigo)
        self._procesar_pendientes()

    # ------------------------------------------------------------------ red
    def listar_conectados(self):
        return self._sincronizador_directorio.listar_conectados()

    def listar_usuarios(self):
        return self._sincronizador_directorio.listar_usuarios()

    def expulsar_otras_sesiones(self):
        return self._gestor_reconexion.expulsar_otras_sesiones()

    def enviar_texto(self, destinatario, texto, id_msg=None):
        return self._coordinador_mensajeria.enviar_texto(destinatario, texto, id_msg)

    def enviar_imagen(self, destinatario, ruta_archivo, id_msg=None):
        return self._coordinador_mensajeria.enviar_imagen(destinatario, ruta_archivo, id_msg)

    def enviar_archivo(self, destinatario, ruta_archivo, id_msg=None, on_progreso=None):
        return self._coordinador_mensajeria.enviar_archivo(
            destinatario, ruta_archivo, id_msg, on_progreso)

    def difundir(self, texto):
        return self._coordinador_mensajeria.difundir(texto)

    def descargar_archivo(self, archivo_id, ruta_destino, on_progreso=None):
        return self._lector_historial.descargar_archivo(archivo_id, ruta_destino, on_progreso)

    def marcar_descargado(self, id_mensaje, ruta_archivo):
        return self._lector_historial.marcar_descargado(id_mensaje, ruta_archivo)

    def obtener_imagen(self, id_mensaje, ruta_destino):
        return self._lector_historial.obtener_imagen(id_mensaje, ruta_destino)

    def marcar_leidos(self, otro):
        return self._coordinador_mensajeria.marcar_leidos(otro)

    # -------------------------------------------------------------- historial
    def obtener_conversacion(self, destinatario, limit=50, offset=0):
        return self._lector_historial.obtener_conversacion(destinatario, limit, offset)

    def contar_conversacion(self, destinatario):
        return self._lector_historial.contar_conversacion(destinatario)

    def buscar_en_historial(self, destinatario, patron, limite=200):
        return self._lector_historial.buscar_en_historial(destinatario, patron, limite)

    def traer_historial(self, destinatario, pagina):
        return self._lector_historial.traer_historial(destinatario, pagina)

    def pendientes(self):
        return self._reintentador_pendientes.pendientes()

    def reintentar_pendientes(self):
        return self._reintentador_pendientes.reintentar_pendientes()


    def directorio_local(self):
        return self._sincronizador_directorio.directorio_local()

    def recientes(self):
        return self._sincronizador_directorio.recientes()

    # ---------------------------------------------------------------- interno
    def _procesar_pendientes(self):
        try:
            self.reintentar_pendientes()
        except Exception:
            pass

    def _exigir_sesion(self):
        if not self.codigo_actual:
            raise RuntimeError("inicia sesion primero")

    def _asegurar_en_cache(self, codigo):
        return self._sincronizador_directorio._asegurar_en_cache(codigo)

    def _handle_unsolicited_message(self, msg):
        return self._receptor_entrante.al_recibir(msg)


    def _handle_cierre(self, msg):
        return self._receptor_entrante.al_cierre(msg)

    def _handle_desconexion(self):
        return self._receptor_entrante.al_desconexion()
