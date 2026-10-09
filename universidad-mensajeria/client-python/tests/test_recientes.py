"""Conversaciones recientes: agregación pura y consulta SQL local."""
import os
import tempfile
import unittest

from componentes.historial_local import HistorialLocal
from fachada.servicios.constructor_recientes import ConstructorRecientes
from puertos.datos import RecienteChat, UsuarioLocal
from store.database import Database

ESQUEMA = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "common-protocol", "dto", "src", "main", "resources",
    "schema-local.sql"))


class TestConstructorRecientes(unittest.TestCase):
    def test_combina_presencia_orden_historial_y_contactos_vacios(self):
        base = [
            RecienteChat("B002", "antiguo", "MENSAJE_TEXTO", "2026-01-01", 2, False),
            RecienteChat("C003", "nuevo", "MENSAJE_TEXTO", "2026-02-01", 0, False),
            RecienteChat("D004", "medio", "MENSAJE_TEXTO", "2026-01-15", 1, False),
        ]
        directorio = [
            UsuarioLocal("A001", "Ana", "Uno", "", False, None, None),
            UsuarioLocal("C003", "Carla", "Tres", "", True, None, None),
            UsuarioLocal("E005", "Eva", "Cinco", "", True, None, None),
            UsuarioLocal("B002", "Beto", "Dos", "", False, None, None),
            UsuarioLocal("D004", "Dora", "Cuatro", "", False, None, None),
        ]

        recientes = ConstructorRecientes.armar(base, directorio, "A001")

        self.assertEqual([r.otro for r in recientes],
                         ["C003", "D004", "B002", "E005"])
        self.assertTrue(recientes[0].conectado)
        self.assertEqual(recientes[0].ultimo_contenido, "nuevo")
        self.assertEqual(recientes[-1].no_leidos, 0)

    def test_la_fecha_manda_sobre_la_presencia(self):
        # Orden WhatsApp: el más nuevo arriba AUNQUE esté desconectado; la
        # presencia pinta el punto pero no mueve la fila bajo el dedo.
        base = [
            RecienteChat("C003", "viejo conectado", "MENSAJE_TEXTO",
                         "2026-01-01", 0, False),
            RecienteChat("B002", "nuevo desconectado", "MENSAJE_TEXTO",
                         "2026-02-01", 1, False),
        ]
        directorio = [
            UsuarioLocal("A001", "Ana", "Uno", "", False, None, None),
            UsuarioLocal("C003", "Carla", "Tres", "", True, None, None),
            UsuarioLocal("B002", "Beto", "Dos", "", False, None, None),
            UsuarioLocal("Z009", "Zoe", "Nueve", "", True, None, None),
            UsuarioLocal("M008", "Mia", "Ocho", "", False, None, None),
        ]

        recientes = ConstructorRecientes.armar(base, directorio, "A001")

        self.assertEqual([r.otro for r in recientes],
                         ["B002", "C003", "Z009", "M008"])
        self.assertFalse(recientes[0].conectado)
        # Sin conversación: al final, alfabéticos ("Nueve" antes que "Ocho").


class TestConsultaRecientes(unittest.TestCase):
    def setUp(self):
        temporal = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        temporal.close()
        self.addCleanup(os.unlink, temporal.name)
        db = Database("sqlite:///" + temporal.name, esquema_path=ESQUEMA)
        self.historial = HistorialLocal(db)

    def test_elige_ultimo_mensaje_y_cuenta_no_leidos_por_contacto(self):
        self.historial.guardar_mensaje(
            "m1", "B002", "A001", "MENSAJE_TEXTO", "recibido viejo",
            "2026-01-01T10:00:00", estado="ENTREGADO")
        self.historial.guardar_mensaje(
            "m2", "A001", "B002", "MENSAJE_TEXTO", "enviado reciente",
            "2026-01-02T10:00:00", estado="ENVIADO")
        self.historial.guardar_mensaje(
            "m3", "B002", "A001", "MENSAJE_TEXTO", "recibido nuevo",
            "2026-01-03T10:00:00", estado="PENDIENTE")
        self.historial.guardar_mensaje(
            "bc", "A001", "*", "BROADCAST", "aviso propio",
            "2026-01-04T10:00:00")

        recientes = self.historial.recientes("A001")

        self.assertEqual(len(recientes), 1)
        self.assertEqual(recientes[0].otro, "B002")
        self.assertEqual(recientes[0].ultimo_contenido, "recibido nuevo")
        self.assertEqual(recientes[0].no_leidos, 2)
        self.assertFalse(recientes[0].conectado)

    def test_cache_usuarios_en_lote_deja_el_directorio(self):
        self.historial.actualizar_cache_usuarios([
            ("B002", "Bea", "Dos", "", 1, "", "2026-10-08T10:00:00"),
            ("C003", "Ce", "Tres", "", 0, "", "2026-10-08T10:00:00"),
        ])
        self.historial.actualizar_cache_usuarios([
            ("B002", "Bea", "Dos", "Sistemas", 0, "", "2026-10-08T11:00:00"),
        ])

        cache = {u["codigo"]: u for u in self.historial.listar_cache_usuarios()}

        self.assertEqual({"B002", "C003"}, set(cache))
        self.assertEqual("Sistemas", cache["B002"]["programa"])
        self.assertEqual(0, cache["B002"]["conectado"])
        self.assertEqual(0, cache["C003"]["conectado"])


if __name__ == "__main__":
    unittest.main()
