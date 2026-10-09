import threading

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QHBoxLayout, QLabel, QVBoxLayout, QWidget
from qfluentwidgets import ComboBox, LineEdit, PasswordLineEdit, PrimaryPushButton

from ui.tema import (
    MODO_OSCURO,
    TOKENS_CANONICOS,
    aplicar_tema_fluent,
    estilo_cabecera,
    estilo_tarjeta,
    guardar_modo,
    modo_guardado,
    modo_por_indice,
    normalizar,
    tokens,
)


class LoginWindow(QWidget):
    login_succeeded = Signal(str)

    def __init__(self, fachada, aviso=""):
        super().__init__()
        self.fachada = fachada
        self.modo = self._leer_modo()
        self.setWindowTitle("Mensajería Académica | Iniciar sesión")
        self.setMinimumSize(440, 560)

        root = QVBoxLayout(self)
        root.setContentsMargins(24, 24, 24, 24)
        self.card = QWidget()
        layout = QVBoxLayout(self.card)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(10)

        header = QWidget()
        header_layout = QVBoxLayout(header)
        header_layout.setContentsMargins(16, 14, 16, 14)
        self.eyebrow = QLabel("UNIVERSIDAD")
        self.title = QLabel("Mensajería Académica")
        self.subtitle = QLabel("Iniciar sesión")
        self.subtitle.setWordWrap(True)
        header_layout.addWidget(self.eyebrow)
        header_layout.addWidget(self.title)
        header_layout.addWidget(self.subtitle)
        layout.addWidget(header)
        self._header = header

        self.lbl_codigo = QLabel("Código universitario")
        layout.addWidget(self.lbl_codigo)
        self.entry_codigo = LineEdit()
        self.entry_codigo.setAccessibleName("Código universitario")
        self.entry_codigo.setPlaceholderText("A001234567")
        self.entry_codigo.setClearButtonEnabled(True)
        layout.addWidget(self.entry_codigo)

        self.lbl_password = QLabel("Contraseña")
        layout.addWidget(self.lbl_password)
        self.entry_password = PasswordLineEdit()
        self.entry_password.setAccessibleName("Contraseña")
        self.entry_password.setPlaceholderText("••••••••••")
        self.entry_password.returnPressed.connect(self.login)
        layout.addWidget(self.entry_password)

        fila_server = QHBoxLayout()
        fila_server.setSpacing(10)
        host_box = QVBoxLayout()
        self.lbl_host = QLabel("Servidor")
        host_box.addWidget(self.lbl_host)
        self.entry_host = LineEdit()
        self.entry_host.setAccessibleName("Servidor configurado, solo informativo")
        config = getattr(fachada, "config", None)
        host_inicial = "localhost"
        puerto_inicial = 5000
        try:
            if config is not None:
                host_inicial = config.get("host", host_inicial)
                puerto_inicial = int(config.get("puerto", puerto_inicial))
        except (TypeError, ValueError):
            pass
        self.entry_host.setText(str(host_inicial))
        self.entry_host.setClearButtonEnabled(False)
        self.entry_host.setEnabled(False)
        host_box.addWidget(self.entry_host)
        puerto_box = QVBoxLayout()
        self.lbl_puerto = QLabel("Puerto")
        puerto_box.addWidget(self.lbl_puerto)
        # Puerto solo lectura sin botones (igual que Java/.NET).
        self.entry_puerto = LineEdit()
        self.entry_puerto.setAccessibleName("Puerto configurado, solo informativo")
        self.entry_puerto.setText(str(puerto_inicial))
        self.entry_puerto.setClearButtonEnabled(False)
        self.entry_puerto.setEnabled(False)
        puerto_box.addWidget(self.entry_puerto)
        fila_server.addLayout(host_box, 2)
        fila_server.addLayout(puerto_box, 1)
        layout.addLayout(fila_server)

        fila_tema = QHBoxLayout()
        fila_tema.setSpacing(10)
        self.lbl_tema = QLabel("Tema")
        # ComboBox Fluent (como el del chat): un QComboBox de serie conserva la
        # paleta del arranque y su texto quedaba blanco sobre blanco en claro.
        self.combo_tema = ComboBox()
        self.combo_tema.setAccessibleName("Tema de la interfaz")
        self.combo_tema.addItems(["Oscuro", "Claro"])
        self.combo_tema.setCurrentIndex(0 if self.modo == MODO_OSCURO else 1)
        self.combo_tema.currentIndexChanged.connect(self._cambiar_tema)
        fila_tema.addWidget(self.lbl_tema)
        fila_tema.addWidget(self.combo_tema, 1)
        layout.addLayout(fila_tema)

        self.btn_login = PrimaryPushButton("Iniciar sesión →")
        self.btn_login.setAccessibleName("Iniciar sesión")
        self.btn_login.setMinimumHeight(40)
        self.btn_login.setMinimumWidth(220)
        self.btn_login.clicked.connect(self.login)
        layout.addWidget(self.btn_login, alignment=Qt.AlignmentFlag.AlignHCenter)

        self.lbl_error = QLabel("")
        self.lbl_error.setStyleSheet(
            "color: #B91C1C; background: #FDECEA; border: 1px solid #F1A9A5;"
            " border-radius: 8px; padding: 8px;"
        )
        self.lbl_error.setWordWrap(True)
        self.lbl_error.setVisible(False)
        layout.addWidget(self.lbl_error)

        self.lbl_aviso = QLabel("")
        self.lbl_aviso.setStyleSheet(
            "color: #B45309; background: #FFFBEB; border: 1px solid #F2CE84;"
            " border-radius: 8px; padding: 8px;"
        )
        self.lbl_aviso.setWordWrap(True)
        self.lbl_aviso.setVisible(bool(aviso))
        if aviso:
            self.lbl_aviso.setText(f"⏳ {aviso}")
        layout.addWidget(self.lbl_aviso)

        root.addWidget(self.card)
        self.aplicar_tema(self.modo)
        self.entry_codigo.setFocus(Qt.FocusReason.OtherFocusReason)
        self.entry_codigo.textChanged.connect(self._actualizar_boton)
        self.entry_password.textChanged.connect(self._actualizar_boton)
        self._actualizar_boton()

    def _leer_modo(self):
        return modo_guardado(self.fachada)

    def _guardar_modo(self):
        guardar_modo(self.fachada, self.modo)

    def _cambiar_tema(self, indice):
        self.modo = modo_por_indice(indice)
        self.aplicar_tema(self.modo)
        self._guardar_modo()

    def aplicar_tema(self, modo):
        self.modo = normalizar(modo)
        # Sin esto los controles Fluent (campos, combo, botón) se quedaban en
        # el tema global del arranque: modo claro con campos oscuros.
        aplicar_tema_fluent(self.modo)
        t = tokens(self.modo)
        canon = TOKENS_CANONICOS[self.modo]
        self.setStyleSheet(f"background: {t['ventana']};")
        self.card.setStyleSheet(estilo_tarjeta(t))
        self._header.setStyleSheet(estilo_cabecera(t))
        header_txt = (
            "background: transparent; border: none;"
        )
        self.eyebrow.setStyleSheet(
            f"color: {canon['login.eyebrow.fg']}; font-size: 11px; font-weight: 700; {header_txt}")
        self.title.setStyleSheet(
            f"font-size: 20px; font-weight: 700; color: {canon['login.title.fg']}; {header_txt}")
        self.subtitle.setStyleSheet(
            f"color: {canon['login.subtitle.fg']}; font-size: 12px; {header_txt}")
        etiqueta = (
            f"color: {canon['login.label.fg']}; background: transparent; border: none;"
        )
        for lbl in (self.lbl_codigo, self.lbl_password, self.lbl_host,
                    self.lbl_puerto, self.lbl_tema):
            lbl.setStyleSheet(etiqueta)
        self.combo_tema.setCurrentIndex(0 if self.modo == MODO_OSCURO else 1)

    def _actualizar_boton(self):
        # UIX-00e: con el login en vuelo el boton sigue deshabilitado. Antes no
        # hacia falta porque el hilo de la GUI estaba bloqueado y no habia forma
        # de pulsarlo dos veces; en segundo plano si.
        if getattr(self, "_login_en_curso", False):
            self.btn_login.setEnabled(False)
            return
        self.btn_login.setEnabled(bool(self.entry_codigo.text().strip()
                                        and self.entry_password.text()))

    def login(self):
        codigo = self.entry_codigo.text().strip()
        contrasena = self.entry_password.text()
        if not codigo or not contrasena:
            self.lbl_error.setText("✖ Ingresa el código y la contraseña.")
            self.lbl_error.setVisible(True)
            return
        if getattr(self, "_login_en_curso", False):
            return

        self.btn_login.setEnabled(False)
        self.btn_login.setText("Conectando…")
        self.lbl_error.setVisible(False)

        # UIX-00e: login() se llamaba en el hilo de la GUI y puede bloquear hasta
        # 15 s (timeout de la fachada), dejando la ventana congelada. Ahora va en
        # un hilo de fondo y el resultado vuelve al hilo de la GUI, igual que
        # LoginController en Java. La excepcion se transporta en un atributo:
        # PySide no la propaga entre hilos.
        self._login_en_curso = True

        def conectar():
            try:
                exito = bool(self.fachada.login(codigo, contrasena).get("exito"))
                fallo = None
            except Exception as error:  # noqa: BLE001 - se muestra al usuario
                exito, fallo = False, error
            self._terminar_login(exito, fallo)

        threading.Thread(target=conectar, name="login-fondo", daemon=True).start()

    def _terminar_login(self, exito, fallo):
        """Vuelve al hilo de la GUI: solo aqui se toca Qt."""
        self._login_en_curso = False
        self.btn_login.setEnabled(True)
        self.btn_login.setText("Iniciar sesión →")
        self._actualizar_boton()

        if fallo is not None:
            self.lbl_error.setText(
                f"✖ No se pudo conectar ({type(fallo).__name__}): {fallo}")
            self.lbl_error.setVisible(True)
            return
        if exito:
            self.login_succeeded.emit(self.entry_codigo.text().strip())
        else:
            self.lbl_error.setText("✖ Usuario no registrado o datos incorrectos.")
            self.lbl_error.setVisible(True)
