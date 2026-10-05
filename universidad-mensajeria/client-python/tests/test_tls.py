"""SEG-01: TLS opcional del cliente Python (paridad Java/.NET, FASE 11.2).

- validar_tls: fail-fast ANTES de abrir sockets (colision, almacen ausente).
- Handshake real contra un servidor TLS en-proceso con CA propia: verifica
  cadena + hostname y que una CA ajena NO valida (sin downgrade silencioso).

`cryptography` solo existe como tooling de tests (igual que pytest); si no
esta, los tests de handshake se omiten pero los de fail-fast corren igual.
"""
import os
import socket
import ssl
import tempfile
import threading
import unittest
from datetime import datetime, timedelta, timezone

from componentes.conexion_red import ConexionRed
from transversal.tls import TLS_PUERTO_DEFECTO, crear_contexto, validar_tls

try:
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID
    _CRYPTO = True
except ImportError:
    _CRYPTO = False


class TestValidarTls(unittest.TestCase):
    def test_apagado_no_valida_nada(self):
        validar_tls(False, 5000, 5000, "/no/existe.pem")
        validar_tls("false", 5000, 5000, "/no/existe.pem")

    def test_colision_puertos_fijos(self):
        with self.assertRaisesRegex(ValueError, "comparten el puerto 5000"):
            validar_tls(True, 5000, 5000, "")
        with self.assertRaisesRegex(ValueError, "tls.puerto"):
            validar_tls("true", 5000, "5000", "")

    def test_puerto_efimero_no_colisiona(self):
        validar_tls(True, 5000, 0, "")
        validar_tls(True, 0, 0, "")

    def test_almacen_ausente(self):
        with self.assertRaisesRegex(ValueError, "almacen de confianza no existe"):
            validar_tls(True, 5000, 5001, "/no/existe-ca.pem")

    def test_puerto_basura_usa_defecto(self):
        validar_tls(True, 5000, "basura", "")

    def test_fail_fast_antes_de_conectar(self):
        red = ConexionRed("127.0.0.1", 5000, lambda m: None,
                          tls_habilitado=True, tls_puerto=5000, tls_confianza="")
        with self.assertRaisesRegex(ValueError, "comparten el puerto"):
            red.conectar()
        self.assertIsNone(red.sock)
        self.assertFalse(red.conectado)


def _emitir_ca(nombre):
    clave = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    sujeto = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, nombre)])
    cert = (x509.CertificateBuilder()
            .subject_name(sujeto).issuer_name(sujeto)
            .public_key(clave.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.now(timezone.utc) - timedelta(days=1))
            .not_valid_after(datetime.now(timezone.utc) + timedelta(days=1))
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), True)
            .sign(clave, hashes.SHA256()))
    return clave, cert


def _emitir_servidor(ca_clave, ca_cert):
    clave = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    cert = (x509.CertificateBuilder()
            .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")]))
            .issuer_name(ca_cert.subject)
            .public_key(clave.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.now(timezone.utc) - timedelta(days=1))
            .not_valid_after(datetime.now(timezone.utc) + timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName(
                [x509.DNSName("localhost"), x509.IPAddress(__import__("ipaddress").IPv4Address("127.0.0.1"))]), False)
            .sign(ca_clave, hashes.SHA256()))
    return clave, cert


def _pem(obj, es_clave):
    if es_clave:
        return obj.private_bytes(serialization.Encoding.PEM,
                                 serialization.PrivateFormat.TraditionalOpenSSL,
                                 serialization.NoEncryption())
    return obj.public_bytes(serialization.Encoding.PEM)


@unittest.skipUnless(_CRYPTO, "cryptography no instalado (tooling de tests)")
class TestHandshakeTls(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        ca_clave, ca_cert = _emitir_ca("CA-pruebas-mensajeria")
        srv_clave, srv_cert = _emitir_servidor(ca_clave, ca_cert)
        self.ca_path = os.path.join(self.tmp.name, "ca.pem")
        self.srv_crt = os.path.join(self.tmp.name, "srv.crt")
        self.srv_key = os.path.join(self.tmp.name, "srv.key")
        for ruta, datos in ((self.ca_path, _pem(ca_cert, False)),
                            (self.srv_crt, _pem(srv_cert, False)),
                            (self.srv_key, _pem(srv_clave, True))):
            with open(ruta, "wb") as f:
                f.write(datos)
        # Servidor TLS en-proceso: handshake y espera (el framing real lo
        # cubre el E2E; aqui se prueba cadena + hostname del cliente).
        self.escucha = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.escucha.bind(("127.0.0.1", 0))
        self.escucha.listen(1)
        self.puerto = self.escucha.getsockname()[1]
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(self.srv_crt, self.srv_key)
        self.ctx = ctx
        self.aceptados = []
        self.hilo = threading.Thread(target=self._servir, daemon=True)
        self.hilo.start()

    def _servir(self):
        try:
            crudo, _ = self.escucha.accept()
            with self.ctx.wrap_socket(crudo, server_side=True) as tls:
                self.aceptados.append(tls.version())
                tls.recv(1)
        except Exception:
            pass
        finally:
            try:
                self.escucha.close()
            except OSError:
                pass

    def tearDown(self):
        try:
            self.escucha.close()
        except OSError:
            pass

    def test_handshake_valida_cadena_y_hostname(self):
        # Via ConexionRed completa: lo determinista es que conectar() NO
        # eleve (con CERT_REQUIRED + hostname, una cadena/host invalido
        # eleva siempre: ver test_ca_ajena) y deje un SSLSocket.
        red = ConexionRed("127.0.0.1", 5000, lambda m: None,
                          tls_habilitado=True, tls_puerto=self.puerto,
                          tls_confianza=self.ca_path)
        try:
            red.conectar()
            self.assertIsInstance(red.sock, ssl.SSLSocket)
        finally:
            red.desconectar()
        self.hilo.join(timeout=10)
        self.assertEqual(1, len(self.aceptados))

    def test_contexto_negocia_tls12_mas_sin_hilos(self):
        # Handshake puro (sin recv concurrente): version/cipher/cert estables.
        from transversal.tls import crear_contexto
        crudo = socket.create_connection(("127.0.0.1", self.puerto))
        try:
            with crear_contexto(self.ca_path).wrap_socket(
                    crudo, server_hostname="127.0.0.1") as tls:
                version = tls.version() or ""
                self.assertTrue(version.startswith("TLSv1.2")
                                or version.startswith("TLSv1.3"), version)
                suite = (tls.cipher() or [None])[0] or ""
                self.assertTrue(suite.startswith("TLS_"), suite)
                self.assertIsNotNone(tls.getpeercert())
        finally:
            try:
                crudo.close()
            except OSError:
                pass

    def test_ca_ajena_no_valida_sin_downgrade(self):
        otra_clave, otra_cert = _emitir_ca("CA-ajena")
        otra_path = os.path.join(self.tmp.name, "otra-ca.pem")
        with open(otra_path, "wb") as f:
            f.write(_pem(otra_cert, False))
        red = ConexionRed("127.0.0.1", 5000, lambda m: None,
                          tls_habilitado=True, tls_puerto=self.puerto,
                          tls_confianza=otra_path)
        with self.assertRaisesRegex(ConnectionError, "certificado no valido"):
            red.conectar()
        self.assertIsNone(red.sock)

    def test_contexto_exige_tls12_y_hostname(self):
        ctx = crear_contexto(self.ca_path)
        self.assertTrue(ctx.check_hostname)
        self.assertEqual(ctx.verify_mode, ssl.CERT_REQUIRED)
        if hasattr(ssl, "TLSVersion"):
            self.assertGreaterEqual(ctx.minimum_version, ssl.TLSVersion.TLSv1_2)

    def test_fachada_cablea_tls_desde_config(self):
        from store.database import Database
        from fachada.fachada_cliente import FachadaCliente
        from transversal.config import Config
        props = os.path.join(self.tmp.name, "client.properties")
        with open(props, "w", encoding="utf-8") as f:
            f.write("host=127.0.0.1\npuerto=5000\ndb.url=sqlite:///:memory:\n"
                    "tls.habilitado=true\ntls.puerto=5443\ntls.confianza=/tmp/ca.pem\n")
        tmpdb = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        tmpdb.close()
        self.addCleanup(os.unlink, tmpdb.name)
        esquema = os.path.normpath(os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "..", "..", "common-protocol", "dto", "src", "main", "resources",
            "schema-local.sql"))
        db = Database("sqlite:///" + tmpdb.name, esquema_path=esquema)
        f = FachadaCliente(config=Config(props), db=db)
        self.assertTrue(f.conexion.tls_habilitado)
        self.assertEqual(5443, f.conexion.tls_puerto)
        self.assertEqual("/tmp/ca.pem", f.conexion.tls_confianza)


if __name__ == "__main__":
    unittest.main()
