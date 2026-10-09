package universidad.mensajeria.cliente.fachada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.cliente.componentes.autenticacion.Autenticacion;
import universidad.mensajeria.cliente.componentes.cache.CacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

/**
 * El receptor entrante persiste y traduce sin decidir políticas (SRP):
 * guarda entrantes, marca acuses, aplica presencia y distingue el cierre.
 */
class ReceptorEntranteTest {

    private HistorialLocal historial;
    private CacheUsuarios cache;
    private SalidasFalsas salidas;
    private ReceptorEntranteCliente receptor;

    @BeforeEach
    void montar() throws Exception {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:receptor"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(props));
        store.inicializar();
        historial = new HistorialLocal(store);
        cache = new CacheUsuarios(store);
        InterfazConexionRed red = new RedFalsa();
        salidas = new SalidasFalsas();
        receptor = new ReceptorEntranteCliente(red, new Autenticacion(red), historial,
                cache, new MapeadorDatosServidor(), salidas);
    }

    @Test
    void textoEntranteSeGuardaYSePublica() throws Exception {
        receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_TEXTO).id("m1").remitente("B002")
                .destinatario("A001").contenido("hola")
                .etapasFiltrado("[{\"etapa\":\"grises\",\"ms\":12}]")
                .fechaHora(LocalDateTime.now().toString()).build()));

        List<MensajeLocal> pagina = historial.pagina("A001", "B002", 10, 0);
        assertEquals(1, pagina.size());
        assertEquals("hola", pagina.get(0).contenido());
        assertEquals("[{\"etapa\":\"grises\",\"ms\":12}]",
                pagina.get(0).etapasFiltrado());
        assertEquals(1, salidas.eventos.size());
        assertEquals(TipoMensajeCliente.MENSAJE_TEXTO, salidas.eventos.get(0).tipo());
    }

    @Test
    void acuseMarcaElEstadoDelMensaje() throws Exception {
        historial.guardar(new MensajeLocal("m1", "A001", "B002",
                TipoMensajeCliente.MENSAJE_TEXTO.name(), "hola", null, 4, 1,
                null, null, null, LocalDateTime.now().toString(), true, true,
                EstadoMensaje.ENVIADO.name()));

        receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_LEIDO).contenido("m1").build()));

        List<MensajeLocal> pagina = historial.pagina("A001", "B002", 10, 0);
        assertEquals(EstadoMensaje.LEIDO.name(), pagina.get(0).estado());
    }

    @Test
    void presenciaActualizaLaCache() throws Exception {
        receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.PRESENCIA).codigo("B002")
                .usuariosConectados(List.of("B002")).build()));

        assertTrue(cache.porCodigo("B002").isPresent());
        assertEquals(1, salidas.eventos.size());
    }

    @Test
    void cierreDelServidorPideVolverAEntrar() {
        receptor.alCierre(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.CLOSE_NOTICE).build()));

        assertEquals(EstadoConexion.SESION_CERRADA, salidas.ultimoEstado);
        assertTrue(salidas.olvidoCredenciales);
    }

    @Test
    void corteDeRedPasaASinRedYProgramaReconexion() {
        receptor.alDesconexion();

        assertEquals(EstadoConexion.SIN_RED, salidas.ultimoEstado);
        assertTrue(salidas.reconexionProgramada);
    }

    @Test
    void corteConSesionYaCerradaNoReintenta() {
        salidas.estado = EstadoConexion.SESION_CERRADA;

        receptor.alDesconexion();

        assertTrue(!salidas.reconexionProgramada);
    }

    private static final class SalidasFalsas implements ReceptorEntranteCliente.Salidas {
        private final List<RespuestaRed> eventos = new ArrayList<>();
        private EstadoConexion estado = EstadoConexion.CONECTADO;
        private EstadoConexion ultimoEstado;
        private boolean reconexionProgramada;
        private boolean olvidoCredenciales;

        @Override
        public EstadoConexion estadoConexion() {
            return estado;
        }

        @Override
        public void cambiarEstado(EstadoConexion nuevo) {
            estado = nuevo;
            ultimoEstado = nuevo;
        }

        @Override
        public void publicarEvento(RespuestaRed mensaje) {
            eventos.add(mensaje);
        }

        @Override
        public void programarReconexion() {
            reconexionProgramada = true;
        }

        @Override
        public void olvidarCredenciales() {
            olvidoCredenciales = true;
        }
    }

    private static final class RedFalsa implements InterfazConexionRed {
        @Override
        public void conectar() {
        }

        @Override
        public void desconectar() {
        }

        @Override
        public boolean conectado() {
            return true;
        }

        @Override
        public void suscribir(ReceptorMensajes receptor) {
        }

        @Override
        public RespuestaRed login(String codigo, String contrasena) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed enviarTexto(String remitente, String destinatario, String contenido) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed enviarImagen(String remitente, String destinatario, String nombreArchivo,
                                         String mime, String contenidoBase64) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed difundir(String remitente, String contenido) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed listarConectados() {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed expulsar(String codigo) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public RespuestaRed descargarArchivo(String archivoId) {
            throw new UnsupportedOperationException("fuera de alcance");
        }

        @Override
        public void logout() {
        }
    }
}
