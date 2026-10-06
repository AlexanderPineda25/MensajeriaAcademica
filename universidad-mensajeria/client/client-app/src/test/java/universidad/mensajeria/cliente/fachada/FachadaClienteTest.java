package universidad.mensajeria.cliente.fachada;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import universidad.mensajeria.cliente.componentes.autenticacion.Autenticacion;
import universidad.mensajeria.cliente.componentes.cache.CacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.presentacion.ChatController;
import universidad.mensajeria.cliente.presentacion.ClienteApp;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FachadaCliente con red falsa y DAOs reales (H2 en memoria):
 * online guarda, offline encola pendiente y reintenta al reconectar.
 */
class FachadaClienteTest {

    private RedFalsa red;
    private FachadaCliente fachada;
    private HistorialLocal historial;
    private final List<EventoCliente> eventosUi = new ArrayList<>();

    @AfterEach
    void apagarWorkers() {
        if (fachada != null) fachada.cerrar();
    }

    @BeforeEach
    void montar() throws Exception {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:fachada" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(props));
        store.inicializar();
        historial = new HistorialLocal(store);
        CacheUsuarios cache = new CacheUsuarios(store);
        red = new RedFalsa();
        fachada = new FachadaCliente(red, new Autenticacion(red), historial, cache,
                new MapeadorDatosServidor());
        fachada.suscribirEventos(eventosUi::add);
    }

    @Test
    void loginGuardaSesionYReintentaPendientes() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        assertEquals("A001", fachada.codigoActual());
        assertEquals(1, red.vecesLogin);
    }

    @Test
    void credencialesRechazadasNoAbrenSesionNiSeConservanParaReconectar() throws Exception {
        red.loginRechazado = true;

        assertFalse(fachada.login("A001", "incorrecta"));
        assertNull(fachada.codigoActual());
        assertEquals(EstadoConexion.SIN_RED, fachada.estadoConexion());
    }

    @Test
    void enviarTextoOnlineGuardaLocalSinPendiente() throws Exception {
        fachada.login("A001", "Secreta123");

        assertTrue(fachada.enviarTexto("B002", "hola"));

        List<MensajeLocal> conversacion = fachada.conversacion("B002", 10, 0);
        assertEquals(1, conversacion.size());
        assertEquals(conversacion.get(0).idMensaje(), red.idTexto,
                "el ID de H2 se usa también para correlacionar el envío TCP");
        assertEquals("hola", fachada.blobMensaje(conversacion.get(0).idMensaje()).orElseThrow());
        assertTrue(conversacion.get(0).enviado());
        assertTrue(fachada.pendientes().isEmpty());
    }

    @Test
    void flushWorkerVaciaPendientesSinLlamadaExplicita() throws Exception {
        fachada.login("A001", "Secreta123");
        fachada.detenerFlushPendientes();
        red.caida = true;

        assertFalse(fachada.enviarTexto("B002", "hola luego"));
        assertEquals(1, fachada.pendientes().size());

        red.caida = false;
        fachada.iniciarFlushPendientes(5);
        try {
            long limite = System.currentTimeMillis() + 15000;
            while (!fachada.pendientes().isEmpty() && System.currentTimeMillis() < limite) {
                Thread.sleep(100);
            }
            assertTrue(fachada.pendientes().isEmpty(), "el worker debio reenviar solo");
        } finally {
            fachada.detenerFlushPendientes();
        }
    }

    @Test
    void enviarTextoOfflineGuardaLocalYEncolaPendiente() throws Exception {
        fachada.login("A001", "Secreta123");
        red.caida = true;

        assertFalse(fachada.enviarTexto("B002", "hola luego"));

        MensajeLocal fila = fachada.conversacion("B002", 10, 0).get(0);
        assertEquals(1, fachada.pendientes().size());

        red.caida = false;
        assertEquals(1, fachada.reintentarPendientes());
        assertEquals(fila.idMensaje(), red.idTexto, "el reintento conserva el ID de correlación");
        assertTrue(fachada.pendientes().isEmpty());
    }

    @Test
    void mensajeRecibidoSePersisteYSeAvisaALaUi() throws Exception {
        fachada.login("A001", "Secreta123");

        red.receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO)
                .id("srv-1").fechaHora(LocalDateTime.now().toString())
                .remitente("B002").destinatario("A001").contenido("hola A")
                .hashSha256("h").numCaracteres(6).numPalabras(2).build()));

        List<MensajeLocal> conversacion = fachada.conversacion("B002", 10, 0);
        assertEquals(1, conversacion.size());
        // FASE 13.9: la lista es metadata; el cuerpo va por blobMensaje.
        assertEquals("hola A", fachada.blobMensaje(conversacion.get(0).idMensaje()).orElseThrow());
        assertFalse(conversacion.get(0).enviado());
        assertEquals(1, eventosUi.size());
    }

    @Test
    void broadcastRecibidoApareceEnConversacionConSuRemitente() throws Exception {
        fachada.login("A001", "Secreta123");

        red.receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.BROADCAST)
                .id("bc-1").fechaHora(LocalDateTime.now().toString())
                .remitente("B002").contenido("aviso general").build()));

        List<MensajeLocal> conversacion = fachada.conversacion("B002", 10, 0);
        assertEquals(1, conversacion.size());
        assertEquals("aviso general",
                fachada.blobMensaje(conversacion.get(0).idMensaje()).orElseThrow());
        assertEquals("B002", conversacion.get(0).origen());
    }

    @Test
    void conversacionVieneEnOrdenCronologico() throws Exception {        fachada.login("A001", "Secreta123");
        fachada.enviarTexto("B002", "primero");
        // El orden del contrato es por fecha_envio: separar marcas de tiempo.
        Thread.sleep(20);
        fachada.enviarTexto("B002", "segundo");

        List<MensajeLocal> conversacion = fachada.conversacion("B002", 10, 0);

        assertEquals(2, conversacion.size());
        assertEquals("primero",
                fachada.blobMensaje(conversacion.get(0).idMensaje()).orElseThrow());
        assertEquals("segundo",
                fachada.blobMensaje(conversacion.get(1).idMensaje()).orElseThrow());
    }

    /** Red programable: responde éxito o simula corte. */
    private static final class RedFalsa implements InterfazConexionRed {
        private ReceptorMensajes receptor;
        private boolean caida;
        private boolean conectada = true;
        private boolean loginRechazado;
        private int vecesLogin;

        @Override
        public void conectar() {
            conectada = true;
        }

        @Override
        public void desconectar() {
            conectada = false;
        }

        @Override
        public boolean conectado() {
            return conectada && !caida;
        }

        @Override
        public void suscribir(ReceptorMensajes receptor) {
            this.receptor = receptor;
        }

        /** PAT-03: el hilo lector muere (corte de red). */
        void caer() {
            conectada = false;
            if (receptor != null) {
                receptor.alDesconexion();
            }
        }

        /** PAT-03: el servidor manda CLOSE_NOTICE y cierra. */
        void cerrarSesion() {
            conectada = false;
            if (receptor != null) {
                receptor.alCierre(RespuestaRedPrueba.desde(
                        Mensaje.builder().tipo(TipoMensaje.CLOSE_NOTICE).build()));
            }
        }

        @Override
        public RespuestaRed login(String codigo, String contrasena) {
            vecesLogin++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.LOGIN_RESPUESTA)
                    .exito(!loginRechazado)
                    .mensajeError(loginRechazado ? "credenciales invalidas" : null).build());
        }

        private String idTexto;
        private String idImagen;
        private int vecesDescarga;
        private boolean fallaDescarga;

        @Override
        public RespuestaRed enviarTexto(String remitente, String destinatario, String contenido)
                throws IOException {
            if (caida) {
                throw new IOException("corte simulado");
            }
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).build());
        }

        @Override
        public RespuestaRed enviarTexto(String id, String remitente, String destinatario, String contenido)
                throws IOException {
            idTexto = id;
            return enviarTexto(remitente, destinatario, contenido);
        }

        @Override
        public RespuestaRed enviarImagen(String remitente, String destinatario, String nombreArchivo,
                                    String mime, String contenidoBase64) throws IOException {
            if (caida) {
                throw new IOException("corte simulado");
            }
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.IMAGE_FILTERED_RESULT)
                    .exito(true).hashSha256("h").etapasFiltrado("[]").archivoId("1").build());
        }

        @Override
        public RespuestaRed enviarImagen(String id, String remitente, String destinatario,
                                    String nombreArchivo, String mime, String contenidoBase64)
                throws IOException {
            idImagen = id;
            return enviarImagen(remitente, destinatario, nombreArchivo, mime, contenidoBase64);
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
        public RespuestaRed listarUsuarios() {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.LISTAR_USUARIOS_RESPUESTA).exito(true)
                    .contenido("""
                            [{"codigo":"A001","nombres":"Ana","apellidos":"Pérez","programa":"Sistemas","conectado":true},
                             {"codigo":"B002","nombres":"Luis","apellidos":"Díaz","programa":"Matemáticas","conectado":false}]
                            """).build());
        }

        @Override
        public RespuestaRed expulsar(String codigo) {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).build());
        }

        @Override
        public RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina) {
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.HISTORIAL_PAGE).pagina(pagina)
                    .totalPaginas(totalPaginasRemotas).exito(true)
                    .contenido(paginaRemotaJson).build());
        }

        private String paginaRemotaJson = "[]";
        private int totalPaginasRemotas = 1;

        @Override
        public RespuestaRed descargarArchivo(String archivoId) throws IOException {
            vecesDescarga++;
            if (caida || fallaDescarga) throw new IOException("descarga interrumpida");
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.DESCARGAR_ARCHIVO_RESPUESTA).archivoId(archivoId).exito(true).contenidoImagen("AAAA").build());
        }

        private int vecesLectura;

        @Override
        public RespuestaRed enviarLectura(String lector, String autor, String idOriginal) throws IOException {
            if (caida) throw new IOException("corte simulado");
            vecesLectura++;
            return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).build());
        }

        @Override
        public void logout() {
        }
    }

    @Test
    void fondoEjecutaTareasYCerrarApagaElPool() throws Exception {
        // FASE 13.5: el executor acotado sustituye los `new Thread` por evento.
        java.util.concurrent.CountDownLatch hecho = new java.util.concurrent.CountDownLatch(1);
        fachada.fondo().execute(hecho::countDown);
        assertTrue(hecho.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "el fondo debe ejecutar la tarea");
        fachada.cerrar();
    }

    @Test
    void marcarLeidosFondoEsBestEffort() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        fachada.marcarLeidosFondo("B002");
        fachada.cerrar();
    }

    @Test
    void marcarLeidosAvistaUnaVezYLuegoCalla() throws Exception {
        // FASE 13.19: sin filtro, cada acuse re-disparaba el acuse (tormenta).
        assertTrue(fachada.login("A001", "Secreta123"));
        red.receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id("l-1")
                .fechaHora(LocalDateTime.now().toString())
                .remitente("B002").destinatario("A001").contenido("hola").build()));
        fachada.marcarLeidos("B002");
        assertEquals(1, red.vecesLectura, "el no-leído se acusa una vez");
        assertEquals("LEIDO", historial.pagina("A001", "B002", 10, 0).get(0).estado());
        fachada.marcarLeidos("B002");
        assertEquals(1, red.vecesLectura, "ya LEIDO: cero reenvíos");
    }

    @Test
    void acuseDeLecturaOfflinePermaneceEnColaYSeReintentaUnaVez() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        red.receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id("lectura-offline")
                .fechaHora(LocalDateTime.now().toString()).remitente("B002").destinatario("A001")
                .contenido("pendiente de acuse").build()));
        red.caida = true;

        fachada.marcarLeidos("B002");

        assertEquals(1, fachada.pendientes().size());
        assertEquals("lectura:lectura-offline", fachada.pendientes().get(0).id());
        assertEquals("LEIDO", historial.pagina("A001", "B002", 10, 0).get(0).estado());
        red.caida = false;
        assertEquals(1, fachada.reintentarPendientes());
        assertTrue(fachada.pendientes().isEmpty());
        fachada.marcarLeidos("B002");
        assertEquals(1, red.vecesLectura, "el reintento no dispara nuevos acuses");
    }

    @Test
    void sincronizaDirectorioCompletoConNombresYPresencia() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));

        var usuarios = fachada.sincronizarDirectorio();

        assertEquals(2, usuarios.size());
        assertEquals("Ana", usuarios.stream().filter(u -> u.codigo().equals("A001"))
                .findFirst().orElseThrow().nombres());
        assertEquals("Díaz", usuarios.stream().filter(u -> u.codigo().equals("B002"))
                .findFirst().orElseThrow().apellidos());
    }

    @Test
    void envioDeImagenPersisteElMismoIdYArchivoRemoto() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));

        assertTrue(fachada.enviarImagen("B002", new byte[]{1, 2, 3}, "foto.png", "image/png"));

        MensajeLocal fila = fachada.conversacion("B002", 10, 0).get(0);
        assertEquals(fila.idMensaje(), red.idImagen);
        assertEquals("1", historial.archivoIdPorId(fila.idMensaje()).orElseThrow());
        assertEquals("AQID", fachada.blobMensaje(fila.idMensaje()).orElseThrow());
        assertEquals(3, fachada.descargarImagen(fila.idMensaje()).length);
        assertEquals(0, red.vecesDescarga, "la miniatura local no pide red");
    }

    @Test
    void elCompositorNoHabilitaEnviarPorCitaSolaYJavaNoOfreceRegistro() {
        assertFalse(ChatController.puedeEnviar("  ", false, true, false));
        assertFalse(ChatController.puedeEnviar("", false, false, false));
        assertTrue(ChatController.puedeEnviar("texto", false, true, false));
        assertTrue(ChatController.puedeEnviar("", true, true, false));
        assertTrue(java.util.Arrays.stream(ClienteApp.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("mostrarRegistro")));
    }

    @Test
    void ecoPropioConservaEnviado() throws Exception {
        // FASE 13.20: mi auto-mensaje re-entregado no se vuelve "recibido".
        assertTrue(fachada.login("A001", "Secreta123"));
        red.receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id("yo-1")
                .fechaHora(LocalDateTime.now().toString())
                .remitente("A001").destinatario("A001").contenido("nota").build()));

        var fila = historial.pagina("A001", "A001", 10, 0).get(0);

        assertTrue(fila.enviado(), "el eco propio sigue siendo mío (burbuja derecha)");
    }

    @Test
    void eventoDeAplicacionDistingueAcusesSinUsarTiposDeRed() {
        assertTrue(new EventoCliente(EventoCliente.Tipo.ACUSE_LECTURA,
                "B002", "A001").esAcuse());
        assertTrue(new EventoCliente(EventoCliente.Tipo.ACUSE_ENTREGA,
                "B002", "A001").esAcuse());
        assertFalse(new EventoCliente(EventoCliente.Tipo.MENSAJE,
                "B002", "A001").esAcuse());
        assertFalse(new EventoCliente(EventoCliente.Tipo.PRESENCIA,
                "B002", "A001").esAcuse());
    }

    @Test
    void fachadaTraduceAcuseDelProtocoloAEventoDeAplicacion() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        red.receptor.alRecibir(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_LEIDO)
                .remitente("B002").destinatario("A001").build()));

        assertEquals(new EventoCliente(EventoCliente.Tipo.ACUSE_LECTURA,
                "B002", "A001"), eventosUi.get(eventosUi.size() - 1));
    }

    @Test
    void desuscribirQuitaAlObservador() throws Exception {
        // FASE 13.10: sin esto, cada re-login dejaba un refresco fantasma.
        assertTrue(fachada.login("A001", "Secreta123"));
        java.util.concurrent.atomic.AtomicInteger avisos =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<EventoCliente> sonda = e -> avisos.incrementAndGet();
        Mensaje texto = Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id("sonda-1")
                .fechaHora(LocalDateTime.now().toString())
                .remitente("B002").destinatario("A001").contenido("hola").build();
        fachada.suscribirEventos(sonda);
        red.receptor.alRecibir(RespuestaRedPrueba.desde(texto));
        assertEquals(1, avisos.get());
        fachada.desuscribirEventos(sonda);
        red.receptor.alRecibir(RespuestaRedPrueba.desde(texto));
        assertEquals(1, avisos.get(), "desuscripto: sin más avisos");
        fachada.desuscribirEventos(sonda);
    }

    @Test
    void suscribirIgnoraDuplicados() throws Exception {
        // FASE 13.16: el mismo observador dos veces = un solo aviso.
        assertTrue(fachada.login("A001", "Secreta123"));
        java.util.concurrent.atomic.AtomicInteger avisos =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<EventoCliente> sonda = e -> avisos.incrementAndGet();
        Mensaje texto = Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id("dup-1")
                .fechaHora(LocalDateTime.now().toString())
                .remitente("B002").destinatario("A001").contenido("hola").build();
        fachada.suscribirEventos(sonda);
        fachada.suscribirEventos(sonda);
        assertEquals(2, fachada.cantidadObservadoresEventos(), "observadores sin duplicados");
        red.receptor.alRecibir(RespuestaRedPrueba.desde(texto));
        assertEquals(1, avisos.get());
    }

    @Test
    void traerHistorialRemotoPersistePaginaYRegistraArchivoId() throws Exception {
        // FASE 13.12: ↑ trae páginas viejas del servidor (upsert por id).
        assertTrue(fachada.login("A001", "Secreta123"));
        red.paginaRemotaJson = """
                [{"id": 9001, "remitente": "B002", "destinatario": "A001",
                  "tipo": "MENSAJE_TEXTO", "contenido": "hola viejo",
                  "fechaEnvio": "2025-01-01T10:00:00"},
                 {"id": 9002, "remitente": "B002", "destinatario": "A001",
                  "tipo": "MENSAJE_IMAGEN", "nombreArchivo": "vieja.png",
                  "hashSha256": "abc123", "archivoId": "77",
                  "fechaEnvio": "2025-01-02T10:00:00"}]""";
        red.totalPaginasRemotas = 3;

        var pagina = fachada.traerHistorial("B002", 0);

        assertEquals(2, pagina.recibidos());
        assertEquals(3, pagina.totalPaginas());
        assertEquals("hola viejo",
                fachada.blobMensaje("9001").orElseThrow());
        assertEquals("[imagen] vieja.png",
                historial.recientes("A001").stream()
                        .filter(r -> r.otro().equals("B002")).findFirst().orElseThrow()
                        .ultimoContenido());
    }

    @Test
    void descargarImagenGuardaBytesYLanzaSinArchivoId() throws Exception {
        // FASE 13.13: clic en "[sin bytes]" → bytes → H2 → miniatura.
        assertTrue(fachada.login("A001", "Secreta123"));
        red.paginaRemotaJson = """
                [{"id": 9003, "remitente": "B002", "destinatario": "A001",
                  "tipo": "MENSAJE_IMAGEN", "nombreArchivo": "otra.png",
                  "archivoId": "78", "fechaEnvio": "2025-01-03T10:00:00"}]""";
        fachada.traerHistorial("B002", 0);

        byte[] bytes = fachada.descargarImagen("9003");

        assertEquals(3, bytes.length, "AAAA decodificado");
        assertEquals("AAAA",
                historial.blobPorId("9003").orElseThrow(), "bytes guardados en H2");
        assertEquals(3, fachada.descargarImagen("9003").length, "la segunda lectura usa la caché local");
        assertEquals(1, red.vecesDescarga, "solo una descarga de red");
    }

    @Test
    void unaDescargaFallidaSePuedeReintentarSinUsarBytesIncompletos() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        red.paginaRemotaJson = """
                [{"id":9004,"remitente":"B002","destinatario":"A001",
                  "tipo":"MENSAJE_IMAGEN","nombreArchivo":"retry.png","archivoId":"79",
                  "fechaEnvio":"2025-01-04T10:00:00"}]""";
        fachada.traerHistorial("B002", 0);
        red.fallaDescarga = true;

        assertThrows(IOException.class, () -> fachada.descargarImagen("9004"));
        red.fallaDescarga = false;
        assertEquals(3, fachada.descargarImagen("9004").length);
        assertEquals(2, red.vecesDescarga);
    }

    @Test
    void descargarImagenSinArchivoIdExplicaCargarHistorial() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        try {
            fachada.descargarImagen("inexistente");
            assertTrue(false, "debió lanzar IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("↑") || e.getMessage().contains("historial"));
        }
    }

    @Test
    void recientesIncluyeChatConUnoMismo() throws Exception {
        // El chat propio (A→A) debe existir: el servidor lo acepta.
        assertTrue(fachada.login("A001", "Secreta123"));
        historial.guardar(new MensajeLocal("yo-1", "A001", "A001", "MENSAJE_TEXTO",
                "nota para mi", "h", 11, 3, null, null, null,
                "2026-01-01T10:00:00", true, true));

        var filas = fachada.recientes();

        assertTrue(filas.stream().anyMatch(r -> r.otro().equals("A001")),
                "el propio código aparece en recientes");
    }

    @Test
    void refrescarPresenciaTraeRedSinTocarRecientes() throws Exception {
        // FASE 13.14: red solo explícita; recientes() es solo caché.
        assertTrue(fachada.login("A001", "Secreta123"));
        var conectados = fachada.refrescarPresencia();
        assertTrue(conectados.containsAll(java.util.List.of("A001", "B002")));
    }

    // ---------------------------------------------------------------- PAT-03

    @Test
    void estadoDeConexionAvanzaConectandoConectadoYAvisaAlSuscribirse() throws Exception {
        List<EstadoConexion> vistos = new ArrayList<>();
        fachada.alCambiarEstado(vistos::add);

        // Entrega el estado actual de inmediato: quien se suscribe tarde no se
        // queda sin pintar (UIX-00c).
        assertEquals(List.of(EstadoConexion.SIN_RED), vistos);

        fachada.conectar();
        assertEquals(EstadoConexion.CONECTADO, fachada.estadoConexion());
        assertTrue(fachada.login("A001", "Secreta123"));
        assertEquals(List.of(EstadoConexion.SIN_RED, EstadoConexion.CONECTANDO,
                EstadoConexion.CONECTADO), vistos);
    }

    @Test
    void caidaDelLectorPasaASinRedYNoRepiteSiYaEstaba() throws Exception {
        List<EstadoConexion> vistos = new ArrayList<>();
        fachada.conectar();
        fachada.alCambiarEstado(vistos::add);
        fachada.login("A001", "Secreta123");

        red.caer();

        assertEquals(EstadoConexion.SIN_RED, fachada.estadoConexion());
        assertEquals(1, vistos.stream().filter(e -> e == EstadoConexion.SIN_RED).count(),
                "la caída se avisa una sola vez");
    }

    @Test
    void reconectaYReautenticaTrasUnaCaidaDelServidor() throws Exception {
        fachada.login("A001", "Secreta123");

        red.caer();
        long limite = System.currentTimeMillis() + 5000;
        while (fachada.estadoConexion() != EstadoConexion.CONECTADO
                && System.currentTimeMillis() < limite) {
            Thread.sleep(25);
        }

        assertEquals(EstadoConexion.CONECTADO, fachada.estadoConexion());
        assertEquals(2, red.vecesLogin, "debe reautenticar al recuperar el socket");
        assertEquals("A001", fachada.codigoActual());
    }

    @Test
    void esperaDeReconexionTieneLimiteDeTreintaSegundos() {
        assertEquals(1, FachadaCliente.esperaReconexionSegundos(0));
        assertEquals(2, FachadaCliente.esperaReconexionSegundos(1));
        assertEquals(16, FachadaCliente.esperaReconexionSegundos(4));
        assertEquals(30, FachadaCliente.esperaReconexionSegundos(5));
        assertEquals(30, FachadaCliente.esperaReconexionSegundos(100));
    }

    @Test
    void cierreDelServidorNoSeConfundeConCaidaDeRed() throws Exception {
        fachada.conectar();
        fachada.login("A001", "Secreta123");

        red.cerrarSesion();

        assertEquals(EstadoConexion.SESION_CERRADA, fachada.estadoConexion(),
                "CLOSE_NOTICE es sesion cerrada, no 'sin red': la UI no debe ofrecer reconectar");
    }

    @Test
    void logoutYDesconectarDejanElEstadoCoherente() throws Exception {
        fachada.conectar();
        fachada.login("A001", "Secreta123");

        fachada.desconectar();
        assertEquals(EstadoConexion.SIN_RED, fachada.estadoConexion());

        fachada.conectar();
        fachada.login("A001", "Secreta123");
        fachada.logout();
        assertEquals(EstadoConexion.SESION_CERRADA, fachada.estadoConexion());
    }

    @Test
    void unCierreVoluntarioNoSeReportaComoCaida() throws Exception {
        // PAT-03: tras un logout, el hilo lector viejo puede terminar su finally
        // y avisar de una caida que no existe. Si lo hiciera, el banner
        // parpadearia en un cliente recien desconectado por el usuario.
        fachada.conectar();
        fachada.login("A001", "Secreta123");
        fachada.logout();
        EstadoConexion trasLogout = fachada.estadoConexion();

        red.caer();

        assertEquals(trasLogout, fachada.estadoConexion(),
                "un lector obsoleto no debe degradar el estado de una sesion cerrada");
    }

    @Test
    void losEstadosDegradadosMuestranBannerYLosConectadosNo() {
        assertFalse(EstadoConexion.CONECTADO.muestraBanner());
        assertFalse(EstadoConexion.CONECTANDO.muestraBanner());
        assertTrue(EstadoConexion.SIN_RED.muestraBanner());
        assertTrue(EstadoConexion.SESION_CERRADA.muestraBanner());
    }

    @Test
    void buscarEnHistorialDevuelveIdsDeMasNuevoAMasViejo() throws Exception {
        assertTrue(fachada.login("A001", "Secreta123"));
        historial.guardar(new universidad.mensajeria.cliente.transversal.records.MensajeLocal(
                "s1", "A001", "B002", "MENSAJE_TEXTO", "hola mundo", null, 4, 1,
                null, null, null, "2026-01-01T10:00:00", true, true, "ENVIADO", null));
        historial.guardar(new universidad.mensajeria.cliente.transversal.records.MensajeLocal(
                "s2", "B002", "A001", "MENSAJE_TEXTO", "HOLA de vuelta", null, 4, 1,
                null, null, null, "2026-01-01T10:05:00", false, true, "ENTREGADO", null));

        assertEquals(java.util.List.of("s2", "s1"), fachada.buscarEnHistorial("B002", "HOLA"));
        assertTrue(fachada.buscarEnHistorial("B002", "inexistente").isEmpty());
    }
}
