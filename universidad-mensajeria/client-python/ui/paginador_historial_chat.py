"""Paginación y representación del historial de una conversación."""

from PySide6.QtWidgets import QAbstractItemView

from transversal.fechas import agrupar, clave_dia, etiqueta_dia
from transversal.tipo_mensaje import TipoMensaje
from ui.lista_virtual import ROL_ITEM


class PaginadorHistorialChat:
    def _hay_mensajes(self):
        return bool(self._modelo_mensajes.mensajes())

    def _actualizar_vacio_chat(self):
        """UIX-13/UIX-14: qué se ve cuando no hay mensajes."""
        if self._hay_mensajes():
            self.lbl_vacio_chat.setVisible(False)
            self.lista_mensajes.setVisible(True)
            return
        self.lista_mensajes.setVisible(False)
        if getattr(self, "_cargando_historial", False):
            self.lbl_vacio_chat.setText("Cargando historial…")
        elif self.usuario_seleccionado:
            self.lbl_vacio_chat.setText("Sin mensajes aún. Escribe el primero.")
        else:
            self.lbl_vacio_chat.setText("Elige un contacto para empezar a conversar.")
        self.lbl_vacio_chat.setVisible(True)

    def _solicitar_historial(self, usuario, offset):
        # UIX-14: mientras carga se muestra "Cargando historial…".
        self._cargando_historial = True
        self._actualizar_vacio_chat()
        gen = self._gen_historial
        def cargar():
            try:
                metodo = self.fachada.obtener_conversacion
                try:
                    filas = metodo(usuario, limit=50, offset=offset)
                except TypeError:
                    filas = metodo(usuario)
                total = self.fachada.contar_conversacion(usuario) if hasattr(
                    self.fachada, "contar_conversacion") else len(filas)
                return usuario, filas, offset, total, gen
            except Exception as error:
                raise RuntimeError(str(error)) from error
        self._ejecutar("historial", cargar)

    def _cargar_anteriores(self):
        usuario = self.usuario_seleccionado
        if usuario is None:
            return
        def consultar():
            total_local = self.fachada.contar_conversacion(usuario)
            if self._offset + 50 < total_local:
                return usuario, "local", self._offset + 50
            siguiente = self._pagina_remota.get(usuario, 0) + 1
            if siguiente < self._total_remoto.get(usuario, 0):
                return usuario, "remoto", siguiente
            return usuario, "ninguno", self._offset
        self._ejecutar("pagina_anterior", consultar)

    def _solicitar_historial_remoto(self, usuario, pagina, older):
        gen = self._gen_historial
        self._ejecutar("historial_remoto", lambda: (
            usuario, pagina, self.fachada.traer_historial(usuario, pagina), older, gen))

    def _capturar_ancla_scroll(self):
        viewport = self.lista_mensajes.viewport().rect()
        for fila in range(self._modelo_mensajes.rowCount()):
            indice = self._modelo_mensajes.index(fila)
            rect = self.lista_mensajes.visualRect(indice)
            if not rect.isValid() or not viewport.intersects(rect):
                continue
            item = self._modelo_mensajes.data(indice, ROL_ITEM)
            if isinstance(item, dict) and item.get("id"):
                return item["id"], rect.top()
        return None

    def _restaurar_ancla_scroll(self, ancla):
        if ancla is None:
            return
        id_mensaje, y_original = ancla
        indice = self._modelo_mensajes.indice_por_id(id_mensaje)
        if not indice.isValid():
            return
        self.lista_mensajes.doItemsLayout()
        self.lista_mensajes.scrollTo(
            indice, QAbstractItemView.ScrollHint.PositionAtTop)
        self.lista_mensajes.doItemsLayout()
        rect = self.lista_mensajes.visualRect(indice)
        if not rect.isValid():
            return
        barra = self.lista_mensajes.verticalScrollBar()
        barra.setValue(barra.value() + rect.top() - y_original)

    def _pintar_historial(self, filas, prepend=False):
        ordenados = list(reversed(filas))
        # UIX-16: conserva el primer mensaje realmente visible y su posición
        # dentro del viewport al anteponer una página antigua.
        ancla = self._capturar_ancla_scroll() if prepend else None
        # Un solo layout al final: 50 filas x QWidget son ~ms, no cientos.
        self.lista_mensajes.setUpdatesEnabled(False)
        try:
            nuevas = []
            for fila in (reversed(ordenados) if prepend else ordenados):
                id_mensaje = fila.get("id_mensaje") or fila.get("id") or ""
                if id_mensaje and id_mensaje in self._historial_ids:
                    indice = self._modelo_mensajes.indice_por_id(id_mensaje)
                    if indice.isValid():
                        existente = self._modelo_mensajes.data(indice, ROL_ITEM)
                        if not isinstance(existente, dict):
                            continue
                        raw_state = fila.get("estado", "")
                        if existente.get("is_own"):
                            existente["state"] = self._estado_visible(raw_state)
                            existente["raw_state"] = raw_state
                            self._modelo_mensajes.refrescar(id_mensaje)
                        if fila.get("archivo_id"):
                            existente["archivo_id"] = fila["archivo_id"]
                            self._modelo_mensajes.refrescar(id_mensaje)
                        if fila.get("etapas_filtrado"):
                            existente["etapas_filtrado"] = fila["etapas_filtrado"]
                            self._modelo_mensajes.refrescar(id_mensaje)
                    continue
                if id_mensaje:
                    self._historial_ids.add(id_mensaje)
                nuevas.append(fila)
            # UIX-04/UIX-05: separadores de día y rachas sin remitente repetido.
            # `nuevas` va en orden de inserción; `_insertar_nuevas` la pasa a
            # cronológico para decidir.
            if prepend:
                nuevas = list(reversed(nuevas))
            self._insertar_nuevas(nuevas, prepend)
        finally:
            self.lista_mensajes.setUpdatesEnabled(True)

        if not prepend:
            if self._seguir_abajo():
                self.lista_mensajes.scrollToBottom()
                self._nuevos_pendientes = 0
                self.btn_nuevos.setVisible(False)
        else:
            self._restaurar_ancla_scroll(ancla)
        # UIX-13/UIX-14: terminó la pintura (también en llamadas directas).
        self._cargando_historial = False
        self._actualizar_vacio_chat()
        self._cargar_imagenes_visibles()

    def _construir_item(self, fila):
        """Convierte una fila cruda de historial en el dict que pinta la fila.

        Extraído de `_pintar_historial` sin cambiar nada: el remitente, el texto
        de la burbuja y el estado se calculan igual que antes. El flag
        `mostrar_remitente` (UIX-05) y los separadores (UIX-04) los pone
        `_insertar_nuevas`, que sí ve a los vecinos.
        """
        origen = fila.get("origen") or ""
        usuario = next((u for u in self._directorio if u.get("codigo") == origen), {})
        nombre = " ".join(filter(None, [usuario.get("nombres"), usuario.get("apellidos")])).strip()
        is_own = (origen == self.codigo)
        remitente = "Tú" if is_own else (f"{nombre} [{origen}]" if nombre else origen)
        tipo = fila.get("tipo")
        es_imagen = tipo == TipoMensaje.MENSAJE_IMAGEN
        es_archivo = tipo == TipoMensaje.MENSAJE_ARCHIVO
        raw_state = fila.get("estado", "")
        nombre_arch = fila.get("nombre_archivo") or ""
        tamano_bytes = fila.get("tamano_archivo") or 0
        if tamano_bytes >= 1024 * 1024:
            tamano_str = f"{tamano_bytes/1024/1024:.1f} MB"
        elif tamano_bytes > 0:
            tamano_str = f"{tamano_bytes/1024:.1f} KB"
        else:
            tamano_str = ""
        if es_archivo:
            texto_burbuja = f"📎 {nombre_arch}  ({tamano_str})" if nombre_arch else "[Archivo]"
        elif es_imagen:
            texto_burbuja = ""
        else:
            texto_burbuja = fila.get("contenido") or ""
        id_mensaje = fila.get("id_mensaje") or fila.get("id") or ""
        return {
            "id": id_mensaje, "sender": remitente, "date": fila.get("fecha_envio", ""),
            "origen": origen,
            "text": texto_burbuja,
            "state": self._estado_visible(raw_state) if is_own else "",
            "raw_state": raw_state if is_own else "",
            "is_own": is_own,
            "is_image": es_imagen, "es_archivo": es_archivo,
            "archivo_id": fila.get("archivo_id"),
            "etapas_filtrado": fila.get("etapas_filtrado"),
            "filename": nombre_arch, "path": fila.get("ruta_archivo") or "",
            "image_loaded": False, "image_loading": False,
            "image_state": "Imagen" if es_imagen else "",
            "mostrar_remitente": True,
            "_cruda": fila,
        }

    def _mensaje_en_indice(self, indice):
        """Item dict del mensaje en esa fila, o None (separador/fuera de rango)."""
        modelo = self._modelo_mensajes
        if indice < 0 or indice >= modelo.rowCount():
            return None
        candidato = modelo.data(modelo.index(indice), ROL_ITEM)
        if isinstance(candidato, dict) and candidato.get("id"):
            return candidato
        return None

    def _agregar_separador(self, etiqueta, indice=None):
        """Fila de cabecera de día (UIX-04): centrada, sin id, no seleccionable."""
        fila = {"es_separador": True, "etiqueta": etiqueta}
        if indice is None:
            self._modelo_mensajes.agregar(fila)
        else:
            self._modelo_mensajes.insertar_en(indice, fila)

    @staticmethod
    def _cruda_de(item):
        """Lo que `agrupar` necesita: origen + fecha_envio de un item dict."""
        if not isinstance(item, dict):
            return None
        return {"origen": item.get("origen", ""), "fecha_envio": item.get("date", "")}

    def _insertar_nuevas(self, nuevas, prepend):
        """Inserta filas nuevas y reconcilia cabeceras y rachas (UIX-04/UIX-05)."""
        if not nuevas:
            return
        items = [self._construir_item(fila) for fila in nuevas]
        if prepend:
            for item in reversed(items):
                self._agregar_fila(item, prepend=True, revisar_imagenes=False)
        else:
            for item in items:
                self._agregar_fila(item, prepend=False, revisar_imagenes=False)
        self._reconciliar_filas()

    def _reconciliar_filas(self):
        """Deja exactamente un separador donde toca y recalcula las rachas.

        Invariante: separador antes del primer mensaje y entre mensajes de
        distinto día; sin remitente si va pegado al anterior del mismo autor.
        Solo crea/borra filas separadoras; los mensajes conservan su dict.
        Al cargar páginas viejas los separadores se mueven solos al sitio
        correcto, sin lógica de unión en cada inserción.
        """
        modelo = self._modelo_mensajes
        modelo.quitar_separadores()
        mensajes = modelo.mensajes()
        # Posiciones (índices actuales) donde va separador: antes del primero
        # con día conocido y entre días distintos.
        pos_actual = {}
        for i in range(modelo.rowCount()):
            f = modelo.data(modelo.index(i), ROL_ITEM)
            if isinstance(f, dict) and f.get("id"):
                pos_actual[f["id"]] = i
        cortes = set()
        for pos, d in enumerate(mensajes):
            dia = clave_dia(d.get("date", ""))
            if not dia:
                continue
            if pos == 0:
                cortes.add(pos_actual[d["id"]])
                continue
            dia_prev = clave_dia(mensajes[pos - 1].get("date", ""))
            if dia_prev and dia_prev != dia:
                cortes.add(pos_actual[d["id"]])
        for i in sorted(cortes, reverse=True):
            d = modelo.data(modelo.index(i), ROL_ITEM)
            if isinstance(d, dict):
                self._agregar_separador(etiqueta_dia(d.get("date", "")), i)
        # Rachas sobre la secuencia de mensajes (los separadores no cuentan).
        # El delegado lee `mostrar_remitente` al pintar: basta refrescar.
        previo = None
        for d in mensajes:
            d["mostrar_remitente"] = not agrupar(
                self._cruda_de(previo), self._cruda_de(d))
            modelo.refrescar(d.get("id"))
            previo = d

    def _agregar_fila(self, mensaje, prepend=False, revisar_imagenes=True):
        # UIX-01: sin QWidget por fila; el dict vive en el modelo y lo pinta
        # el delegado bajo demanda.
        mensaje["is_own"] = bool(
            mensaje.get("is_own") or mensaje.get("sender") == "Tú")
        if prepend:
            self._modelo_mensajes.insertar_en(0, mensaje)
        else:
            self._modelo_mensajes.agregar(mensaje)
        # El pintado en lote (_pintar_historial) escanea una sola vez al
        # final: este scan por fila era O(n^2) por carga de historial.
        if revisar_imagenes:
            self._cargar_imagenes_visibles()
