from pathlib import Path

from PySide6.QtCore import QSize, QThreadPool, QTimer
from PySide6.QtWidgets import QMainWindow

from transversal.estado_conexion import CONECTADO
from ui.busqueda_historial_chat import BusquedaHistorialChat
from ui.chat_view_model import ChatViewModel
from ui.componentes_chat import EventosChat as _UiEvents, TareaChat as _Task
from ui.constructor_vista_chat import ConstructorVistaChat
from ui.flujo_conversacion_chat import FlujoConversacionChat
from ui.gestor_adjuntos_chat import GestorAdjuntosChat
from ui.gestor_salida_sesion_chat import GestorSalidaSesionChat
from ui.paginador_historial_chat import PaginadorHistorialChat
from ui.preferencias_chat import PreferenciasChat
from ui.procesador_eventos_chat import ProcesadorEventosChat
from ui.procesador_tareas_chat import ProcesadorTareasChat
from ui.refrescador_contactos_chat import RefrescadorContactosChat
from ui.renderizado_texto import resaltar_html
from ui.tema import (
    MODO_OSCURO,
    aplicar_tema_fluent,
    estilo_cita,
    estilo_preview,
    guardar_modo,
    modo_guardado,
    modo_por_indice,
    normalizar,
    tokens,
)
from ui.visor_imagen_chat import VisorImagenChat as _VisorImagen


class ChatWindow(GestorSalidaSesionChat, FlujoConversacionChat, ProcesadorEventosChat, ProcesadorTareasChat, RefrescadorContactosChat, BusquedaHistorialChat, GestorAdjuntosChat, PaginadorHistorialChat, ConstructorVistaChat, QMainWindow):
    def __init__(self, fachada, codigo, on_logout):
        super().__init__()
        self.fachada = fachada
        self.codigo = codigo
        self.on_logout = on_logout
        self.usuario_seleccionado = None
        self.imagen_pendiente = None
        self._respuesta_mensaje = None
        self._cerrando = False
        self._offset = 0
        self._pagina_remota = {}
        self._total_remoto = {}
        self._historial_ids = set()
        self._gen_historial = 0
        self._gen_directorio = 0
        self._reconstruyendo = False
        self._directorio = []
        # Tarjetas de conversación: orden fecha-desc, repintado coalescido.
        self._recientes = None
        self._huella_pintada = None
        self._timer_recientes = QTimer(self)
        self._timer_recientes.setSingleShot(True)
        self._timer_recientes.setInterval(400)
        self._timer_recientes.timeout.connect(self._pintar_recientes_desde_cache)
        # UIX-06/UIX-07: búsqueda en el historial con debounce.
        self.modelo_vista = ChatViewModel()
        self._preferencias_chat = PreferenciasChat()
        self._restauracion_ultimo_chat_pendiente = True
        self._timer_buscar = QTimer(self)
        self._timer_buscar.setSingleShot(True)
        self._timer_buscar.setInterval(300)
        self._timer_buscar.timeout.connect(self._ejecutar_busqueda)
        # UIX-07: el filtro de contactos también va con debounce.
        self._timer_filtro = QTimer(self)
        self._timer_filtro.setSingleShot(True)
        self._timer_filtro.setInterval(300)
        self._timer_filtro.timeout.connect(self._filtrar_usuarios)
        # UIX-09: cargas de imagen en vuelo por id + ids cancelados. Antes no
        # había forma de abortarlas al salir de la vista.
        self._cargas_imagen = {}
        self._imagen_canceladas = set()
        # UIX-13/UIX-14: estado de carga del historial para el aviso.
        self._cargando_historial = False
        # Coalescencia de rafagas de presencia: un solo refresh por ventana.
        self._timer_presencia = QTimer(self)
        self._timer_presencia.setSingleShot(True)
        self._timer_presencia.setInterval(1500)
        self._timer_presencia.timeout.connect(lambda: self.actualizar_usuarios())
        self._eventos = _UiEvents()
        self._eventos.message.connect(self._procesar_mensaje)
        self._eventos.closure.connect(self._procesar_cierre)
        self._eventos.presence.connect(self._procesar_presencia)
        self._eventos.disconnected.connect(self._procesar_desconexion)
        self._eventos.state.connect(self._procesar_estado_conexion)
        self._eventos.task.connect(self._procesar_tarea)
        self._pool = QThreadPool(self)
        self._pool.setMaxThreadCount(4)
        self._directorio_imagenes = Path(__file__).resolve().parents[1] / "imagenes"

        self.setWindowTitle(f"Mensajería Académica | {codigo}")
        self.setMinimumSize(QSize(900, 600))
        self.resize(1120, 740)
        self.setAcceptDrops(True)
        self.modo = self._leer_modo()
        self._construir_ui()
        self._restaurar_divisor_lateral()
        self._divisor_lateral.splitterMoved.connect(self._guardar_divisor_lateral)
        self.aplicar_tema(self.modo)

        self.fachada.on_mensaje_recibido_ui = self._eventos.message.emit
        self.fachada.on_cierre_ui = self._eventos.closure.emit
        self.fachada.on_presencia_ui = self._eventos.presence.emit
        self.fachada.on_desconexion_ui = self._eventos.disconnected.emit
        # PAT-03: la vista pinta el estado que publica la fachada. Antes solo se
        # enteraba de la caida por un texto suelto y el banner offline se movia
        # dentro del resultado de `directorio`.
        if hasattr(self.fachada, "on_estado_ui"):
            self.fachada.on_estado_ui = self._eventos.state.emit
            self._procesar_estado_conexion(getattr(self.fachada, "estado_conexion", CONECTADO))
        self.lista_mensajes.verticalScrollBar().valueChanged.connect(self._cargar_imagenes_visibles)
        self.actualizar_usuarios()


    def _leer_modo(self):
        return modo_guardado(self.fachada)

    def _guardar_modo(self):
        guardar_modo(self.fachada, self.modo)

    def _restaurar_divisor_lateral(self):
        tamanos = self._preferencias_chat.divisor_lateral(self.codigo)
        if tamanos is not None:
            self._divisor_lateral.setSizes(list(tamanos))

    def _guardar_divisor_lateral(self, *_):
        self._preferencias_chat.guardar_divisor_lateral(
            self.codigo, self._divisor_lateral.sizes())

    def _cambiar_tema(self, indice):
        self.modo = modo_por_indice(indice)
        self.aplicar_tema(self.modo)
        self._guardar_modo()

    def aplicar_tema(self, modo):
        """Aplica el modo claro/oscuro a paneles y burbujas (UI-09)."""
        self.modo = normalizar(modo)
        aplicar_tema_fluent(self.modo)
        t = tokens(self.modo)
        self._panel_usuarios.setStyleSheet(f"background: {t['tarjeta']};")
        self.lista_usuarios.setStyleSheet(
            f"QListWidget {{ background: {t['tarjeta']}; border: none; }}")
        self._delegado_contactos.actualizar_tema(self.modo)
        self.lista_usuarios.viewport().update()
        self._panel_chat.setStyleSheet(f"background: {t['lista']};")
        self._header.setStyleSheet(
            f"background: {t['tarjeta']}; border-bottom: 1px solid {t['tarjeta_borde']};")
        self._composer.setStyleSheet(
            f"background: {t['tarjeta']}; border-top: 1px solid {t['tarjeta_borde']};")
        self.lbl_sesion.setStyleSheet(
            f"font-size: 16px; font-weight: 700; color: {t['texto']};")
        self.lbl_contactos.setStyleSheet(
            f"font-weight: 700; color: {t['texto']};")
        self.lbl_tema.setStyleSheet(
            f"color: {t['texto']}; background: transparent; border: none;")
        self.lbl_conversacion.setStyleSheet(
            f"font-size: 17px; font-weight: 700; color: {t['texto']};")
        self.lbl_presencia.setStyleSheet(
            f"color: {t['texto_suave']}; font-size: 11px;")
        self.lista_mensajes.setStyleSheet(
            "QListView { border: 0; padding: 9px; }")
        nombre_fondo = "fondo_chat_claro.jpg" if self.modo != MODO_OSCURO \
            else "fondo_chat_oscuro.jpg"
        self.lista_mensajes.fijar_fondo(
            Path(__file__).resolve().parent / "fondos" / nombre_fondo)
        # UIX-01: el delegado pinta con el modo; sin bucle por filas.
        self._delegado_mensajes.set_modo(self.modo)
        self.lista_mensajes.viewport().update()
        self.cita_container.setStyleSheet(estilo_cita(t))
        self.lbl_respuesta.setStyleSheet(
            f"color: {t['cita_texto']}; font-size: 11px; border: none; background: transparent;")
        self.preview_container.setStyleSheet(estilo_preview(t))
        self.lbl_preview_thumb.setStyleSheet(
            f"border-radius: 4px; border: 1px solid {t['preview_borde']};"
            f" background: {t['preview_thumb_fondo']};")
        self.lbl_adjunto.setStyleSheet(
            f"color: {t['texto']}; font-size: 12px; font-weight: 500;")
        if not self.lbl_offline.isVisible():
            self.lbl_estado_red.setStyleSheet(
                f"color: {t['texto_suave']}; font-size: 11px;")
        self.combo_tema.setCurrentIndex(0 if self.modo == MODO_OSCURO else 1)

    def _ejecutar(self, tipo, accion):
        tarea = _Task(tipo, accion, self._eventos)
        self._pool.start(tarea)
        return tarea

    def cancelar_cargas_imagen(self):
        """UIX-09: aborta las descargas en vuelo al salir de la vista.

        Las que aún están en cola se sacan del pool; las que ya corren se
        marcan y su resultado se descarta al llegar (no pueden interrumpirse
        a mitad de socket, pero ya no tocan la UI).
        """
        for mid, tarea in list(self._cargas_imagen.items()):
            try:
                self._pool.tryTake(tarea)
            except Exception:
                pass
            self._imagen_canceladas.add(mid)
        self._cargas_imagen.clear()


























    # ------------------------------------------------- UIX-06/UIX-07: buscar













    # SEG-02: paridad con Java/.NET y con la tuberia del servidor (ImageIO del
    # JDK solo decodifica PNG/JPEG/GIF/BMP; ver Imagenes.esImagenValida +
    # ImagenMagicaTest.rechazaWebpSinSoporteEnTuberia). .webp se OFRECE en el
    # dialogo pero viaja como MENSAJE_ARCHIVO, igual que en los otros clientes.






    def _aviso_red(self, texto):
        """Banner inline para errores de red/descarga (no abre ventanas)."""
        self.lbl_estado_red.setText(texto)
        self.lbl_estado_red.setStyleSheet("color: #B91C1C; font-size: 11px; font-weight: 700;")

















    @staticmethod
    def _estado_visible(estado):
        """Texto del tick de la burbuja.

        Los 5 estados y sus glifos son los de `contrato/estado-mensaje.json`
        (cierra VIS-02). `ERROR` estaba fuera del diccionario: `.get(estado, ...)`
        devolvia la cadena cruda, asi que un mensaje que el servidor rechazo
        se quedaba sin indicador en la burbuja mientras Java si lo mostraba.
        """
        return {
            "PENDIENTE": "⏳ Pendiente",
            "ENVIADO": "✓ Enviado",
            "ENTREGADO": "✓✓ Entregado",
            "LEIDO": "✓✓ Leído",
            "ERROR": "! No enviado",
        }.get(estado, "" if estado == "ERROR" else (estado or ""))
