"""UIX-01: lista de mensajes virtualizada (QListView + delegado propio).

Antes cada mensaje era un QWidget vivo dentro de un QListWidget: con
historiales largos la UI se arrastraba (un widget por fila + layouts por fila).
Ahora el modelo guarda los mismos dicts de siempre y el delegado solo pinta
las filas visibles; no hay ningún QWidget por mensaje.

Lo que cambia para el resto del código:
- `lista_mensajes` es QListView; los items son filas del modelo, no QWidgets.
- El estado visual vive en el dict (image_loaded/loading/error, descargando,
  mostrar_remitente...) y el pixmap en una caché acotada del delegado.
- Los clics (reintentar, descargar, ampliar, responder) los resuelve el
  delegado por coordenadas y llama a los mismos callbacks de antes.
- El texto deja de ser seleccionable con el ratón (QLabel con
  TextSelectableByMouse): clic derecho copia el cuerpo al portapapeles.

Contrato visual idéntico al _MessageRow anterior: burbuja redondeada, tope
relativo UIX-10, remitente UIX-05, fecha UIX-03, resaltado UIX-06, separadores
UIX-04, ticks de estado.
"""
from collections import OrderedDict

from PySide6.QtCore import QAbstractListModel, QModelIndex, QSize, Qt, QTimer
from PySide6.QtGui import QColor, QFont, QFontMetrics, QPalette, QPixmap, QTextDocument
from PySide6.QtWidgets import QListView, QStyledItemDelegate, QStyle

from transversal.estado_conexion import CONECTADO  # noqa: F401 (documenta dependencia)
from transversal.fechas import etiqueta_dia, hora_corta
from ui.tema import tokens

ROL_ITEM = Qt.ItemDataRole.UserRole

ANCHO_MAX_BURBUJA = 580
ANCHO_MIN_BURBUJA = 280
MARGEN_FILA = 10
BUBBLE_PAD_H = 12
BUBBLE_PAD_V = 8
ESPACIADO = 5
MAX_CACHE_PIXMAP = 100


class MensajeListModel(QAbstractListModel):
    """Filas del historial: dicts de mensaje o {'es_separador': True, ...}."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._filas = []

    def rowCount(self, _parent=QModelIndex()):
        return len(self._filas)

    def data(self, index, rol=Qt.ItemDataRole.DisplayRole):
        if not index.isValid() or not 0 <= index.row() < len(self._filas):
            return None
        fila = self._filas[index.row()]
        if rol == ROL_ITEM:
            return fila
        if rol == Qt.ItemDataRole.ToolTipRole and isinstance(fila, dict) \
                and fila.get("id") and fila.get("date"):
            dia = etiqueta_dia(fila.get("date", ""))
            if dia:
                return f"{dia} {hora_corta(fila.get('date', ''))}"
        return None

    def flags(self, index):
        base = super().flags(index)
        fila = self.data(index, ROL_ITEM)
        if isinstance(fila, dict) and fila.get("es_separador"):
            return Qt.ItemFlag.NoItemFlags
        return base | Qt.ItemFlag.ItemIsSelectable | Qt.ItemFlag.ItemIsEnabled

    def sustituir(self, filas):
        self.beginResetModel()
        self._filas = list(filas)
        self.endResetModel()

    def limpiar(self):
        self.sustituir([])

    def mensajes(self):
        return [f for f in self._filas
                if isinstance(f, dict) and f.get("id")]

    def indice_por_id(self, id_mensaje):
        for i, f in enumerate(self._filas):
            if isinstance(f, dict) and f.get("id") == id_mensaje:
                return self.index(i)
        return QModelIndex()

    def quitar_separadores(self):
        self._filas = [f for f in self._filas
                       if not (isinstance(f, dict) and f.get("es_separador"))]
        self.layoutChanged.emit()

    def insertar_en(self, indice, fila):
        self.beginInsertRows(QModelIndex(), indice, indice)
        self._filas.insert(indice, fila)
        self.endInsertRows()

    def agregar(self, fila):
        self.insertar_en(len(self._filas), fila)

    def refrescar(self, id_mensaje):
        idx = self.indice_por_id(id_mensaje)
        if idx.isValid():
            self.dataChanged.emit(idx, idx, [ROL_ITEM])

    def refrescar_todo(self):
        if self._filas:
            self.dataChanged.emit(self.index(0),
                                  self.index(len(self._filas) - 1), [ROL_ITEM])


class MensajeDelegate(QStyledItemDelegate):
    """Pinta burbujas y resuelve clics por coordenadas."""

    def __init__(self, parent=None, modo="oscuro"):
        super().__init__(parent)
        self._modo = modo
        self._patron = ""
        self._pixmaps = OrderedDict()
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
        self._pixmaps[id_mensaje] = pixmap
        while len(self._pixmaps) > MAX_CACHE_PIXMAP:
            self._pixmaps.popitem(last=False)

    def pixmap_de(self, id_mensaje):
        return self._pixmaps.get(id_mensaje)

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
            from ui.chat_window import resaltar_html
            doc = QTextDocument()
            doc.setDefaultFont(self._fuente_cuerpo)
            doc.setHtml(resaltar_html(texto, self._patron))
            doc.setTextWidth(interior)
            h = int(doc.size().height()) + 2
            lay["cuerpo"] = (x_burbuja + BUBBLE_PAD_H, y, interior, h)
            lay["_doc"] = doc
            y += h + ESPACIADO
        if item.get("is_image"):
            px = self._pixmaps.get(item.get("id"))
            if px is not None and not px.isNull():
                esc = px.scaled(420, 280, Qt.AspectRatioMode.KeepAspectRatio,
                                Qt.TransformationMode.SmoothTransformation)
                lay["imagen"] = (x_burbuja + BUBBLE_PAD_H, y,
                                 esc.width(), esc.height())
                lay["_escalado"] = esc
                y += esc.height() + ESPACIADO
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
        painter.setPen(pal.mid())
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
