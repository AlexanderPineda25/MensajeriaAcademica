package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.componentes.autenticacion.InterfazAutenticacion;
import universidad.mensajeria.cliente.componentes.cache.InterfazCacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
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
import universidad.mensajeria.cliente.fachada.servicios.GestorReconexionSesion;
import universidad.mensajeria.cliente.transversal.utilerias.ValidacionCliente;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
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
    private final CoordinadorHistorialCliente coordinadorHistorial;
    /** Fase 4: servicio enfocado en envío de mensajes (texto, imagen, archivo). */
    private final CoordinadorMensajeriaCliente coordinadorMensajeria;
    private final List<Consumer<EventoCliente>> observadoresEventos =
            new CopyOnWriteArrayList<>();
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
    private final GestorReconexionSesion gestorReconexion;

    public FachadaCliente(InterfazConexionRed red, InterfazAutenticacion autenticacion,
                          InterfazHistorialLocal historial, InterfazCacheUsuarios cache,
                          InterfazMapeadorDatosServidor mapeadorDatosServidor) {
        this.red = red;
        this.autenticacion = autenticacion;
        this.historial = historial;
        this.cache = cache;
        this.mapeadorDatosServidor = mapeadorDatosServidor;
        this.coordinadorHistorial = new CoordinadorHistorialCliente(historial);
        this.coordinadorMensajeria = new CoordinadorMensajeriaCliente(red, historial);
        ScheduledExecutorService reconector = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "cliente-reconexion");
            hilo.setDaemon(true);
            return hilo;
        });
        this.gestorReconexion = new GestorReconexionSesion(red, autenticacion,
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
        red.suscribir(new ReceptorInterno());
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
            } catch (Exception e) {
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
        if (!observadoresEventos.contains(observador)) {
            observadoresEventos.add(observador);
        }
    }

    @Override
    public void desuscribirEventos(Consumer<EventoCliente> observador) {
        observadoresEventos.remove(observador);
    }

    @Override
    public int cantidadObservadoresEventos() {
        return observadoresEventos.size();
    }

    private void publicarEvento(RespuestaRed mensaje) {
        EventoCliente evento = adaptarEvento(mensaje);
        for (Consumer<EventoCliente> observador : observadoresEventos) {
            try {
                observador.accept(evento);
            } catch (RuntimeException error) {
                LOG.log(Level.WARNING, "observador de eventos del cliente falló", error);
            }
        }
    }

    private static EventoCliente adaptarEvento(RespuestaRed mensaje) {
        EventoCliente.Tipo tipo = switch (mensaje.tipo()) {
            case PRESENCIA -> EventoCliente.Tipo.PRESENCIA;
            case CLOSE_NOTICE -> EventoCliente.Tipo.SESION_CERRADA;
            case MENSAJE_ENTREGADO -> EventoCliente.Tipo.ACUSE_ENTREGA;
            case MENSAJE_LEIDO -> EventoCliente.Tipo.ACUSE_LECTURA;
            default -> EventoCliente.Tipo.MENSAJE;
        };
        return new EventoCliente(tipo, mensaje.remitente(), mensaje.destinatario());
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

    public boolean login(String codigo, String contrasena) throws Exception {
        olvidarCredencialesEnMemoria();
        historial.prepararCuenta(codigo);
        if (!red.conectado()) {
            conectar();
        }
        boolean ok = autenticacion.login(codigo, contrasena);
        if (ok) {
            gestorReconexion.guardarSesion(codigo, contrasena);
            cambiarEstado(EstadoConexion.CONECTADO);
            asegurarEnCache(codigo);
            reintentarPendientes();
            iniciarFlushPendientes(INTERVALO_FLUSH_SEGUNDOS);
        } else {
            olvidarCredencialesEnMemoria();
        }
        return ok;
    }

    public void logout() throws Exception {
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
     * Fase 4: delega a {@link GestorReconexionSesion#esperaSegundos}.
     */
    public static int esperaReconexionSegundos(int intento) {
        return GestorReconexionSesion.esperaSegundos(intento);
    }

    private void programarReconexion() {
        gestorReconexion.programarReconexion();
    }

    private void olvidarCredencialesEnMemoria() {
        gestorReconexion.olvidarSesion();
    }

    private void alRestaurarSesion(String codigo) {
        try {
            asegurarEnCache(codigo);
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo restaurar la caché de usuarios", e);
        }
        try {
            sincronizarDirectorio();
        } catch (Exception e) {
            LOG.log(Level.FINE, "No se sincronizó el directorio al reconectar", e);
        }
        try {
            refrescarPresencia();
        } catch (Exception e) {
            LOG.log(Level.FINE, "No se actualizó presencia al reconectar", e);
        }
        try {
            reintentarPendientes();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Quedaron envíos pendientes tras reconectar", e);
        }
        iniciarFlushPendientes(INTERVALO_FLUSH_SEGUNDOS);
    }

    /** Conectados del servidor + refresco de la caché local. */
    public List<String> listarConectados() throws Exception {
        exigirSesion();
        RespuestaRed respuesta = red.listarConectados();
        List<String> conectados = respuesta.usuariosConectados() != null
                ? respuesta.usuariosConectados() : List.of();
        cache.marcarConectados(conectados);
        for (String codigo : conectados) {
            asegurarEnCache(codigo);
        }
        return conectados;
    }

    /** Directorio local (offline): conectados primero. */
    public List<UsuarioLocal> directorioLocal() throws Exception {
        return cache.listar();
    }

    /** Carga el directorio completo solo al entrar o bajo actualización manual. */
    public List<UsuarioLocal> sincronizarDirectorio() throws Exception {
        exigirSesion();
        RespuestaRed respuesta = red.listarUsuarios();
        if (respuesta.tipo() != TipoMensajeCliente.LISTAR_USUARIOS_RESPUESTA
                || respuesta.contenido() == null || respuesta.contenido().isBlank()) {
            throw new IllegalStateException("directorio de usuarios no disponible");
        }
        List<UsuarioLocal> usuarios = mapeadorDatosServidor
                .mapearDirectorio(respuesta.contenido());
        boolean incluyeSesion = usuarios.stream()
                .anyMatch(usuario -> usuario.codigo().equals(codigoActual()));
        if (usuarios.isEmpty() || !incluyeSesion) {
            throw new IllegalStateException("directorio incompleto; se conserva la caché local");
        }
        cache.reemplazarDirectorio(usuarios);
        return cache.listar();
    }

    /**
     * Chat 1-a-1 texto: con red envía y guarda; sin red guarda + pendiente.
     * Devuelve true si salió por red.
     * Fase 4: delega al {@link CoordinadorMensajeriaCliente}.
     */
    public boolean enviarTexto(String destinatario, String contenido) throws Exception {
        exigirSesion();
        return coordinadorMensajeria.enviarTexto(codigoActual(), destinatario, contenido);
    }

    /**
     * Chat 1-a-1 imagen con preview previa en la UI (bytes ya elegidos).
     * Fase 4: delega al {@link CoordinadorMensajeriaCliente}.
     */
    public boolean enviarImagen(String destinatario, byte[] bytes,
                                String nombreArchivo, String mime) throws Exception {
        exigirSesion();
        return coordinadorMensajeria.enviarImagen(codigoActual(), destinatario, bytes,
                nombreArchivo, mime);
    }

    /**
     * Chat 1-a-1 archivo generico (PDF, docs, zip): mismo flujo que imagen.
     * Fase 4: delega al {@link CoordinadorMensajeriaCliente}.
     */
    public boolean enviarArchivo(String destinatario, byte[] bytes,
                                 String nombreArchivo, String mime) throws Exception {
        exigirSesion();
        return coordinadorMensajeria.enviarArchivo(codigoActual(), destinatario, bytes,
                nombreArchivo, mime);
    }

    /** Broadcast a todos los conectados (req. servidor #9). */
    public void difundir(String contenido) throws Exception {
        exigirSesion();
        ValidacionCliente.contenido(contenido);
        String id = java.util.UUID.randomUUID().toString();
        historial.guardar(new MensajeLocal(id, codigoActual(),
                DESTINO_BROADCAST, TipoMensajeCliente.BROADCAST.name(), contenido, null,
                contenido.length(), contarPalabras(contenido), null, null, null,
                LocalDateTime.now().toString(), true, true, EstadoMensaje.PENDIENTE.name()));
        try {
            RespuestaRed ack = red.difundir(id, codigoActual(), contenido);
            if (!Boolean.TRUE.equals(ack.exito())) {
                throw new IllegalStateException(ack.mensajeError() != null
                        ? ack.mensajeError() : "difusion rechazada");
            }
            historial.marcarEstado(id, EstadoMensaje.ENVIADO.name());
        } catch (Exception e) {
            historial.marcarEstado(id, EstadoMensaje.ERROR.name());
            throw e;
        }
    }

    /** Recientes con presencia de CACHÉ (nunca red: el push PRESENCIA la mantiene). */
    public List<RecienteChat> recientes() throws Exception {
        exigirSesion();
        long inicio = System.nanoTime();
        List<RecienteChat> base = historial.recientes(codigoActual());
        java.util.Set<String> conectados = new java.util.HashSet<>();
        // FASE 13.14: SOLO caché local. La versión anterior llamaba a red aquí y
        // cada auto-refresco podía colgarse hasta el timeout (15 s) si algo se
        // atoraba; la presencia llega por push (ReceptorInterno) y el refresh
        // manual usa refrescarPresencia().
        for (UsuarioLocal u : cache.listar()) {
            if (u.conectado()) {
                conectados.add(u.codigo());
            }
        }
        List<RecienteChat> salida = new ArrayList<>();
        for (RecienteChat r : base) {
            salida.add(new RecienteChat(r.otro(), r.ultimoContenido(), r.ultimoTipo(),
                    r.fechaUltimo(), r.noLeidos(), conectados.contains(r.otro())));
        }
        salida.sort((a, b) -> {
            if (a.conectado() != b.conectado()) {
                return a.conectado() ? -1 : 1;
            }
            return b.fechaUltimo().compareTo(a.fechaUltimo());
        });
        // Directorio sin conversación también aparece (sin preview).
        for (UsuarioLocal u : cache.listar()) {
            if (u.codigo().equals(codigoActual())) {
                continue;
            }
            boolean ya = salida.stream().anyMatch(r -> r.otro().equals(u.codigo()));
            if (!ya) {
                salida.add(new RecienteChat(u.codigo(), "", "", "", 0,
                        conectados.contains(u.codigo())));
            }
        }
        LOG.fine(() -> "[perf] recientes h2=" + entre(System.nanoTime(), inicio)
                + "ms red=0 (solo cache)"
                + " (" + salida.size() + " chats)");
        return salida;
    }

    /**
     * FASE 13.14: presencia fresca de RED (bloquea hasta 15 s). Solo para login,
     * "Actualizar" manual y arranque: NUNCA en el auto-refresco.
     */
    public List<String> refrescarPresencia() throws Exception {
        return listarConectados();
    }

    /** FASE 13.9: bytes de UN mensaje (miniatura visible), nunca la lista. */
    public java.util.Optional<String> blobMensaje(String idMensaje) throws Exception {
        return coordinadorHistorial.blobMensaje(idMensaje);
    }

    /** Marca como leídos los entrantes y conserva cada acuse hasta enviarlo. */
    public void marcarLeidos(String otro) throws Exception {
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
            } catch (Exception e) {
                LOG.fine(() -> "[perf] marcarLeidosFondo omitido: " + e.getMessage());
            }
        });
    }

    public void expulsarOtrasSesiones() throws Exception {
        exigirSesion();
        red.expulsar(codigoActual());
    }

    /** Historial offline paginado (más reciente primero, como el contrato). */
    public List<MensajeLocal> conversacion(String otro, int limite, int offset) throws Exception {
        exigirSesion();
        long inicio = System.nanoTime();
        List<MensajeLocal> pagina = coordinadorHistorial.conversacion(codigoActual(), otro, limite, offset);
        List<MensajeLocal> cronologica = new ArrayList<>(pagina);
        java.util.Collections.reverse(cronologica);
        LOG.fine(() -> "[perf] conversacion=" + entre(System.nanoTime(), inicio)
                + "ms (" + cronologica.size() + " msgs)");
        return cronologica;
    }

    public int contarConversacion(String otro) throws Exception {
        exigirSesion();
        return coordinadorHistorial.contarConversacion(codigoActual(), otro);
    }

    /**
     * UIX-06: ids de mensajes con el patrón (de más nuevo a más viejo,
     * tope 200). La vista salta entre ellos sin traer el historial entero.
     */
    public List<String> buscarEnHistorial(String otro, String patron) throws Exception {
        exigirSesion();
        return coordinadorHistorial.buscarEnHistorial(codigoActual(), otro, patron, 200);
    }

    /**
     * FASE 13.12: trae UNA página remota (`HISTORIAL_PAGE`, 50 msg, sin bytes),
     * la persiste (upsert por id) y registra `archivoId` para descargas.
     * Llamar en hilo de fondo: `pedir()` bloquea hasta 15 s.
     */
    public PaginaHistorialRemota traerHistorial(String otro, int pagina) throws Exception {
        exigirSesion();
        long inicio = System.nanoTime();
        RespuestaRed respuesta = red.solicitarHistorial(codigoActual(), otro, pagina);
        if (!Boolean.TRUE.equals(respuesta.exito())) {
            throw new IllegalStateException(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : "historial rechazado");
        }
        List<MensajeLocal> mensajes = mapeadorDatosServidor
                .mapearPaginaHistorial(respuesta.contenido(), codigoActual());
        for (MensajeLocal mensaje : mensajes) {
            historial.guardar(mensaje);
        }
        int recibidos = mensajes.size();
        int total = respuesta.totalPaginas() != null ? respuesta.totalPaginas() : 0;
        LOG.fine("[perf] historialRemoto pag=" + pagina + " recibidos=" + recibidos
                + " en " + entre(System.nanoTime(), inicio) + "ms");
        return new PaginaHistorialRemota(recibidos, pagina, total);
    }

    /**
     * FASE 13.13: descarga los bytes de una imagen (`DESCARGAR_ARCHIVO`),
     * los guarda en H2 y devuelve los bytes para la miniatura.
     */
    public byte[] descargarImagen(String idMensaje) throws Exception {
        exigirSesion();
        java.util.Optional<String> local = historial.blobPorId(idMensaje);
        if (local.isPresent() && !local.get().isBlank()) {
            return Base64.getDecoder().decode(local.get());
        }
        String archivoId = historial.archivoIdPorId(idMensaje).orElse(null);
        if (archivoId == null) {
            throw new IllegalStateException(
                    "metadatos de imagen pendientes; actualiza esta página del historial");
        }
        RespuestaRed respuesta = red.descargarArchivo(archivoId);
        if (!Boolean.TRUE.equals(respuesta.exito()) || respuesta.contenidoImagen() == null) {
            throw new IllegalStateException(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : "descarga rechazada");
        }
        byte[] bytes = Base64.getDecoder().decode(respuesta.contenidoImagen());
        historial.actualizarContenido(idMensaje,
                Base64.getEncoder().encodeToString(bytes));
        return bytes;
    }

    /** Descarga un adjunto sin exponer el DTO de red a la presentación. */
    @Override
    public byte[] descargarContenidoArchivo(String archivoId) throws Exception {
        exigirSesion();
        RespuestaRed respuesta = red.descargarArchivo(archivoId);
        if (!Boolean.TRUE.equals(respuesta.exito()) || respuesta.contenidoImagen() == null) {
            throw new IllegalStateException(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : "descarga no disponible");
        }
        return Base64.getDecoder().decode(respuesta.contenidoImagen());
    }

    /** FASE 6 — Descargar archivo (imagen) del servidor bajo demanda. */
    public RespuestaRed descargarArchivo(String archivoId) throws Exception {
        exigirSesion();
        return red.descargarArchivo(archivoId);
    }

    public List<PendienteEnvio> pendientes() throws Exception {
        return historial.pendientes();
    }

    /** Reenvía pendientes en orden; devuelve cuántos salieron. */
    public int reintentarPendientes() throws Exception {
        if (codigoActual() == null || !red.conectado()) {
            return 0;
        }
        int enviados = 0;
        for (PendienteEnvio pendiente : historial.pendientes()) {
            try {
                if (TipoMensajeCliente.MENSAJE_LEIDO.name().equals(pendiente.tipo())) {
                    RespuestaRed ack = red.enviarLectura(
                            pendiente.origen(), pendiente.destino(), pendiente.contenido());
                    exigirExito(ack, "acuse de lectura rechazado");
                } else if (TipoMensajeCliente.MENSAJE_IMAGEN.name().equals(pendiente.tipo())) {
                    String base64 = pendiente.payload() == null ? ""
                            : Base64.getEncoder().encodeToString(pendiente.payload());
                    RespuestaRed ack = red.enviarImagen(pendiente.id(), pendiente.origen(), pendiente.destino(),
                            pendiente.nombreArchivo(), "application/octet-stream", base64);
                    exigirExito(ack, "reenvio de imagen rechazado");
                    if (ack.archivoId() != null) {
                        historial.actualizarArchivoId(pendiente.id(), ack.archivoId());
                    }
                    historial.marcarEstado(pendiente.id(), EstadoMensaje.ENVIADO.name());
                } else if (TipoMensajeCliente.MENSAJE_ARCHIVO.name().equals(pendiente.tipo())) {
                    String base64 = pendiente.payload() == null ? ""
                            : Base64.getEncoder().encodeToString(pendiente.payload());
                    RespuestaRed ack = red.enviarArchivo(pendiente.id(), pendiente.origen(), pendiente.destino(),
                            pendiente.nombreArchivo(), "application/octet-stream", base64);
                    if (!Boolean.TRUE.equals(ack.exito())) {
                        throw new IllegalStateException(ack.mensajeError() != null
                                ? ack.mensajeError() : "reenvio de archivo rechazado");
                    }
                    if (ack.archivoId() != null) {
                        historial.actualizarArchivoId(pendiente.id(), ack.archivoId());
                    }
                    historial.marcarEstado(pendiente.id(), EstadoMensaje.ENVIADO.name());
                } else {
                    RespuestaRed ack = red.enviarTexto(pendiente.id(), pendiente.origen(),
                            pendiente.destino(), pendiente.contenido());
                    exigirExito(ack, "reenvio de mensaje rechazado");
                    historial.marcarEstado(pendiente.id(), EstadoMensaje.ENVIADO.name());
                }
                historial.eliminarPendiente(pendiente.id());
                enviados++;
            } catch (IllegalStateException rechazo) {
                // Rechazo definitivo del servidor (destinatario inexistente, contenido
                // vacio...): no reintentar eternamente ni bloquear los acuses detras.
                historial.marcarEstado(pendiente.id(), EstadoMensaje.ERROR.name());
                historial.eliminarPendiente(pendiente.id());
                LOG.fine(() -> "[reintento] pendiente descartado por rechazo: "
                        + pendiente.id() + " (" + rechazo.getMessage() + ")");
            } catch (IOException | RuntimeException e) {
                historial.incrementarIntento(pendiente.id(), e.getMessage());
                break;
            }
        }
        return enviados;
    }

    private void exigirSesion() {
        if (codigoActual() == null) {
            throw new IllegalStateException("inicia sesion primero");
        }
    }

    private void asegurarEnCache(String codigo) throws Exception {
        if (cache.porCodigo(codigo).isEmpty()) {
            String ahora = LocalDateTime.now().toString();
            cache.guardar(new UsuarioLocal(codigo, codigo, "", "", false, ahora, ahora));
        }
    }

    private static void exigirExito(RespuestaRed respuesta, String error) {
        if (!Boolean.TRUE.equals(respuesta.exito())
                && respuesta.tipo() != TipoMensajeCliente.IMAGE_FILTERED_RESULT) {
            throw new IllegalStateException(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : error);
        }
    }

    private static int contarPalabras(String contenido) {
        return contenido.trim().split("\\s+").length;
    }

    /** FASE 13.6/13.11: milisegundos entre dos `nanoTime`. */
    private static long entre(long finNanos, long inicioNanos) {
        return (finNanos - inicioNanos) / 1_000_000;
    }

    /** Persiste lo entrante y lo reenvía a la UI. */
    private final class ReceptorInterno implements ReceptorMensajes {

        @Override
        public void alRecibir(RespuestaRed mensaje) {
            try {
                if (mensaje.tipo() == TipoMensajeCliente.MENSAJE_ENTREGADO) {
                    if (mensaje.contenido() != null) {
                        historial.marcarEstado(mensaje.contenido(),
                                EstadoMensaje.ENTREGADO.name());
                    }
                } else if (mensaje.tipo() == TipoMensajeCliente.MENSAJE_LEIDO) {
                    if (mensaje.contenido() != null) {
                        historial.marcarEstado(mensaje.contenido(),
                                EstadoMensaje.LEIDO.name());
                    }
                } else if (mensaje.tipo() == TipoMensajeCliente.PRESENCIA) {
                    if (mensaje.codigo() != null) {
                        asegurarEnCache(mensaje.codigo());
                    }
                    if (mensaje.usuariosConectados() != null) {
                        try {
                            cache.marcarConectados(mensaje.usuariosConectados());
                        } catch (Exception ignorada) {
                            // presencia best-effort
                        }
                    }
                } else if (mensaje.tipo() == TipoMensajeCliente.MENSAJE_TEXTO
                        || mensaje.tipo() == TipoMensajeCliente.MENSAJE_IMAGEN
                        || mensaje.tipo() == TipoMensajeCliente.MENSAJE_ARCHIVO
                        || mensaje.tipo() == TipoMensajeCliente.BROADCAST
                        || mensaje.tipo() == TipoMensajeCliente.SYNC_LOGIN) {
                    // Lo entrante siempre es (remitente → yo): así el broadcast
                    // recibido aparece en la conversación con su remitente.
                    String yo = codigoActual();
                    String destino = yo != null ? yo : mensaje.destinatario();
                    String cuerpo = mensaje.contenido();
                    if ((mensaje.tipo() == TipoMensajeCliente.MENSAJE_IMAGEN
                            || mensaje.tipo() == TipoMensajeCliente.MENSAJE_ARCHIVO)
                            && mensaje.contenidoImagen() != null) {
                        cuerpo = mensaje.contenidoImagen();
                    }
                    historial.guardar(new MensajeLocal(
                            mensaje.id() != null ? mensaje.id() : UUID.randomUUID().toString(),
                            mensaje.remitente(), destino,
                            mensaje.tipo() == TipoMensajeCliente.SYNC_LOGIN
                                    ? mapeadorDatosServidor.normalizarTipoMensaje(
                                            mensaje.tipoOriginal()) : mensaje.tipo().name(),
                            cuerpo, mensaje.hashSha256(),
                            mensaje.numCaracteres(), mensaje.numPalabras(),
                            mensaje.nombreArchivo(), null, mensaje.tamanoArchivo(),
                            mensaje.fechaHora() != null ? mensaje.fechaHora()
                                    : LocalDateTime.now().toString(),
                            // FASE 13.20: el eco de mi propio mensaje (auto-chat u
                            // otra sesión) conserva enviado=1; si no, mi burbuja
                            // caía al lado equivocado y el tick se perdía.
                            yo != null && yo.equals(mensaje.remitente()), true,
                            EstadoMensaje.ENTREGADO.name(), mensaje.archivoId()));
                    if (mensaje.remitente() != null) {
                        asegurarEnCache(mensaje.remitente());
                    }
                }
            } catch (Exception e) {
                // PAT-03/UIX-00d: persistir no puede tumbar al lector, pero el
                // fallo ya no se traga en silencio. El aviso a la vista va DESPUES
                // de este bloque, asi que el mensaje igualmente se pinta en
                // pantalla; lo que faltaba era que nadie lo supiera.
                // Ojo: una base local en solo lectura NO es "sin red", asi que
                // aqui no se toca el estado de conexion.
                LOG.warning("no se pudo persistir la trama " + mensaje.tipo()
                        + ": " + e.getMessage());
            }
            publicarEvento(mensaje);
        }

        @Override
        public void alCierre(RespuestaRed aviso) {
            // PAT-03: CLOSE_NOTICE es "el servidor cerró mi sesión", no una caída
            // de red. Distinguirlo permite que la UI no ofrezca "reintentar" en un
            // sitio donde lo que hace falta es volver a entrar.
            olvidarCredencialesEnMemoria();
            cambiarEstado(EstadoConexion.SESION_CERRADA);
            publicarEvento(aviso);
        }

        @Override
        public void alError(String motivo) {
            // PAT-03/UIX-00c: esto ya no es un comentario. Un ERROR de trama es un
            // fallo de una petición, no del socket: se registra y se propaga a la UI
            // como estado degradado solo si además no hay socket vivo.
            LOG.warning("error del servidor: " + motivo);
            if (!red.conectado()) {
                cambiarEstado(EstadoConexion.SIN_RED);
            }
        }

        @Override
        public void alDesconexion() {
            // PAT-03/UIX-00c: el hilo lector murió. Aquí se.enteraba la UI por
            // sondeo cada 30 s; ahora es un evento.
            LOG.info("conexión con el servidor perdida");
            if (estadoConexion != EstadoConexion.SESION_CERRADA) {
                cambiarEstado(EstadoConexion.SIN_RED);
                programarReconexion();
            }
        }
    }
}
