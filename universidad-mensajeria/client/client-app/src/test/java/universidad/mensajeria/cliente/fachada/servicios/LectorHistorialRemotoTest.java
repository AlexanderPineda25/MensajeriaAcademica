package universidad.mensajeria.cliente.fachada.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.fachada.RedParaPruebas;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;

/**
 * La lectura remota vive en su propio servicio (SRP): trae páginas, las
 * persiste y descarga bytes bajo demanda sin traer la lista entera.
 */
class LectorHistorialRemotoTest {

    private HistorialLocal historial;
    private RedParaPruebas red;
    private String sesion = "A001";

    @BeforeEach
    void montar() throws Exception {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:lector"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(props));
        store.inicializar();
        historial = new HistorialLocal(store);
        red = new RedParaPruebas();
    }

    @Test
    void traerHistorialPersisteLaPagina() throws Exception {
        red.paginaHistorial("""
                [{"id": 9001, "remitente": "B002", "destinatario": "A001",
                  "tipo": "MENSAJE_TEXTO", "contenido": "hola viejo",
                  "fechaEnvio": "2025-01-01T10:00:00",
                  "etapasFiltrado": "[{\\\"etapa\\\":\\\"sepia\\\",\\\"ms\\\":7}]"}]""", 3);

        var pagina = servicio().traerHistorial("B002", 0);

        assertEquals(1, pagina.recibidos());
        assertEquals(3, pagina.totalPaginas());
        assertEquals("hola viejo", servicio().blobMensaje("9001").orElseThrow());
        assertEquals("[{\"etapa\":\"sepia\",\"ms\":7}]",
                historial.pagina("A001", "B002", 10, 0).get(0).etapasFiltrado());
    }

    @Test
    void conversacionDevuelveOrdenCronologicoParaPintar() throws Exception {
        guardar("m1", "2025-01-01T10:00:00", "primero");
        guardar("m2", "2025-01-02T10:00:00", "segundo");

        var pagina = servicio().conversacion("B002", 10, 0);

        assertEquals(List.of("m1", "m2"),
                pagina.stream().map(MensajeLocal::idMensaje).toList());
        assertEquals(2, servicio().contarConversacion("B002"));
    }

    @Test
    void descargarImagenGuardaBytesYReusaLaCache() throws Exception {
        red.paginaHistorial("""
                [{"id": 9003, "remitente": "B002", "destinatario": "A001",
                  "tipo": "MENSAJE_IMAGEN", "nombreArchivo": "otra.png",
                  "archivoId": "78", "fechaEnvio": "2025-01-03T10:00:00"}]""", 1);
        servicio().traerHistorial("B002", 0);

        byte[] primera = servicio().descargarImagen("9003");
        byte[] segunda = servicio().descargarImagen("9003");

        assertEquals(3, primera.length);
        assertEquals(3, segunda.length);
        assertEquals("AAAA", historial.blobPorId("9003").orElseThrow());
    }

    @Test
    void sinSesionRechazaLaLectura() {
        sesion = null;

        assertThrows(ExcepcionSesion.class, () -> servicio().conversacion("B002", 10, 0));
        assertThrows(ExcepcionSesion.class, () -> servicio().traerHistorial("B002", 0));
    }

    @Test
    void descargaSinArchivoIdPideActualizar() {
        assertThrows(ExcepcionSesion.class, () -> servicio().descargarImagen("inexistente"));
    }

    private LectorHistorialRemotoCliente servicio() {
        return new LectorHistorialRemotoCliente(red, historial,
                new MapeadorDatosServidor(),
                new CoordinadorHistorialCliente(historial), () -> sesion);
    }

    private void guardar(String id, String fecha, String contenido) throws Exception {
        historial.guardar(new MensajeLocal(id, "A001", "B002",
                TipoMensajeCliente.MENSAJE_TEXTO.name(), contenido, null, contenido.length(),
                1, null, null, null, fecha, true, true, EstadoMensaje.ENVIADO.name()));
    }
}
