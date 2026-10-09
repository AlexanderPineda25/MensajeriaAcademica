package universidad.mensajeria.cliente.fachada.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PendienteEnvio;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

/**
 * El reintento de la cola local vive en su propio servicio (SRP):
 * sin sesión no intenta, con red vacía la cola y sin red la conserva.
 */
class ReintentadorPendientesTest {

    private HistorialLocal historial;
    private RedControlable red;
    private String sesion = "A001";

    @BeforeEach
    void montar() throws Exception {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:reintento"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(props));
        store.inicializar();
        historial = new HistorialLocal(store);
        red = new RedControlable();
    }

    @Test
    void sinSesionNoIntentaNada() throws Exception {
        sesion = null;
        pendienteTexto("m1", "hola");

        int enviados = servicio().reintentarPendientes();

        assertEquals(0, enviados);
        assertEquals(0, red.envios);
        assertEquals(1, historial.pendientes().size());
    }

    @Test
    void conRedReenviaTextoYVacíaLaCola() throws Exception {
        pendienteTexto("m1", "hola");

        int enviados = servicio().reintentarPendientes();

        assertEquals(1, enviados);
        assertEquals(1, red.envios);
        assertTrue(historial.pendientes().isEmpty());
    }

    @Test
    void sinRedConservaElPendienteYCuentaElIntento() throws Exception {
        red.fallaEnvio = true;
        pendienteTexto("m1", "hola");

        int enviados = servicio().reintentarPendientes();

        assertEquals(0, enviados);
        assertEquals(1, historial.pendientes().size());
        assertEquals(1, historial.pendientes().get(0).intentos());
    }

    @Test
    void desconectadoNoIntentaYDejaLaColaIntacta() throws Exception {
        red.caida = true;
        pendienteTexto("m1", "hola");

        int enviados = servicio().reintentarPendientes();

        assertEquals(0, enviados);
        assertEquals(0, red.envios);
        assertEquals(1, historial.pendientes().size());
        assertEquals(0, historial.pendientes().get(0).intentos());
    }

    @Test
    void rechazoDelServidorDescartaElPendienteComoError() throws Exception {
        red.rechazo = "duplicado";
        pendienteTexto("m1", "hola");

        servicio().reintentarPendientes();

        assertTrue(historial.pendientes().isEmpty());
    }

    private ReintentadorPendientesCliente servicio() {
        return new ReintentadorPendientesCliente(red, historial, () -> sesion);
    }

    private void pendienteTexto(String id, String contenido) throws Exception {
        historial.guardar(new MensajeLocal(id, "A001", "B002",
                TipoMensajeCliente.MENSAJE_TEXTO.name(), contenido, null, contenido.length(),
                1, null, null, null, LocalDateTime.now().toString(), true, true,
                EstadoMensaje.PENDIENTE.name()));
        historial.registrarPendiente(new PendienteEnvio(id,
                TipoMensajeCliente.MENSAJE_TEXTO.name(), "A001", "B002", contenido,
                null, null, LocalDateTime.now().toString(), 0, null));
    }

    /** Red mínima: éxito configurable, caída, fallo de envío o rechazo por bandera. */
    private static final class RedControlable implements InterfazConexionRed {
        private boolean caida;
        private boolean fallaEnvio;
        private String rechazo;
        private int envios;

        @Override
        public void conectar() {
        }

        @Override
        public void desconectar() {
        }

        @Override
        public boolean conectado() {
            return !caida;
        }

        @Override
        public void suscribir(universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes receptor) {
        }

        private universidad.mensajeria.cliente.transversal.records.RespuestaRed ack()
                throws IOException {
            if (caida || fallaEnvio) {
                throw new IOException("corte simulado");
            }
            envios++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK)
                    .exito(rechazo == null).mensajeError(rechazo).build());
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed login(
                String codigo, String contrasena) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarTexto(
                String remitente, String destinatario, String contenido) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarTexto(
                String id, String remitente, String destinatario, String contenido) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarImagen(
                String remitente, String destinatario, String nombreArchivo, String mime,
                String contenidoBase64) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarImagen(
                String id, String remitente, String destinatario, String nombreArchivo,
                String mime, String contenidoBase64) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarArchivo(
                String remitente, String destinatario, String nombreArchivo, String mime,
                String contenidoBase64) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarArchivo(
                String id, String remitente, String destinatario, String nombreArchivo,
                String mime, String contenidoBase64) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed difundir(
                String remitente, String contenido) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed listarConectados() {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed expulsar(String codigo) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed solicitarHistorial(
                String remitente, String destinatario, int pagina) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed descargarArchivo(
                String archivoId) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public void logout() {
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed enviarLectura(
                String lector, String autor, String idOriginal) throws IOException {
            return ack();
        }

        @Override
        public universidad.mensajeria.cliente.transversal.records.RespuestaRed listarUsuarios() {
            throw new UnsupportedOperationException("fuera de alcance");
        }
    }
}
