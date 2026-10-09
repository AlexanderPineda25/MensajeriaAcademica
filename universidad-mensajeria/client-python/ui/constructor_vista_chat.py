"""Construcción de la interfaz visual de la conversación."""

from PySide6.QtCore import Qt
from PySide6.QtGui import QShortcut
from PySide6.QtWidgets import (
    QComboBox, QFrame, QHBoxLayout, QLabel,
    QSplitter, QVBoxLayout, QWidget,
)
from qfluentwidgets import (
    ComboBox, LineEdit, ListWidget, PrimaryPushButton, PushButton,
)

from ui.componentes_chat import CampoMensaje as _ComposerEdit
from ui.celdas.contacto_delegate import ContactoDelegate
from ui.celdas.mensaje_delegate import MensajeDelegate
from ui.lista_virtual import MensajeListModel
from ui.lista_mensajes_chat import ListaMensajesChat


class ConstructorVistaChat:
    def _construir_ui(self):
        central = QWidget()
        root = QHBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        self.setCentralWidget(central)
        splitter = QSplitter(Qt.Orientation.Horizontal)
        self._divisor_lateral = splitter
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
        self.btn_actualizar.setAccessibleName("Actualizar usuarios")
        self.btn_actualizar.clicked.connect(self.actualizar_usuarios)
        fila_contactos.addWidget(self.lbl_contactos, 1)
        fila_contactos.addWidget(self.btn_actualizar)
        self.lista_usuarios = ListWidget()
        # Tarjeta de conversación (punto, nombre, preview, hora e insignia),
        # pintada por delegado: miles de filas no cuestan widgets por fila.
        self._delegado_contactos = ContactoDelegate(self.lista_usuarios, self.modo)
        self.lista_usuarios.setItemDelegate(self._delegado_contactos)
        self.lista_usuarios.setUniformItemSizes(True)
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

        self.lista_mensajes = ListaMensajesChat()
        self.lista_mensajes.setSpacing(3)
        self.lista_mensajes.setStyleSheet("QListView { border: 0; padding: 9px; }")
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
        self.btn_anteriores.setAccessibleName("Cargar mensajes anteriores")
        self.btn_anteriores.clicked.connect(self._cargar_anteriores)
        self.btn_anteriores.setVisible(False)
        # En la cabecera a la derecha, como el cliente Java (UI-10).
        header_row.addWidget(self.btn_anteriores)
        # UIX-16: baja a lo nuevo sin perder el punto de lectura.
        self.btn_nuevos = PushButton("↓ Nuevos")
        self.btn_nuevos.setAccessibleName("Ir a mensajes nuevos")
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
        self.btn_quitar_respuesta.setAccessibleName("Cancelar respuesta")
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
        self.btn_quitar_adjunto.setAccessibleName("Quitar imagen adjunta")
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
        self.btn_imagen.setAccessibleName("Adjuntar imagen")
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
        self.btn_buscar_ant.setAccessibleName("Anterior coincidencia")
        self.btn_buscar_ant.setToolTip("Anterior coincidencia (Mayús+Enter)")
        self.btn_buscar_ant.clicked.connect(self._buscar_anterior)
        self.btn_buscar_sig = PushButton("↓")
        self.btn_buscar_sig.setFixedWidth(36)
        self.btn_buscar_sig.setAccessibleName("Siguiente coincidencia")
        self.btn_buscar_sig.setToolTip("Siguiente coincidencia (Enter)")
        self.btn_buscar_sig.clicked.connect(self._buscar_siguiente)
        self.lbl_buscar_cuenta = QLabel("")
        self.btn_buscar_cerrar = PushButton("✕")
        self.btn_buscar_cerrar.setFixedWidth(36)
        self.btn_buscar_cerrar.setAccessibleName("Cerrar búsqueda")
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
