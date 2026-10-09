"""Fase 1.2: flush periodico de la cola offline (paridad Java y .NET).

Sin el, si el cliente se queda sin red, vuelve la red y el usuario no toca
nada, `pendientes_envio` no se vacia: el mensaje se queda en "pendiente"
hasta que el usuario entre, cambie de cuenta o marque leidos.

Se prueba el ciclo de verdad (con periodos cortos inyectados en `_bucle_flush`)
porque lo que importa es que el hilo siga vivo aunque un reintento levante.
"""
import os
import tempfile
import threading
import time
import unittest

from fachada.fachada_cliente import (
    FachadaCliente,
    INTERVALO_FLUSH_MINIMO_SEGUNDOS,
    INTERVALO_FLUSH_SEGUNDOS,
)
from store.database import Database
from transversal.tipo_mensaje import TipoMensaje

ESQUEMA = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    '..', '..', 'common-protocol', 'dto', 'src', 'main', 'resources',
    'schema-local.sql'))

#: Periodo de los tests del ciclo. Va directo a `_bucle_flush` para saltarse el
#: suelo de 5 s, que protege al usuario y no al test.
PERIODO_TEST = 0.05


class RedFalsaFlush:
    """Guion minimo: ACK al reintento y corte de red conmutable."""

    def __init__(self):
        self.conectado = True
        self.caida = False

    def conectar(self):
        self.conectado = True

    def desconectar(self):
        self.conectado = False

    def pedir(self, msg, timeout=15):
        if self.caida or not self.conectado:
            raise ConnectionError("corte de red simulado")
        if msg.get('tipo') == TipoMensaje.LOGIN:
            return {"tipo": TipoMensaje.LOGIN_RESPUESTA,
                    "id": msg.get("id"), "exito": True}
        return {"tipo": TipoMensaje.ACK, "id": msg.get("id"), "exito": True}

    def enviar_async(self, msg):
        if self.caida or not self.conectado:
            raise ConnectionError("corte de red simulado")


class TestFlushPendientes(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        tmp.close()
        self.addCleanup(os.unlink, tmp.name)
        db = Database("sqlite:///" + tmp.name, esquema_path=ESQUEMA)
        self.red = RedFalsaFlush()
        self.f = FachadaCliente(db=db, conexion=self.red)
        self.addCleanup(self.f.detener_flush_pendientes)

    def _correr_flush(self, facade, periodo=PERIODO_TEST):
        """Arranca SOLO el ciclo con periodo corto; devuelve (hilo, Event)."""
        parar = threading.Event()
        hilo = threading.Thread(
            target=facade._bucle_flush, args=(periodo, parar),
            name="flush-test", daemon=True)
        hilo.start()
        self.addCleanup(parar.set)
        return hilo, parar

    @staticmethod
    def _esperar_hasta(condicion, segundos=3.0):
        limite = time.time() + segundos
        while time.time() < limite:
            if condicion():
                return True
            time.sleep(0.01)
        return False

    # ---------------------------------------------------------- arranque/parada
    def test_periodo_de_paridad_con_java_y_dotnet(self):
        # FachadaCliente.INTERVALO_FLUSH_SEGUNDOS / FachadaCliente.IntervaloFlushSegundos
        self.assertEqual(INTERVALO_FLUSH_SEGUNDOS, 30)
        self.assertEqual(INTERVALO_FLUSH_MINIMO_SEGUNDOS, 5)

    def test_login_correcto_arranca_el_flush(self):
        self.assertFalse(self.f.flush_activo())
        self.f.login("A001", "Secreta123")
        self.assertTrue(self.f.flush_activo())

    def test_login_rechazado_no_arranca_el_flush(self):
        self.red.caida = True
        with self.assertRaises(ConnectionError):
            self.f.login("A001", "Secreta123")
        self.assertFalse(self.f.flush_activo())

    def test_restauracion_de_sesion_tambien_lo_arranca(self):
        # `fijar_cuenta` es el gancho que usara el GestorReconexion de la
        # Fase 2: si la red vuelve sola, la cola tiene que vaciarse sola.
        self.f.fijar_cuenta("A001")
        self.assertTrue(self.f.flush_activo())

    def test_arrancar_dos_veces_no_crea_dos_hilos(self):
        self.f.login("A001", "Secreta123")
        primero = self.f._flush_hilo
        self.f.iniciar_flush_pendientes()
        self.f.iniciar_flush_pendientes(INTERVALO_FLUSH_SEGUNDOS)
        self.assertIs(self.f._flush_hilo, primero)

    def test_arrancar_tras_parar_sigue_siendo_un_solo_hilo(self):
        self.f.login("A001", "Secreta123")
        primero = self.f._flush_hilo
        self.f.detener_flush_pendientes()
        self.assertFalse(self.f.flush_activo())
        self.f.iniciar_flush_pendientes()
        self.assertIsNot(self.f._flush_hilo, primero)
        self.assertTrue(self.f.flush_activo())

    def test_desconectar_detiene_el_flush(self):
        self.f.login("A001", "Secreta123")
        self.assertTrue(self.f.flush_activo())
        self.f.desconectar()
        self.assertFalse(self.f.flush_activo())

    def test_parar_dos_veces_no_rompe(self):
        self.f.login("A001", "Secreta123")
        self.f.detener_flush_pendientes()
        self.f.detener_flush_pendientes()
        self.assertFalse(self.f.flush_activo())

    def test_un_periodo_menor_que_el_suelo_no_dispara_el_flush(self):
        # Math.max(5, periodo) en Java: 1 s convertiria el flush en un bucle.
        llamadas = []
        self.f.reintentar_pendientes = lambda: llamadas.append(1)
        self.f.iniciar_flush_pendientes(1)
        time.sleep(PERIODO_TEST * 12)
        self.assertEqual(llamadas, [])

    # --------------------------------------------------------------- el ciclo
    def test_el_ciclo_con_la_cola_vacia_no_rompe_nada(self):
        self.f.login("A001", "Secreta123")
        self.assertEqual(self.f.pendientes(), [])
        ciclo, _ = self._correr_flush(self.f)
        self.assertTrue(ciclo.is_alive())
        time.sleep(PERIODO_TEST * 6)
        self.assertTrue(ciclo.is_alive())
        self.assertEqual(self.f.pendientes(), [])

    def test_un_fallo_en_el_flush_no_tumba_el_hilo(self):
        intentos = []

        def revienta():
            intentos.append(1)
            raise RuntimeError("rechazo del servidor")

        self.f.login("A001", "Secreta123")
        self.f.reintentar_pendientes = revienta
        ciclo, _ = self._correr_flush(self.f)
        # Con 1 sola exception el hilo moria: por eso se exigen 3 intentos.
        self.assertTrue(self._esperar_hasta(lambda: len(intentos) >= 3))
        self.assertTrue(ciclo.is_alive())

    def test_el_flush_vacia_la_cola_al_volver_la_red(self):
        self.f.login("A001", "Secreta123")
        self.red.caida = True
        self.assertFalse(self.f.enviar_texto("B002", "sale solo"))
        self.assertEqual(len(self.f.pendientes()), 1)
        # Vuelve la red y NADIE toca nada: solo esta el flush periodico.
        self.red.caida = False
        ciclo, parar = self._correr_flush(self.f)
        self.assertTrue(self._esperar_hasta(lambda: self.f.pendientes() == []))
        parar.set()
        ciclo.join(timeout=2)
        self.assertFalse(ciclo.is_alive())