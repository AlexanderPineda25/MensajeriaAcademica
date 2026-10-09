"""Actualización, filtrado y selección del directorio de contactos."""

from PySide6.QtCore import Qt

from ui.celdas.usuario_cell import ROL_BASE, ROL_CODIGO, ROL_NO_LEIDOS, UsuarioCell


class RefrescadorContactosChat:
    def actualizar_usuarios(self):
        # Primero la caché (instantáneo: SQLite local, sin red): con un
        # servidor lento o caído la lista aparece igualmente al abrir.
        self._pintar_recientes_desde_cache()
        gen = self._gen_directorio = self._gen_directorio + 1
        def cargar():
            try:
                if hasattr(self.fachada, "listar_usuarios"):
                    usuarios = self.fachada.listar_usuarios()
                else:
                    codigos = self.fachada.listar_conectados()
                    local = {u["codigo"]: u for u in self.fachada.directorio_local()}
                    usuarios = [dict(local.get(c, {"codigo": c}), conectado=1) for c in codigos]
                try:
                    no_leidos = self._no_leidos_desde_historial()
                except Exception:
                    no_leidos = {}
                return (gen, usuarios, no_leidos, None)
            except Exception as error:
                try:
                    return (gen, self.fachada.directorio_local(), {}, error)
                except Exception:
                    return (gen, [], {}, error)
        self._ejecutar("directorio", cargar)

    def _no_leidos_desde_historial(self):
        """Camino legado para fachadas sin `recientes()` (dobles de prueba)."""
        try:
            historial = getattr(self.fachada, "historial", None)
            if historial is not None and hasattr(historial, "contar_no_leidos_por_contacto"):
                return historial.contar_no_leidos_por_contacto(self.codigo)
        except Exception:
            pass
        return {}

    def _recientes_de_fachada(self):
        """Recientes ordenados por fecha; None si la fachada no los tiene."""
        try:
            if hasattr(self.fachada, "recientes"):
                return list(self.fachada.recientes())
        except Exception:
            pass
        return None

    def _recientes_fallback(self):
        """Sin `recientes()` en la fachada: directorio alfabético sin preview."""
        def clave(usuario):
            return ((usuario.get("apellidos") or "").casefold(),
                    usuario.get("codigo", ""))
        return [_RecientePlano(
            usuario.get("codigo", ""), "", "", "",
            int((self._no_leidos or {}).get(usuario.get("codigo", ""), 0) or 0),
            bool(usuario.get("conectado"))) for usuario in sorted(
                self._directorio, key=clave)]

    def _huella_recientes(self, recientes):
        """Cambio real de datos (no de tick de presencia): evita repintar igual."""
        return tuple((r.otro, bool(r.conectado), int(r.no_leidos),
                      r.fecha_ultimo or "", (r.ultimo_contenido or "")[:40])
                     for r in recientes)

    def _pintar_recientes_desde_cache(self):
        """Repintado instantáneo desde SQLite; la red llega después si quiere."""
        recientes = self._recientes_de_fachada()
        if recientes is None:
            return
        if not recientes and not self._directorio and not getattr(
                self, "_recientes", []):
            return  # primer arranque sin caché: queda el estado "Conectando..."
        if not self._directorio:
            try:
                self._directorio = [u for u in self.fachada.directorio_local()
                                    if u.get("codigo") != self.codigo]
            except Exception:
                pass
        huella = self._huella_recientes(recientes)
        if huella == getattr(self, "_huella_pintada", None):
            return
        self._huella_pintada = huella
        self._recientes = recientes
        self._no_leidos = {r.otro: r.no_leidos for r in recientes}
        self._filtrar_usuarios(seleccionado=self.usuario_seleccionado)

    def _programar_refresco_recientes(self):
        """Ráfagas de mensajes coalescen en un solo repintado de tarjetas."""
        if not self._timer_recientes.isActive():
            self._timer_recientes.start()

    def _filtrar_usuarios(self, text=None, seleccionado=None):
        if seleccionado is None:
            seleccionado = self.usuario_seleccionado
        filtro = (self.entry_buscar.text() or "").strip().casefold()
        solo_conectados = False
        solo_no_leidos = False
        try:
            texto_filtro = self.combo_filtro.currentText()
            solo_conectados = texto_filtro == "Conectados"
            solo_no_leidos = texto_filtro == "Con no leídos"
        except Exception:
            pass
        recientes = getattr(self, "_recientes", None) or self._recientes_fallback()
        nombres = {usuario.get("codigo", ""): " ".join(filter(None, [
            usuario.get("nombres"), usuario.get("apellidos")])).strip()
            for usuario in self._directorio}
        # Sin repintados parciales: con 1000+ contactos el rebuild por
        # tecla/evento congela la UI (medido: ~236 ms). Un solo layout al final.
        # Ademas se ignoran las selecciones TRANSITORIAS del rebuild: clear()
        # borra fila por fila y Qt mueve el cursor a la siguiente, lo que el
        # handler tomaba como seleccion real (robaba la conversacion al
        # escribir en el buscador o con cada refresh de presencia).
        # El orden NO se toca aquí: lo fija `ConstructorRecientes` (recientes
        # primero por fecha) para que la presencia no mueva filas.
        self._reconstruyendo = True
        self.lista_usuarios.setUpdatesEnabled(False)
        try:
            self.lista_usuarios.clear()
            seleccionado_item = None
            for fila in recientes:
                codigo = fila.otro
                online = bool(fila.conectado)
                if solo_conectados and not online:
                    continue
                pendientes = int(fila.no_leidos or 0)
                if solo_no_leidos and pendientes <= 0:
                    continue
                nombre = nombres.get(codigo) or ""
                identidad = f"{nombre} [{codigo}]" if nombre else codigo
                busqueda = f"{identidad} {codigo}".casefold()
                if filtro and filtro not in busqueda:
                    continue
                preview = self._preview_tarjeta(fila)
                item = UsuarioCell.crear(
                    {"codigo": codigo, "conectado": online}, identidad,
                    pendientes, preview, fila.fecha_ultimo)
                self.lista_usuarios.addItem(item)
                if codigo == self.usuario_seleccionado:
                    seleccionado_item = item
            # Solo restaura la seleccion VIVA si sigue visible: jamas mueve
            # al usuario a otra conversacion distinta de la viva.
            if seleccionado_item is not None:
                self.lista_usuarios.setCurrentItem(seleccionado_item)
        finally:
            self._reconstruyendo = False
            self.lista_usuarios.setUpdatesEnabled(True)
        # UIX-13: si el filtro no deja nada, decir por qué en vez de blanco.
        if self.lista_usuarios.count() == 0:
            if not recientes:
                self.lbl_vacio_usuarios.setText("Sin contactos todavía.")
            elif (self.entry_buscar.text() or "").strip() \
                    or self.combo_filtro.currentText() != "Todos":
                self.lbl_vacio_usuarios.setText("Sin resultados para esta búsqueda.")
            else:
                self.lbl_vacio_usuarios.setText("Sin contactos todavía.")
            self.lbl_vacio_usuarios.setVisible(True)
        else:
            self.lbl_vacio_usuarios.setVisible(False)

    @staticmethod
    def _preview_tarjeta(fila):
        """Una línea: espacios colapsados (como `UsuarioCell` de JavaFX)."""
        texto = " ".join((fila.ultimo_contenido or "").split())
        return texto

    def _item_por_codigo(self, codigo):
        """Fila visible de un contacto (None si el filtro la oculta)."""
        for indice in range(self.lista_usuarios.count()):
            item = self.lista_usuarios.item(indice)
            try:
                if item.data(Qt.ItemDataRole.UserRole) == codigo:
                    return item
            except RuntimeError:
                continue
        return None

    def _restaurar_ultima_conversacion(self):
        if not getattr(self, "_restauracion_ultimo_chat_pendiente", False):
            return
        self._restauracion_ultimo_chat_pendiente = False
        ultimo = self._preferencias_chat.ultimo_chat(self.codigo)
        if ultimo:
            item = self._item_por_codigo(ultimo)
            if item is not None:
                self.lista_usuarios.setCurrentItem(item)

    def _limpiar_insignia(self, codigo):
        """Pone a cero los no-leídos sin reconstruir la lista (UIX-02).

        Antes el conteo vivía dentro del texto ("  (N)") y se quitaba con una
        regex; ahora es dato (UserRole+2) y el texto sin insignia está guardado
        (UserRole+3), así que no hay nada que parsear.
        """
        item = self._item_por_codigo(codigo)
        if item is None:
            return
        try:
            item.setData(ROL_NO_LEIDOS, 0)
            base = item.data(ROL_BASE)
            if base:
                item.setText(base)
        except RuntimeError:
            return

    def _filtrar_usuarios_debounced(self, *_):
        """UIX-07: el rebuild por tecla se espera 300 ms (antes era directo)."""
        self._timer_filtro.stop()
        self._timer_filtro.start()

    def _procesar_presencia(self, mensaje):
        # Rafagas de presencia (entradas/salidas) coalescen en un solo
        # refresh: primero pinta la caché (instantáneo) y la red llega detrás.
        if not self._timer_presencia.isActive():
            self._timer_presencia.start()
        self._pintar_recientes_desde_cache()


class _RecientePlano:
    """Sustituto mínimo de `RecienteChat` para fachadas sin `recientes()`."""

    __slots__ = ("otro", "ultimo_contenido", "ultimo_tipo", "fecha_ultimo",
                 "no_leidos", "conectado")

    def __init__(self, otro, ultimo_contenido, ultimo_tipo, fecha_ultimo,
                 no_leidos, conectado):
        self.otro = otro
        self.ultimo_contenido = ultimo_contenido
        self.ultimo_tipo = ultimo_tipo
        self.fecha_ultimo = fecha_ultimo
        self.no_leidos = no_leidos
        self.conectado = conectado
