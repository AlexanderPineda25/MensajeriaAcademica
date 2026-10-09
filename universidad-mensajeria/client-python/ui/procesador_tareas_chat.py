"""Entrega en el hilo de UI de resultados de tareas asíncronas."""

from pathlib import Path

from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import QMessageBox


class ProcesadorTareasChat:
    def _procesar_tarea(self, tipo, resultado, error):
        if self._cerrando:
            return
        if error is not None:
            if tipo == "imagen":
                mid = getattr(error, "mensaje_id", "")
                self._cargas_imagen.pop(mid, None)
                # UIX-09: error de una carga cancelada: no se ofrece reintentar
                # algo que el usuario ya dejó atrás.
                if mid in self._imagen_canceladas:
                    self._imagen_canceladas.discard(mid)
                    return
                row = self._row_por_id(mid)
                if row is not None:
                    row["image_loading"] = False
                    row["image_error"] = str(error)
                    self._modelo_mensajes.refrescar(mid)
            elif tipo == "envio":
                self.btn_enviar.setEnabled(True)
                self._aviso_red(f"No se pudo enviar: {error}")
            elif tipo == "descarga":
                mid = getattr(error, "mensaje_id", "")
                mensaje = self._mensaje_por_id(mid)
                if mensaje is not None:
                    mensaje["descargando"] = False
                    self._modelo_mensajes.refrescar(mid)
                self._aviso_red(f"No se pudo descargar el archivo: {error}")
            elif tipo == "historial":
                self.lbl_estado_red.setText("No se pudo cargar el historial local")
                self._cargando_historial = False
                self._actualizar_vacio_chat()
            elif tipo == "historial_remoto":
                self.lbl_presencia.setText("Mostrando historial local")
            return

        if tipo == "directorio":
            gen, usuarios, no_leidos, error_red = resultado
            if gen != self._gen_directorio:
                # Refresh obsoleto (el usuario cambio de chat o filtro
                # mientras la red respondia): no pisa la seleccion viva.
                return
            propio = next((u for u in usuarios if u.get("codigo") == self.codigo), None)
            if propio is not None:
                nombre_propio = " ".join(filter(None, [propio.get("nombres"),
                                                        propio.get("apellidos")])).strip()
                identidad_propia = (f"{nombre_propio} [{self.codigo}]"
                                    if nombre_propio else self.codigo)
                self.lbl_sesion.setText(f"Mensajería\nSesión activa · {identidad_propia}")
            self._directorio = [u for u in usuarios if u.get("codigo") != self.codigo]
            self._no_leidos = dict(no_leidos or {})
            # Tarjetas: recientes por fecha con preview (de caché local; la
            # tarea ya la actualizó por red). Sin `recientes()` en la fachada,
            # `_filtrar_usuarios` usa el directorio plano (dobles de prueba).
            recientes = self._recientes_de_fachada()
            if recientes is not None:
                self._recientes = recientes
                self._no_leidos = {r.otro: r.no_leidos for r in recientes}
            else:
                self._recientes = None
            # Restaura la seleccion VIVA (no la capturada al pedir): si el
            # usuario cambio de chat o escribio un filtro mientras tanto,
            # un refresh obsoleto jamas lo mueve de conversacion.
            self._huella_pintada = None
            self._filtrar_usuarios(seleccionado=self.usuario_seleccionado)
            if self._recientes is not None:
                self._huella_pintada = self._huella_recientes(self._recientes)
            self._restaurar_ultima_conversacion()
            online = sum(bool(u.get("conectado")) for u in self._directorio)
            sin_red = error_red is not None
            self.lbl_offline.setVisible(sin_red)
            if sin_red:
                self.lbl_estado_red.setText("Sin conexión: directorio local · cola activa")
                self.lbl_estado_red.setStyleSheet("color: #B45309; font-size: 11px; font-weight: 700;")
            else:
                self.lbl_estado_red.setText(f"{online} contactos en línea")
                self.lbl_estado_red.setStyleSheet("color: #61717F; font-size: 11px;")
        elif tipo == "historial":
            usuario, filas, offset, total, gen = resultado
            if usuario != self.usuario_seleccionado or gen != self._gen_historial:
                return
            if usuario == self.usuario_seleccionado:
                self._pintar_historial(filas, prepend=offset > 0)
                self._offset = offset
                hay_local = offset + 50 < total
                # UIX-06: si hay un salto pendiente (búsqueda), se reintenta
                # ahora que hay más mensajes cargados.
                pendiente = self.modelo_vista.consumir_salto()
                if pendiente is not None:
                    pendiente, intentos = pendiente
                    self._saltar_a(pendiente, intentos)
                hay_remoto = self._pagina_remota.get(usuario, 0) + 1 < self._total_remoto.get(usuario, 0)
                self.btn_anteriores.setVisible(hay_local or hay_remoto)
                self._cargar_imagenes_visibles()
        elif tipo == "historial_remoto":
            usuario, pagina, total_paginas, older, gen = resultado
            if usuario != self.usuario_seleccionado or gen != self._gen_historial:
                return
            self._pagina_remota[usuario] = pagina
            self._total_remoto[usuario] = total_paginas
            if self.usuario_seleccionado == usuario:
                self._solicitar_historial(usuario, self._offset if older else 0)
        elif tipo == "pagina_anterior":
            usuario, destino, pagina = resultado
            if usuario != self.usuario_seleccionado:
                return
            if destino == "local":
                self._solicitar_historial(usuario, pagina)
            elif destino == "remoto":
                self._solicitar_historial_remoto(usuario, pagina, True)
        elif tipo == "envio":
            usuario, result, ids_enviados = resultado
            self.btn_enviar.setEnabled(True)
            if result is False:
                self.lbl_estado_red.setText("Sin red: guardado para reintentar")
            else:
                for mid in ids_enviados:
                    mensaje = self._mensaje_por_id(mid)
                    if mensaje is not None and mensaje.get("raw_state") == "PENDIENTE":
                        mensaje["state"] = self._estado_visible("ENVIADO")
                        mensaje["raw_state"] = "ENVIADO"
                        self._modelo_mensajes.refrescar(mid)
            self._solicitar_historial(usuario, 0)
            # Mi envío también es "último mensaje": la tarjeta sube arriba.
            self._programar_refresco_recientes()
        elif tipo == "salir_todos":
            self._cerrando = True
            self.fachada.on_mensaje_recibido_ui = None
            self.fachada.on_cierre_ui = None
            self.fachada.on_presencia_ui = None
            self.fachada.on_desconexion_ui = None
            self.fachada.desconectar()
            self.on_logout()
            self.close()
        elif tipo == "imagen":
            mensaje_id, imagen, ruta = resultado
            # UIX-09: resultado de una carga cancelada: se descarta sin tocar
            # la UI (ya es otro chat) y se limpia la marca.
            self._cargas_imagen.pop(mensaje_id, None)
            if mensaje_id in self._imagen_canceladas:
                self._imagen_canceladas.discard(mensaje_id)
                return
            mensaje = self._mensaje_por_id(mensaje_id)
            if mensaje is not None:
                self._delegado_mensajes.guardar_pixmap(
                    mensaje_id, QPixmap.fromImage(imagen))
                mensaje["image_state"] = Path(ruta).name
                mensaje["image_loaded"] = True
                mensaje["image_loading"] = False
                mensaje["image_error"] = ""
                self._modelo_mensajes.refrescar(mensaje_id)
        elif tipo == "descarga":
            mensaje_id, ruta_guardada = resultado
            mensaje = self._mensaje_por_id(mensaje_id)
            if mensaje is not None:
                mensaje["descargando"] = False
                mensaje["descargado_ruta"] = ruta_guardada
                self._modelo_mensajes.refrescar(mensaje_id)
            QMessageBox.information(
                self, "Descarga completa",
                f"Archivo guardado exitosamente en:\n{ruta_guardada}")
