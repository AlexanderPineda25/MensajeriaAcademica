import datetime

from transversal.tipo_mensaje import TipoMensaje


class Autenticacion:
    def __init__(self, conexion):
        self.conexion = conexion

    def login(self, codigo, contrasena):
        req = {
            "tipo": TipoMensaje.LOGIN,
            "codigo": codigo,
            "contrasena": contrasena,
            "fechaHora": datetime.datetime.now().isoformat()
        }
        resp = self.conexion.pedir(req)
        if resp.get("tipo") == TipoMensaje.LOGIN_RESPUESTA:
            return resp
        raise Exception("Respuesta inesperada")


