"""Sincroniza directorio y presencia con la cache local por cuenta."""
import datetime
import json

from fachada.servicios.constructor_recientes import ConstructorRecientes
from puertos.datos import UsuarioLocal
from transversal.tipo_mensaje import TipoMensaje


class SincronizadorDirectorioCliente:
    def __init__(self, conexion, historial_actual, codigo_actual, exigir_sesion):
        self.conexion = conexion
        self._historial_actual = historial_actual
        self._codigo_actual = codigo_actual
        self._exigir_sesion = exigir_sesion

    @property
    def historial(self):
        return self._historial_actual()

    @property
    def codigo_actual(self):
        return self._codigo_actual()

    def listar_conectados(self):
            self._exigir_sesion()
            req = {
                "tipo": TipoMensaje.LISTAR_CONECTADOS,
                "codigo": self.codigo_actual,
                "fechaHora": datetime.datetime.now().isoformat()
            }
            resp = self.conexion.pedir(req)
            conectados = resp.get("usuariosConectados") or []
            self.historial.marcar_conectados(conectados)
            for codigo in conectados:
                self._asegurar_en_cache(codigo)
            return conectados

    def listar_usuarios(self):
            """Directorio completo desde LISTAR_USUARIOS_RESPUESTA.contenido (JSON).

            El servidor envia el arreglo en `contenido`, no en `usuarios`
            (ListarCaso.java, igual que lee FachadaCliente Java). Si la red
            falla o el contenido es ilegible/vacio se conserva la cache local.
            """
            self._exigir_sesion()
            resp = self.conexion.pedir({
                "tipo": TipoMensaje.LISTAR_USUARIOS,
                "codigo": self.codigo_actual,
                "fechaHora": datetime.datetime.now().isoformat()
            })
            contenido = resp.get("contenido")
            if not contenido or not str(contenido).strip():
                raise RuntimeError("directorio de usuarios no disponible")
            try:
                filas = json.loads(contenido)
            except (TypeError, ValueError) as error:
                raise RuntimeError("directorio de usuarios ilegible") from error
            if not isinstance(filas, list):
                raise RuntimeError("directorio de usuarios ilegible")
            ahora = datetime.datetime.now().isoformat()
            usuarios = []
            for usuario in filas:
                if not isinstance(usuario, dict):
                    continue
                codigo = usuario.get("codigo")
                if not codigo or not str(codigo).strip():
                    continue
                usuarios.append({
                    "codigo": codigo,
                    "nombres": usuario.get("nombres") or "",
                    "apellidos": usuario.get("apellidos") or "",
                    "programa": usuario.get("programa") or "",
                    "conectado": bool(usuario.get("conectado")),
                })
            if not usuarios:
                raise RuntimeError("directorio incompleto; se conserva la caché local")
            self.historial.actualizar_cache_usuarios([
                (usuario["codigo"], usuario["nombres"], usuario["apellidos"],
                 usuario["programa"], int(usuario["conectado"]), "", ahora)
                for usuario in usuarios])
            self.historial.marcar_conectados(
                [u["codigo"] for u in usuarios if u["conectado"]])
            return self.historial.listar_cache_usuarios()

    def directorio_local(self):
            return self.historial.listar_cache_usuarios()

    def recientes(self):
        self._exigir_sesion()
        directorio = [UsuarioLocal(
            usuario["codigo"], usuario.get("nombres", ""),
            usuario.get("apellidos", ""), usuario.get("programa", ""),
            bool(usuario.get("conectado")), usuario.get("fecha_registro"),
            usuario.get("actualizado"))
            for usuario in self.historial.listar_cache_usuarios()]
        return ConstructorRecientes.armar(
            self.historial.recientes(self.codigo_actual), directorio,
            self.codigo_actual)

    def _asegurar_en_cache(self, codigo):
            ahora = datetime.datetime.now().isoformat()
            existentes = {u["codigo"] for u in self.historial.listar_cache_usuarios()}
            if codigo not in existentes:
                self.historial.actualizar_cache_usuario(codigo, codigo, "", "", 0, ahora, ahora)
