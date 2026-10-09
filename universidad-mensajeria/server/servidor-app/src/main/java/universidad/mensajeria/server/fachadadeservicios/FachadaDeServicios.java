package universidad.mensajeria.server.fachadadeservicios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import universidad.mensajeria.colas.ConsumidorDeMensajes;
import universidad.mensajeria.colas.InterfazGestorMensajes;
import universidad.mensajeria.common.interno.EstadoServidor;
import universidad.mensajeria.common.interno.EstadoPool;
import universidad.mensajeria.common.interno.DetalleUsuarioDTO;
import universidad.mensajeria.common.interno.EventoEtapa;
import universidad.mensajeria.common.interno.FechasUsuarioDTO;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.interno.InformeDTO;
import universidad.mensajeria.common.interno.InformeFiltroDTO;
import universidad.mensajeria.common.interno.LimitesDTO;
import universidad.mensajeria.common.interno.LoginConteoDTO;
import universidad.mensajeria.common.interno.ConexionInformeDTO;
import universidad.mensajeria.common.interno.MensajeResumenDTO;
import universidad.mensajeria.common.interno.PaginaDTO;
import universidad.mensajeria.common.interno.ResultadoMensajeDTO;
import universidad.mensajeria.common.interno.UsuarioInformeDTO;
import universidad.mensajeria.common.interno.UsuarioResumen;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

import universidad.mensajeria.protocolo.InterfazProtocoloComunicacion;
import universidad.mensajeria.protocolo.ReceptorDeTramas;

import universidad.mensajeria.clienteservidor.InterfazClienteServidor;
import universidad.mensajeria.clienteservidor.SalidaClienteServidor;

import universidad.mensajeria.servicios.InterfazServiciosDisponibles;

import universidad.mensajeria.usuarios.InterfazUsuariosDisponibles;

import universidad.mensajeria.server.fachadadeservicios.casosdeuso.Casos;
import universidad.mensajeria.server.fachadadeservicios.casosdeuso.EntregaCaso;
import universidad.mensajeria.server.conexionesclientes.InterfazConexionesClientes;
import universidad.mensajeria.server.logdeeventos.InterfazLogDeEventos;
import universidad.mensajeria.server.mensajes.InterfazMensajes;
import universidad.mensajeria.common.interno.TipoAccion;
import universidad.mensajeria.server.transversal.fachada.Fachada;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * FACADE + MEDIADOR (PLAN2 §2.1/§2.5): unico nodo conectado a todos. Inyecta
 * por constructor SOLO abstracciones (5 int-* + interfaces de nucleo). No
 * implementa logica: delega en casosdeuso (una clase por operacion).
 * Tambien es ReceptorDeTramas (red) y ConsumidorDeMensajes (cola): al
 * terminar cada futuro de Mensajes encadena envio + ACK.
 */
public class FachadaDeServicios implements Fachada, SalidaClienteServidor.FachadaSalida,
        ReceptorDeTramas, ConsumidorDeMensajes {

    private static final Logger LOG = LoggerFactory.getLogger(FachadaDeServicios.class);

    /**
     * Cuenta buzón del sistema: remitente de la difusión administrativa y
     * destinatario de los mensajes que los usuarios envían al servidor.
     * Existe en usuarios_iniciales.csv, así la difusión persiste por usuario
     * y los desconectados la reciben al reconectar (pendientes/SYNC_LOGIN).
     */
    public static final String CUENTA_SISTEMA = "SERVIDOR";

    private final InterfazProtocoloComunicacion protocolo;
    private final java.util.function.Supplier<InterfazClienteServidor> clienteServidor;
    private final InterfazServiciosDisponibles servicios;
    private final InterfazUsuariosDisponibles usuarios;
    private final InterfazGestorMensajes colas;
    private final InterfazMensajes mensajes;
    private final InterfazLogDeEventos eventos;
    private final InterfazConexionesClientes conexiones;
    private final LimitesDTO limites;
    private final Casos casos;

    /** Sesion emisora del hilo actual (encolar avisa en linea: mismo hilo). */
    private final ThreadLocal<IdSesion> origenActual = new ThreadLocal<>();
    private final AtomicLong mensajesProcesados = new AtomicLong();

    /**
     * Pool acotado SOLO para entrega TCP del abanico (broadcast/difusión).
     * Lo crea Fabrica (raiz de composicion) y lo recibe por constructor; un
     * cliente lento retiene como máximo un hilo de entrega. Daemon para
     * no retener el apagado; se cierra en {@link #apagarEntrega()}.
     */
    private final java.util.concurrent.ExecutorService poolEntrega;

    /** Cierra el pool de entrega (lo invoca Fabrica al apagar el sistema). */
    public void apagarEntrega() {
        poolEntrega.shutdownNow();
    }

    public FachadaDeServicios(InterfazProtocoloComunicacion protocolo,
                              java.util.function.Supplier<InterfazClienteServidor> clienteServidor,
                              InterfazServiciosDisponibles servicios,
                              InterfazUsuariosDisponibles usuarios,
                              InterfazGestorMensajes colas,
                              InterfazMensajes mensajes,
                              InterfazLogDeEventos eventos,
                              InterfazConexionesClientes conexiones,
                              LimitesDTO limites,
                              Casos casos,
                              java.util.concurrent.ExecutorService poolEntrega) {
        this.protocolo = protocolo;
        this.clienteServidor = clienteServidor;
        this.servicios = servicios;
        this.usuarios = usuarios;
        this.colas = colas;
        this.mensajes = mensajes;
        this.eventos = eventos;
        this.conexiones = conexiones;
        this.limites = limites;
        this.casos = casos;
        this.poolEntrega = poolEntrega;
        this.rutasCola = Map.copyOf(rutasCola());
    }

    /**
     * Tabla de despacho de la cola (mismo patron que Despachador, sin switch):
     * un tipo encolable nuevo es una entrada mas, no una rama nueva.
     */
    private Map<TipoMensaje, java.util.function.BiConsumer<Mensaje, IdSesion>> rutasCola() {
        Map<TipoMensaje, java.util.function.BiConsumer<Mensaje, IdSesion>> rutas =
                new java.util.EnumMap<>(TipoMensaje.class);
        rutas.put(TipoMensaje.MENSAJE_TEXTO, this::procesarTexto);
        rutas.put(TipoMensaje.MENSAJE_IMAGEN, this::procesarImagen);
        rutas.put(TipoMensaje.MENSAJE_ARCHIVO, this::procesarArchivo);
        rutas.put(TipoMensaje.BROADCAST, this::procesarDifusion);
        return rutas;
    }

    private final Map<TipoMensaje, java.util.function.BiConsumer<Mensaje, IdSesion>> rutasCola;

    // ------------------------------------------------------- contrato Fachada

    @Override
    public void iniciarServidor() {
        protocolo.iniciar();
    }

    @Override
    public void detenerServidor() {
        protocolo.detener();
    }

    @Override
    public EstadoServidor estadoServidor() {
        EstadoPool pool = protocolo.estadoPool();
        return new EstadoServidor(
                protocolo.estaActivo(),
                protocolo.puerto(),
                conexiones.cantidadUsuariosConectados(),
                conexiones.totalSesiones(),
                servicios.maxConexiones(limites),
                colas.tamano(),
                pool.ocupados(),
                pool.disponibles(),
                pool.total(),
                mensajesProcesados.get());
    }

    @Override
    public List<String> usuariosConectados() {
        return conexiones.codigosConectados();
    }

    @Override
    public List<UsuarioResumen> usuariosRegistrados() {
        Set<String> conectados = new HashSet<>(conexiones.codigosConectados());
        return usuarios.listar().stream()
                .map(u -> new UsuarioResumen(u.codigo(), u.nombres(), u.apellidos(),
                        u.programa(), conectados.contains(u.codigo())))
                .toList();
    }

    @Override
    public DetalleUsuarioDTO detalleUsuario(String codigo) {
        String cuenta = codigo == null ? "" : codigo.trim();
        if (cuenta.isEmpty()) {
            throw new IllegalArgumentException("El codigo de usuario es obligatorio");
        }
        var usuario = usuarios.buscar(cuenta)
                .orElseThrow(() -> new IllegalArgumentException("Usuario inexistente: " + cuenta));
        Set<IdSesion> sesiones = conexiones.sesionesDe(cuenta);
        FechasUsuarioDTO fechas = fechasPorCodigo().get(cuenta);
        long logins = eventos.consultarConexiones(InformeFiltroDTO.sinFiltro()).stream()
                .filter(conteo -> cuenta.equalsIgnoreCase(conteo.codigo()))
                .mapToLong(LoginConteoDTO::veces)
                .findFirst().orElse(0);
        var actividad = mensajes.actividadUsuario(cuenta);
        return new DetalleUsuarioDTO(usuario.codigo(), usuario.nombres(), usuario.apellidos(),
                usuario.programa(), !sesiones.isEmpty(),
                fechas == null ? null : fechas.fechaRegistro(),
                fechas == null ? null : fechas.fechaUltimaConexion(), logins, sesiones.size(),
                actividad.totalMensajes(), actividad.totalArchivos(),
                actividad.contactoPrincipal(), actividad.mensajesCompartidos());
    }

    @Override
    public PaginaDTO<MensajeResumenDTO> paginaMensajesUsuario(String codigo,
            boolean soloArchivos, int pagina, int tamano) {
        return mensajes.paginaDetalleUsuario(codigo, soloArchivos, pagina, tamano);
    }

    @Override
    public List<IdSesion> sesionesActivas() {
        return conexiones.codigosConectados().stream()
                .flatMap(codigo -> conexiones.sesionesDe(codigo).stream())
                .sorted(java.util.Comparator.comparing(IdSesion::codigo)
                        .thenComparing(IdSesion::id))
                .toList();
    }

    @Override
    public int mensajesEnCola() {
        return colas.tamano();
    }

    @Override
    public void difundirAdministrativo(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El mensaje de difusion no puede estar vacio");
        }
        colas.encolar(Mensaje.builder()
                .tipo(TipoMensaje.BROADCAST)
                .id(java.util.UUID.randomUUID().toString())
                .fechaHora(LocalDateTime.now().toString())
                .remitente(CUENTA_SISTEMA)
                .contenido(contenido.trim())
                .ipRemitente(RedLocal.ipLocal())
                .build());
    }

    @Override
    public String ipLocal() {
        return RedLocal.ipLocal();
    }

    @Override
    public void suscribirEventos(Consumer<String> observador) {
        eventos.suscribir(observador);
    }

    // ------------------------------------------------------- FASE 9: informes

    /**
     * Reúne los datos por las flechas del diagrama (§8) y le pasa los DTOs a
     * Servicios para que arme el informe (lógica pura, sin tocar BD).
     */
    @Override
    public InformeDTO informeUsuarios(InformeFiltroDTO filtro) {
        Map<String, FechasUsuarioDTO> fechas = fechasPorCodigo();
        Set<String> conectados = new HashSet<>(conexiones.codigosConectados());
        List<UsuarioInformeDTO> filas = usuarios.listar().stream()
                .map(u -> {
                    FechasUsuarioDTO f = fechas.get(u.codigo());
                    return new UsuarioInformeDTO(u.codigo(), u.nombres(), u.apellidos(),
                            u.programa(),
                            f == null ? null : f.fechaRegistro(),
                            f == null ? null : f.fechaUltimaConexion(),
                            conectados.contains(u.codigo()));
                })
                .toList();
        return servicios.armarInformeUsuarios(filas, filtro);
    }

    @Override
    public InformeDTO informeConexiones(InformeFiltroDTO filtro) {
        Map<String, Long> conteos = new HashMap<>();
        for (LoginConteoDTO conteo : eventos.consultarConexiones(filtro)) {
            conteos.put(conteo.codigo(), conteo.veces());
        }
        Map<String, FechasUsuarioDTO> fechas = fechasPorCodigo();
        List<ConexionInformeDTO> filas = usuarios.listar().stream()
                .map(u -> {
                    FechasUsuarioDTO f = fechas.get(u.codigo());
                    return new ConexionInformeDTO(u.codigo(),
                            conteos.getOrDefault(u.codigo(), 0L),
                            conexiones.sesionesDe(u.codigo()).size(),
                            f == null ? null : f.fechaUltimaConexion(),
                            u.nombres(), u.apellidos());
                })
                .toList();
        return servicios.armarInformeConexiones(filas, filtro);
    }

    @Override
    public InformeDTO informeMensajes(InformeFiltroDTO filtro) {
        return servicios.armarInformeMensajes(mensajes.detalle(filtro), filtro);
    }

    @Override
    public InformeDTO informeAuditoria(InformeFiltroDTO filtro) {
        return servicios.armarInformeAuditoria(eventos.consultar(filtro), filtro);
    }

    @Override
    public java.util.List<universidad.mensajeria.common.interno.MensajeResumenDTO> mensajesDetalle(
            InformeFiltroDTO filtro) {
        return mensajes.detalle(filtro);
    }

    @Override
    public byte[] bytesArchivoParaVista(long archivoId) {
        return casos.archivoVista().bytes(archivoId);
    }

    private Map<String, FechasUsuarioDTO> fechasPorCodigo() {
        return casos.fechasUsuarios().porCodigo();
    }

    // ------------------------------------------------------- ReceptorDeTramas

    @Override
    public void alConectar(IdSesion sesion) {
        LOG.debug("Conexion entrante: {}", sesion);
    }

    @Override
    public void alRecibir(IdSesion sesion, Mensaje mensaje) {
        clienteServidor.get().procesar(mensaje, sesion);
    }

    @Override
    public void alRechazoPorSaturacion(String ip, int maximo) {
        eventos.registrar(TipoAccion.LIMITE_ALCANZADO,
                "Pool saturado (max=%d): conexion rechazada".formatted(maximo), null, ip, null);
    }

    @Override
    public void alDesconectar(IdSesion sesion, String motivo) {
        String codigo = sesion.codigo();
        if (codigo == null) {
            return;
        }
        if (conexiones.remover(codigo, sesion)) {
            if (conexiones.sesionesDe(codigo).isEmpty()) {
                casos.entrega().emitirPresencia(codigo, false);
            }
            eventos.registrar(TipoAccion.DESCONECTADO,
                    "Sesion desconectada (" + motivo + ")", codigo,
                    sesion.direccionIp(), null);
        }
    }

    // ------------------------------------------------- ConsumidorDeMensajes

    @Override
    public void procesar(Mensaje trama) {
        // Corre en linea dentro de colas.encolar(): mismo hilo que el caso,
        // por eso origenActual sigue disponible aqui. El caso lo limpia al salir.
        IdSesion origen = origenActual.get();
        if (trama.tipo() == TipoMensaje.MENSAJE_TEXTO
                || trama.tipo() == TipoMensaje.MENSAJE_IMAGEN
                || trama.tipo() == TipoMensaje.MENSAJE_ARCHIVO
                || trama.tipo() == TipoMensaje.BROADCAST) {
            mensajesProcesados.incrementAndGet();
        }
        var ruta = rutasCola.get(trama.tipo());
        if (ruta != null) {
            ruta.accept(trama, origen);
        } else {
            LOG.warn("Trama no procesable en cola: {}", trama.tipo());
        }
    }

    private void procesarDifusion(Mensaje trama, IdSesion origen) {
        casos.difusion().difundir(trama, origen);
    }

    private void procesarTexto(Mensaje trama, IdSesion origen) {
        String ipDestinatario = casos.entrega().fotoIpDestinatario(trama.destinatario());
        mensajes.procesarTexto(trama.remitente(), trama.destinatario(),
                        trama.contenido(), trama.ipRemitente(), ipDestinatario, trama.id())
                .thenAccept(dto -> {
                    Mensaje entrega = Mensaje.builder()
                            .tipo(TipoMensaje.MENSAJE_TEXTO)
                            .id(trama.id())
                            .fechaHora(LocalDateTime.now().toString())
                            .remitente(trama.remitente())
                            .destinatario(trama.destinatario())
                            .contenido(trama.contenido())
                            .hashSha256(dto.hashSha256())
                            .numCaracteres(dto.numCaracteres())
                            .numPalabras(dto.numPalabras())
                            .ipRemitente(trama.ipRemitente())
                            .build();
                    casos.entrega().entregar(trama.destinatario(), entrega);
                    eventos.registrar(TipoAccion.MENSAJE_TEXTO,
                            bloque("TEXTO", LocalDateTime.now().toString(),
                                    trama.remitente(), trama.destinatario(), dto.hashSha256(),
                                    "%d caracteres, %d palabras".formatted(
                                            dto.numCaracteres(), dto.numPalabras())),
                            trama.remitente(), trama.ipRemitente(), null);
                    casos.entrega().responder(origen, trama.id(), TipoMensaje.ACK);
                })
                .exceptionally(fallo -> {
                    casos.entrega().responderError(origen, trama, fallo);
                    return null;
                });
    }

    private void procesarImagen(Mensaje trama, IdSesion origen) {
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(trama.contenidoImagen());
        } catch (IllegalArgumentException e) {
            casos.entrega().responderError(origen, trama, e);
            return;
        }
        String ipDestinatario = casos.entrega().fotoIpDestinatario(trama.destinatario());
        mensajes.procesarImagen(trama.remitente(), trama.destinatario(), bytes,
                        trama.nombreArchivo(), trama.mime(), trama.ipRemitente(),
                        ipDestinatario, trama.id())
                .thenAccept(dto -> {
                    Mensaje entrega = Mensaje.builder()
                            .tipo(TipoMensaje.MENSAJE_IMAGEN)
                            .id(trama.id())
                            .fechaHora(LocalDateTime.now().toString())
                            .remitente(trama.remitente())
                            .destinatario(trama.destinatario())
                            .nombreArchivo(trama.nombreArchivo())
                            .mime(trama.mime())
                            .contenidoImagen(trama.contenidoImagen())
                            .hashSha256(dto.hashSha256())
                            .etapasFiltrado(EntregaCaso.etapasJson(dto))
                            .archivoId(dto.archivoId() == null ? null : String.valueOf(dto.archivoId()))
                            .ipRemitente(trama.ipRemitente())
                            .build();
                    casos.entrega().entregar(trama.destinatario(), entrega);
                    eventos.registrar(TipoAccion.MENSAJE_IMAGEN,
                            bloque("IMAGEN", LocalDateTime.now().toString(),
                                    trama.remitente(), trama.destinatario(), dto.hashSha256(),
                                    rutasResultado(dto)),
                            trama.remitente(), trama.ipRemitente(), null);
                    casos.entrega().responderImagen(origen, trama, dto);
                })
                .exceptionally(fallo -> {
                    casos.entrega().responderError(origen, trama, fallo);
                    return null;
                });
    }

    private void procesarArchivo(Mensaje trama, IdSesion origen) {
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(trama.contenidoImagen());
        } catch (IllegalArgumentException e) {
            casos.entrega().responderError(origen, trama, e);
            return;
        }
        String ipDestinatario = casos.entrega().fotoIpDestinatario(trama.destinatario());
        mensajes.procesarArchivo(trama.remitente(), trama.destinatario(), bytes,
                        trama.nombreArchivo(), trama.mime(), trama.ipRemitente(),
                        ipDestinatario, trama.id())
                .thenAccept(dto -> {
                    Mensaje entrega = Mensaje.builder()
                            .tipo(TipoMensaje.MENSAJE_ARCHIVO)
                            .id(trama.id())
                            .fechaHora(LocalDateTime.now().toString())
                            .remitente(trama.remitente())
                            .destinatario(trama.destinatario())
                            .nombreArchivo(trama.nombreArchivo())
                            .mime(trama.mime())
                            .contenidoImagen(trama.contenidoImagen())
                            .hashSha256(dto.hashSha256())
                            .archivoId(dto.archivoId() == null ? null : String.valueOf(dto.archivoId()))
                            .ipRemitente(trama.ipRemitente())
                            .build();
                    casos.entrega().entregar(trama.destinatario(), entrega);
                    eventos.registrar(TipoAccion.MENSAJE_IMAGEN,
                            bloque("ARCHIVO", LocalDateTime.now().toString(),
                                    trama.remitente(), trama.destinatario(), dto.hashSha256(),
                                    trama.nombreArchivo() == null ? "archivo" : trama.nombreArchivo()),
                            trama.remitente(), trama.ipRemitente(), null);
                    casos.entrega().responder(origen, trama.id(), TipoMensaje.ACK);
                })
                .exceptionally(fallo -> {
                    casos.entrega().responderError(origen, trama, fallo);
                    return null;
                });
    }

    /**
     * Bloque uniforme por mensaje entrante (RF-S36): fecha, remitente,
     * destinatario, tipo, hash y resultado del procesamiento, en multilinea
     * para consola y log. El escritorio lo reestructura en su panel.
     */
    static String bloque(String tipo, String fechaHora, String remitente, String destinatario,
                         String hash, String resultado) {
        return "[mensaje]%n  fecha=%s%n  remitente=%s%n  destinatario=%s%n  tipo=%s%n  hash=%s%n  resultado=%s"
                .formatted(fechaHora, remitente, destinatario, tipo,
                        hash == null ? "-" : hash, resultado);
    }

    /** RF-S36: el resultado de IMAGEN nombra las 5 derivadas guardadas. */
    static String rutasResultado(ResultadoMensajeDTO dto) {
        if (dto == null || dto.rutasDerivadas() == null || dto.rutasDerivadas().isEmpty()) {
            return "5 filtros aplicados";
        }
        return String.join(", ", dto.rutasDerivadas());
    }

    // ------------------------------------------------- SalidaClienteServidor

    @Override
    public void autenticar(IdSesion sesion, String codigo, String contrasena, String idSolicitud) {
        casos.autenticar().autenticar(sesion, codigo, contrasena, idSolicitud);
    }

    @Override
    public void cerrar(IdSesion sesion, String idSolicitud) {
        casos.cerrar().cerrar(sesion, idSolicitud);
    }

    @Override
    public void expulsarOtrasSesiones(IdSesion sesion, String codigo, String idSolicitud) {
        casos.expulsar().expulsarOtrasSesiones(sesion, codigo, idSolicitud);
    }

    @Override
    public int cerrarConexionAdmin(String objetivo, String motivo) {
        return casos.cerrarAdmin().cerrar(objetivo, motivo);
    }

    @Override
    public void recibirTexto(IdSesion sesion, String destinatario, String contenido,
                             String idSolicitud, String ip) {
        origenActual.set(sesion);
        try {
            casos.encolar().texto(sesion, destinatario, contenido, idSolicitud, ip);
        } finally {
            origenActual.remove();
        }
    }

    @Override
    public void recibirImagen(IdSesion sesion, String destinatario, String nombreArchivo,
                              String mime, String contenidoBase64, String idSolicitud, String ip) {
        origenActual.set(sesion);
        try {
            casos.encolar().imagen(sesion, destinatario, nombreArchivo, mime, contenidoBase64,
                    idSolicitud, ip);
        } finally {
            origenActual.remove();
        }
    }

    @Override
    public void recibirArchivo(IdSesion sesion, String destinatario, String nombreArchivo,
                               String mime, String contenidoBase64, String idSolicitud, String ip) {
        origenActual.set(sesion);
        try {
            casos.encolar().archivo(sesion, destinatario, nombreArchivo, mime, contenidoBase64,
                    idSolicitud, ip);
        } finally {
            origenActual.remove();
        }
    }

    @Override
    public void iniciarArchivo(IdSesion sesion, String destinatario, String nombreArchivo,
                               String mime, long tamanoTotal, int totalPartes,
                               String idSolicitud, String ip) {
        casos.fragmentos().iniciar(sesion, destinatario, nombreArchivo, mime,
                tamanoTotal, totalPartes, idSolicitud, ip);
    }

    @Override
    public void parteArchivo(IdSesion sesion, String idTransferencia, int indiceParte,
                             String contenidoBase64, String idSolicitud, String ip) {
        casos.fragmentos().parte(sesion, idTransferencia, indiceParte,
                contenidoBase64, idSolicitud, ip);
    }

    @Override
    public void finalizarArchivo(IdSesion sesion, String idTransferencia, String hashSha256,
                                 String idSolicitud, String ip) {
        casos.fragmentos().finalizar(sesion, idTransferencia, hashSha256, idSolicitud, ip);
    }

    @Override
    public void difundir(IdSesion sesion, String contenido, String idSolicitud, String ip) {
        origenActual.set(sesion);
        try {
            casos.encolar().difundir(sesion, contenido, idSolicitud, ip);
        } finally {
            origenActual.remove();
        }
    }

    @Override
    public void historial(IdSesion sesion, String otro, int pagina, String idSolicitud) {
        casos.historial().historial(sesion, otro, pagina, idSolicitud);
    }

    @Override
    public void notificarLectura(IdSesion sesion, String destinatario, String idOriginal,
                                 String idSolicitud) {
        Mensaje reenvio = Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_LEIDO)
                .fechaHora(LocalDateTime.now().toString())
                .remitente(sesion.codigo())
                .destinatario(destinatario)
                .contenido(idOriginal)
                .build();
        for (IdSesion s : conexiones.sesionesDe(destinatario)) {
            try {
                protocolo.enviar(s, reenvio);
            } catch (Exception e) {
                LOG.debug("No se pudo reenviar lectura a {}: {}", destinatario, e.getMessage());
            }
        }
    }

    @Override
    public void descargar(IdSesion sesion, String archivoId, String idSolicitud) {
        casos.descarga().descargar(sesion, archivoId, idSolicitud);
    }

    @Override
    public void listarConectados(IdSesion sesion, String idSolicitud) {
        casos.listar().listarConectados(sesion, idSolicitud);
    }

    @Override
    public void listarUsuarios(IdSesion sesion, String idSolicitud) {
        casos.listar().listarUsuarios(sesion, idSolicitud);
    }

    @Override
    public void responder(IdSesion sesion, Mensaje respuesta) {
        casos.responder().responder(sesion, respuesta);
    }

    @Override
    public void error(IdSesion sesion, String idSolicitud, TipoMensaje tipo, String motivo) {
        casos.responder().fallo(sesion, idSolicitud, tipo, motivo);
    }
}
