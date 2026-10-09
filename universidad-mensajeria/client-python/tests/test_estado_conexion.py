"""PAT-03 en Python: el estado de conexión es observable y no se confunde
una caída de red con un cierre de sesión.

Réplica de `estadoDeConexionAvanzaConectandoConectadoY...` en Java: los estados
y el orden de las transiciones son el mismo contrato en los 3 clientes.
"""
import os
import tempfile
import time
import unittest

from fachada.fachada_cliente import FachadaCliente
from store.database import Database
from transversal import estado_conexion as ec

ESQUEMA = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    '..', '..', 'common-protocol', 'dto', 'src', 'main', 'resources',
    'schema-local.sql'))


class RedFalsaEstado:
    def __init__(self):
        self.conectado = True
        self.caida = False
        self.on_cierre = None
        self.on_desconexion = None

    def conectar(self):
        self.conectado = True

    def desconectar(self):
        self.conectado = False

    def pedir(self, msg, timeout=15):
        if self.caida or not self.conectado:
            raise ConnectionError("corte de red simulado")
        return {"tipo": "LOGIN_RESPUESTA", "id": msg.get("id"), "exito": True}

    def enviar_async(self, msg):
        pass

    def caer(self):
        self.conectado = False
        if self.on_desconexion:
            self.on_desconexion()

    def cerrar_sesion(self):
        self.conectado = False
        if self.on_cierre:
            self.on_cierre({"tipo": "CLOSE_NOTICE"})


class TestEstadoConexion(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        tmp.close()
        self.addCleanup(os.unlink, tmp.name)
        db = Database("sqlite:///" + tmp.name, esquema_path=ESQUEMA)
        self.red = RedFalsaEstado()
        self.f = FachadaCliente(db=db, conexion=self.red)
        self.addCleanup(self.f._gestor_reconexion.cerrar)
        # El cableado que hace la fachada cuando construye la ConexionRed; con una
        # inyectada hay que replicarlo, si no el doble no recibe nada.
        self.red.on_cierre = self.f._handle_cierre
        self.red.on_desconexion = self.f._handle_desconexion

    def test_entrega_el_estado_actual_al_suscribirse(self):
        # Quien se suscribe tarde no se queda sin pintar.
        vistos = []
        self.f.on_estado_ui = vistos.append
        self.f._cambiar_estado(ec.CONECTADO)
        assert vistos == [ec.CONECTADO]

    def test_conectar_pasa_por_conectando_y_termina_en_conectado(self):
        vistos = []
        self.f.on_estado_ui = vistos.append
        self.f._cambiar_estado(ec.CONECTADO)

        self.f.conectar()

        assert self.f.estado_conexion == ec.CONECTADO
        assert ec.CONECTANDO in vistos
        # El estado repetido no se reavisa.
        assert len([v for v in vistos if v == ec.CONECTADO]) == 2  # el inicial + el de conectar

    def test_caida_del_hilo_receptor_pasa_a_sin_red(self):
        self.f.conectar()
        self.f.login("A001", "clave")

        self.red.caer()

        assert self.f.estado_conexion == ec.SIN_RED

    def test_cierre_del_servidor_no_es_caida_de_red(self):
        # PAT-03: CLOSE_NOTICE es sesion cerrada. La UI no debe ofrecer
        # "reconectar" donde lo que hace falta es volver a entrar.
        self.f.conectar()
        self.f.login("A001", "clave")

        self.red.cerrar_sesion()

        assert self.f.estado_conexion == ec.SESION_CERRADA

    def test_reconecta_y_restaura_sesion_con_backoff(self):
        self.f.login("A001", "clave")
        estados = []
        self.f.on_estado_ui = estados.append

        self.red.caer()

        limite = time.monotonic() + 3.0
        while time.monotonic() < limite and self.f.estado_conexion != ec.CONECTADO:
            time.sleep(0.02)
        assert self.f.estado_conexion == ec.CONECTADO
        assert ec.SIN_RED in estados
        assert self.f.codigo_actual == "A001"

    def test_un_cierre_voluntario_no_se_reporta_como_caida(self):
        # El hilo receptor tambien muere al cerrar a proposito. Si eso degradara
        # el estado, el banner parpadearia tras un logout normal.
        self.f.conectar()
        self.f.login("A001", "clave")
        self.f.desconectar()
        tras_desconectar = self.f.estado_conexion

        self.red.caer()

        assert self.f.estado_conexion == tras_desconectar

    def test_los_estados_degradados_muestran_banner(self):
        assert not ec.muestra_banner(ec.CONECTADO)
        assert not ec.muestra_banner(ec.CONECTANDO)
        assert ec.muestra_banner(ec.SIN_RED)
        assert ec.muestra_banner(ec.SESION_CERRADA)

    def test_los_textos_coinciden_con_los_de_los_otros_clientes(self):
        # Contrato compartido (VIS-02): el mismo texto en los 3 clientes.
        assert ec.texto(ec.SIN_RED) == (
            "Sin conexión — mensajes en cola local, se reintentan solos")
        assert ec.texto(ec.CONECTADO) == "En línea"
        assert ec.texto(ec.CONECTANDO) == "Conectando…"
        assert ec.texto(ec.SESION_CERRADA) == "Sesión cerrada"


if __name__ == "__main__":
    unittest.main()
