import base64
import datetime
import uuid
from pathlib import Path

from PySide6.QtCore import QObject, QRunnable, QSize, Qt, QThreadPool, QTimer, Signal
from PySide6.QtGui import QImage, QPixmap, QShortcut
from PySide6.QtWidgets import (
            QComboBox, QDialog, QFileDialog, QFrame, QHBoxLayout, QLabel, QListView,
            QListWidgetItem, QMainWindow, QMessageBox, QSplitter, QVBoxLayout, QWidget,
        )
from qfluentwidgets import ComboBox, LineEdit, ListWidget, PrimaryPushButton, PushButton, TextEdit

from transversal.estado_conexion import (
    CONECTADO,
    muestra_banner,
    texto as texto_estado,
)
from transversal.fechas import agrupar, clave_dia, etiqueta_dia
from ui.lista_virtual import ROL_ITEM, MensajeDelegate, MensajeListModel
from ui.tema import (
    MODO_OSCURO,
    estilo_cita,
    estilo_preview,
    guardar_modo,
    modo_guardado,
    modo_por_indice,
    normalizar,
    tokens,
)


def resaltar_html(texto, patron):
    """UIX-06: envuelve las coincidencias en un span resaltado (HTML escapado).

    Misma idea que `textoResaltado` (Java): insensible a mayúsculas, sin regex
    (el patrón puede traer `.*+?` literales). Vacío = texto escapado sin marcas.
    """
    import html as _html
    texto = texto or ""
    patron = (patron or "").strip()
    if not patron:
        return _html.escape(texto)
    minus, mp = texto.casefold(), patron.casefold()
    partes, pos = [], 0
    while True:
        i = minus.find(mp, pos)
        if i < 0:
            break
        partes.append(_html.escape(texto[pos:i]))
        partes.append('<span style="background:#7B61B8;color:#FFFFFF;">'
                      + _html.escape(texto[i:i + len(mp)]) + "</span>")
        pos = i + len(mp)
    partes.append(_html.escape(texto[pos:]))
    return "".join(partes)


class _VisorImagen(QDialog):
    """UIX-08: visor con zoom y pan en vez de ventana fija.

    Rueda = zoom anclado al ratón, arrastrar = pan, `+`/`-`/`0`/`F` por
    teclado y `Esc` cierra (comportamiento por defecto del diálogo).
    """

    def __init__(self, pixmap, titulo, parent=None):
        from PySide6.QtWidgets import QGraphicsPixmapItem, QGraphicsScene, QGraphicsView
        super().__init__(parent)
        self.setWindowTitle(titulo or "Imagen")
        self.resize(940, 760)
        self.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, True)
        self._zoom = 1.0
        self._escena = QGraphicsScene(self)
        self._escena.addItem(QGraphicsPixmapItem(pixmap))
        self._vista = QGraphicsView(self._escena, self)
        self._vista.setTransformationAnchor(
            QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self._vista.setResizeAnchor(
            QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self._vista.setDragMode(QGraphicsView.DragMode.ScrollHandDrag)
        self._vista.setBackgroundBrush(Qt.GlobalColor.black)
        caja = QVBoxLayout(self)
        caja.setContentsMargins(0, 0, 0, 0)
        caja.addWidget(self._vista)
        fila = QHBoxLayout()
        fila.setContentsMargins(8, 4, 8, 8)
        menos = PushButton("−")
        menos.setFixedWidth(40)
        menos.setToolTip("Alejar")
        menos.clicked.connect(lambda: self._aplicar_zoom(1 / 1.25))
        cero = PushButton("0")
        cero.setFixedWidth(40)
        cero.setToolTip("Tamaño original (F)")
        cero.clicked.connect(lambda: self._aplicar_zoom(None))
        mas = PushButton("+")
        mas.setFixedWidth(40)
        mas.setToolTip("Acercar")
        mas.clicked.connect(lambda: self._aplicar_zoom(1.25))
        fila.addStretch(1)
        fila.addWidget(menos)
        fila.addWidget(cero)
        fila.addWidget(mas)
        caja.addLayout(fila)
        atajo_mas = QShortcut("Ctrl++", self)
        atajo_mas.activated.connect(lambda: self._aplicar_zoom(1.25))
        atajo_menos = QShortcut("Ctrl+-", self)
        atajo_menos.activated.connect(lambda: self._aplicar_zoom(1 / 1.25))
        atajo_cero = QShortcut("Ctrl+0", self)
        atajo_cero.activated.connect(lambda: self._aplicar_zoom(None))

    def _aplicar_zoom(self, factor):
        if factor is None:
            self._vista.resetTransform()
            self._zoom = 1.0
            return
        nuevo = min(8.0, max(0.2, self._zoom * factor))
        self._vista.scale(nuevo / self._zoom, nuevo / self._zoom)
        self._zoom = nuevo

    def wheelEvent(self, evento):
        self._aplicar_zoom(1.15 if evento.angleDelta().y() > 0 else 1 / 1.15)
        evento.accept()

    def keyPressEvent(self, evento):
        tecla = evento.key()
        if tecla in (Qt.Key.Key_Plus, Qt.Key.Key_Equal):
            self._aplicar_zoom(1.25)
        elif tecla == Qt.Key.Key_Minus:
            self._aplicar_zoom(1 / 1.25)
        elif tecla in (Qt.Key.Key_0, Qt.Key.Key_F):
            self._aplicar_zoom(None)
        else:
            super().keyPressEvent(evento)


class _ComposerEdit(TextEdit):
    """UIX-12: campo multilínea con Enter=enviar y Mayús+Enter=salto de línea.

    Antes era un LineEdit y el salto era imposible. `texto()` es el accesorio
    compatible con los call sites que usaban `text()`.
    """

    enviar_pedido = Signal()

    def keyPressEvent(self, evento):
        if evento.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter) \
                and not evento.modifiers() & Qt.KeyboardModifier.ShiftModifier:
            self.enviar_pedido.emit()
            evento.accept()
            return
        super().keyPressEvent(evento)

    def texto(self):
        return self.toPlainText()


class _UiEvents(QObject):
    message = Signal(object)
    closure = Signal(object)
    presence = Signal(object)
    disconnected = Signal()
    # PAT-03: estado de conexion (ver transversal/estado_conexion.py).
    state = Signal(str)
    task = Signal(str, object, object)


class _Task(QRunnable):
    def __init__(self, kind, action, events):
        super().__init__()
        self.kind = kind
        self.action = action
        self.events = events

    def run(self):
        try:
            self.events.task.emit(self.kind, self.action(), None)
        except Exception as error:
            self.events.task.emit(self.kind, None, error)


class ChatWindow(QMainWindow):
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
        # UIX-06/UIX-07: búsqueda en el historial con debounce.
        self._ids_busqueda = []
        self._indice_busqueda = -1
        self._patron_busqueda = ""
        self._salto_pendiente = None
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

    def _construir_ui(self):
        central = QWidget()
        root = QHBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        self.setCentralWidget(central)
        splitter = QSplitter(Qt.Orientation.Horizontal)
        root.addWidget(splitter)

        panel_usuarios = QWidget()
        panel_usuarios.setMinimumWidth(245)
        panel_usuarios.setMaximumWidth(350)
        panel_usuarios.setStyleSheet("background: #182533;")
        self._panel_usuarios = panel_usuarios
        left = QVBoxLayout(panel_usuarios)
        left.setContentsMargins(16, 16, 14, 12)
        left.setSpacing(9)
        self.lbl_sesion = QLabel(f"Mensajería\nSesión activa · {self.codigo}")
        self.lbl_sesion.setStyleSheet("font-size: 16px; font-weight: 700; color: #E8EEF2;")
        self.lbl_sesion.setWordWrap(True)
        self.entry_buscar = LineEdit()
        self.entry_buscar.setPlaceholderText("Buscar nombre o código")
        # UIX-07: sin debounce cada tecla reconstruía la lista de contactos.
        self.entry_buscar.textChanged.connect(self._filtrar_usuarios_debounced)
        self.combo_filtro = ComboBox()
        self.combo_filtro.addItems(["Todos", "Conectados", "Con no leídos"])
        self.combo_filtro.setCurrentIndex(0)
        self.combo_filtro.currentIndexChanged.connect(lambda _i: self._filtrar_usuarios())
        fila_contactos = QHBoxLayout()
        self.lbl_contactos = QLabel("Contactos")
        self.lbl_contactos.setStyleSheet("font-weight: 700; color: #E8EEF2;")
        self.btn_actualizar = PushButton("Actualizar")
        self.btn_actualizar.clicked.connect(self.actualizar_usuarios)
        fila_contactos.addWidget(self.lbl_contactos, 1)
        fila_contactos.addWidget(self.btn_actualizar)
        self.lista_usuarios = ListWidget()
        self.lista_usuarios.currentItemChanged.connect(self._al_seleccionar_usuario)
        self.lbl_estado_red = QLabel("Conectando...")
        self.lbl_estado_red.setStyleSheet("color: #A8B8C1; font-size: 11px;")
        self.lbl_estado_red.setWordWrap(True)
        self.lbl_offline = QLabel("⛔ Sin conexión — mensajes en cola local, se reintentan solos")
        self.lbl_offline.setStyleSheet(
            "color: #B45309; background: #FFFBEB; border: 1px solid #F2CE84;"
            " border-radius: 8px; padding: 8px; font-size: 11px; font-weight: 700;"
        )
        self.lbl_offline.setWordWrap(True)
        self.lbl_offline.setVisible(False)
        self._no_leidos = {}
        left.addWidget(self.lbl_sesion)
        left.addWidget(self.entry_buscar)
        left.addWidget(self.combo_filtro)
        left.addLayout(fila_contactos)
        left.addWidget(self.lista_usuarios, 1)
        # UIX-13: estados vacíos en vez de panel en blanco.
        self.lbl_vacio_usuarios = QLabel("Sin contactos todavía.")
        self.lbl_vacio_usuarios.setWordWrap(True)
        self.lbl_vacio_usuarios.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_vacio_usuarios.setStyleSheet("color: #A8B8C1; font-size: 12px; padding: 16px;")
        self.lbl_vacio_usuarios.setVisible(False)
        left.addWidget(self.lbl_vacio_usuarios)
        # Fila de acciones al pie del sidebar, como el cliente Java (UI-10).
        fila_acciones = QHBoxLayout()
        fila_acciones.setSpacing(8)
        self.btn_salir = PushButton("Salir")
        self.btn_salir.clicked.connect(self.close)
        self.btn_salir_todos = PushButton("Salir en todos")
        self.btn_salir_todos.clicked.connect(self.salir_en_todos)
        fila_acciones.addWidget(self.btn_salir)
        fila_acciones.addWidget(self.btn_salir_todos)
        left.addLayout(fila_acciones)
        fila_tema = QHBoxLayout()
        fila_tema.setSpacing(8)
        self.lbl_tema = QLabel("Tema")
        self.combo_tema = QComboBox()
        self.combo_tema.addItems(["Oscuro", "Claro"])
        self.combo_tema.currentIndexChanged.connect(self._cambiar_tema)
        fila_tema.addWidget(self.lbl_tema)
        fila_tema.addWidget(self.combo_tema, 1)
        left.addLayout(fila_tema)
        left.addWidget(self.lbl_offline)
        left.addWidget(self.lbl_estado_red)
        splitter.addWidget(panel_usuarios)

        panel_chat = QWidget()
        panel_chat.setStyleSheet("background: #0E1621;")
        self._panel_chat = panel_chat
        right = QVBoxLayout(panel_chat)
        right.setContentsMargins(0, 0, 0, 0)
        right.setSpacing(0)
        header = QWidget()
        header.setStyleSheet("background: #182533; border-bottom: 1px solid #2A3B4D;")
        self._header = header
        header_row = QHBoxLayout(header)
        header_row.setContentsMargins(18, 12, 18, 12)
        self.lbl_conversacion = QLabel("Selecciona una conversación")
        self.lbl_conversacion.setStyleSheet("font-size: 17px; font-weight: 700; color: #E8EEF2;")
        self.lbl_presencia = QLabel("")
        self.lbl_presencia.setStyleSheet("color: #A8B8C1; font-size: 11px;")
        title_box = QVBoxLayout()
        title_box.setSpacing(2)
        title_box.addWidget(self.lbl_conversacion)
        title_box.addWidget(self.lbl_presencia)
        header_row.addLayout(title_box, 1)

        self.lista_mensajes = QListView()
        self.lista_mensajes.setSpacing(3)
        self.lista_mensajes.setStyleSheet("QListView { background: #0E1621; border: 0; padding: 9px; }")
        self.lista_mensajes.setAcceptDrops(False)
        # UIX-01: modelo + delegado virtualizados en vez de un QWidget por
        # fila (antes: _MessageRow por mensaje, O(n) widgets vivos).
        self._modelo_mensajes = MensajeListModel(self)
        self._delegado_mensajes = MensajeDelegate(self, modo=self.modo)
        self._delegado_mensajes.responder = self._responder
        self._delegado_mensajes.reintentar = self._reintentar_imagen
        self._delegado_mensajes.descargar = self._descargar_adjunto
        self._delegado_mensajes.ampliar = self._ampliar_imagen
        self.lista_mensajes.setModel(self._modelo_mensajes)
        self.lista_mensajes.setItemDelegate(self._delegado_mensajes)
        self.lista_mensajes.doubleClicked.connect(self._responder_dobleclick)
        self.btn_anteriores = PushButton("↑ Cargar mensajes anteriores")
        self.btn_anteriores.clicked.connect(self._cargar_anteriores)
        self.btn_anteriores.setVisible(False)
        # En la cabecera a la derecha, como el cliente Java (UI-10).
        header_row.addWidget(self.btn_anteriores)
        # UIX-16: baja a lo nuevo sin perder el punto de lectura.
        self.btn_nuevos = PushButton("↓ Nuevos")
        self.btn_nuevos.setVisible(False)
        self.btn_nuevos.clicked.connect(self._ir_a_nuevos)
        header_row.addWidget(self.btn_nuevos)
        self._nuevos_pendientes = 0

        composer = QWidget()
        composer.setStyleSheet("background: #182533; border-top: 1px solid #2A3B4D;")
        self._composer = composer
        composer_layout = QVBoxLayout(composer)
        composer_layout.setContentsMargins(14, 8, 14, 12)
        self.cita_container = QFrame()
        self.cita_container.setObjectName("citaContainer")
        self.cita_container.setStyleSheet("""
            QFrame#citaContainer {
                background: #243447;
                border: 1px solid #A78BFA;
                border-left: 3px solid #A78BFA;
                border-radius: 6px;
            }
        """)
        cita_layout = QHBoxLayout(self.cita_container)
        cita_layout.setContentsMargins(8, 4, 8, 4)
        cita_layout.setSpacing(10)
        self.lbl_respuesta = QLabel("")
        self.lbl_respuesta.setStyleSheet("color: #C4B5FD; font-size: 11px; border: none; background: transparent;")
        self.lbl_respuesta.setWordWrap(True)
        self.btn_quitar_respuesta = PushButton("✕")
        self.btn_quitar_respuesta.setFixedSize(26, 26)
        self.btn_quitar_respuesta.clicked.connect(self._cancelar_respuesta)
        cita_layout.addWidget(self.lbl_respuesta, 1)
        cita_layout.addWidget(self.btn_quitar_respuesta)
        self.cita_container.setVisible(False)

        self.preview_container = QFrame()
        self.preview_container.setObjectName("previewContainer")
        self.preview_container.setStyleSheet("""
            QFrame#previewContainer {
                background: #243447;
                border: 1px solid #2A3B4D;
                border-radius: 6px;
            }
        """)
        preview_layout = QHBoxLayout(self.preview_container)
        preview_layout.setContentsMargins(8, 4, 8, 4)
        preview_layout.setSpacing(10)

        self.lbl_preview_thumb = QLabel()
        self.lbl_preview_thumb.setFixedSize(48, 48)
        self.lbl_preview_thumb.setStyleSheet("border-radius: 4px; border: 1px solid #2A3B4D; background: #0E1621;")
        self.lbl_preview_thumb.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.lbl_adjunto = QLabel("")
        self.lbl_adjunto.setStyleSheet("color: #E8EEF2; font-size: 12px; font-weight: 500;")
        self.lbl_adjunto.setWordWrap(True)

        self.btn_quitar_adjunto = PushButton("✕ Quitar")
        self.btn_quitar_adjunto.setFixedSize(75, 26)
        self.btn_quitar_adjunto.clicked.connect(self._quitar_adjunto)

        preview_layout.addWidget(self.lbl_preview_thumb)
        preview_layout.addWidget(self.lbl_adjunto, 1)
        preview_layout.addWidget(self.btn_quitar_adjunto)
        self.preview_container.setVisible(False)

        fila_envio = QHBoxLayout()
        fila_envio.setSpacing(8)
        self._fila_envio = fila_envio
        self.entry_msg = _ComposerEdit()
        self.entry_msg.setPlaceholderText("Escribe un mensaje (Enter para enviar, Mayús+Enter para salto)")
        self.entry_msg.setFixedHeight(56)
        self.entry_msg.enviar_pedido.connect(self.enviar)
        self.entry_msg.textChanged.connect(self._actualizar_envio)
        self.acceso_cancelar = QShortcut(Qt.Key.Key_Escape, self)
        self.acceso_cancelar.setContext(Qt.ShortcutContext.WidgetWithChildrenShortcut)
        self.acceso_cancelar.activated.connect(self._cancelar_composicion)
        # UIX-06: Ctrl+F abre la búsqueda en el historial.
        self.acceso_buscar = QShortcut(Qt.KeyboardModifier.ControlModifier | Qt.Key.Key_F, self)
        self.acceso_buscar.setContext(Qt.ShortcutContext.WidgetWithChildrenShortcut)
        self.acceso_buscar.activated.connect(self._mostrar_buscar)
        self.btn_imagen = PushButton("＋")
        self.btn_imagen.setFixedSize(40, 32)
        self.btn_imagen.setToolTip("Adjuntar archivo (Todos *.*)")
        self.btn_imagen.clicked.connect(self.adjuntar_imagen)
        self.btn_enviar = PrimaryPushButton("Enviar")
        self.btn_enviar.clicked.connect(self.enviar)
        # Composer como Java: ＋ a la izquierda, Enviar a la derecha (UI-10).
        fila_envio.addWidget(self.btn_imagen)
        fila_envio.addWidget(self.entry_msg, 1)
        fila_envio.addWidget(self.btn_enviar)
        composer_layout.addWidget(self.cita_container)
        composer_layout.addWidget(self.preview_container)
        composer_layout.addLayout(fila_envio)

        right.addWidget(header)
        # UIX-06: buscar en el historial (Ctrl+F). Oculta hasta invocarla.
        self.barra_buscar = QWidget()
        fila_buscar = QHBoxLayout(self.barra_buscar)
        fila_buscar.setContentsMargins(18, 6, 18, 6)
        fila_buscar.setSpacing(6)
        self.entry_buscar_historial = LineEdit()
        self.entry_buscar_historial.setPlaceholderText("Buscar en el historial")
        self.entry_buscar_historial.textChanged.connect(self._buscar_historial_debounced)
        self.entry_buscar_historial.returnPressed.connect(self._buscar_siguiente)
        acceso_ant = QShortcut(
            Qt.KeyboardModifier.ShiftModifier | Qt.Key.Key_Return,
            self.entry_buscar_historial)
        acceso_ant.setContext(Qt.ShortcutContext.WidgetShortcut)
        acceso_ant.activated.connect(self._buscar_anterior)
        self.btn_buscar_ant = PushButton("↑")
        self.btn_buscar_ant.setFixedWidth(36)
        self.btn_buscar_ant.setToolTip("Anterior coincidencia (Mayús+Enter)")
        self.btn_buscar_ant.clicked.connect(self._buscar_anterior)
        self.btn_buscar_sig = PushButton("↓")
        self.btn_buscar_sig.setFixedWidth(36)
        self.btn_buscar_sig.setToolTip("Siguiente coincidencia (Enter)")
        self.btn_buscar_sig.clicked.connect(self._buscar_siguiente)
        self.lbl_buscar_cuenta = QLabel("")
        self.btn_buscar_cerrar = PushButton("✕")
        self.btn_buscar_cerrar.setFixedWidth(36)
        self.btn_buscar_cerrar.setToolTip("Cerrar búsqueda (Esc)")
        self.btn_buscar_cerrar.clicked.connect(self._cerrar_buscar)
        fila_buscar.addWidget(self.entry_buscar_historial, 1)
        fila_buscar.addWidget(self.btn_buscar_ant)
        fila_buscar.addWidget(self.btn_buscar_sig)
        fila_buscar.addWidget(self.lbl_buscar_cuenta)
        fila_buscar.addWidget(self.btn_buscar_cerrar)
        self.barra_buscar.setVisible(False)
        right.addWidget(self.barra_buscar)
        right.addWidget(self.lista_mensajes, 1)
        # UIX-13: conversación vacía en vez de rectángulo negro sin explicación.
        # UIX-14 usa el mismo sitio para "Cargando historial…".
        self.lbl_vacio_chat = QLabel("Elige un contacto para empezar a conversar.")
        self.lbl_vacio_chat.setWordWrap(True)
        self.lbl_vacio_chat.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_vacio_chat.setStyleSheet("color: #A8B8C1; font-size: 12px; padding: 16px;")
        self.lbl_vacio_chat.setVisible(False)
        right.addWidget(self.lbl_vacio_chat, 1)
        right.addWidget(composer)
        splitter.addWidget(panel_chat)
        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)
        splitter.setSizes([300, 820])
        self._actualizar_envio()

    def _leer_modo(self):
        return modo_guardado(self.fachada)

    def _guardar_modo(self):
        guardar_modo(self.fachada, self.modo)

    def _cambiar_tema(self, indice):
        self.modo = modo_por_indice(indice)
        self.aplicar_tema(self.modo)
        self._guardar_modo()

    def aplicar_tema(self, modo):
        """Aplica el modo claro/oscuro a paneles y burbujas (UI-09)."""
        self.modo = normalizar(modo)
        t = tokens(self.modo)
        self._panel_usuarios.setStyleSheet(f"background: {t['tarjeta']};")
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
            f"QListView {{ background: {t['lista']}; border: 0; padding: 9px; }}")
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

    def actualizar_usuarios(self):
        seleccionado = self.usuario_seleccionado
        gen = self._gen_directorio = self._gen_directorio + 1
        def cargar():
            try:
                if hasattr(self.fachada, "listar_usuarios"):
                    usuarios = self.fachada.listar_usuarios()
                else:
                    codigos = self.fachada.listar_conectados()
                    local = {u["codigo"]: u for u in self.fachada.directorio_local()}
                    usuarios = [dict(local.get(c, {"codigo": c}), conectado=1) for c in codigos]
                try:
                    historial = getattr(self.fachada, "historial", None)
                    if historial is not None and hasattr(historial, "contar_no_leidos_por_contacto"):
                        no_leidos = historial.contar_no_leidos_por_contacto(self.codigo)
                    else:
                        no_leidos = {}
                except Exception:
                    no_leidos = {}
                return (gen, usuarios, no_leidos, None)
            except Exception as error:
                try:
                    return (gen, self.fachada.directorio_local(), {}, error)
                except Exception:
                    return (gen, [], {}, error)
        self._ejecutar("directorio", cargar)

    def _procesar_tarea(self, tipo, resultado, error):
        if self._cerrando:
            return
        if error is not None:
            if tipo == "imagen":
                mid = getattr(error, "mensaje_id", "")
                self._cargas_imagen.pop(mid, None)
                # UIX-09: error de una carga cancelada: no se ofrece reintentar
                # algo que el usuario ya dejó atrás.
                if mid in self._imagen_canceladas:
                    self._imagen_canceladas.discard(mid)
                    return
                row = self._row_por_id(mid)
                if row is not None:
                    row["image_loading"] = False
                    row["image_error"] = str(error)
                    self._modelo_mensajes.refrescar(mid)
            elif tipo == "envio":
                self.btn_enviar.setEnabled(True)
                self._aviso_red(f"No se pudo enviar: {error}")
            elif tipo == "descarga":
                mid = getattr(error, "mensaje_id", "")
                mensaje = self._mensaje_por_id(mid)
                if mensaje is not None:
                    mensaje["descargando"] = False
                    self._modelo_mensajes.refrescar(mid)
                self._aviso_red(f"No se pudo descargar el archivo: {error}")
            elif tipo == "historial":
                self.lbl_estado_red.setText("No se pudo cargar el historial local")
                self._cargando_historial = False
                self._actualizar_vacio_chat()
            elif tipo == "historial_remoto":
                self.lbl_presencia.setText("Mostrando historial local")
            return

        if tipo == "directorio":
            gen, usuarios, no_leidos, error_red = resultado
            if gen != self._gen_directorio:
                # Refresh obsoleto (el usuario cambio de chat o filtro
                # mientras la red respondia): no pisa la seleccion viva.
                return
            propio = next((u for u in usuarios if u.get("codigo") == self.codigo), None)
            if propio is not None:
                nombre_propio = " ".join(filter(None, [propio.get("nombres"),
                                                        propio.get("apellidos")])).strip()
                identidad_propia = (f"{nombre_propio} [{self.codigo}]"
                                    if nombre_propio else self.codigo)
                self.lbl_sesion.setText(f"Mensajería\nSesión activa · {identidad_propia}")
            self._directorio = [u for u in usuarios if u.get("codigo") != self.codigo]
            self._no_leidos = dict(no_leidos or {})
            # Restaura la seleccion VIVA (no la capturada al pedir): si el
            # usuario cambio de chat o escribio un filtro mientras tanto,
            # un refresh obsoleto jamas lo mueve de conversacion.
            self._filtrar_usuarios(seleccionado=self.usuario_seleccionado)
            online = sum(bool(u.get("conectado")) for u in self._directorio)
            sin_red = error_red is not None
            self.lbl_offline.setVisible(sin_red)
            if sin_red:
                self.lbl_estado_red.setText("Sin conexión: directorio local · cola activa")
                self.lbl_estado_red.setStyleSheet("color: #B45309; font-size: 11px; font-weight: 700;")
            else:
                self.lbl_estado_red.setText(f"{online} contactos en línea")
                self.lbl_estado_red.setStyleSheet("color: #61717F; font-size: 11px;")
        elif tipo == "historial":
            usuario, filas, offset, total, gen = resultado
            if usuario != self.usuario_seleccionado or gen != self._gen_historial:
                return
            if usuario == self.usuario_seleccionado:
                self._pintar_historial(filas, prepend=offset > 0)
                self._offset = offset
                hay_local = offset + 50 < total
                # UIX-06: si hay un salto pendiente (búsqueda), se reintenta
                # ahora que hay más mensajes cargados.
                if self._salto_pendiente is not None:
                    pendiente, intentos = self._salto_pendiente
                    self._salto_pendiente = None
                    self._saltar_a(pendiente, intentos)
                hay_remoto = self._pagina_remota.get(usuario, 0) + 1 < self._total_remoto.get(usuario, 0)
                self.btn_anteriores.setVisible(hay_local or hay_remoto)
                self._cargar_imagenes_visibles()
        elif tipo == "historial_remoto":
            usuario, pagina, total_paginas, older, gen = resultado
            if usuario != self.usuario_seleccionado or gen != self._gen_historial:
                return
            self._pagina_remota[usuario] = pagina
            self._total_remoto[usuario] = total_paginas
            if self.usuario_seleccionado == usuario:
                self._solicitar_historial(usuario, self._offset if older else 0)
        elif tipo == "pagina_anterior":
            usuario, destino, pagina = resultado
            if usuario != self.usuario_seleccionado:
                return
            if destino == "local":
                self._solicitar_historial(usuario, pagina)
            elif destino == "remoto":
                self._solicitar_historial_remoto(usuario, pagina, True)
        elif tipo == "envio":
            usuario, result, ids_enviados = resultado
            self.btn_enviar.setEnabled(True)
            if result is False:
                self.lbl_estado_red.setText("Sin red: guardado para reintentar")
            else:
                for mid in ids_enviados:
                    mensaje = self._mensaje_por_id(mid)
                    if mensaje is not None and mensaje.get("raw_state") == "PENDIENTE":
                        mensaje["state"] = self._estado_visible("ENVIADO")
                        mensaje["raw_state"] = "ENVIADO"
                        self._modelo_mensajes.refrescar(mid)
            self._solicitar_historial(usuario, 0)
        elif tipo == "salir_todos":
            self._cerrando = True
            self.fachada.on_mensaje_recibido_ui = None
            self.fachada.on_cierre_ui = None
            self.fachada.on_presencia_ui = None
            self.fachada.on_desconexion_ui = None
            self.fachada.desconectar()
            self.on_logout()
            self.close()
        elif tipo == "imagen":
            mensaje_id, imagen, ruta = resultado
            # UIX-09: resultado de una carga cancelada: se descarta sin tocar
            # la UI (ya es otro chat) y se limpia la marca.
            self._cargas_imagen.pop(mensaje_id, None)
            if mensaje_id in self._imagen_canceladas:
                self._imagen_canceladas.discard(mensaje_id)
                return
            mensaje = self._mensaje_por_id(mensaje_id)
            if mensaje is not None:
                self._delegado_mensajes.guardar_pixmap(
                    mensaje_id, QPixmap.fromImage(imagen))
                mensaje["image_state"] = Path(ruta).name
                mensaje["image_loaded"] = True
                mensaje["image_loading"] = False
                mensaje["image_error"] = ""
                self._modelo_mensajes.refrescar(mensaje_id)
        elif tipo == "descarga":
            mensaje_id, ruta_guardada = resultado
            mensaje = self._mensaje_por_id(mensaje_id)
            if mensaje is not None:
                mensaje["descargando"] = False
                mensaje["descargado_ruta"] = ruta_guardada
                self._modelo_mensajes.refrescar(mensaje_id)
            QMessageBox.information(
                self, "Descarga completa",
                f"Archivo guardado exitosamente en:\n{ruta_guardada}")

    def _filtrar_usuarios(self, text=None, seleccionado=None):
        if seleccionado is None:
            seleccionado = self.usuario_seleccionado
        filtro = (self.entry_buscar.text() or "").strip().casefold()
        solo_conectados = False
        solo_no_leidos = False
        try:
            texto_filtro = self.combo_filtro.currentText()
            solo_conectados = texto_filtro == "Conectados"
            solo_no_leidos = texto_filtro == "Con no leídos"
        except Exception:
            pass
        usuarios = sorted(self._directorio,
                          key=lambda u: (not bool(u.get("conectado")),
                                         (u.get("apellidos") or "").casefold(), u.get("codigo", "")))
        # Sin repintados parciales: con 1000+ contactos el rebuild por
        # tecla/evento congela la UI (medido: ~236 ms). Un solo layout al final.
        # Ademas se ignoran las selecciones TRANSITORIAS del rebuild: clear()
        # borra fila por fila y Qt mueve el cursor a la siguiente, lo que el
        # handler tomaba como seleccion real (robaba la conversacion al
        # escribir en el buscador o con cada refresh de presencia).
        self._reconstruyendo = True
        self.lista_usuarios.setUpdatesEnabled(False)
        try:
            self.lista_usuarios.clear()
            seleccionado_item = None
            for usuario in usuarios:
                codigo = usuario.get("codigo", "")
                if solo_conectados and not bool(usuario.get("conectado")):
                    continue
                nombre = " ".join(filter(None, [usuario.get("nombres"), usuario.get("apellidos")])).strip()
                identidad = f"{nombre} [{codigo}]" if nombre else codigo
                busqueda = f"{identidad} {codigo}".casefold()
                if filtro and filtro not in busqueda:
                    continue
                online = bool(usuario.get("conectado"))
                pendientes = 0
                try:
                    pendientes = int((self._no_leidos or {}).get(codigo, 0))
                except Exception:
                    pendientes = 0
                if solo_no_leidos and pendientes <= 0:
                    continue
                insignia = f"  ({pendientes})" if pendientes > 0 else ""
                punto = "●" if online else "○"
                base = f"{punto}  {identidad}\n     {'en línea' if online else 'desconectado'}"
                item = QListWidgetItem(base + insignia)
                item.setData(Qt.ItemDataRole.UserRole, codigo)
                item.setData(Qt.ItemDataRole.UserRole + 1, identidad)
                # UIX-02: la insignia es dato, no texto mutado. `base` guarda el
                # texto sin insignia para limpiarla sin regex.
                item.setData(Qt.ItemDataRole.UserRole + 2, pendientes)
                item.setData(Qt.ItemDataRole.UserRole + 3, base)
                item.setForeground(Qt.GlobalColor.darkGreen if online else Qt.GlobalColor.gray)
                self.lista_usuarios.addItem(item)
                if codigo == self.usuario_seleccionado:
                    seleccionado_item = item
            # Solo restaura la seleccion VIVA si sigue visible: jamas mueve
            # al usuario a otra conversacion distinta de la viva.
            if seleccionado_item is not None:
                self.lista_usuarios.setCurrentItem(seleccionado_item)
        finally:
            self._reconstruyendo = False
            self.lista_usuarios.setUpdatesEnabled(True)
        # UIX-13: si el filtro no deja nada, decir por qué en vez de blanco.
        if self.lista_usuarios.count() == 0:
            if not self._directorio:
                self.lbl_vacio_usuarios.setText("Sin contactos todavía.")
            elif (self.entry_buscar.text() or "").strip() \
                    or self.combo_filtro.currentText() != "Todos":
                self.lbl_vacio_usuarios.setText("Sin resultados para esta búsqueda.")
            else:
                self.lbl_vacio_usuarios.setText("Sin contactos todavía.")
            self.lbl_vacio_usuarios.setVisible(True)
        else:
            self.lbl_vacio_usuarios.setVisible(False)

    def _hay_mensajes(self):
        return bool(self._modelo_mensajes.mensajes())

    def _actualizar_vacio_chat(self):
        """UIX-13/UIX-14: qué se ve cuando no hay mensajes."""
        if self._hay_mensajes():
            self.lbl_vacio_chat.setVisible(False)
            self.lista_mensajes.setVisible(True)
            return
        self.lista_mensajes.setVisible(False)
        if getattr(self, "_cargando_historial", False):
            self.lbl_vacio_chat.setText("Cargando historial…")
        elif self.usuario_seleccionado:
            self.lbl_vacio_chat.setText("Sin mensajes aún. Escribe el primero.")
        else:
            self.lbl_vacio_chat.setText("Elige un contacto para empezar a conversar.")
        self.lbl_vacio_chat.setVisible(True)

    def _item_por_codigo(self, codigo):
        """Fila visible de un contacto (None si el filtro la oculta)."""
        for indice in range(self.lista_usuarios.count()):
            item = self.lista_usuarios.item(indice)
            try:
                if item.data(Qt.ItemDataRole.UserRole) == codigo:
                    return item
            except RuntimeError:
                continue
        return None

    def _limpiar_insignia(self, codigo):
        """Pone a cero los no-leídos sin reconstruir la lista (UIX-02).

        Antes el conteo vivía dentro del texto ("  (N)") y se quitaba con una
        regex; ahora es dato (UserRole+2) y el texto sin insignia está guardado
        (UserRole+3), así que no hay nada que parsear.
        """
        item = self._item_por_codigo(codigo)
        if item is None:
            return
        try:
            item.setData(Qt.ItemDataRole.UserRole + 2, 0)
            base = item.data(Qt.ItemDataRole.UserRole + 3)
            if base:
                item.setText(base)
        except RuntimeError:
            return

    def _al_seleccionar_usuario(self, actual, anterior=None):
        if actual is None or getattr(self, "_seleccionando", False) \
                or getattr(self, "_reconstruyendo", False):
            return
        try:
            codigo = actual.data(Qt.ItemDataRole.UserRole)
            identidad = actual.data(Qt.ItemDataRole.UserRole + 1)
        except RuntimeError:
            # El item fue liberado por una reconstruccion de la lista
            # (_filtrar_usuarios hace clear()): no hay nada que mostrar.
            return
        if not codigo:
            return
        self.usuario_seleccionado = codigo
        # UIX-09: al cambiar de chat, las descargas del anterior ya no pintan
        # aquí. (Si es el mismo, no se toca nada.)
        if codigo != getattr(self, "_chat_cargas", codigo):
            self.cancelar_cargas_imagen()
        self._chat_cargas = codigo
        self._nuevos_pendientes = 0
        self.btn_nuevos.setVisible(False)
        self._cancelar_respuesta()
        # UIX-06: los resultados son por conversación; al cambiar, fuera.
        if self.barra_buscar.isVisible():
            self._cerrar_buscar()
        # Limpia la insignia de no-leidos EN EL SITIO: antes se reconstruia
        # la lista entera aqui (~236 ms con 1000+ contactos) y el setCurrentItem
        # anidado podia robar la seleccion en plena rafaga de presencia.
        try:
            if isinstance(getattr(self, "_no_leidos", None), dict) and codigo in self._no_leidos:
                self._no_leidos.pop(codigo, None)
                self._limpiar_insignia(codigo)
        except Exception:
            pass
        self.lbl_conversacion.setText(identidad or codigo)
        self.lbl_presencia.setText("en línea" if any(
            u.get("codigo") == codigo and u.get("conectado") for u in self._directorio)
            else "desconectado")
        self._offset = 0
        self._gen_historial += 1
        self._pagina_remota[codigo] = 0
        self._total_remoto.pop(codigo, None)
        self._historial_ids.clear()
        self._modelo_mensajes.limpiar()
        self._solicitar_historial(codigo, 0)
        if hasattr(self.fachada, "traer_historial"):
            self._solicitar_historial_remoto(codigo, 0, False)
        if hasattr(self.fachada, "marcar_leidos"):
            self._ejecutar("marcar_leidos", lambda: self.fachada.marcar_leidos(codigo))

    def _solicitar_historial(self, usuario, offset):
        # UIX-14: mientras carga se muestra "Cargando historial…".
        self._cargando_historial = True
        self._actualizar_vacio_chat()
        gen = self._gen_historial
        def cargar():
            try:
                metodo = self.fachada.obtener_conversacion
                try:
                    filas = metodo(usuario, limit=50, offset=offset)
                except TypeError:
                    filas = metodo(usuario)
                total = self.fachada.contar_conversacion(usuario) if hasattr(
                    self.fachada, "contar_conversacion") else len(filas)
                return usuario, filas, offset, total, gen
            except Exception as error:
                raise RuntimeError(str(error)) from error
        self._ejecutar("historial", cargar)

    def _cargar_anteriores(self):
        usuario = self.usuario_seleccionado
        if usuario is None:
            return
        def consultar():
            total_local = self.fachada.contar_conversacion(usuario)
            if self._offset + 50 < total_local:
                return usuario, "local", self._offset + 50
            siguiente = self._pagina_remota.get(usuario, 0) + 1
            if siguiente < self._total_remoto.get(usuario, 0):
                return usuario, "remoto", siguiente
            return usuario, "ninguno", self._offset
        self._ejecutar("pagina_anterior", consultar)

    def _solicitar_historial_remoto(self, usuario, pagina, older):
        gen = self._gen_historial
        self._ejecutar("historial_remoto", lambda: (
            usuario, pagina, self.fachada.traer_historial(usuario, pagina), older, gen))

    def _seguir_abajo(self):
        """UIX-16: True si ya se está al fondo (margen 60 px)."""
        try:
            barra = self.lista_mensajes.verticalScrollBar()
            return barra.value() >= barra.maximum() - 60
        except Exception:
            return True

    def _pintar_historial(self, filas, prepend=False):
        ordenados = list(reversed(filas))
        # UIX-16: ancla en el primer mensaje visible para no perder el punto
        # de lectura al anteponer páginas (antes: +50 filas fijas).
        ancla = None
        if prepend:
            visibles = self._modelo_mensajes.mensajes()
            if visibles:
                ancla = visibles[0].get("id")
        # Un solo layout al final: 50 filas x QWidget son ~ms, no cientos.
        self.lista_mensajes.setUpdatesEnabled(False)
        try:
            nuevas = []
            for fila in (reversed(ordenados) if prepend else ordenados):
                id_mensaje = fila.get("id_mensaje") or fila.get("id") or ""
                if id_mensaje and id_mensaje in self._historial_ids:
                    indice = self._modelo_mensajes.indice_por_id(id_mensaje)
                    if indice.isValid():
                        existente = self._modelo_mensajes.data(indice, ROL_ITEM)
                        if not isinstance(existente, dict):
                            continue
                        raw_state = fila.get("estado", "")
                        if existente.get("is_own"):
                            existente["state"] = self._estado_visible(raw_state)
                            existente["raw_state"] = raw_state
                            self._modelo_mensajes.refrescar(id_mensaje)
                        if fila.get("archivo_id"):
                            existente["archivo_id"] = fila["archivo_id"]
                            self._modelo_mensajes.refrescar(id_mensaje)
                    continue
                if id_mensaje:
                    self._historial_ids.add(id_mensaje)
                nuevas.append(fila)
            # UIX-04/UIX-05: separadores de día y rachas sin remitente repetido.
            # `nuevas` va en orden de inserción; `_insertar_nuevas` la pasa a
            # cronológico para decidir.
            if prepend:
                nuevas = list(reversed(nuevas))
            self._insertar_nuevas(nuevas, prepend)
        finally:
            self.lista_mensajes.setUpdatesEnabled(True)

        if not prepend:
            if self._seguir_abajo():
                self.lista_mensajes.scrollToBottom()
                self._nuevos_pendientes = 0
                self.btn_nuevos.setVisible(False)
        else:
            # Vuelve al ancla: las filas nuevas van todas encima.
            indice = self._modelo_mensajes.indice_por_id(ancla) if ancla else None
            if indice is not None and indice.isValid():
                self.lista_mensajes.scrollTo(indice)
            else:
                self.lista_mensajes.scrollToTop()
        # UIX-13/UIX-14: terminó la pintura (también en llamadas directas).
        self._cargando_historial = False
        self._actualizar_vacio_chat()
        self._cargar_imagenes_visibles()

    def _construir_item(self, fila):
        """Convierte una fila cruda de historial en el dict que pinta la fila.

        Extraído de `_pintar_historial` sin cambiar nada: el remitente, el texto
        de la burbuja y el estado se calculan igual que antes. El flag
        `mostrar_remitente` (UIX-05) y los separadores (UIX-04) los pone
        `_insertar_nuevas`, que sí ve a los vecinos.
        """
        origen = fila.get("origen") or ""
        usuario = next((u for u in self._directorio if u.get("codigo") == origen), {})
        nombre = " ".join(filter(None, [usuario.get("nombres"), usuario.get("apellidos")])).strip()
        is_own = (origen == self.codigo)
        remitente = "Tú" if is_own else (f"{nombre} [{origen}]" if nombre else origen)
        tipo = fila.get("tipo")
        es_imagen = tipo == "MENSAJE_IMAGEN"
        es_archivo = tipo == "MENSAJE_ARCHIVO"
        raw_state = fila.get("estado", "")
        nombre_arch = fila.get("nombre_archivo") or ""
        tamano_bytes = fila.get("tamano_archivo") or 0
        if tamano_bytes >= 1024 * 1024:
            tamano_str = f"{tamano_bytes/1024/1024:.1f} MB"
        elif tamano_bytes > 0:
            tamano_str = f"{tamano_bytes/1024:.1f} KB"
        else:
            tamano_str = ""
        if es_archivo:
            texto_burbuja = f"📎 {nombre_arch}  ({tamano_str})" if nombre_arch else "[Archivo]"
        elif es_imagen:
            texto_burbuja = ""
        else:
            texto_burbuja = fila.get("contenido") or ""
        id_mensaje = fila.get("id_mensaje") or fila.get("id") or ""
        return {
            "id": id_mensaje, "sender": remitente, "date": fila.get("fecha_envio", ""),
            "origen": origen,
            "text": texto_burbuja,
            "state": self._estado_visible(raw_state) if is_own else "",
            "raw_state": raw_state if is_own else "",
            "is_own": is_own,
            "is_image": es_imagen, "es_archivo": es_archivo,
            "archivo_id": fila.get("archivo_id"),
            "filename": nombre_arch, "path": fila.get("ruta_archivo") or "",
            "image_loaded": False, "image_loading": False,
            "image_state": "Imagen" if es_imagen else "",
            "mostrar_remitente": True,
            "_cruda": fila,
        }

    def _mensaje_en_indice(self, indice):
        """Item dict del mensaje en esa fila, o None (separador/fuera de rango)."""
        modelo = self._modelo_mensajes
        if indice < 0 or indice >= modelo.rowCount():
            return None
        candidato = modelo.data(modelo.index(indice), ROL_ITEM)
        if isinstance(candidato, dict) and candidato.get("id"):
            return candidato
        return None

    def _agregar_separador(self, etiqueta, indice=None):
        """Fila de cabecera de día (UIX-04): centrada, sin id, no seleccionable."""
        fila = {"es_separador": True, "etiqueta": etiqueta}
        if indice is None:
            self._modelo_mensajes.agregar(fila)
        else:
            self._modelo_mensajes.insertar_en(indice, fila)

    @staticmethod
    def _cruda_de(item):
        """Lo que `agrupar` necesita: origen + fecha_envio de un item dict."""
        if not isinstance(item, dict):
            return None
        return {"origen": item.get("origen", ""), "fecha_envio": item.get("date", "")}

    def _insertar_nuevas(self, nuevas, prepend):
        """Inserta filas nuevas y reconcilia cabeceras y rachas (UIX-04/UIX-05)."""
        if not nuevas:
            return
        items = [self._construir_item(fila) for fila in nuevas]
        if prepend:
            for item in reversed(items):
                self._agregar_fila(item, prepend=True, revisar_imagenes=False)
        else:
            for item in items:
                self._agregar_fila(item, prepend=False, revisar_imagenes=False)
        self._reconciliar_filas()

    def _reconciliar_filas(self):
        """Deja exactamente un separador donde toca y recalcula las rachas.

        Invariante: separador antes del primer mensaje y entre mensajes de
        distinto día; sin remitente si va pegado al anterior del mismo autor.
        Solo crea/borra filas separadoras; los mensajes conservan su dict.
        Al cargar páginas viejas los separadores se mueven solos al sitio
        correcto, sin lógica de unión en cada inserción.
        """
        modelo = self._modelo_mensajes
        modelo.quitar_separadores()
        mensajes = modelo.mensajes()
        # Posiciones (índices actuales) donde va separador: antes del primero
        # con día conocido y entre días distintos.
        pos_actual = {}
        for i in range(modelo.rowCount()):
            f = modelo.data(modelo.index(i), ROL_ITEM)
            if isinstance(f, dict) and f.get("id"):
                pos_actual[f["id"]] = i
        cortes = set()
        for pos, d in enumerate(mensajes):
            dia = clave_dia(d.get("date", ""))
            if not dia:
                continue
            if pos == 0:
                cortes.add(pos_actual[d["id"]])
                continue
            dia_prev = clave_dia(mensajes[pos - 1].get("date", ""))
            if dia_prev and dia_prev != dia:
                cortes.add(pos_actual[d["id"]])
        for i in sorted(cortes, reverse=True):
            d = modelo.data(modelo.index(i), ROL_ITEM)
            if isinstance(d, dict):
                self._agregar_separador(etiqueta_dia(d.get("date", "")), i)
        # Rachas sobre la secuencia de mensajes (los separadores no cuentan).
        # El delegado lee `mostrar_remitente` al pintar: basta refrescar.
        previo = None
        for d in mensajes:
            d["mostrar_remitente"] = not agrupar(
                self._cruda_de(previo), self._cruda_de(d))
            modelo.refrescar(d.get("id"))
            previo = d

    def _agregar_fila(self, mensaje, prepend=False, revisar_imagenes=True):
        # UIX-01: sin QWidget por fila; el dict vive en el modelo y lo pinta
        # el delegado bajo demanda.
        mensaje["is_own"] = bool(
            mensaje.get("is_own") or mensaje.get("sender") == "Tú")
        if prepend:
            self._modelo_mensajes.insertar_en(0, mensaje)
        else:
            self._modelo_mensajes.agregar(mensaje)
        # El pintado en lote (_pintar_historial) escanea una sola vez al
        # final: este scan por fila era O(n^2) por carga de historial.
        if revisar_imagenes:
            self._cargar_imagenes_visibles()

    def _cita_actual(self):
        mensaje = getattr(self, "_respuesta_mensaje", None)
        if not mensaje:
            return ""
        if mensaje.get("is_image"):
            referencia = f"[Imagen: {mensaje.get('filename') or 'imagen'}]"
        else:
            referencia = '"%s"' % ((mensaje.get("text") or "mensaje")[:120])
        return f"Respuesta a {mensaje.get('sender')}: {referencia}"

    def _responder(self, mensaje):
        self._respuesta_mensaje = dict(mensaje)
        self.lbl_respuesta.setText(self._cita_actual())
        self.cita_container.setVisible(True)
        self.entry_msg.setFocus()

    def _responder_dobleclick(self, indice):
        """Telegram Web: doble-click en cualquier mensaje = responder."""
        try:
            mensaje = self._modelo_mensajes.data(indice, ROL_ITEM)
        except Exception:
            mensaje = None
        if isinstance(mensaje, dict) and mensaje.get("id"):
            self._responder(mensaje)

    def _cancelar_respuesta(self):
        self._respuesta_mensaje = None
        self.lbl_respuesta.clear()
        self.cita_container.setVisible(False)

    def _cancelar_composicion(self):
        """Esc: cierra la búsqueda si está abierta; si no, cita y adjunto."""
        if self.barra_buscar.isVisible():
            self._cerrar_buscar()
            return
        self._cancelar_respuesta()
        self._quitar_adjunto()

    # ------------------------------------------------- UIX-06/UIX-07: buscar

    def _filtrar_usuarios_debounced(self, *_):
        """UIX-07: el rebuild por tecla se espera 300 ms (antes era directo)."""
        self._timer_filtro.stop()
        self._timer_filtro.start()

    def _mostrar_buscar(self):
        if not self.usuario_seleccionado:
            self.lbl_estado_red.setText("Elige una conversación para buscar en su historial")
            return
        self.barra_buscar.setVisible(True)
        self.entry_buscar_historial.setFocus()
        self.entry_buscar_historial.selectAll()

    def _cerrar_buscar(self):
        self.barra_buscar.setVisible(False)
        self.entry_buscar_historial.clear()
        self._ids_busqueda = []
        self._indice_busqueda = -1
        self._patron_busqueda = ""
        self._salto_pendiente = None
        self.lbl_buscar_cuenta.setText("")
        self._repintar_resaltado("")

    def _buscar_historial_debounced(self, *_):
        """UIX-07: la query a SQLite espera 300 ms tras la última tecla."""
        self._timer_buscar.stop()
        self._timer_buscar.start()

    def _ejecutar_busqueda(self):
        patron = (self.entry_buscar_historial.text() or "").strip()
        if not self.usuario_seleccionado or not patron:
            self._ids_busqueda = []
            self._indice_busqueda = -1
            self._patron_busqueda = ""
            self._actualizar_cuenta_busqueda()
            self._repintar_resaltado("")
            return
        try:
            ids = self.fachada.buscar_en_historial(self.usuario_seleccionado, patron)
        except Exception:
            self.lbl_buscar_cuenta.setText("Sin resultados")
            return
        self._patron_busqueda = patron
        self._ids_busqueda = list(ids)
        self._indice_busqueda = 0 if self._ids_busqueda else -1
        self._actualizar_cuenta_busqueda()
        self._repintar_resaltado(patron)
        if self._ids_busqueda:
            self._saltar_a(self._ids_busqueda[0], intentos=5)

    def _actualizar_cuenta_busqueda(self):
        if not self._ids_busqueda:
            self.lbl_buscar_cuenta.setText(
                "" if not self._patron_busqueda else "Sin resultados")
        else:
            self.lbl_buscar_cuenta.setText(
                f"{self._indice_busqueda + 1}/{len(self._ids_busqueda)}")

    def _buscar_siguiente(self):
        if not self._ids_busqueda:
            return
        self._indice_busqueda = (self._indice_busqueda + 1) % len(self._ids_busqueda)
        self._actualizar_cuenta_busqueda()
        self._saltar_a(self._ids_busqueda[self._indice_busqueda], intentos=5)

    def _buscar_anterior(self):
        if not self._ids_busqueda:
            return
        self._indice_busqueda = (self._indice_busqueda - 1) % len(self._ids_busqueda)
        self._actualizar_cuenta_busqueda()
        self._saltar_a(self._ids_busqueda[self._indice_busqueda], intentos=5)

    def _repintar_resaltado(self, patron):
        # UIX-01: el patrón vive en el delegado; repinta lo visible.
        self._delegado_mensajes.set_patron(patron or "")

    def _saltar_a(self, id_mensaje, intentos=5):
        """Baja al mensaje: si no está cargado, trae una página más y reintenta."""
        if not self.usuario_seleccionado or not id_mensaje:
            return
        indice = self._modelo_mensajes.indice_por_id(id_mensaje)
        if indice.isValid():
            self.lista_mensajes.scrollTo(indice)
            self.lista_mensajes.setCurrentIndex(indice)
            return
        if intentos <= 0 or not self.btn_anteriores.isVisible():
            self.lbl_buscar_cuenta.setText("Fuera del historial cargado")
            return
        self._salto_pendiente = (id_mensaje, intentos - 1)
        self._cargar_anteriores()

    def _actualizar_envio(self):
        self.btn_enviar.setEnabled(bool(self.usuario_seleccionado)
                                   and bool(self.entry_msg.texto().strip() or self.imagen_pendiente))

    def enviar(self):
        texto = self.entry_msg.texto().strip()
        usuario = self.usuario_seleccionado
        ruta = self.imagen_pendiente
        if not usuario or (not texto and not ruta):
            return
        cita = self._cita_actual()
        texto_envio = (cita + "\n\n" + texto) if cita and texto else (cita or texto)
        self.btn_enviar.setEnabled(False)

        ahora = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        id_texto = str(uuid.uuid4()) if texto_envio else None
        id_imagen = str(uuid.uuid4()) if ruta else None

        # Reflejo optimista inmediato en la UI con estado PENDIENTE
        if id_texto:
            item_texto = {
                "id": id_texto, "sender": "Tú", "date": ahora,
                "text": texto_envio, "state": self._estado_visible("PENDIENTE"),
                "raw_state": "PENDIENTE", "is_own": True,
                "is_image": False, "archivo_id": None,
                "filename": "", "path": "", "image_loaded": False,
                "image_loading": False, "image_state": ""
            }
            self._historial_ids.add(id_texto)
            self._agregar_fila(item_texto)

        if id_imagen:
            nombre_adj = Path(ruta).name
            ext_adj = Path(ruta).suffix.casefold()
            es_imagen_adj = ext_adj in self._EXTENSIONES_IMAGEN
            tamano_kb = Path(ruta).stat().st_size / 1024
            tamano_str = (f"{tamano_kb/1024:.1f} MB" if tamano_kb >= 1024
                          else f"{tamano_kb:.1f} KB")
            item_imagen = {
                "id": id_imagen, "sender": "Tú", "date": ahora,
                "text": "" if es_imagen_adj else f"📎 {nombre_adj}  ({tamano_str})",
                "state": self._estado_visible("PENDIENTE"),
                "raw_state": "PENDIENTE", "is_own": True,
                "is_image": es_imagen_adj, "archivo_id": None,
                "filename": nombre_adj, "path": ruta,
                "image_loaded": False, "image_loading": False,
                "image_state": nombre_adj if es_imagen_adj else ""
            }
            self._historial_ids.add(id_imagen)
            self._agregar_fila(item_imagen)

        self.lista_mensajes.scrollToBottom()

        # Limpiar entrada, cita y adjunto inmediatamente
        self.entry_msg.clear()
        self._cancelar_respuesta()
        self._quitar_adjunto()

        def enviar_ambos():
            en_red = True
            ids_enviados = []
            if id_texto:
                try:
                    ok = self.fachada.enviar_texto(usuario, texto_envio, id_msg=id_texto)
                except TypeError:
                    ok = self.fachada.enviar_texto(usuario, texto_envio)
                en_red = (ok is not False) and en_red
                ids_enviados.append(id_texto)
            if id_imagen:
                ext = Path(ruta).suffix.casefold()
                if ext in self._EXTENSIONES_IMAGEN:
                    try:
                        ok = self.fachada.enviar_imagen(usuario, ruta, id_msg=id_imagen)
                    except TypeError:
                        ok = self.fachada.enviar_imagen(usuario, ruta)
                else:
                    try:
                        ok = self.fachada.enviar_archivo(usuario, ruta, id_msg=id_imagen)
                    except TypeError:
                        ok = self.fachada.enviar_archivo(usuario, ruta)
                en_red = (ok is not False) and en_red
                ids_enviados.append(id_imagen)
            return usuario, en_red, ids_enviados

        self._ejecutar("envio", enviar_ambos)

    # SEG-02: paridad con Java/.NET y con la tuberia del servidor (ImageIO del
    # JDK solo decodifica PNG/JPEG/GIF/BMP; ver Imagenes.esImagenValida +
    # ImagenMagicaTest.rechazaWebpSinSoporteEnTuberia). .webp se OFRECE en el
    # dialogo pero viaja como MENSAJE_ARCHIVO, igual que en los otros clientes.
    _EXTENSIONES_IMAGEN = {".png", ".jpg", ".jpeg", ".gif", ".bmp"}

    def _fijar_imagen_adjunta(self, ruta):
        if not ruta or not Path(ruta).is_file():
            return
        self.imagen_pendiente = str(Path(ruta).resolve())
        ext = Path(ruta).suffix.casefold()
        es_imagen = ext in self._EXTENSIONES_IMAGEN
        if es_imagen:
            pixmap = QPixmap(self.imagen_pendiente)
            if not pixmap.isNull():
                thumb = pixmap.scaled(48, 48, Qt.AspectRatioMode.KeepAspectRatio,
                                      Qt.TransformationMode.SmoothTransformation)
                self.lbl_preview_thumb.setPixmap(thumb)
            else:
                self.lbl_preview_thumb.setText("🖼")
        else:
            # Ícono genérico por extensión
            iconos = {
                ".pdf": "📄", ".docx": "📝", ".xlsx": "📊",
                ".pptx": "📑", ".txt": "📃", ".zip": "🗜", ".rar": "🗜",
            }
            self.lbl_preview_thumb.setText(iconos.get(ext, "📎"))
            self.lbl_preview_thumb.setStyleSheet(
                "border-radius: 4px; border: 1px solid #CCD6D8; "
                "background: #FFFFFF; font-size: 24px;")
        tamano_kb = Path(ruta).stat().st_size / 1024
        tamano_str = (f"{tamano_kb/1024:.1f} MB" if tamano_kb >= 1024
                      else f"{tamano_kb:.1f} KB")
        self.lbl_adjunto.setText(f"{Path(ruta).name}  ({tamano_str})")
        self.preview_container.setVisible(True)
        self._actualizar_envio()

    def _quitar_adjunto(self):
        self.imagen_pendiente = None
        self.lbl_preview_thumb.clear()
        self.lbl_adjunto.clear()
        self.preview_container.setVisible(False)
        self._actualizar_envio()

    def adjuntar_imagen(self):
        """Adjunta cualquier archivo (imagen o documento) a la conversación."""
        if not self.usuario_seleccionado:
            QMessageBox.information(self, "Seleccionar conversación",
                                    "Selecciona una conversación primero.")
            return
        ruta, _ = QFileDialog.getOpenFileName(
            self, "Seleccionar archivo", "",
            "Todos los archivos (*.*);;"
            "Imágenes (*.png *.jpg *.jpeg *.gif *.bmp *.webp);;"
            "Documentos (*.pdf *.docx *.xlsx *.pptx *.txt *.zip)")
        if ruta:
            self._fijar_imagen_adjunta(ruta)

    def dragEnterEvent(self, event):
        if event.mimeData().hasUrls():
            for url in event.mimeData().urls():
                if Path(url.toLocalFile()).is_file():
                    event.acceptProposedAction()
                    return
        event.ignore()

    def dragMoveEvent(self, event):
        if event.mimeData().hasUrls():
            for url in event.mimeData().urls():
                if Path(url.toLocalFile()).is_file():
                    event.acceptProposedAction()
                    return
        event.ignore()

    def _aviso_red(self, texto):
        """Banner inline para errores de red/descarga (no abre ventanas)."""
        self.lbl_estado_red.setText(texto)
        self.lbl_estado_red.setStyleSheet("color: #B91C1C; font-size: 11px; font-weight: 700;")

    def dropEvent(self, event):
        if event.mimeData().hasUrls():
            for url in event.mimeData().urls():
                ruta = url.toLocalFile()
                if Path(ruta).is_file():
                    if not self.usuario_seleccionado:
                        QMessageBox.information(
                            self, "Seleccionar conversación",
                            "Selecciona una conversación primero.")
                        event.acceptProposedAction()
                        return
                    self._fijar_imagen_adjunta(ruta)
                    event.acceptProposedAction()
                    return
            self._aviso_red("El archivo arrastrado no se pudo leer.")
            event.ignore()
            return
        event.ignore()

    def salir_en_todos(self):
        respuesta = QMessageBox.question(
            self, "Confirmar salida", "¿Cerrar todas las sesiones de esta cuenta?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No)
        if respuesta != QMessageBox.StandardButton.Yes:
            return
        self._ejecutar("salir_todos", self.fachada.expulsar_otras_sesiones)

    def _ir_a_nuevos(self):
        """UIX-16: baja al fondo y apaga el aviso."""
        self._nuevos_pendientes = 0
        self.btn_nuevos.setVisible(False)
        self.lista_mensajes.scrollToBottom()

    def _avisar_nuevo(self):
        """UIX-16: llegó algo mientras se lee historial viejo."""
        self._nuevos_pendientes = getattr(self, "_nuevos_pendientes", 0) + 1
        self.btn_nuevos.setText(f"↓ {self._nuevos_pendientes} nuevos")
        self.btn_nuevos.setVisible(True)

    def _procesar_mensaje(self, mensaje):
        tipo = mensaje.get("tipo")
        if tipo in ("MENSAJE_ENTREGADO", "MENSAJE_LEIDO"):
            id_mensaje = mensaje.get("contenido")
            if id_mensaje:
                nuevo_estado = "LEIDO" if tipo == "MENSAJE_LEIDO" else "ENTREGADO"
                existente = self._mensaje_por_id(id_mensaje)
                if existente is not None and existente.get("is_own"):
                    existente["state"] = self._estado_visible(nuevo_estado)
                    existente["raw_state"] = nuevo_estado
                    self._modelo_mensajes.refrescar(id_mensaje)
            if self.usuario_seleccionado:
                self._solicitar_historial(self.usuario_seleccionado, 0)
            return
        if tipo == "PRESENCIA":
            return
        remitente = mensaje.get("remitente")
        if remitente == self.usuario_seleccionado:
            id_mensaje = mensaje.get("id") or str(uuid.uuid4())
            if id_mensaje not in self._historial_ids:
                usuario = next((u for u in self._directorio if u.get("codigo") == remitente), {})
                nombre = " ".join(filter(None, [usuario.get("nombres"), usuario.get("apellidos")])).strip()
                identidad = f"{nombre} [{remitente}]" if nombre else remitente
                es_imagen = tipo == "MENSAJE_IMAGEN"
                es_archivo = tipo == "MENSAJE_ARCHIVO"
                if es_archivo:
                    texto_burbuja = f"📎 {mensaje.get('nombreArchivo') or 'archivo'}"
                else:
                    texto_burbuja = "" if es_imagen else (mensaje.get("contenido") or "")
                item = {
                    "id": id_mensaje, "sender": identidad, "date": mensaje.get("fechaHora", ""),
                    "text": texto_burbuja,
                    "state": "", "raw_state": "", "is_own": False,
                    "is_image": es_imagen, "es_archivo": es_archivo,
                    "archivo_id": mensaje.get("archivoId"),
                    "filename": mensaje.get("nombreArchivo") or "",
                    "path": "", "image_loaded": False, "image_loading": False,
                    "image_state": "Imagen", "image_base64": mensaje.get("contenidoImagen")
                }
                self._historial_ids.add(id_mensaje)
                self._agregar_fila(item)
                # UIX-16: solo baja solo si ya se estaba abajo; si no, avisa.
                if self._seguir_abajo():
                    self.lista_mensajes.scrollToBottom()
                else:
                    self._avisar_nuevo()
            self._solicitar_historial(remitente, 0)
            if hasattr(self.fachada, "marcar_leidos"):
                self._ejecutar("marcar_leidos", lambda: self.fachada.marcar_leidos(remitente))

    def _procesar_presencia(self, mensaje):
        # Rafagas de presencia (entradas/salidas) coalescen en un solo
        # refresh: cada uno cuesta red + rebuild de toda la lista.
        if not self._timer_presencia.isActive():
            self._timer_presencia.start()

    def _procesar_desconexion(self):
        # PAT-03: el estado lo lleva _procesar_estado_conexion. Aquí solo queda
        # el aviso de siempre para no perder texto propio.
        self.lbl_estado_red.setText("Conexión cerrada")

    def _procesar_estado_conexion(self, estado):
        """PAT-03: pinta el estado. Llega ya en el hilo de la GUI (Signal)."""
        self.lbl_offline.setVisible(muestra_banner(estado))
        if estado != CONECTADO:
            self.lbl_estado_red.setText(texto_estado(estado))
        elif self.fachada.codigo_actual:
            # Al volver la red no se pisa el contador de contactos que calcula
            # `_pintar_directorio`; solo se limpa el aviso de caída.
            if "Sin conexi" in self.lbl_estado_red.text() or \
                    "Sesión cerrada" in self.lbl_estado_red.text():
                self.lbl_estado_red.setText("En línea")

    def _cargar_imagenes_visibles(self, *_):
        modelo = self._modelo_mensajes
        if not self.isVisible() or modelo.rowCount() == 0:
            return
        viewport = self.lista_mensajes.viewport().rect()
        for indice in range(modelo.rowCount()):
            qi = modelo.index(indice)
            rect = self.lista_mensajes.visualRect(qi)
            if not rect.isValid() or not viewport.intersects(rect):
                continue
            mensaje = modelo.data(qi, ROL_ITEM)
            if not isinstance(mensaje, dict):
                continue
            if not mensaje.get("is_image") or mensaje.get("image_loading") or mensaje.get("image_loaded"):
                continue
            mensaje["image_loading"] = True
            mensaje["image_error"] = ""
            modelo.refrescar(mensaje.get("id"))
            def cargar(m=mensaje):
                try:
                    return self._cargar_imagen(m)
                except Exception as error:
                    error.mensaje_id = m.get("id")
                    raise
            # UIX-09: la tarea queda rastreada por id para poder cancelarla.
            self._cargas_imagen[mensaje.get("id")] = self._ejecutar("imagen", cargar)

    def _cargar_imagen(self, mensaje):
        carpeta = self._directorio_imagenes
        carpeta.mkdir(parents=True, exist_ok=True)
        extension = Path(mensaje.get("filename") or "").suffix or ".img"
        ruta = mensaje.get("path")
        if not ruta or not Path(ruta).is_file():
            ruta = str(carpeta / ((mensaje.get("id") or "imagen") + extension))
            imagen_b64 = mensaje.get("image_base64")
            if imagen_b64:
                Path(ruta).write_bytes(base64.b64decode(imagen_b64))
            else:
                self.fachada.obtener_imagen(mensaje["id"], ruta)
        imagen = QImage(ruta)
        if imagen.isNull():
            raise RuntimeError("El archivo descargado no es una imagen válida")
        return mensaje.get("id"), imagen, ruta

    def _mensaje_por_id(self, mensaje_id):
        """Dict del mensaje con ese id, o None si no está cargado."""
        if not mensaje_id:
            return None
        indice = self._modelo_mensajes.indice_por_id(mensaje_id)
        if not indice.isValid():
            return None
        mensaje = self._modelo_mensajes.data(indice, ROL_ITEM)
        return mensaje if isinstance(mensaje, dict) else None

    def _item_y_row_por_id(self, mensaje_id):
        # Compatibilidad: devuelve (dict, None); ya no hay widgets por fila.
        return self._mensaje_por_id(mensaje_id), None

    def _row_por_id(self, mensaje_id):
        return self._mensaje_por_id(mensaje_id)

    def _ampliar_imagen(self, mensaje_id):
        """Un click en imagen abre el visor con zoom (no modal)."""
        try:
            pixmap = self._delegado_mensajes.pixmap_de(mensaje_id)
            if pixmap is None or pixmap.isNull():
                return
            mensaje = self._mensaje_por_id(mensaje_id) or {}
            visor = _VisorImagen(pixmap, mensaje.get("filename") or "Imagen",
                                 self.window())
            visor.show()
        except Exception:
            pass

    def _reintentar_imagen(self, mensaje):
        mensaje["image_loading"] = True
        mensaje["image_error"] = ""
        self._modelo_mensajes.refrescar(mensaje.get("id"))

        def cargar():
            try:
                return self._cargar_imagen(mensaje)
            except Exception as error:
                error.mensaje_id = mensaje.get("id")
                raise

        # UIX-00b: esto vivia DENTRO de cargar(), detras del raise, asi que era
        # codigo inalcanzable y el boton "Reintentar imagen" no reintentaba nada.
        # Se lanza fuera de la funcion; el error lo recoge _ejecutar, que ya
        # muestra el aviso y vuelve a ofrecer Reintentar.
        # UIX-09: rastreada para poder cancelarla al salir de la vista.
        self._cargas_imagen[mensaje.get("id")] = self._ejecutar("imagen", cargar)

    def _descargar_adjunto(self, mensaje):
        es_imagen = bool(mensaje.get("is_image"))
        archivo_id = mensaje.get("archivo_id")
        if not archivo_id and not es_imagen:
            QMessageBox.warning(self, "No se pudo descargar", "El identificador del archivo no está disponible.")
            return
        if es_imagen:
            nombre_defecto = mensaje.get("filename") or "imagen.png"
        else:
            nombre_defecto = mensaje.get("filename") or "archivo.bin"
        ruta_sugerida, _ = QFileDialog.getSaveFileName(
            self, "Guardar archivo", nombre_defecto, "Todos los archivos (*.*)")
        if not ruta_sugerida:
            return

        mensaje["descargando"] = True
        self._modelo_mensajes.refrescar(mensaje.get("id"))

        def descargar():
            try:
                if es_imagen:
                    # obtener_imagen resuelve caché local, base64 o archivoId.
                    self.fachada.obtener_imagen(mensaje.get("id"), ruta_sugerida)
                else:
                    self.fachada.descargar_archivo(archivo_id, ruta_sugerida)
                return mensaje.get("id"), ruta_sugerida
            except Exception as error:
                error.mensaje_id = mensaje.get("id")
                raise

        self._ejecutar("descarga", descargar)

    @staticmethod
    def _estado_visible(estado):
        return {
            "PENDIENTE": "⏳ Pendiente",
            "ENVIADO": "✓ Enviado",
            "ENTREGADO": "✓✓ Entregado",
            "LEIDO": "✓✓ Leído",
        }.get(estado, estado or "")

    def _procesar_cierre(self, mensaje):
        motivo = mensaje.get("motivo") or mensaje.get("mensajeError") or "Sesión cerrada por el servidor."
        QMessageBox.information(self, "Sesión cerrada", motivo)
        self._cerrando = True
        self.fachada.desconectar()
        self.on_logout()
        self.close()


    def closeEvent(self, evento):
        if self._cerrando:
            evento.accept()
            return
        respuesta = QMessageBox.question(
            self, "Confirmar salida", "¿Deseas cerrar esta sesión y volver al inicio?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No)
        if respuesta != QMessageBox.StandardButton.Yes:
            evento.ignore()
            return
        self._cerrando = True
        self.fachada.on_mensaje_recibido_ui = None
        self.fachada.on_cierre_ui = None
        self.fachada.on_presencia_ui = None
        self.fachada.on_desconexion_ui = None
        self.fachada.desconectar()
        self.on_logout()
        evento.accept()
