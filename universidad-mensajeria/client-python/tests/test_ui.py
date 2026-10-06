import base64
import os
import tempfile
import time
from pathlib import Path
import threading
import unittest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtCore import QBuffer, QCoreApplication, QEvent, QEventLoop, QIODevice, QTimer, Qt
from PySide6.QtGui import QImage
from PySide6.QtWidgets import QApplication

from ui.chat_window import ChatWindow
from ui.lista_virtual import ROL_ITEM
from ui.login_window import LoginWindow
from ui.tema import tokens


class FachadaFalsa:
    def __init__(self):
        self.on_mensaje_recibido_ui = None
        self.on_cierre_ui = None
        self.on_presencia_ui = None
        self.on_desconexion_ui = None
        self.enviados = []

    def login(self, codigo, contrasena):
        return {"exito": codigo == "A001" and contrasena == "clave"}

    def listar_conectados(self):
        return ["A001", "B002"]

    def directorio_local(self):
        return [{"codigo": "B002"}]

    def obtener_conversacion(self, usuario):
        return []

    def enviar_texto(self, usuario, texto):
        self.enviados.append(("texto", usuario, texto))

    def enviar_imagen(self, usuario, ruta):
        self.enviados.append(("imagen", usuario, ruta))

    def difundir(self, texto):
        self.enviados.append(("difusion", texto))

    def desconectar(self):
        pass


class PruebasUI(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self.fachada = FachadaFalsa()
        self.login = LoginWindow(self.fachada)
        self.chat = ChatWindow(self.fachada, "A001", lambda: None)
        temp_images = tempfile.TemporaryDirectory()
        self.addCleanup(temp_images.cleanup)
        self.chat._directorio_imagenes = Path(temp_images.name)
        self._wait()

    def _wait(self, ms=350):
        loop = QEventLoop()
        QTimer.singleShot(ms, loop.quit)
        loop.exec()

    def tearDown(self):
        self.chat._cerrando = True
        self.chat.close()
        self.login.close()
        self.chat.deleteLater()
        self.login.deleteLater()
        QCoreApplication.sendPostedEvents(None, QEvent.Type.DeferredDelete)
        self.app.processEvents()

    def test_login_emite_codigo_solo_si_autentica(self):
        codigos = []
        self.login.login_succeeded.connect(codigos.append)
        self.login.entry_codigo.setText("A001")
        self.login.entry_password.setText("clave")
        self.login.login()
        # UIX-00e: login() ya no corre en el hilo de la GUI (bloqueaba hasta
        # 15 s), asi que el resultado llega por el hilo de fondo. Se espera a que
        # termine en vez de suponerlo sincrono.
        limite = time.time() + 5
        while not codigos and time.time() < limite:
            self._wait(50)
        self.assertEqual(["A001"], codigos)

    def test_chat_envia_a_usuario_seleccionado(self):
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat.entry_msg.setText("Hola")
        self.chat.enviar()
        self._wait()
        self.assertEqual([("texto", "B002", "Hola")], self.fachada.enviados)

    def test_evento_del_hilo_de_red_se_aplica_en_la_vista(self):
        self.chat.lista_usuarios.setCurrentRow(0)
        mensaje = {
            "tipo": "MENSAJE_TEXTO",
            "remitente": "B002",
            "fechaHora": "2026-10-01T10:00:00",
            "contenido": "recibido",
        }
        hilo = threading.Thread(target=lambda: self.fachada.on_mensaje_recibido_ui(mensaje))
        hilo.start()
        loop = QEventLoop()
        QTimer.singleShot(250, loop.quit)
        loop.exec()
        hilo.join()
        self._wait()
        modelo = self.chat._modelo_mensajes
        contenidos = [modelo.data(modelo.index(i), ROL_ITEM).get("text", "")
                      for i in range(modelo.rowCount())]
        self.assertTrue(any("recibido" in texto for texto in contenidos))

    def test_mensaje_imagen_inserta_preview_en_historial(self):
        imagen = QImage(2, 2, QImage.Format.Format_ARGB32)
        imagen.fill(Qt.GlobalColor.darkGreen)
        buffer = QBuffer()
        buffer.open(QIODevice.OpenModeFlag.WriteOnly)
        imagen.save(buffer, "PNG")
        contenido = base64.b64encode(bytes(buffer.data())).decode("ascii")

        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat.show()
        self.fachada.on_mensaje_recibido_ui({
            "tipo": "MENSAJE_IMAGEN", "id": "imagen-ui", "remitente": "B002",
            "fechaHora": "2026-10-01T10:00:00", "nombreArchivo": "preview.png",
            "contenidoImagen": contenido,
        })
        self._wait(500)
        mensaje = self.chat._mensaje_por_id("imagen-ui")
        self.assertIsNotNone(mensaje)
        pixmap = self.chat._delegado_mensajes.pixmap_de("imagen-ui")
        self.assertIsNotNone(pixmap)
        self.assertFalse(pixmap.isNull())

    def test_historial_repetido_no_duplica_burbujas(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._al_seleccionar_usuario(self.chat.lista_usuarios.currentItem())
        filas = [{"id_mensaje": "m1", "origen": "B002", "destino": "A001",
                  "tipo": "MENSAJE_TEXTO", "contenido": "hola",
                  "fecha_envio": "2026-10-01T10:00:00", "estado": "ENTREGADO"}]
        self.chat._pintar_historial(filas)
        self.chat._pintar_historial(filas)
        # UIX-04: 1 burbuja + su cabecera de día (no 2 burbujas).
        modelo = self.chat._modelo_mensajes
        self.assertEqual(2, modelo.rowCount())
        burbujas = [modelo.data(modelo.index(i), ROL_ITEM)
                    for i in range(modelo.rowCount())]
        self.assertEqual(1, sum(1 for b in burbujas if isinstance(b, dict) and b.get("id") == "m1"))

    def test_historial_viejo_se_descarta(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._al_seleccionar_usuario(self.chat.lista_usuarios.currentItem())
        gen_viejo = self.chat._gen_historial - 1
        filas = [{"id_mensaje": "m9", "origen": "B002", "destino": "A001",
                  "tipo": "MENSAJE_TEXTO", "contenido": "viejo",
                  "fecha_envio": "2026-10-01T10:00:00", "estado": "ENTREGADO"}]
        self.chat._procesar_tarea("historial", ("B002", filas, 0, 1, gen_viejo), None)
        self.assertEqual(0, self.chat._modelo_mensajes.rowCount())

    def _layout_de(self, mensaje_id):
        """Layout que pintaría el delegado para ese mensaje (UIX-01)."""
        mensaje = self.chat._mensaje_por_id(mensaje_id)
        self.assertIsNotNone(mensaje)
        return self.chat._delegado_mensajes.distribuir(
            mensaje, 600, tokens(self.chat.modo))

    def test_descarga_visible_en_imagen_sin_archivo_id(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._al_seleccionar_usuario(self.chat.lista_usuarios.currentItem())
        fila = {"id_mensaje": "img1", "origen": "B002", "destino": "A001",
                "tipo": "MENSAJE_IMAGEN", "nombre_archivo": "foto.png",
                "fecha_envio": "2026-10-01T10:00:00", "estado": "ENTREGADO"}
        self.chat._pintar_historial([fila])
        # La imagen siempre ofrece descarga (aunque aún no tenga archivo_id).
        self.assertIn("descarga", self._layout_de("img1"))

    def test_boton_descargar_aparece_al_llegar_archivo_id(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._al_seleccionar_usuario(self.chat.lista_usuarios.currentItem())
        base = {"id_mensaje": "a1", "origen": "B002", "destino": "A001",
                "tipo": "MENSAJE_ARCHIVO", "nombre_archivo": "doc.pdf",
                "fecha_envio": "2026-10-01T10:00:00", "estado": "ENTREGADO"}
        self.chat._pintar_historial([dict(base)])
        # Sin archivo_id el adjunto aún no es descargable.
        self.assertNotIn("descarga", self._layout_de("a1"))
        con_id = dict(base, archivo_id="777")
        self.chat._pintar_historial([con_id])
        self.assertIn("descarga", self._layout_de("a1"))

    def test_esc_cancela_cita_sin_borrar_texto(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._responder({"sender": "Luis García [B002]", "text": "hola", "is_image": False})
        self.chat.entry_msg.setText("mi respuesta")
        self.chat._cancelar_composicion()
        self.assertEqual("", self.chat._cita_actual())
        self.assertFalse(self.chat.cita_container.isVisible())
        self.assertEqual("mi respuesta", self.chat.entry_msg.texto())

    def test_responder_muestra_tarjeta_sin_inyectar_texto(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._responder({"sender": "Luis García [B002]", "text": "hola", "is_image": False})
        self.assertEqual("", self.chat.entry_msg.texto())
        self.assertTrue(self.chat.cita_container.isVisible())
        self.assertIn('Respuesta a Luis García [B002]: "hola"', self.chat.lbl_respuesta.text())
        self.chat._cancelar_respuesta()
        self.assertFalse(self.chat.cita_container.isVisible())
        self.assertEqual("", self.chat._cita_actual())

    def test_alineacion_burbujas_propia_y_recibida(self):
        self.chat.lista_usuarios.setCurrentRow(0)
        # Mensaje propio
        self.chat._agregar_fila({
            "id": "propio-1", "sender": "Tú", "is_own": True,
            "text": "mensaje mío", "state": "⏳ Pendiente", "raw_state": "PENDIENTE",
            "is_image": False, "date": "2026-10-01 10:00:00"
        })
        # Mensaje recibido
        self.chat._agregar_fila({
            "id": "recibido-1", "sender": "B002", "is_own": False,
            "text": "mensaje de B", "state": "", "raw_state": "",
            "is_image": False, "date": "2026-10-01 10:00:01"
        })
        propio = self.chat._mensaje_por_id("propio-1")
        recibido = self.chat._mensaje_por_id("recibido-1")

        self.assertTrue(propio["is_own"])
        self.assertFalse(recibido["is_own"])
        # Alineación: la propia va a la derecha, la ajena a la izquierda.
        self.assertTrue(self._layout_de("propio-1")["propia"])
        self.assertFalse(self._layout_de("recibido-1")["propia"])

    def test_estados_legibles_y_visto_azul(self):
        self.assertEqual(ChatWindow._estado_visible("PENDIENTE"), "⏳ Pendiente")
        self.assertEqual(ChatWindow._estado_visible("ENVIADO"), "✓ Enviado")
        self.assertEqual(ChatWindow._estado_visible("ENTREGADO"), "✓✓ Entregado")
        self.assertEqual(ChatWindow._estado_visible("LEIDO"), "✓✓ Leído")

        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat._agregar_fila({
            "id": "estado-test", "sender": "Tú", "is_own": True,
            "text": "verificando color", "state": "✓✓ Leído", "raw_state": "LEIDO",
            "is_image": False, "date": "2026-10-01 10:00:00"
        })
        mensaje = self.chat._mensaje_por_id("estado-test")
        self.assertEqual("✓✓ Leído", mensaje["state"])
        # El visto azul vive en los tokens que usa el delegado al pintar.
        from ui.tema import MODO_OSCURO as _OSCURO
        self.assertEqual("#5EB1FF", tokens(_OSCURO)["visto_leido"])

    def test_adjunto_preview_y_quitar(self):
        self.chat.show()
        self.chat.lista_usuarios.setCurrentRow(0)
        imagen = QImage(16, 16, QImage.Format.Format_ARGB32)
        imagen.fill(Qt.GlobalColor.blue)
        tmp = tempfile.NamedTemporaryFile(suffix=".png", delete=False)
        tmp.close()
        self.addCleanup(os.unlink, tmp.name)
        imagen.save(tmp.name, "PNG")

        self.chat._fijar_imagen_adjunta(tmp.name)
        self.assertEqual(self.chat.imagen_pendiente, str(Path(tmp.name).resolve()))
        self.assertFalse(self.chat.preview_container.isHidden())
        self.assertFalse(self.chat.lbl_preview_thumb.pixmap().isNull())
        self.assertIn(Path(tmp.name).name, self.chat.lbl_adjunto.text())

        # Quitar adjunto
        self.chat._quitar_adjunto()
        self.assertIsNone(self.chat.imagen_pendiente)
        self.assertTrue(self.chat.preview_container.isHidden())

    def test_webp_viaja_como_archivo_paridad_servidor(self):
        # SEG-02: la tuberia del servidor (ImageIO) solo decodifica
        # PNG/JPEG/GIF/BMP; .webp se ofrece en el dialogo pero viaja como
        # MENSAJE_ARCHIVO, igual que en Java (.NET). Ver
        # ImagenMagicaTest.rechazaWebpSinSoporteEnTuberia.
        from ui.chat_window import ChatWindow
        self.assertEqual(
            ChatWindow._EXTENSIONES_IMAGEN, {".png", ".jpg", ".jpeg", ".gif", ".bmp"})
        self.chat.lista_usuarios.setCurrentRow(0)
        tmp = tempfile.NamedTemporaryFile(suffix=".webp", delete=False)
        tmp.write(b"RIFF....WEBP-falso")
        tmp.close()
        self.addCleanup(os.unlink, tmp.name)
        self.chat._fijar_imagen_adjunta(tmp.name)
        # Icono generico de archivo, sin preview de imagen.
        self.assertTrue(self.chat.lbl_preview_thumb.pixmap().isNull())
        self.chat._quitar_adjunto()

    def test_envio_optimista_inmediato(self):
        self.chat.lista_usuarios.setCurrentRow(0)
        self.chat.entry_msg.setText("Texto optimista")
        self.chat.enviar()

        # Inmediatamente en la lista debe haber aparecido el mensaje con estado PENDIENTE
        modelo = self.chat._modelo_mensajes
        self.assertGreater(modelo.rowCount(), 0)
        data = modelo.data(modelo.index(modelo.rowCount() - 1), ROL_ITEM)
        self.assertEqual(data["text"], "Texto optimista")
        self.assertEqual(data["raw_state"], "PENDIENTE")
        self.assertEqual(data["state"], "⏳ Pendiente")
        self.assertTrue(data["is_own"])
        # Y el composer debe haberse limpiado de inmediato
        self.assertEqual(self.chat.entry_msg.texto(), "")

    def test_modo_oscuro_por_defecto_y_conmuta(self):
        from ui.tema import MODO_CLARO, MODO_OSCURO
        self.assertEqual(MODO_OSCURO, self.chat.modo)
        self.chat.aplicar_tema(MODO_CLARO)
        self.assertEqual(MODO_CLARO, self.chat.modo)
        self.chat._agregar_fila({
            "id": "tema-1", "sender": "Tú", "is_own": True,
            "text": "hola", "state": "✓✓ Leído", "raw_state": "LEIDO",
            "is_image": False, "date": "2026-10-01 10:00:00"
        })
        # El modo vive en el delegado (pinta todas las filas a la vez).
        self.assertEqual(MODO_CLARO, self.chat._delegado_mensajes._modo)
        self.assertEqual("#E1F5FE", tokens(MODO_CLARO)["burbuja_propia"])
        self.chat.aplicar_tema(MODO_OSCURO)
        self.assertEqual(MODO_OSCURO, self.chat._delegado_mensajes._modo)
        self.assertEqual("#7B61B8", tokens(MODO_OSCURO)["burbuja_propia"])

    def test_composer_con_mas_a_la_izquierda_y_salir_en_sidebar(self):
        fila = self.chat._fila_envio
        self.assertIs(fila.itemAt(0).widget(), self.chat.btn_imagen)
        self.assertIs(fila.itemAt(1).widget(), self.chat.entry_msg)
        self.assertIs(fila.itemAt(2).widget(), self.chat.btn_enviar)
        lateral = self.chat.btn_salir.parentWidget()
        self.assertIsNotNone(lateral)
        self.assertNotEqual(lateral, self.chat.btn_imagen.parentWidget())

    def test_login_sin_textos_viejos_y_puerto_solo_lectura(self):
        self.assertEqual("Iniciar sesión", self.login.subtitle.text())
        self.assertFalse(hasattr(self.login, "lbl_servidor"))
        self.assertFalse(self.login.entry_puerto.isEnabled())
        self.assertEqual("＋", self.chat.btn_imagen.text())

    def _directorio_dos(self):
        return [
            {"codigo": "B002", "nombres": "Bea", "apellidos": "Dos", "conectado": 0},
            {"codigo": "C003", "nombres": "Ce", "apellidos": "Tres", "conectado": 0},
        ]

    def test_directorio_obsoleto_no_roba_seleccion(self):
        # Auditoría: un refresh async que termina tarde no pisa la viva.
        self.chat._gen_directorio = 5
        self.chat.usuario_seleccionado = "C003"
        antes = self.chat.lista_usuarios.count()
        self.chat._procesar_tarea("directorio", (4, self._directorio_dos(), {}, None), None)
        self.assertEqual("C003", self.chat.usuario_seleccionado)
        self.assertEqual(antes, self.chat.lista_usuarios.count())
        self.chat._procesar_tarea("directorio", (5, self._directorio_dos(), {}, None), None)
        self.assertEqual(2, self.chat.lista_usuarios.count())

    def test_seleccionar_con_no_leidos_no_reconstruye(self):
        # Auditoría: limpiar la insignia no rebuild de 1000+ filas.
        self.chat._procesar_tarea(
            "directorio", (self.chat._gen_directorio, self._directorio_dos(), {"B002": 3}, None), None)
        self.chat.lista_usuarios.setCurrentRow(0)
        self.assertEqual("B002", self.chat.usuario_seleccionado)
        self.assertEqual(2, self.chat.lista_usuarios.count())
        self.assertNotIn("(3)", self.chat.lista_usuarios.item(0).text())
        self.assertEqual(0, self.chat.lista_usuarios.currentRow())

    def test_filtrar_no_mueve_a_otra_conversacion(self):
        self.chat._procesar_tarea(
            "directorio", (self.chat._gen_directorio, self._directorio_dos(), {}, None), None)
        self.chat.lista_usuarios.setCurrentRow(0)
        self.assertEqual("B002", self.chat.usuario_seleccionado)
        # Un pedido obsoleto (otra conversacion) no mueve la seleccion viva.
        gen_antes = self.chat._gen_historial
        self.chat._filtrar_usuarios(seleccionado="C003")
        self.assertEqual("B002", self.chat.usuario_seleccionado)
        self.assertEqual(0, self.chat.lista_usuarios.currentRow())
        # Y ninguna seleccion transitoria del rebuild pidio historial ajeno.
        self.assertEqual(gen_antes, self.chat._gen_historial)


    def test_no_leidos_es_dato_y_filtra_sin_regex(self):
        # UIX-02: la insignia vive en UserRole+2 (no en el texto) y el filtro
        # "Con no leídos" solo muestra esos contactos.
        self.chat._procesar_tarea(
            "directorio", (self.chat._gen_directorio, self._directorio_dos(), {"B002": 3}, None), None)
        self.assertEqual(2, self.chat.lista_usuarios.count())

        item_b = self.chat._item_por_codigo("B002")
        self.assertIn("(3)", item_b.text())
        self.assertEqual(3, item_b.data(Qt.ItemDataRole.UserRole + 2))

        item_c = self.chat._item_por_codigo("C003")
        self.assertNotIn("(3)", item_c.text())
        self.assertEqual(0, item_c.data(Qt.ItemDataRole.UserRole + 2))

        indice = self.chat.combo_filtro.findText("Con no leídos")
        self.assertGreaterEqual(indice, 0)
        self.chat.combo_filtro.setCurrentIndex(indice)
        self.chat._filtrar_usuarios()
        self.assertEqual(1, self.chat.lista_usuarios.count())
        self.assertEqual("B002", self.chat.lista_usuarios.item(0).data(Qt.ItemDataRole.UserRole))

        # Al seleccionar se limpia sin regex: el texto vuelve a la base.
        self.chat.lista_usuarios.setCurrentRow(0)
        self.assertNotIn("(3)", self.chat.lista_usuarios.item(0).text())
        self.assertEqual(0, self.chat.lista_usuarios.item(0).data(Qt.ItemDataRole.UserRole + 2))


    def test_historial_dos_dias_muestra_separador_y_agrupa(self):
        # UIX-04/UIX-05 cableado: pinta historial real de dos días y comprueba
        # que aparece la cabecera y que la racha oculta el remitente.
        import datetime
        hoy = datetime.datetime.now().replace(hour=10, minute=0, second=0)
        ayer = (hoy - datetime.timedelta(days=1)).replace(hour=10, minute=0)
        filas = [
            # Como las devuelve obtener_conversacion: de más nueva a más vieja.
            {"id_mensaje": "hoy-1", "origen": "B002", "destino": "A001",
             "tipo": "MENSAJE_TEXTO", "contenido": "hoy uno",
             "fecha_envio": hoy.strftime("%Y-%m-%d %H:%M:%S"), "estado": "LEIDO"},
            {"id_mensaje": "ayer-2", "origen": "B002", "destino": "A001",
             "tipo": "MENSAJE_TEXTO", "contenido": "ayer dos",
             "fecha_envio": (ayer + datetime.timedelta(minutes=2)).strftime("%Y-%m-%d %H:%M:%S"),
             "estado": "LEIDO"},
            {"id_mensaje": "ayer-1", "origen": "B002", "destino": "A001",
             "tipo": "MENSAJE_TEXTO", "contenido": "ayer uno",
             "fecha_envio": ayer.strftime("%Y-%m-%d %H:%M:%S"), "estado": "LEIDO"},
        ]
        self.chat._directorio = [{"codigo": "B002", "nombres": "Bea",
                                  "apellidos": "Dos", "conectado": 0}]
        self.chat.codigo = "A001"
        self.chat.show()
        self.chat._pintar_historial(filas)
        self._wait(200)

        modelo = self.chat._modelo_mensajes
        n = modelo.rowCount()
        # 3 mensajes + 2 separadores (Ayer, Hoy).
        self.assertEqual(5, n)
        etiquetas = [modelo.data(modelo.index(i), ROL_ITEM).get("etiqueta")
                     for i in range(n)
                     if isinstance(modelo.data(modelo.index(i), ROL_ITEM), dict)
                     and modelo.data(modelo.index(i), ROL_ITEM).get("es_separador")]
        self.assertIn("Ayer", etiquetas)
        self.assertIn("Hoy", etiquetas)
        # La racha (ayer-1, ayer-2) oculta el segundo remitente.
        fila2 = self.chat._mensaje_por_id("ayer-2")
        self.assertFalse(fila2["mostrar_remitente"])
        fila1 = self.chat._mensaje_por_id("ayer-1")
        self.assertTrue(fila1["mostrar_remitente"])


    def test_buscar_en_historial_cuenta_resalta_y_salta(self):
        # UIX-06: la búsqueda cuenta, resalta y salta entre coincidencias.
        self.chat._directorio = [{"codigo": "B002", "nombres": "Bea",
                                  "apellidos": "Dos", "conectado": 0}]
        self.chat.codigo = "A001"
        self.chat.usuario_seleccionado = "B002"
        filas = [
            {"id_mensaje": "m3", "origen": "B002", "destino": "A001",
             "tipo": "MENSAJE_TEXTO", "contenido": "sin coincidencia",
             "fecha_envio": "2026-10-02T10:02:00", "estado": "LEIDO"},
            {"id_mensaje": "m2", "origen": "B002", "destino": "A001",
             "tipo": "MENSAJE_TEXTO", "contenido": "hola de nuevo",
             "fecha_envio": "2026-10-02T10:01:00", "estado": "LEIDO"},
            {"id_mensaje": "m1", "origen": "A001", "destino": "B002",
             "tipo": "MENSAJE_TEXTO", "contenido": "hola",
             "fecha_envio": "2026-10-02T10:00:00", "estado": "LEIDO"},
        ]
        self.chat._pintar_historial(filas)
        self.chat.fachada.buscar_en_historial = lambda o, p: ["m2", "m1"]
        self.chat.show()
        self.chat.entry_buscar_historial.setText("hola")
        self.chat._ejecutar_busqueda()

        self.assertEqual("1/2", self.chat.lbl_buscar_cuenta.text())
        # El resaltado vive en el delegado: patrón + HTML con marca.
        self.assertEqual("hola", self.chat._delegado_mensajes._patron)
        doc = self._layout_de("m2")["_doc"]
        self.assertIn("<span", doc.toHtml())

        self.chat._buscar_siguiente()
        self.assertEqual("2/2", self.chat.lbl_buscar_cuenta.text())
        actual = self.chat._modelo_mensajes.data(
            self.chat.lista_mensajes.currentIndex(), ROL_ITEM)
        self.assertEqual("m1", actual.get("id"))

        self.chat._cerrar_buscar()
        self.assertFalse(self.chat.barra_buscar.isVisible())
        doc = self._layout_de("m2")["_doc"]
        self.assertNotIn("<span", doc.toHtml())


    def test_cancelar_cargas_descarta_resultados_tardios(self):
        # UIX-09: al salir de la vista, lo en vuelo se marca y su resultado
        # tardío no toca la UI (ni deja la marca puesta).
        self.chat._cargas_imagen["img-1"] = object()
        self.chat.cancelar_cargas_imagen()

        self.assertEqual({}, self.chat._cargas_imagen)
        antes = self.chat._modelo_mensajes.rowCount()
        self.chat._procesar_tarea("imagen", ("img-1", None, "x.png"), None)

        self.assertEqual(antes, self.chat._modelo_mensajes.rowCount())
        self.assertNotIn("img-1", self.chat._imagen_canceladas)


    def test_visor_imagen_zoom_y_cerrar(self):
        # UIX-08: el visor aplica zoom y se cierra sin errores.
        from ui.chat_window import _VisorImagen
        from PySide6.QtGui import QImage, QPixmap
        imagen = QImage(16, 16, QImage.Format.Format_ARGB32)
        imagen.fill(Qt.GlobalColor.blue)
        visor = _VisorImagen(QPixmap.fromImage(imagen), "prueba.png", self.chat)
        visor.show()
        self.assertAlmostEqual(1.0, visor._zoom)

        visor._aplicar_zoom(1.25)
        self.assertAlmostEqual(1.25, visor._zoom)
        visor._aplicar_zoom(1 / 1.25)
        self.assertAlmostEqual(1.0, visor._zoom)
        visor._aplicar_zoom(None)
        self.assertAlmostEqual(1.0, visor._zoom)
        # Tope superior e inferior.
        for _ in range(20):
            visor._aplicar_zoom(1.25)
        self.assertLessEqual(visor._zoom, 8.0)
        visor.close()
        self.assertTrue(True)


    def test_shift_enter_inserta_salto_y_enter_envia(self):
        # UIX-12: Mayús+Enter = salto de línea, Enter solo = enviar.
        from PySide6.QtTest import QTest
        self.chat.lista_usuarios.setCurrentRow(0)
        pedidos = []
        self.chat.entry_msg.enviar_pedido.connect(lambda: pedidos.append(1))
        self.chat.entry_msg.setPlainText("hola")

        QTest.keyClick(self.chat.entry_msg, Qt.Key.Key_Return,
                       Qt.KeyboardModifier.ShiftModifier)
        self.assertEqual([], pedidos)
        self.assertIn("\n", self.chat.entry_msg.texto())

        QTest.keyClick(self.chat.entry_msg, Qt.Key.Key_Return)
        self.assertEqual([1], pedidos)


if __name__ == "__main__":
    unittest.main()
