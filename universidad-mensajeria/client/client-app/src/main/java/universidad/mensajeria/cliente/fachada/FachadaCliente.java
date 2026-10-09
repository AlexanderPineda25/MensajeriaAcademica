package universidad.mensajeria.cliente.fachada;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

import universidad.mensajeria.cliente.componentes.autenticacion.InterfazAutenticacion;
import universidad.mensajeria.cliente.componentes.cache.InterfazCacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.InterfazMapeadorDatosServidor;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;
import universidad.mensajeria.cliente.transversal.records.PendienteEnvio;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;
import universidad.mensajeria.cliente.fachada.servicios.CoordinadorHistorialCliente;
import universidad.mensajeria.cliente.fachada.servicios.CoordinadorMensajeriaCliente;
import universidad.mensajeria.cliente.fachada.servicios.ConstructorRecientes;
import universidad.mensajeria.cliente.fachada.servicios.GestorReconexionSesion;
import universidad.mensajeria.cliente.fachada.servicios.ReintentadorPendientesCliente;
import universidad.mensajeria.cliente.fachada.servicios.PublicadorEventosCliente;
import universidad.mensajeria.cliente.fachada.servicios.LectorHistorialRemotoCliente;
import universidad.mensajeria.cliente.fachada.servicios.SincronizadorDirectorioCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.cliente.transversal.utilerias.TextoCliente;
import universidad.mensajeria.cliente.transversal.utilerias.ValidacionCliente;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adaptador del puerto de aplicación consumido por la UI (§7.1): inyecta interfaces, nunca
 * implementaciones (DIP + DI por constructor). Orquesta red + historial:
 * lo enviado con red se guarda local (enviado), sin red va a pendientes y
 * lo recibido se persiste para el modo offline. Avisos a la UI vía Observer.
 */
public final class FachadaCliente implements InterfazFachadaCliente {

    /** Marcador de destino para broadcasts propios (sin destinatario único). */
    public static final String DESTINO_BROADCAST = "*";

    private static final Logger LOG = Logger.getLogger(FachadaCliente.class.getName());

    private final InterfazConexionRed red;
    private final InterfazAutenticacion autenticacion;
    private final InterfazHistorialLocal historial;
    private final InterfazCacheUsuarios cache;
    private final InterfazMapeadorDatosServidor mapeadorDatosServidor;
    /** Fase 4: servicio enfocado en consultas de historial (delega sobre {@link #historial}). */
    private final InterfazCoordinadorHistorial coordinadorHistorial;
    /** Fase 4: servicio enfocado en envío de mensajes (texto, imagen, archivo). */
    private final InterfazCoordinadorMensajeria coordinadorMensajeria;
    /** Reenvío ordenado de la cola de pendientes (extraído por SRP). */
    private final InterfazReintentadorPendientes reintentadorPendientes;
    /** Directorio y presencia (extraído por SRP). */
    private final InterfazSincronizadorDirectorio sincronizadorDirectorio;
    /** Historial remoto y descargas bajo demanda (extraído por SRP). */
    private final InterfazLectorHistorialRemoto lectorHistorial;
    /** Bus de eventos hacia la UI, patrón Observer (extraído por SRP). */
    private final PublicadorEventosCliente avisos = new PublicadorEventosCliente();
    /**
     * FASE 13.5: UN executor acotado para todo el trabajo de fondo del cliente
     * (leídos, reintentos, tareas de la UI). Adiós `new Thread` por evento.
     */
    private final ExecutorService fondo;
    /**
     * FASE 11.1: worker daemon que vacia `pendientes_envio` en orden cada
     * {@link #INTERVALO_FLUSH_SEGUNDOS} s (best-effort, nunca tumba el hilo).
     * Cubre la reconexion silenciosa: si la red vuelve entre dos logins, los
     * pendientes salen solos sin accion de la UI.
     */
    public static final long INTERVALO_FLUSH_SEGUNDOS = 30;
    private volatile ScheduledExecutorService flush;
    private final InterfazGestorReconexion gestorReconexion;

    public FachadaCliente(InterfazConexionRed red, InterfazAutenticacion autenticacion,
                          InterfazHistorialLocal historial, InterfazCacheUsuarios cache,
                          InterfazMapeadorDatosServidor mapeadorDatosServidor) {
        this(red, autenticacion, historial, cache, mapeadorDatosServidor,
                new CoordinadorHistorialCliente(historial),
                new CoordinadorMensajeriaCliente(red, historial),
                null);
    }

    /**
     * Constructor con inyección total (DIP): los coordinadores y el gestor
     * los crea el composition root, nunca la fachada.
     */
    public FachadaCliente(InterfazConexionRed red, InterfazAutenticacion autenticacion,
                          InterfazHistorialLocal historial, InterfazCacheUsuarios cache,
                          InterfazMapeadorDatosServidor mapeadorDatosServidor,
                          InterfazCoordinadorHistorial coordinadorHistorial,
                          InterfazCoordinadorMensajeria coordinadorMensajeria,
                          InterfazGestorReconexion gestorExistente) {
        this(red, autenticacion, historial, cache, mapeadorDatosServidor,
                coordinadorHistorial, coordinadorMensajeria, gestorExistente, null, null, null);
    }

    /**
     * Constructor con inyección total (DIP): los coordinadores, los servicios
     * y el gestor los crea el composition root, nunca la fachada.
     */
    public FachadaCliente(InterfazConexionRed red, InterfazAutenticacion autenticacion,
                          InterfazHistorialLocal historial, InterfazCacheUsuarios cache,
                          InterfazMapeadorDatosServidor mapeadorDatosServidor,
                          InterfazCoordinadorHistorial coordinadorHistorial,
                          InterfazCoordinadorMensajeria coordinadorMensajeria,
                          InterfazGestorReconexion gestorExistente,
                          InterfazReintentadorPendientes reintentadorExistente,
                          InterfazSincronizadorDirectorio sincronizadorExistente,
                          InterfazLectorHistorialRemoto lectorExistente) {
        this.red = red;
        this.autenticacion = autenticacion;
        this.historial = historial;
        this.cache = cache;
        this.mapeadorDatosServidor = mapeadorDatosServidor;
        this.coordinadorHistorial = coordinadorHistorial;
        this.coordinadorMensajeria = coordinadorMensajeria;
        this.reintentadorPendientes = reintentadorExistente != null ? reintentadorExistente
                : new ReintentadorPendientesCliente(red, historial, this::codigoActual);
        this.sincronizadorDirectorio = sincronizadorExistente != null ? sincronizadorExistente
                : new SincronizadorDirectorioCliente(red, cache, mapeadorDatosServidor,
                        this::codigoActual);
        this.lectorHistorial = lectorExistente != null ? lectorExistente
                : new LectorHistorialRemotoCliente(red, historial, mapeadorDatosServidor,
                        coordinadorHistorial, this::codigoActual);
        ScheduledExecutorService reconector = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "cliente-reconexion");
            hilo.setDaemon(true);
            return hilo;
        });
        this.gestorReconexion = gestorExistente != null ? gestorExistente
                : new GestorReconexionSesion(red, autenticacion,
                        this::cambiarEstado, this::alRestaurarSesion, reconector);
        ThreadFactory hilos = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, "cliente-fondo-" + n.incrementAndGet());
                hilo.setDaemon(true);
                return hilo;
            }
        };
        this.fondo = Executors.newFixedThreadPool(2, hilos);
        red.suscribir(new ReceptorEntranteCliente(red, autenticacion, historial, cache,
                mapeadorDatosServidor, new ReceptorEntranteCliente.Salidas() {
                    @Override
                    public EstadoConexion estadoConexion() {
                        return estadoConexion;
                    }

                    @Override
                    public void cambiarEstado(EstadoConexion nuevo) {
                        FachadaCliente.this.cambiarEstado(nuevo);
                    }

                    @Override
                    public void publicarEvento(RespuestaRed mensaje) {
                        avisos.publicar(mensaje);
                    }

                    @Override
                    public void programarReconexion() {
                        FachadaCliente.this.programarReconexion();
                    }

                    @Override
                    public void olvidarCredenciales() {
                        FachadaCliente.this.olvidarCredencialesEnMemoria();
                    }
                }));
    }

    /** Executor para las `Task` de JavaFX (nunca bloquear el hilo UI). */
    public Executor fondo() {
        return fondo;
    }

    /** Apaga el executor (al salir de la app). */
    public void cerrar() {
        olvidarCredencialesEnMemoria();
        detenerFlushPendientes();
        gestorReconexion.cerrar();
        fondo.shutdownNow();
    }

    /** Arranca el worker de flush (idempotente); periodo en segundos. */
    public synchronized void iniciarFlushPendientes(long periodoSegundos) {
        if (flush != null && !flush.isShutdown()) {
            return;
        }
        long periodo = Math.max(5, periodoSegundos);
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "cliente-flush");
            hilo.setDaemon(true);
            return hilo;
        });
        worker.scheduleWithFixedDelay(() -> {
            try {
                reintentarPendientes();
            } catch (ExcepcionCliente | java.io.IOException | RuntimeException e) {
                LOG.fine(() -> "[flush] reintento omitido: " + e.getMessage());
            }
        }, periodo, periodo, TimeUnit.SECONDS);
        flush = worker;
    }

    /** Detiene el worker de flush (idempotente). */
    public synchronized void detenerFlushPendientes() {
        if (flush != null) {
            flush.shutdownNow();
            flush = null;
        }
    }

    @Override
    public void suscribirEventos(Consumer<EventoCliente> observador) {
        avisos.suscribirEventos(observador);
    }

    @Override
    public void desuscribirEventos(Consumer<EventoCliente> observador) {
        avisos.desuscribirEventos(observador);
    }

    @Override
    public int cantidadObservadoresEventos() {
        return avisos.cantidadObservadoresEventos();
    }

    // ------------------------------------------- PAT-03: estado de conexión

    private volatile EstadoConexion estadoConexion = EstadoConexion.SIN_RED;
    private final List<Consumer<EstadoConexion>> observadoresEstado =
            new CopyOnWriteArrayList<>();

    /** Estado actual de la conexión (UIX-00c: antes solo se sondeaba). */
    public EstadoConexion estadoConexion() {
        return estadoConexion;
    }

    /**
     * La UI se suscribe a los cambios de estado. Se entrega el estado actual de
     * inmediato, para que quien se suscriba tarde no se quede sin pintar.
     */
    public void alCambiarEstado(Consumer<EstadoConexion> observador) {
        observadoresEstado.add(observador);
        observador.accept(estadoConexion);
    }

    public void quitarObservadorEstado(Consumer<EstadoConexion> observador) {
        observadoresEstado.remove(observador);
    }

    private void cambiarEstado(EstadoConexion nuevo) {
        if (nuevo == estadoConexion) {
            return;
        }
        EstadoConexion anterior = estadoConexion;
        estadoConexion = nuevo;
        LOG.fine(() -> "estado de conexión: " + anterior + " -> " + nuevo);
        for (Consumer<EstadoConexion> obs : observadoresEstado) {
            try {
                obs.accept(nuevo);
            } catch (RuntimeException e) {
                LOG.warning("observador de estado falló: " + e.getMessage());
            }
        }
    }

    public void conectar() throws IOException {
        cambiarEstado(EstadoConexion.CONECTANDO);
        try {
            red.conectar();
            cambiarEstado(EstadoConexion.CONECTADO);
        } catch (IOException e) {
            cambiarEstado(EstadoConexion.SIN_RED);
            throw e;
        }
    }

    public void desconectar() {
        olvidarCredencialesEnMemoria();
        red.desconectar();
        cambiarEstado(EstadoConexion.SIN_RED);
    }

    public void cerrarSesionRemota() {
        olvidarCredencialesEnMemoria();
        autenticacion.olvidarSesion();
        red.desconectar();
        cambiarEstado(EstadoConexion.SESION_CERRADA);
    }

    public boolean conectado() {
        return red.conectado();
    }

    public boolean login(String codigo, String contrasena) throws ExcepcionCliente, IOException {
        olvidarCredencialesEnMemoria();
        historial.prepararCuenta(codigo);
        if (!red.conectado()) {
            conectar();
        }
        boolean ok = autenticacion.login(codigo, contrasena);
        if (ok) {
            gestorReconexion.guardarSesion(codigo, contrasena);
            cambiarEstado(EstadoConexion.CONECTADO);
            sincronizadorDirectorio.asegurarEnCache(codigo);
            reintentarPendientes();
            iniciarFlushPendientes(INTERVALO_FLUSH_SEGUNDOS);
        } else {
            olvidarCredencialesEnMemoria();
        }
        return ok;
    }

    public void logout() throws ExcepcionCliente, IOException {
        olvidarCredencialesEnMemoria();
        detenerFlushPendientes();
        autenticacion.logout();
        red.desconectar();
        cambiarEstado(EstadoConexion.SESION_CERRADA);
    }

    public String codigoActual() {
        return autenticacion.codigoActual();
    }

    /**
     * Espera incremental: 1, 2, 4, 8, 16 y luego 30 segundos.
     * Fase 4: delega a {@link InterfazGestorReconexion#esperaSegundos}.
     */
    public static int esperaReconexionSegundos(int intento) {
        return InterfazGestorReconexion.esperaSegundos(intento);
    }

    private void programarReconexion() {
        gestorReconexion.programarReconexion();
    }

    private void olvidarCredencialesEnMemoria() {
        gestorReconexion.olvidarSesion();
    }

    private void alRestaurarSesion(String codigo) {
        try {
            sincronizadorDirectorio.asegurarEnCache(codigo);
        } catch (ExcepcionCliente | java.io.IOException e) {
            throw new ExcepcionSesion("no se pudo restaurar la caché de usuarios", e);
        }
        try {
            sincronizarDirectorio();
        } catch (ExcepcionCliente | java.io.IOException | RuntimeException e) {
            LOG.log(Level.FINE, "No se sincronizó el directorio al reconectar", e);
        }
        try {
            refrescarPresencia();
        } catch (ExcepcionCliente | java.io.IOException | RuntimeException e) {
            LOG.log(Level.FINE, "No se actualizó presencia al reconectar", e);
        }
        try {
            reintentarPendientes();
        } catch (ExcepcionCliente | java.io.IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Quedaron envíos pendientes tras reconectar", e);
        }
        iniciarFlushPendientes(INTERVALO_FLUSH_SEGUNDOS);
    }

    /** Conectados del servidor + refresco de la caché local (delega por SRP). */
    public List<String> listarConectados() throws ExcepcionCliente, IOException {
        return sincronizadorDirectorio.listarConectados();
    }

    /** Directorio local (offline): conectados primero (delega por SRP). */
    public List<UsuarioLocal> directorioLocal() throws ExcepcionCliente {
        return sincronizadorDirectorio.directorioLocal();
    }

    /** Directorio completo bajo acción explícita (delega por SRP). */
    public List<UsuarioLocal> sincronizarDirectorio() throws ExcepcionCliente, IOException {
        return sincronizadorDirectorio.sincronizarDirectorio();
    }

    /**
     * Chat 1-a-1 texto: con red envía y guarda; sin red guarda + pendiente.
     * Devuelve true si salió por red.
     * Fase 4: delega al {@link CoordinadorMensajeriaCliente}.
     */
    public boolean enviarTexto(String destinatario, String contenido) throws ExcepcionCliente, IOException {
        exigirSesion();
        return coordinadorMensajeria.enviarTexto(codigoActual(), destinatario, contenido);
    }

    /**
     * Chat 1-a-1 imagen con preview previa en la UI (bytes ya elegidos).
     * Fase 4: delega al {@link CoordinadorMensajeriaCliente}.
     */
    public boolean enviarImagen(String destinatario, byte[] bytes,
                                String nombreArchivo, String mime) throws ExcepcionCliente {
        exigirSesion();
        return coordinadorMensajeria.enviarImagen(codigoActual(), destinatario, bytes,
                nombreArchivo, mime);
    }

    /**
     * Chat 1-a-1 archivo generico (PDF, docs, zip): mismo flujo que imagen.
     * Fase 4: delega al {@link CoordinadorMensajeriaCliente}.
     */
    public boolean enviarArchivo(String destinatario, byte[] bytes,
                                 String nombreArchivo, String mime) throws ExcepcionCliente {
        exigirSesion();
        return coordinadorMensajeria.enviarArchivo(codigoActual(), destinatario, bytes,
                nombreArchivo, mime);
    }

    /** Broadcast a todos los conectados (req. servidor #9). */
    public void difundir(String contenido) throws ExcepcionCliente, IOException {
        exigirSesion();
        ValidacionCliente.contenido(contenido);
        String id = java.util.UUID.randomUUID().toString();
        historial.guardar(new MensajeLocal(id, codigoActual(),
                DESTINO_BROADCAST, TipoMensajeCliente.BROADCAST.name(), contenido, null,
                contenido.length(), TextoCliente.contarPalabras(contenido), null, null, null,
                LocalDateTime.now().toString(), true, true, EstadoMensaje.PENDIENTE.name()));
        try {
            RespuestaRed ack = red.difundir(id, codigoActual(), contenido);
            if (!Boolean.TRUE.equals(ack.exito())) {
                throw new ExcepcionSesion(ack.mensajeError() != null
                        ? ack.mensajeError() : "difusion rechazada");
            }
            historial.marcarEstado(id, EstadoMensaje.ENVIADO.name());
        } catch (ExcepcionCliente | java.io.IOException e) {
            try {
                historial.marcarEstado(id, EstadoMensaje.ERROR.name());
            } catch (ExcepcionCliente marca) {
                LOG.fine(() -> "no se pudo marcar ERROR: " + marca.getMessage());
            }
            throw e;
        }
    }

    /** Recientes con presencia de CACHÉ (nunca red: el push PRESENCIA la mantiene). */
    public List<RecienteChat> recientes() throws ExcepcionCliente, IOException {
        exigirSesion();
        long inicio = System.nanoTime();
        List<RecienteChat> salida = ConstructorRecientes.armar(
                historial.recientes(codigoActual()), cache.listar(), codigoActual());
        LOG.fine(() -> "[perf] recientes h2=" + entre(System.nanoTime(), inicio)
                + "ms red=0 (solo cache)"
                + " (" + salida.size() + " chats)");
        return salida;
    }

    /**
     * Presencia fresca de red. Solo login, "Actualizar" manual y arranque:
     * NUNCA en el auto-refresco (delega por SRP).
     */
    public List<String> refrescarPresencia() throws ExcepcionCliente, IOException {
        return sincronizadorDirectorio.refrescarPresencia();
    }

    /** Bytes de UN mensaje (miniatura visible), nunca la lista (delega por SRP). */
    public java.util.Optional<String> blobMensaje(String idMensaje) throws ExcepcionCliente {
        return lectorHistorial.blobMensaje(idMensaje);
    }

    /** Marca como leídos los entrantes y conserva cada acuse hasta enviarlo. */
    public void marcarLeidos(String otro) throws ExcepcionCliente, IOException {
        exigirSesion();
        long inicio = System.nanoTime();
        for (String idMensaje : historial.idsNoLeidos(codigoActual(), otro)) {
            String idPendiente = "lectura:" + idMensaje;
            if (historial.pendientePorId(idPendiente).isEmpty()) {
                historial.registrarPendiente(new PendienteEnvio(idPendiente,
                        TipoMensajeCliente.MENSAJE_LEIDO.name(), codigoActual(), otro,
                        idMensaje, null, null, LocalDateTime.now().toString(), 0, null));
            }
        }
        historial.marcarLeidosDe(codigoActual(), otro);
        reintentarPendientes();
        LOG.fine(() -> "[perf] marcarLeidos=" + entre(System.nanoTime(), inicio) + "ms");
    }

    /** FASE 13.5: versión de fondo (best-effort) para la UI. */
    public void marcarLeidosFondo(String otro) {
        fondo.execute(() -> {
            try {
                marcarLeidos(otro);
            } catch (ExcepcionCliente | java.io.IOException | RuntimeException e) {
                LOG.fine(() -> "[perf] marcarLeidosFondo omitido: " + e.getMessage());
            }
        });
    }

    public void expulsarOtrasSesiones() throws ExcepcionCliente, IOException {
        exigirSesion();
        red.expulsar(codigoActual());
    }

    /** Historial offline paginado, más reciente primero (delega por SRP). */
    public List<MensajeLocal> conversacion(String otro, int limite, int offset) throws ExcepcionCliente, IOException {
        return lectorHistorial.conversacion(otro, limite, offset);
    }

    public int contarConversacion(String otro) throws ExcepcionCliente, IOException {
        return lectorHistorial.contarConversacion(otro);
    }

    /** Ids con el patrón, de más nuevo a más viejo (delega por SRP). */
    public List<String> buscarEnHistorial(String otro, String patron) throws ExcepcionCliente, IOException {
        return lectorHistorial.buscarEnHistorial(otro, patron);
    }

    /** Trae UNA página remota y la persiste (delega por SRP). */
    public PaginaHistorialRemota traerHistorial(String otro, int pagina) throws ExcepcionCliente, IOException {
        return lectorHistorial.traerHistorial(otro, pagina);
    }

    /** Descarga los bytes de una imagen y los guarda en H2 (delega por SRP). */
    public byte[] descargarImagen(String idMensaje) throws ExcepcionCliente, IOException {
        return lectorHistorial.descargarImagen(idMensaje);
    }

    /** Descarga un adjunto sin exponer el DTO de red a la presentación. */
    @Override
    public byte[] descargarContenidoArchivo(String archivoId) throws ExcepcionCliente, IOException {
        return lectorHistorial.descargarContenidoArchivo(archivoId);
    }

    /** Descarga de archivo del servidor bajo demanda (delega por SRP). */
    public RespuestaRed descargarArchivo(String archivoId) throws ExcepcionCliente, IOException {
        return lectorHistorial.descargarArchivo(archivoId);
    }

    public List<PendienteEnvio> pendientes() throws ExcepcionCliente, IOException {
        return historial.pendientes();
    }

    /** Reenvía pendientes en orden; delega al servicio extraído (SRP). */
    public int reintentarPendientes() throws ExcepcionCliente, IOException {
        return reintentadorPendientes.reintentarPendientes();
    }

    private void exigirSesion() {
        if (codigoActual() == null) {
            throw new ExcepcionSesion("inicia sesion primero");
        }
    }

    /** FASE 13.6/13.11: milisegundos entre dos `nanoTime`. */
    private static long entre(long finNanos, long inicioNanos) {
        return (finNanos - inicioNanos) / 1_000_000;
    }
}
