"""Acciones de selección, envío y navegación de la conversación."""

import datetime
import uuid
from pathlib import Path

from PySide6.QtCore import Qt

from ui.enviar_mensaje_comando import EnviarMensajeComando


class FlujoConversacionChat:
    def _seguir_abajo(self):
        """UIX-16: True si ya se está al fondo (margen 60 px)."""
        try:
            barra = self.lista_mensajes.verticalScrollBar()
            return barra.value() >= barra.maximum() - 60
        except Exception:
            return True

    def _cancelar_respuesta(self):
        self._respuesta_mensaje = None
        self.lbl_respuesta.clear()
        self.cita_container.setVisible(False)

    def _cancelar_composicion(self):
        """Esc: cierra la búsqueda si está abierta; si no, cita y adjunto."""
        if self.barra_buscar.isVisible():
            self._cerrar_buscar()
            return
        self._cancelar_respuesta()
        self._quitar_adjunto()

    def _avisar_nuevo(self):
        """UIX-16: llegó algo mientras se lee historial viejo."""
        self._nuevos_pendientes = getattr(self, "_nuevos_pendientes", 0) + 1
        self.btn_nuevos.setText(f"↓ {self._nuevos_pendientes} nuevos")
        self.btn_nuevos.setVisible(True)

    def _responder(self, mensaje):
        self._respuesta_mensaje = dict(mensaje)
        self.lbl_respuesta.setText(self._cita_actual())
        self.cita_container.setVisible(True)
        self.entry_msg.setFocus()

    def _cita_actual(self):
        mensaje = getattr(self, "_respuesta_mensaje", None)
        if not mensaje:
            return ""
        if mensaje.get("is_image"):
            referencia = f"[Imagen: {mensaje.get('filename') or 'imagen'}]"
        else:
            referencia = '"%s"' % ((mensaje.get("text") or "mensaje")[:120])
        return f"Respuesta a {mensaje.get('sender')}: {referencia}"

    def _responder_dobleclick(self, indice):
        """Telegram Web: doble-click en cualquier mensaje = responder."""
        try:
            mensaje = self._modelo_mensajes.data(indice, ROL_ITEM)
        except Exception:
            mensaje = None
        if isinstance(mensaje, dict) and mensaje.get("id"):
            self._responder(mensaje)

    def enviar(self):
        texto = self.entry_msg.texto().strip()
        usuario = self.usuario_seleccionado
        ruta = self.imagen_pendiente
        if not usuario or (not texto and not ruta):
            return
        cita = self._cita_actual()
        texto_envio = (cita + "\n\n" + texto) if cita and texto else (cita or texto)
        self.btn_enviar.setEnabled(False)

        ahora = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        id_texto = str(uuid.uuid4()) if texto_envio else None
        id_imagen = str(uuid.uuid4()) if ruta else None

        # Reflejo optimista inmediato en la UI con estado PENDIENTE
        if id_texto:
            item_texto = {
                "id": id_texto, "sender": "Tú", "date": ahora,
                "text": texto_envio, "state": self._estado_visible("PENDIENTE"),
                "raw_state": "PENDIENTE", "is_own": True,
                "is_image": False, "archivo_id": None,
                "filename": "", "path": "", "image_loaded": False,
                "image_loading": False, "image_state": ""
            }
            self._historial_ids.add(id_texto)
            self._agregar_fila(item_texto)

        if id_imagen:
            nombre_adj = Path(ruta).name
            ext_adj = Path(ruta).suffix.casefold()
            es_imagen_adj = ext_adj in self._EXTENSIONES_IMAGEN
            tamano_kb = Path(ruta).stat().st_size / 1024
            tamano_str = (f"{tamano_kb/1024:.1f} MB" if tamano_kb >= 1024
                          else f"{tamano_kb:.1f} KB")
            item_imagen = {
                "id": id_imagen, "sender": "Tú", "date": ahora,
                "text": "" if es_imagen_adj else f"📎 {nombre_adj}  ({tamano_str})",
                "state": self._estado_visible("PENDIENTE"),
                "raw_state": "PENDIENTE", "is_own": True,
                "is_image": es_imagen_adj, "archivo_id": None,
                "filename": nombre_adj, "path": ruta,
                "image_loaded": False, "image_loading": False,
                "image_state": nombre_adj if es_imagen_adj else ""
            }
            self._historial_ids.add(id_imagen)
            self._agregar_fila(item_imagen)

        self.lista_mensajes.scrollToBottom()

        # Limpiar entrada, cita y adjunto inmediatamente
        self.entry_msg.clear()
        self._cancelar_respuesta()
        self._quitar_adjunto()

        comando = EnviarMensajeComando(
            usuario=usuario,
            texto=texto_envio,
            id_texto=id_texto,
            ruta_adjunto=ruta,
            id_adjunto=id_imagen,
            extensiones_imagen=frozenset(self._EXTENSIONES_IMAGEN),
        )
        self._ejecutar("envio", lambda: comando.ejecutar(self.fachada))

    def _ir_a_nuevos(self):
        """UIX-16: baja al fondo y apaga el aviso."""
        self._nuevos_pendientes = 0
        self.btn_nuevos.setVisible(False)
        self.lista_mensajes.scrollToBottom()

    def _al_seleccionar_usuario(self, actual, anterior=None):
        if actual is None or getattr(self, "_seleccionando", False) \
                or getattr(self, "_reconstruyendo", False):
            return
        try:
            codigo = actual.data(Qt.ItemDataRole.UserRole)
            identidad = actual.data(Qt.ItemDataRole.UserRole + 1)
        except RuntimeError:
            # El item fue liberado por una reconstruccion de la lista
            # (_filtrar_usuarios hace clear()): no hay nada que mostrar.
            return
        if not codigo:
            return
        self.usuario_seleccionado = codigo
        self._preferencias_chat.guardar_ultimo_chat(self.codigo, codigo)
        # UIX-09: al cambiar de chat, las descargas del anterior ya no pintan
        # aquí. (Si es el mismo, no se toca nada.)
        if codigo != getattr(self, "_chat_cargas", codigo):
            self.cancelar_cargas_imagen()
        self._chat_cargas = codigo
        self._nuevos_pendientes = 0
        self.btn_nuevos.setVisible(False)
        self._cancelar_respuesta()
        # UIX-06: los resultados son por conversación; al cambiar, fuera.
        if self.barra_buscar.isVisible():
            self._cerrar_buscar()
        # Limpia la insignia de no-leidos EN EL SITIO: antes se reconstruia
        # la lista entera aqui (~236 ms con 1000+ contactos) y el setCurrentItem
        # anidado podia robar la seleccion en plena rafaga de presencia.
        try:
            if isinstance(getattr(self, "_no_leidos", None), dict) and codigo in self._no_leidos:
                self._no_leidos.pop(codigo, None)
                self._limpiar_insignia(codigo)
        except Exception:
            pass
        self.lbl_conversacion.setText(identidad or codigo)
        self.lbl_presencia.setText("en línea" if any(
            u.get("codigo") == codigo and u.get("conectado") for u in self._directorio)
            else "desconectado")
        self._offset = 0
        self._gen_historial += 1
        self._pagina_remota[codigo] = 0
        self._total_remoto.pop(codigo, None)
        self._historial_ids.clear()
        self._modelo_mensajes.limpiar()
        self._solicitar_historial(codigo, 0)
        if hasattr(self.fachada, "traer_historial"):
            self._solicitar_historial_remoto(codigo, 0, False)
        if hasattr(self.fachada, "marcar_leidos"):
            self._ejecutar("marcar_leidos", lambda: self.fachada.marcar_leidos(codigo))

    def _actualizar_envio(self):
        self.btn_enviar.setEnabled(bool(self.usuario_seleccionado)
                                   and bool(self.entry_msg.texto().strip() or self.imagen_pendiente))
