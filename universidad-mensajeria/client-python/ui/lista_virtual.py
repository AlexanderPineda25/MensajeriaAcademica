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
from PySide6.QtCore import QAbstractListModel, QModelIndex, Qt

from transversal.fechas import etiqueta_dia, hora_corta

ROL_ITEM = Qt.ItemDataRole.UserRole


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


def __getattr__(nombre):
    """Conserva la ruta histórica de importación del delegado de mensajes."""
    if nombre == "MensajeDelegate":
        from ui.celdas.mensaje_delegate import MensajeDelegate
        return MensajeDelegate
    raise AttributeError(f"module {__name__!r} has no attribute {nombre!r}")
