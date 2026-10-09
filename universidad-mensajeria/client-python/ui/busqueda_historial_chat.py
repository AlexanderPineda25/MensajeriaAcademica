"""Acciones de búsqueda del historial desde la interfaz de chat."""


class BusquedaHistorialChat:
    def _mostrar_buscar(self):
        if not self.usuario_seleccionado:
            self.lbl_estado_red.setText("Elige una conversación para buscar en su historial")
            return
        self.barra_buscar.setVisible(True)
        self.entry_buscar_historial.setFocus()
        self.entry_buscar_historial.selectAll()

    def _cerrar_buscar(self):
        self.barra_buscar.setVisible(False)
        self.entry_buscar_historial.clear()
        self.modelo_vista.limpiar_busqueda()
        self.lbl_buscar_cuenta.setText("")
        self._repintar_resaltado("")

    def _buscar_historial_debounced(self, *_):
        """UIX-07: la query a SQLite espera 300 ms tras la última tecla."""
        self._timer_buscar.stop()
        self._timer_buscar.start()

    def _ejecutar_busqueda(self):
        patron = (self.entry_buscar_historial.text() or "").strip()
        if not self.usuario_seleccionado or not patron:
            self.modelo_vista.limpiar_busqueda()
            self._actualizar_cuenta_busqueda()
            self._repintar_resaltado("")
            return
        try:
            ids = self.fachada.buscar_en_historial(self.usuario_seleccionado, patron)
        except Exception:
            self.lbl_buscar_cuenta.setText("Sin resultados")
            return
        primer_id = self.modelo_vista.fijar_coincidencias(patron, ids)
        self._actualizar_cuenta_busqueda()
        self._repintar_resaltado(patron)
        if primer_id:
            self._saltar_a(primer_id, intentos=5)

    def _actualizar_cuenta_busqueda(self):
        modelo = self.modelo_vista
        if not modelo.ids_coincidencia:
            self.lbl_buscar_cuenta.setText(
                "" if not modelo.patron_busqueda else "Sin resultados")
        else:
            self.lbl_buscar_cuenta.setText(
                f"{modelo.indice_coincidencia + 1}/{len(modelo.ids_coincidencia)}")

    def _buscar_siguiente(self):
        id_siguiente = self.modelo_vista.mover_coincidencia(1)
        if id_siguiente is None:
            return
        self._actualizar_cuenta_busqueda()
        self._saltar_a(id_siguiente, intentos=5)

    def _buscar_anterior(self):
        id_anterior = self.modelo_vista.mover_coincidencia(-1)
        if id_anterior is None:
            return
        self._actualizar_cuenta_busqueda()
        self._saltar_a(id_anterior, intentos=5)

    def _repintar_resaltado(self, patron):
        # UIX-01: el patrón vive en el delegado; repinta lo visible.
        self._delegado_mensajes.set_patron(patron or "")

    def _saltar_a(self, id_mensaje, intentos=5):
        """Baja al mensaje: si no está cargado, trae una página más y reintenta."""
        if not self.usuario_seleccionado or not id_mensaje:
            return
        indice = self._modelo_mensajes.indice_por_id(id_mensaje)
        if indice.isValid():
            self.lista_mensajes.scrollTo(indice)
            self.lista_mensajes.setCurrentIndex(indice)
            return
        if intentos <= 0 or not self.btn_anteriores.isVisible():
            self.lbl_buscar_cuenta.setText("Fuera del historial cargado")
            return
        self.modelo_vista.programar_salto(id_mensaje, intentos - 1)
        self._cargar_anteriores()
