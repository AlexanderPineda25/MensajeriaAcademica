"""Construcción de la fila visual de un contacto del directorio.

La fila guarda en roles TODO lo que `ContactoDelegate` pinta (punto de
presencia, nombre, preview del último mensaje, hora relativa e insignia):
el texto plano queda para accesibilidad y para las pruebas.
"""
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QListWidgetItem

#: Roles de la fila (compartidos con `ContactoDelegate`; no renumerar:
#: `_limpiar_insignia` y las pruebas los leen por offset desde UserRole).
ROL_CODIGO = Qt.ItemDataRole.UserRole
ROL_NOMBRE = Qt.ItemDataRole.UserRole + 1
ROL_NO_LEIDOS = Qt.ItemDataRole.UserRole + 2
ROL_BASE = Qt.ItemDataRole.UserRole + 3
ROL_PREVIEW = Qt.ItemDataRole.UserRole + 4
ROL_CONECTADO = Qt.ItemDataRole.UserRole + 5
ROL_FECHA = Qt.ItemDataRole.UserRole + 6

#: Literal compartido con Java (`UsuarioCell`): fila sin conversación.
SIN_MENSAJES = "Sin mensajes aún"


class UsuarioCell:
    @staticmethod
    def crear(usuario, identidad, pendientes=0, preview="", fecha=""):
        codigo = usuario.get("codigo", "")
        conectado = bool(usuario.get("conectado"))
        insignia = f"  ({pendientes})" if pendientes > 0 else ""
        detalle = preview or SIN_MENSAJES
        punto = "●" if conectado else "○"
        base = f"{punto}  {identidad}\n    {detalle}"
        item = QListWidgetItem(f"{punto}  {identidad}{insignia}\n    {detalle}")
        item.setData(ROL_CODIGO, codigo)
        item.setData(ROL_NOMBRE, identidad)
        item.setData(ROL_NO_LEIDOS, pendientes)
        item.setData(ROL_BASE, base)
        item.setData(ROL_PREVIEW, detalle)
        item.setData(ROL_CONECTADO, conectado)
        item.setData(ROL_FECHA, fecha or "")
        return item
