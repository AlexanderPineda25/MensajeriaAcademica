package universidad.mensajeria.cliente.fachada.servicios;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PendienteEnvio;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de CoordinadorMensajeriaCliente usando dobles manuales
 * (sin Mockito, alineado con el resto del cliente JavaFX).
 */
@DisplayName("CoordinadorMensajeriaCliente — envío de mensajes")
class CoordinadorMensajeriaClienteTest {

    // -------------------------------------------------------------------------
    // Dobles de prueba
    // -------------------------------------------------------------------------

    /** Stub de red: controla respuesta y puede simular IOException. */
    static class RedStub implements InterfazConexionRed {
        RespuestaRed respuesta = RespuestaRedPrueba.desde(
                Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).exito(true).build());
        boolean lanzarIOException = false;

        @Override public void conectar() {}
        @Override public void desconectar() {}
        @Override public boolean conectado() { return true; }
        @Override public void suscribir(ReceptorMensajes r) {}
        @Override public RespuestaRed login(String c, String p) { return respuesta; }
        @Override public void logout() {}

        @Override
        public RespuestaRed enviarTexto(String de, String a, String c) throws IOException {
            if (lanzarIOException) throw new IOException("sin red");
            return respuesta;
        }
        @Override
        public RespuestaRed enviarImagen(String de, String a,
                                    String nombre, String mime, String base64) throws IOException {
            if (lanzarIOException) throw new IOException("sin red");
            return respuesta;
        }
        @Override
        public RespuestaRed enviarArchivo(String de, String a, String nombre,
                                    String mime, String base64) throws IOException {
            if (lanzarIOException) throw new IOException("sin red");
            return respuesta;
        }
        @Override
        public RespuestaRed listarConectados() { return respuesta; }
        @Override
        public RespuestaRed expulsar(String c) { return respuesta; }
        @Override
        public RespuestaRed difundir(String de, String c) { return respuesta; }
        @Override
        public RespuestaRed solicitarHistorial(String yo, String otro, int pag) { return respuesta; }
        @Override
        public RespuestaRed descargarArchivo(String id) { return respuesta; }
    }

    /** Historial en memoria que registra las llamadas recibidas. */
    static class HistorialMemoria implements InterfazHistorialLocal {
        final List<MensajeLocal> guardados = new ArrayList<>();
        final List<String> estadosMarcados = new ArrayList<>();
        final List<PendienteEnvio> pendientesRegistrados = new ArrayList<>();
        final List<String> archivoIdsActualizados = new ArrayList<>();
        String etapasFiltrado;

        @Override public void guardar(MensajeLocal m) { guardados.add(m); }
        @Override public void marcarEstado(String id, String estado) {
            estadosMarcados.add(id + "=" + estado);
        }
        @Override public void registrarPendiente(PendienteEnvio p) { pendientesRegistrados.add(p); }
        @Override public void actualizarArchivoId(String id, String archivoId) {
            archivoIdsActualizados.add(id + "=" + archivoId);
        }
        @Override public void actualizarEtapasFiltrado(String id, String etapas) {
            etapasFiltrado = etapas;
        }

        // Métodos del contrato no usados en estas pruebas
        @Override public List<MensajeLocal> pagina(String yo, String otro, int lim, int off) { return List.of(); }
        @Override public int contar(String yo, String otro) { return 0; }
        @Override public List<MensajeLocal> buscar(String yo, String otro, String p, int lim) { return List.of(); }
        @Override public void marcarDescargado(String id, String ruta) {}
        @Override public void marcarLeidosDe(String yo, String otro) {}
        @Override public List<String> idsNoLeidos(String yo, String otro) { return List.of(); }
        @Override public List<RecienteChat> recientes(String yo) { return List.of(); }
        @Override public Optional<String> blobPorId(String id) { return Optional.empty(); }
        @Override public Optional<String> archivoIdPorId(String id) { return Optional.empty(); }
        @Override public void actualizarContenido(String id, String blob) {}
        @Override public List<PendienteEnvio> pendientes() { return List.of(); }
        @Override public void incrementarIntento(String id, String error) {}
        @Override public void eliminarPendiente(String id) {}
        @Override public Optional<PendienteEnvio> pendientePorId(String id) { return Optional.empty(); }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    RedStub red;
    HistorialMemoria historial;
    CoordinadorMensajeriaCliente coordinador;

    @BeforeEach
    void setUp() {
        red = new RedStub();
        historial = new HistorialMemoria();
        coordinador = new CoordinadorMensajeriaCliente(red, historial);
    }

    // -------------------------------------------------------------------------
    // enviarTexto
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("enviarTexto — red ok → devuelve true y marca ENVIADO")
    void enviarTextoConRed() throws Exception {
        boolean resultado = coordinador.enviarTexto("YO", "DEST", "hola mundo");

        assertTrue(resultado);
        assertFalse(historial.guardados.isEmpty(), "debe guardar antes de enviar");
        assertTrue(historial.estadosMarcados.stream()
                .anyMatch(s -> s.endsWith("=" + EstadoMensaje.ENVIADO.name())));
        assertTrue(historial.pendientesRegistrados.isEmpty());
    }

    @Test
    @DisplayName("enviarTexto — IOException → devuelve false y registra pendiente")
    void enviarTextoSinRed() throws Exception {
        red.lanzarIOException = true;

        boolean resultado = coordinador.enviarTexto("YO", "DEST", "hola mundo");

        assertFalse(resultado);
        assertEquals(1, historial.pendientesRegistrados.size());
        assertEquals(TipoMensaje.MENSAJE_TEXTO.name(),
                historial.pendientesRegistrados.get(0).tipo());
    }

    @Test
    @DisplayName("enviarTexto — servidor rechaza exito=false → IllegalStateException y marca ERROR")
    void enviarTextoRechazado() {
        red.respuesta = RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_TEXTO)
                .exito(false).mensajeError("prohibido").build());

        assertThrows(IllegalStateException.class,
                () -> coordinador.enviarTexto("YO", "DEST", "hola"));
        assertTrue(historial.estadosMarcados.stream()
                .anyMatch(s -> s.endsWith("=" + EstadoMensaje.ERROR.name())));
    }

    // -------------------------------------------------------------------------
    // enviarImagen
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("enviarImagen — red ok persiste archivoId y etapas del filtro")
    void enviarImagenConArchivoId() throws Exception {
        red.respuesta = RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_IMAGEN)
                .exito(true).archivoId("img-123")
                .etapasFiltrado("[{\"etapa\":\"grises\",\"ms\":12}]").build());
        byte[] bytes = {1, 2, 3};

        boolean resultado = coordinador.enviarImagen("YO", "DEST", bytes, "foto.jpg", "image/jpeg");

        assertTrue(resultado);
        assertEquals(1, historial.archivoIdsActualizados.size());
        assertTrue(historial.archivoIdsActualizados.get(0).endsWith("=img-123"));
        assertEquals("[{\"etapa\":\"grises\",\"ms\":12}]", historial.etapasFiltrado);
    }

    @Test
    @DisplayName("enviarImagen — IOException → registra pendiente con los bytes originales")
    void enviarImagenSinRed() throws Exception {
        red.lanzarIOException = true;
        byte[] bytes = {1, 2, 3};

        boolean resultado = coordinador.enviarImagen("YO", "DEST", bytes, "foto.jpg", "image/jpeg");

        assertFalse(resultado);
        assertEquals(1, historial.pendientesRegistrados.size());
        assertArrayEquals(bytes, historial.pendientesRegistrados.get(0).bytes());
    }

    // -------------------------------------------------------------------------
    // enviarArchivo
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("enviarArchivo — bytes vacíos → excepción sin tocar red ni historial")
    void enviarArchivoVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> coordinador.enviarArchivo("YO", "DEST", new byte[0], "doc.pdf", null));
        assertTrue(historial.guardados.isEmpty(), "no debe guardar si la validación falla");
    }

    @Test
    @DisplayName("enviarArchivo — red ok con mime nulo → usa mime por defecto y marca ENVIADO")
    void enviarArchivoConRedMimeNulo() throws Exception {
        red.respuesta = RespuestaRedPrueba.desde(
                Mensaje.builder().tipo(TipoMensaje.MENSAJE_ARCHIVO).exito(true).build());
        byte[] bytes = {9, 8, 7};

        boolean resultado = coordinador.enviarArchivo("YO", "DEST", bytes, "doc.pdf", null);

        assertTrue(resultado);
        assertTrue(historial.estadosMarcados.stream()
                .anyMatch(s -> s.endsWith("=" + EstadoMensaje.ENVIADO.name())));
    }
}
