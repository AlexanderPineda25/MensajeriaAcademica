package universidad.mensajeria.cliente.fachada;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import universidad.mensajeria.cliente.componentes.autenticacion.Autenticacion;
import universidad.mensajeria.cliente.componentes.cache.CacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;
import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 1: Caracterización de recorridos del chat, mensajería, orden de recientes,
 * paginación, búsqueda, acuses y ciclo de vida de sesiones.
 */
class ChatRecorridosTest {

    private RedPrueba red;
    private FachadaCliente fachada;
    private HistorialLocal historial;
    private CacheUsuarios cache;
    private final List<EventoCliente> eventos = new ArrayList<>();

    @BeforeEach
    void iniciar() throws Exception {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:recorridos" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(props));
        store.inicializar();
        historial = new HistorialLocal(store);
        cache = new CacheUsuarios(store);
        red = new RedPrueba();
        fachada = new FachadaCliente(red, new Autenticacion(red), historial, cache,
                new MapeadorDatosServidor());
        fachada.suscribirEventos(eventos::add);
    }

    @AfterEach
    void terminar() {
        if (fachada != null) {
            fachada.cerrar();
        }
    }

    @Test
    void ordenDeRecientesPriorizaMensajeMasReciente() throws Exception {
        assertTrue(fachada.login("A001", "12345"));

        // Guardar interacción con B002 y con C003
        historial.guardar(new MensajeLocal("m1", "A001", "B002", "MENSAJE_TEXTO",
                "primero con B", null, 14, 3, null, null, null,
                "2026-01-01T10:00:00", true, true, "ENTREGADO", null));
        historial.guardar(new MensajeLocal("m2", "A001", "C003", "MENSAJE_TEXTO",
                "luego con C", null, 11, 3, null, null, null,
                "2026-01-01T10:10:00", true, true, "ENTREGADO", null));

        List<RecienteChat> recientes = fachada.recientes();
        assertEquals(2, recientes.size());
        assertEquals("C003", recientes.get(0).otro(), "El chat con C003 fue más reciente");
        assertEquals("B002", recientes.get(1).otro());

        // Ahora B responde y supera a C
        historial.guardar(new MensajeLocal("m3", "B002", "A001", "MENSAJE_TEXTO",
                "ahora responde B", null, 16, 3, null, null, null,
                "2026-01-01T10:20:00", false, true, "ENTREGADO", null));

        List<RecienteChat> actualizados = fachada.recientes();
        assertEquals("B002", actualizados.get(0).otro(), "B002 pasa al primer puesto");
    }

    @Test
    void envioDeTextoImagenYArchivoSinTexto() throws Exception {
        assertTrue(fachada.login("A001", "12345"));

        // 1. Envío de texto
        assertTrue(fachada.enviarTexto("B002", "mensaje de prueba"));

        // 2. Envío de imagen
        byte[] bytesImagen = new byte[]{1, 2, 3, 4};
        assertTrue(fachada.enviarImagen("B002", bytesImagen, "foto.png", "image/png"));

        // 3. Envío de archivo (PDF/documento)
        byte[] bytesDoc = "contenido del documento".getBytes(StandardCharsets.UTF_8);
        assertTrue(fachada.enviarArchivo("B002", bytesDoc, "documento.pdf", "application/pdf"));

        List<MensajeLocal> conv = fachada.conversacion("B002", 10, 0);
        assertEquals(3, conv.size());
        assertEquals(3, red.envios);
    }

    @Test
    void paginacionYBusquedaEnHistorial() throws Exception {
        assertTrue(fachada.login("A001", "12345"));

        for (int i = 1; i <= 15; i++) {
            historial.guardar(new MensajeLocal("id-" + i, "A001", "B002", "MENSAJE_TEXTO",
                    "mensaje numero " + i, null, 15, 3, null, null, null,
                    String.format("2026-01-01T10:%02d:00", i), true, true, "ENTREGADO", null));
        }

        // Paginación con límite 5
        List<MensajeLocal> pagina1 = fachada.conversacion("B002", 5, 0);
        assertEquals(5, pagina1.size());
        assertEquals(15, fachada.contarConversacion("B002"));

        List<MensajeLocal> pagina2 = fachada.conversacion("B002", 5, 5);
        assertEquals(5, pagina2.size());

        // Búsqueda con y sin resultados
        List<String> resultados1 = fachada.buscarEnHistorial("B002", "numero 10");
        assertEquals(1, resultados1.size());
        assertEquals("id-10", resultados1.get(0));

        List<String> sinResultados = fachada.buscarEnHistorial("B002", "palabraInexistente");
        assertTrue(sinResultados.isEmpty());
    }

    @Test
    void acusesDeLecturaYMarcadoDeLeidos() throws Exception {
        assertTrue(fachada.login("A001", "12345"));

        // Entran mensajes de B002
        historial.guardar(new MensajeLocal("m-no-leido-1", "B002", "A001", "MENSAJE_TEXTO",
                "hola", null, 4, 1, null, null, null,
                "2026-01-01T10:00:00", false, false, "ENTREGADO", null));
        historial.guardar(new MensajeLocal("m-no-leido-2", "B002", "A001", "MENSAJE_TEXTO",
                "estas?", null, 6, 1, null, null, null,
                "2026-01-01T10:01:00", false, false, "ENTREGADO", null));

        assertEquals(2, historial.idsNoLeidos("A001", "B002").size());

        fachada.marcarLeidos("B002");

        assertEquals(0, historial.idsNoLeidos("A001", "B002").size());
        assertEquals(2, red.lecturasEnviadas, "debe enviar acuse de lectura al autor por cada mensaje");
    }

    @Test
    void expulsionDeOtrasSesionesYCierreRemoto() throws Exception {
        assertTrue(fachada.login("A001", "12345"));

        fachada.expulsarOtrasSesiones();
        assertEquals(1, red.vecesExpulsar);

        // Simular aviso CLOSE_NOTICE desde el servidor
        red.simularCierreRemoto();

        assertEquals(EstadoConexion.SESION_CERRADA, fachada.estadoConexion());
        assertTrue(eventos.stream().anyMatch(e -> e.tipo() == EventoCliente.Tipo.SESION_CERRADA));
    }

    private static final class RedPrueba implements InterfazConexionRed {
        private ReceptorMensajes receptor;
        private boolean conectado = true;
        int envios = 0;
        int lecturasEnviadas = 0;
        int vecesExpulsar = 0;

        @Override
        public void conectar() {
            conectado = true;
        }

        @Override
        public void desconectar() {
            conectado = false;
        }

        @Override
        public boolean conectado() {
            return conectado;
        }

        @Override
        public void suscribir(ReceptorMensajes receptor) {
            this.receptor = receptor;
        }

        @Override
        public RespuestaRed login(String codigo, String contrasena) {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.LOGIN_RESPUESTA)
                    .exito(true).build());
        }

        @Override
        public RespuestaRed enviarTexto(String id, String remitente, String destinatario, String contenido) {
            envios++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).id(id).build());
        }

        @Override
        public RespuestaRed enviarTexto(String remitente, String destinatario, String contenido) {
            return enviarTexto("test-id", remitente, destinatario, contenido);
        }

        @Override
        public RespuestaRed enviarImagen(String remitente, String destinatario, String nombreArchivo,
                                    String mime, String contenidoBase64) {
            envios++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.IMAGE_FILTERED_RESULT)
                    .exito(true).hashSha256("hash").archivoId("img-id").build());
        }

        @Override
        public RespuestaRed enviarArchivo(String remitente, String destinatario, String nombreArchivo,
                                     String mime, String contenidoBase64) {
            envios++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).archivoId("doc-id").build());
        }

        @Override
        public RespuestaRed difundir(String remitente, String contenido) {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).build());
        }

        @Override
        public RespuestaRed listarConectados() {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.LISTAR_CONECTADOS_RESPUESTA)
                    .usuariosConectados(List.of("A001", "B002")).build());
        }

        @Override
        public RespuestaRed expulsar(String codigo) {
            vecesExpulsar++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).build());
        }

        @Override
        public RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina) {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.HISTORIAL_PAGE)
                    .contenido("[]").build());
        }

        @Override
        public RespuestaRed descargarArchivo(String archivoId) {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true)
                    .contenido(Base64.getEncoder().encodeToString(new byte[]{1, 2})).build());
        }

        @Override
        public void logout() {
            desconectar();
        }

        @Override
        public RespuestaRed enviarLectura(String lector, String autor, String idOriginal) {
            lecturasEnviadas++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).id(idOriginal).build());
        }

        void simularCierreRemoto() {
            if (receptor != null) {
                receptor.alCierre(RespuestaRedPrueba.desde(Mensaje.builder()
                        .tipo(TipoMensaje.CLOSE_NOTICE).mensajeError("Sesión cerrada").build()));
            }
        }
    }
}
