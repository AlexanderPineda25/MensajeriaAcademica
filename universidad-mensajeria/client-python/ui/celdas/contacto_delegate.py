"""Tarjeta de conversación del panel lateral, estilo WhatsApp/JavaFX.

Espejo de `UsuarioCell.java` (punto de presencia, nombre con elipsis,
preview del último mensaje, insignia de no leídos a la derecha) más la
hora relativa (`hora_corta`), como en la lista de chats de WhatsApp.

Todo se lee de roles (`usuario_cell.ROL_*`): el texto plano del item no se
pinta, así la accesibilidad y las pruebas lo conservan intacto. Los colores
vienen de los tokens canónicos (`ui.tema_tokens`), que son generados: aquí
solo se consumen.
"""
from PySide6.QtCore import QRectF, QSize, Qt
from PySide6.QtGui import QColor, QFontMetrics
from PySide6.QtWidgets import QStyle
from qfluentwidgets import ListItemDelegate

from transversal.fechas import hora_corta
from ui.celdas.usuario_cell import (
    ROL_CONECTADO,
    ROL_FECHA,
    ROL_NO_LEIDOS,
    ROL_NOMBRE,
    ROL_PREVIEW,
)
from ui.tema import MODO_OSCURO, normalizar, tokens
from ui.tema_tokens import TOKENS as CANONICOS

ALTURA_FILA = 64


class ContactoDelegate(ListItemDelegate):
    """El `ListWidget` de QFluentWidgets habla con su delegado (`setHoverRow`,
    `setPressedRow`, `setSelectedRows`): heredar de `ListItemDelegate` conserva
    ese protocolo; aquí solo cambia el pintado.
    """

    def __init__(self, parent, modo=MODO_OSCURO):
        super().__init__(parent)
        self.actualizar_tema(modo)

    def actualizar_tema(self, modo):
        """Los tokens son cadenas hex; se cachean como QColor por modo."""
        canon = CANONICOS[normalizar(modo)]
        heredados = tokens(modo)
        self._nombre = QColor(canon["name.fg"])
        self._preview = QColor(canon["preview.fg"])
        self._fecha = QColor(canon["muted.fg.fecha"])
        self._online = QColor(canon["avatar_online"])
        self._offline = QColor(canon["avatar_offline"])
        self._badge_fondo = QColor(canon["badge_bg"])
        self._badge_texto = QColor(canon["badge_fg"])
        self._seleccion = QColor(canon["list.selected.bg"])
        self._suave = QColor(heredados["texto_suave"])

    def sizeHint(self, option, index):
        return QSize(super().sizeHint(option, index).width(), ALTURA_FILA)

    def paint(self, painter, option, index):
        painter.save()
        painter.setRenderHint(painter.RenderHint.Antialiasing)
        rect = option.rect.adjusted(6, 4, -6, -4)
        if option.state & QStyle.StateFlag.State_Selected:
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(self._seleccion)
            painter.drawRoundedRect(QRectF(rect), 8, 8)

        izquierda = rect.left() + 6
        derecha = rect.right() - 4

        conectado = bool(index.data(ROL_CONECTADO))
        nombre = index.data(ROL_NOMBRE) or ""
        preview = index.data(ROL_PREVIEW) or ""
        no_leidos = int(index.data(ROL_NO_LEIDOS) or 0)
        fecha = hora_corta(index.data(ROL_FECHA) or "")

        # Punto de presencia centrado en vertical.
        centro_y = rect.top() + rect.height() / 2
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(self._online if conectado else self._offline)
        radio = 5
        painter.drawEllipse(int(izquierda + 8 - radio), int(centro_y - radio),
                            radio * 2, radio * 2)
        texto_x = izquierda + 26

        metrica = QFontMetrics(painter.font())
        # Insignia de no leídos: píldora a la derecha, en la línea del preview.
        badge_ancho = 0
        if no_leidos > 0:
            texto_badge = str(no_leidos)
            badge_ancho = max(22, metrica.horizontalAdvance(texto_badge) + 14)
        # Hora relativa arriba a la derecha (solo si hay conversación).
        fecha_ancho = 0
        if fecha:
            fecha_ancho = metrica.horizontalAdvance(fecha) + 4
            painter.setPen(self._fecha)
            fuente = painter.font()
            fuente.setPointSizeF(fuente.pointSizeF() - 1)
            painter.setFont(fuente)
            painter.drawText(QRectF(derecha - fecha_ancho, rect.top() + 8,
                                    fecha_ancho, 18),
                             Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter,
                             fecha)
            fuente.setPointSizeF(fuente.pointSizeF() + 1)
            painter.setFont(fuente)

        # Nombre en negrita con elipsis, primera línea.
        ancho_nombre = max(40, derecha - texto_x - fecha_ancho - 6)
        fuente = painter.font()
        fuente.setBold(True)
        painter.setFont(fuente)
        painter.setPen(self._nombre)
        metrica_bold = QFontMetrics(fuente)
        painter.drawText(QRectF(texto_x, rect.top() + 8, ancho_nombre, 20),
                         Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
                         metrica_bold.elidedText(
                             nombre, Qt.TextElideMode.ElideRight, ancho_nombre))
        fuente.setBold(False)
        painter.setFont(fuente)

        # Preview del último mensaje, segunda línea, atenuado.
        ancho_preview = max(40, derecha - texto_x - badge_ancho - 6)
        painter.setPen(self._preview)
        painter.drawText(QRectF(texto_x, rect.top() + 32, ancho_preview, 20),
                         Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter,
                         metrica.elidedText(
                             preview, Qt.TextElideMode.ElideRight, ancho_preview))

        if no_leidos > 0:
            badge_x = derecha - badge_ancho
            badge_y = rect.top() + 32
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(self._badge_fondo)
            painter.drawRoundedRect(QRectF(badge_x, badge_y, badge_ancho, 20),
                                    10, 10)
            painter.setPen(self._badge_texto)
            fuente = painter.font()
            fuente.setBold(True)
            painter.setFont(fuente)
            painter.drawText(QRectF(badge_x, badge_y, badge_ancho, 20),
                             Qt.AlignmentFlag.AlignCenter, str(no_leidos))
        painter.restore()
