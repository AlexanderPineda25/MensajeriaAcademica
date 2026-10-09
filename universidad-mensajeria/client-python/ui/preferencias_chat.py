"""Preferencias locales del panel de chat, aisladas por cuenta."""

from PySide6.QtCore import QSettings


class PreferenciasChat:
    def __init__(self, ajustes=None):
        self._ajustes = ajustes or QSettings(
            "UniversidadMensajeria", "ClientePython")

    def divisor_lateral(self, codigo):
        if not codigo:
            return None
        valores = self._ajustes.value(self._clave_divisor(codigo))
        if not isinstance(valores, (list, tuple)) or len(valores) != 2:
            return None
        try:
            tamanos = tuple(int(valor) for valor in valores)
        except (TypeError, ValueError):
            return None
        return tamanos if all(valor > 0 for valor in tamanos) else None

    def guardar_divisor_lateral(self, codigo, tamanos):
        if not codigo or len(tamanos) != 2:
            return
        try:
            valores = [int(valor) for valor in tamanos]
        except (TypeError, ValueError):
            return
        if any(valor <= 0 for valor in valores):
            return
        self._ajustes.setValue(self._clave_divisor(codigo), valores)
        self._ajustes.sync()

    def ultimo_chat(self, codigo):
        if not codigo:
            return None
        valor = self._ajustes.value(self._clave_ultimo_chat(codigo), "")
        valor = str(valor).strip() if valor is not None else ""
        return valor or None

    def guardar_ultimo_chat(self, codigo, otro):
        if not codigo or not otro or codigo == otro:
            return
        self._ajustes.setValue(self._clave_ultimo_chat(codigo), otro)
        self._ajustes.sync()

    @staticmethod
    def _clave_divisor(codigo):
        return f"cuentas/{codigo}/panel-chat/divisor-lateral"

    @staticmethod
    def _clave_ultimo_chat(codigo):
        return f"cuentas/{codigo}/panel-chat/ultimo-chat"
