"""Delegado virtualizado que representa cada mensaje visible."""

from PySide6.QtCore import QSize, Qt
from PySide6.QtGui import QColor, QFont, QFontMetrics, QPalette, QTextDocument
from PySide6.QtWidgets import QApplication, QStyledItemDelegate, QStyle

from transversal.fechas import hora_corta
from ui.lista_virtual import ROL_ITEM
from ui.renderizado_texto import resaltar_html
from ui.tema import tokens
from ui.miniaturas_chat import MiniaturasChat
from transversal.etapas_filtrado import resumir_etapas_filtrado

ANCHO_MAX_BURBUJA = 580
ANCHO_MIN_BURBUJA = 280
MARGEN_FILA = 10
BUBBLE_PAD_H = 12
BUBBLE_PAD_V = 8
ESPACIADO = 5
class MensajeDelegate(QStyledItemDelegate):
    """Pinta burbujas y resuelve clics por coordenadas."""

    def __init__(self, parent=None, modo="oscuro"):
        super().__init__(parent)
        self._modo = modo
        self._patron = ""
        self._miniaturas = MiniaturasChat()
        from collections.abc import Callable
        self.responder: Callable[[dict], None] | None = None
        self.reintentar: Callable[[dict], None] | None = None
        self.descargar: Callable[[dict], None] | None = None
        self.ampliar: Callable[[str], None] | None = None
        self._fuente_remitente = QFont()
        self._fuente_remitente.setPointSize(12)
        self._fuente_remitente.setBold(True)
        self._fuente_cuerpo = QFont()
        self._fuente_cuerpo.setPointSize(13)
        self._fuente_mini = QFont()
        self._fuente_mini.setPointSize(11)

    # -- configuración ---------------------------------------------------

    def set_modo(self, modo):
        self._modo = modo

    def set_patron(self, patron):
        self._patron = patron or ""

    def guardar_pixmap(self, id_mensaje, pixmap):
        self._miniaturas.guardar(id_mensaje, pixmap)

    def pixmap_de(self, id_mensaje):
        return self._miniaturas.obtener(id_mensaje)

    # -- layout compartido (sizeHint + paint + hit-test) -------------------

    def _ancho_burbuja(self, ancho_vista):
        if ancho_vista <= 100:
            return ANCHO_MAX_BURBUJA
        return min(ANCHO_MAX_BURBUJA,
                   max(ANCHO_MIN_BURBUJA, int(ancho_vista * 0.65)))

    def distribuir(self, item, ancho_vista, t):
        """Devuelve rectángulos relativos a la fila (x, y, w, h) y alto total.

        Claves: 'burbuja', 'remitente', 'cuerpo', 'imagen', 'estado_img',
        'retry', 'descarga', 'pie', 'fecha'. Todo lo opcional puede faltar.
        """
        if item.get("es_separador"):
            return {"alto": 28, "separador": True}
        tope = self._ancho_burbuja(ancho_vista)
        propia = bool(item.get("is_own"))
        # Ancho útil dentro de la burbuja.
        interior = tope - BUBBLE_PAD_H * 2
        x_burbuja = ancho_vista - MARGEN_FILA - tope if propia \
            else MARGEN_FILA
        y = 3
        lay = {}
        if item.get("mostrar_remitente", True):
            fm = QFontMetrics(self._fuente_remitente)
            h = fm.height() + 2
            lay["remitente"] = (x_burbuja + BUBBLE_PAD_H, y + BUBBLE_PAD_V,
                                interior, h)
            y += BUBBLE_PAD_V + h + ESPACIADO
        else:
            y += BUBBLE_PAD_V
        texto = item.get("text", "") or ""
        if texto:
            doc = QTextDocument()
            doc.setDefaultFont(self._fuente_cuerpo)
            doc.setHtml(resaltar_html(texto, self._patron))
            doc.setTextWidth(interior)
            h = int(doc.size().height()) + 2
            lay["cuerpo"] = (x_burbuja + BUBBLE_PAD_H, y, interior, h)
            lay["_doc"] = doc
            y += h + ESPACIADO
        if item.get("is_image"):
            esc = self._miniaturas.escalar(item.get("id"))
            if esc is not None:
                lay["imagen"] = (x_burbuja + BUBBLE_PAD_H, y,
                                 esc.width(), esc.height())
                lay["_escalado"] = esc
                y += esc.height() + ESPACIADO
                resumen = resumir_etapas_filtrado(item.get("etapas_filtrado"))
                if resumen:
                    x_etapas = x_burbuja + BUBBLE_PAD_H
                    caja = QFontMetrics(self._fuente_mini).boundingRect(
                        0, 0, interior, 1000,
                        Qt.AlignmentFlag.AlignLeft | Qt.TextFlag.TextWordWrap,
                        resumen)
                    alto_etapas = max(QFontMetrics(self._fuente_mini).height(),
                                      caja.height())
                    lay["etapas_filtrado"] = (
                        x_etapas, y, interior, alto_etapas, resumen)
                    y += alto_etapas + ESPACIADO
            elif not item.get("image_loaded"):
                fm = QFontMetrics(self._fuente_mini)
                h = fm.height() + 2
                lay["estado_img"] = (x_burbuja + BUBBLE_PAD_H, y, interior, h)
                y += h + ESPACIADO
        if item.get("image_error"):
            fm = QFontMetrics(self._fuente_mini)
            h = 25
            lay["retry"] = (x_burbuja + BUBBLE_PAD_H, y, 150, h)
            y += h + ESPACIADO
        con_archivo = bool(item.get("es_archivo") and item.get("archivo_id"))
        if con_archivo or item.get("is_image"):
            h = 25
            lay["descarga"] = (x_burbuja + BUBBLE_PAD_H, y, 150, h)
            y += h + ESPACIADO
        fm = QFontMetrics(self._fuente_mini)
        h = fm.height() + 2
        lay["pie"] = (x_burbuja + BUBBLE_PAD_H, y, interior, h)
        y += h + BUBBLE_PAD_V
        # La burbuja se ciñe al contenido hasta el tope.
        lay["burbuja"] = (x_burbuja, 3, tope, y - 3 + 3)
        lay["alto"] = y + 3
        lay["propia"] = propia
        return lay

    # -- QStyledItemDelegate -------------------------------------------------

    def sizeHint(self, option, index):
        item = index.data(ROL_ITEM)
        if not isinstance(item, dict):
            return QSize()
        t = tokens(self._modo)
        lay = self.distribuir(item, option.rect.width() or 600, t)
        return QSize(option.rect.width(), lay["alto"])

    def paint(self, painter, option, index):
        item = index.data(ROL_ITEM)
        if not isinstance(item, dict):
            return
        t = tokens(self._modo)
        painter.save()
        try:
            if option.state & QStyle.StateFlag.State_Selected:
                painter.fillRect(option.rect, QColor("#243447"))
            if item.get("es_separador"):
                self._pintar_separador(painter, option, item, t)
                return
            lay = self.distribuir(item, option.rect.width(), t)
            ox, oy = option.rect.x(), option.rect.y()
            # Burbuja.
            propia = lay["propia"]
            color_fondo = t["burbuja_propia"] if propia else t["burbuja_ajena"]
            painter.setBrush(QColor(color_fondo))
            painter.setPen(QColor(t["burbuja_propia_borde"] if propia
                                 else t["burbuja_ajena_borde"]))
            bx, by, bw, bh = lay["burbuja"]
            painter.drawRoundedRect(ox + bx, oy + by, bw, bh, 8, 8)
            painter.setPen(QColor(t["remitente"]))
            if "remitente" in lay:
                painter.setFont(self._fuente_remitente)
                x, y, w, h = lay["remitente"]
                sender = item.get("sender", "")
                painter.drawText(ox + x, oy + y, w, h,
                                 Qt.AlignmentFlag.AlignLeft
                                 | Qt.AlignmentFlag.AlignVCenter, sender)
            if "cuerpo" in lay:
                doc = lay["_doc"]
                painter.save()
                painter.translate(ox + lay["cuerpo"][0], oy + lay["cuerpo"][1])
                pal = QPalette()
                pal.setColor(QPalette.ColorRole.Text, QColor(
                    t["cuerpo_propia"] if propia else t["cuerpo_ajena"]))
                doc.setDefaultStyleSheet(
                    f"body {{ color: {t['cuerpo_propia'] if propia else t['cuerpo_ajena']}; "
                    f"font-size: 13px; }}")
                doc.drawContents(painter)
                painter.restore()
            if "imagen" in lay:
                x, y, w, h = lay["imagen"]
                painter.drawPixmap(ox + x, oy + y, lay["_escalado"])
            if "etapas_filtrado" in lay:
                x, y, w, h, resumen = lay["etapas_filtrado"]
                painter.setFont(self._fuente_mini)
                painter.setPen(QColor(t["texto_suave"]))
                painter.drawText(
                    ox + x, oy + y, w, h,
                    Qt.AlignmentFlag.AlignLeft | Qt.TextFlag.TextWordWrap,
                    resumen)
            if "estado_img" in lay:
                painter.setFont(self._fuente_mini)
                painter.setPen(QColor(t["texto_suave"]))
                x, y, w, h = lay["estado_img"]
                painter.drawText(ox + x, oy + y, w, h,
                                 Qt.AlignmentFlag.AlignLeft
                                 | Qt.AlignmentFlag.AlignVCenter,
                                 item.get("image_state", ""))
            if "retry" in lay:
                self._pintar_boton(painter, option, lay["retry"],
                                   "Reintentar imagen", t)
            if "descarga" in lay:
                texto = "Descargando…" if item.get("descargando") \
                    else ("⬇ Descargar" if not item.get("descargado_ruta")
                          else "⬇ Descargado")
                self._pintar_boton(painter, option, lay["descarga"], texto, t)
            if "pie" in lay:
                painter.setFont(self._fuente_mini)
                painter.setPen(QColor(t["fecha"]))
                x, y, w, h = lay["pie"]
                painter.drawText(ox + x, oy + y, w, h,
                                 Qt.AlignmentFlag.AlignLeft
                                 | Qt.AlignmentFlag.AlignVCenter,
                                 hora_corta(item.get("date", "")))
                estado = item.get("state", "")
                if estado and propia:
                    painter.setPen(QColor(t["visto_leido"] if "✓✓" in estado
                                         and "Le" in str(item.get("raw_state", ""))
                                         else t["visto_otro"]))
                    fm = QFontMetrics(self._fuente_mini)
                    painter.drawText(ox + x, oy + y, w, h,
                                     Qt.AlignmentFlag.AlignRight
                                     | Qt.AlignmentFlag.AlignVCenter, estado)
        finally:
            painter.restore()

    def _pintar_separador(self, painter, option, item, t):
        painter.setFont(self._fuente_mini)
        painter.setPen(QColor(t["texto_suave"]))
        painter.drawText(option.rect, Qt.AlignmentFlag.AlignCenter,
                         item.get("etiqueta", ""))

    def _pintar_boton(self, painter, option, rect, texto, t):
        from PySide6.QtWidgets import QApplication
        pal = QApplication.palette()
        painter.save()
        painter.setBrush(pal.button())
        painter.setPen(pal.mid().color())
        x, y, w, h = rect
        painter.drawRoundedRect(option.rect.x() + x, option.rect.y() + y,
                                w, h, 4, 4)
        painter.setFont(self._fuente_mini)
        painter.setPen(pal.buttonText().color())
        painter.drawText(option.rect.x() + x, option.rect.y() + y, w, h,
                         Qt.AlignmentFlag.AlignCenter, texto)
        painter.restore()

    # -- interacción ---------------------------------------------------------

    def editorEvent(self, evento, modelo, option, index):
        from PySide6.QtCore import QEvent
        from PySide6.QtGui import QMouseEvent
        item = index.data(ROL_ITEM)
        if not isinstance(item, dict) or item.get("es_separador"):
            return False
        t = tokens(self._modo)
        lay = self.distribuir(item, option.rect.width(), t)
        ox, oy = option.rect.x(), option.rect.y()
        pos = evento.pos() if hasattr(evento, "pos") else None

        if evento.type() == QEvent.Type.MouseButtonPress and pos is not None:
            boton = evento.button() if hasattr(evento, "button") else None
            from PySide6.QtCore import Qt as _Qt
            if boton == _Qt.MouseButton.RightButton:
                texto = item.get("text", "") or ""
                if texto:
                    from PySide6.QtWidgets import QApplication as _App
                    _App.clipboard().setText(texto)
                    return True
                return False
        if evento.type() == QEvent.Type.MouseButtonDblClick:
            if self.responder:
                self.responder(item)
                return True
            return False
        if evento.type() == QEvent.Type.MouseButtonRelease and pos is not None:
            lx, ly = pos.x() - ox, pos.y() - oy
            if "imagen" in lay:
                x, y, w, h = lay["imagen"]
                if x <= lx <= x + w and y <= ly <= y + h and self.ampliar:
                    if item.get("id"):
                        self.ampliar(item["id"])
                    return True
            if "retry" in lay:
                x, y, w, h = lay["retry"]
                if x <= lx <= x + w and y <= ly <= y + h and self.reintentar:
                    self.reintentar(item)
                    return True
            if "descarga" in lay:
                x, y, w, h = lay["descarga"]
                if x <= lx <= x + w and y <= ly <= y + h and self.descargar:
                    self.descargar(item)
                    return True
        return False
