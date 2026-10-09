package universidad.mensajeria.cliente.componentes.conexion;

import universidad.mensajeria.common.codec.TramaCodec;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.cliente.transversal.utilerias.ValidarTls;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Socket TCP del cliente Java (§7.1 red): tramas 4B + JSON vía TramaCodec.
 *
 * Las respuestas del servidor pueden intercalarse con entregas asíncronas
 * (un BROADCAST puede llegar mientras espero mi ACK), así que un hilo lector
 * demultiplexa: tramas con id pendiente completan su futuro; el resto va al
 * {@code ReceptorMensajes}. Escritura sincronizada por socket (§4.3).
 */
public final class ConexionCliente implements InterfazConexionRed {

    private static final int TIMEOUT_RESPUESTA_SEG = 15;

    /**
     * Bytes crudos por {@code ARCHIVO_PARTE}. 1 MiB es el mismo corte que usan Python y .NET:
     * en Base64 ocupa ~1,33 MiB, muy por debajo del tope de 16 MiB de {@link TramaCodec}.
     */
    private static final int PARTE_MAX_BYTES = 1024 * 1024;

    private final String host;
    private final int puerto;
    private final boolean tlsHabilitado;
    private final int tlsPuerto;
    private final String tlsConfianza;

    private final Object candadoSalida = new Object();
    private final ConcurrentHashMap<String, CompletableFuture<Mensaje>> pendientes =
            new ConcurrentHashMap<>();

    private volatile Socket socket;
    private volatile ReceptorMensajes receptor;
    private volatile Thread lector;
    /**
     * PAT-03: generación de la conexión. El hilo lector solo avisa de una caída
     * si sigue siendo el dueño del socket: si reconectamos mientras el lector
     * viejo terminates su `finally`, su aviso dejaría el estado en SIN_RED con la
     * red ya en pie (banner parpadeando). `desconectar()` incrementa la
     * generación, así que un cierre voluntario tampoco se reporta como caída.
     */
    private final java.util.concurrent.atomic.AtomicLong generacion =
            new java.util.concurrent.atomic.AtomicLong();

    public ConexionCliente(String host, int puerto) {
        this(host, puerto, false, 5001, "");
    }

    /** FASE 11.2: TLS opcional (mismo framing; solo cambia el socket). */
    public ConexionCliente(String host, int puerto, boolean tlsHabilitado, int tlsPuerto,
                           String tlsConfianza) {
        this.host = host;
        this.puerto = puerto;
        this.tlsHabilitado = tlsHabilitado;
        this.tlsPuerto = tlsPuerto;
        this.tlsConfianza = tlsConfianza == null ? "" : tlsConfianza;
    }

    @Override
    public synchronized void conectar() throws IOException {
        if (conectado()) {
            return;
        }
        socket = tlsHabilitado ? socketTls() : new Socket(host, puerto);
        socket.setTcpNoDelay(true);
        long mia = generacion.incrementAndGet();
        lector = new Thread(() -> bucleLectura(mia), "cliente-lector");
        lector.setDaemon(true);
        lector.start();
    }

    /**
     * Abre el socket TLS. Antes valida la configuración (SEG-01): si el almacén de confianza
     * no existe o los puertos colisionan, el mensaje dice qué propiedad tocar en vez de salir
     * como un IOException de handshake que no orienta a nadie.
     *
     * <p>El framing es idéntico sobre el socket claro y el cifrado: no cambia nada más aquí.
     */
    private Socket socketTls() throws IOException {
        ValidarTls.validar(tlsHabilitado, puerto, tlsPuerto, tlsConfianza);
        try {
            javax.net.ssl.SSLSocketFactory fabrica;
            if (tlsConfianza.isBlank()) {
                fabrica = (javax.net.ssl.SSLSocketFactory)
                        javax.net.ssl.SSLSocketFactory.getDefault();
            } else {
                char[] clave = {};
                java.security.KeyStore almacen =
                        java.security.KeyStore.getInstance("JKS");
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(
                        java.nio.file.Path.of(tlsConfianza))) {
                    almacen.load(in, clave);
                }
                javax.net.ssl.TrustManagerFactory confianza =
                        javax.net.ssl.TrustManagerFactory.getInstance(
                                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                confianza.init(almacen);
                javax.net.ssl.SSLContext contexto =
                        javax.net.ssl.SSLContext.getInstance("TLS");
                contexto.init(null, confianza.getTrustManagers(), null);
                fabrica = contexto.getSocketFactory();
            }
            Socket abierto = fabrica.createSocket(host, tlsPuerto);
            if (!(abierto instanceof javax.net.ssl.SSLSocket seguro)) {
                // No degradar nunca a conexión sin cifrar si el TLS estaba pedido.
                throw new IOException("la fabrica TLS no produjo un SSLSocket hacia " + host
                        + ":" + tlsPuerto + "; se aborta antes de enviar nada en claro");
            }
            // Paridad con el cliente Python (`check_hostname = True`): el hostname se
            // verifica siempre y nunca hay un downgrade silencioso a socket plano.
            javax.net.ssl.SSLParameters parametros = seguro.getSSLParameters();
            parametros.setEndpointIdentificationAlgorithm("HTTPS");
            parametros.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            seguro.setSSLParameters(parametros);
            return seguro;
        } catch (IOException e) {
            throw e;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("no se pudo abrir TLS hacia " + host + ":" + tlsPuerto
                    + " (revise tls.puerto y tls.confianza): " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void desconectar() {
        // Invalida al lector vivo: su `finally` ya no debe reportar una caída.
        generacion.incrementAndGet();
        pendientes.forEach((id, futuro) ->
                futuro.completeExceptionally(new EOFException("conexion cerrada")));
        pendientes.clear();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignorada) {
                // ya cerrado
            }
        }
        socket = null;
    }

    @Override
    public boolean conectado() {
        Socket actual = socket;
        return actual != null && !actual.isClosed() && actual.isConnected();
    }

    @Override
    public void suscribir(ReceptorMensajes receptor) {
        this.receptor = receptor;
    }

    @Override
    public RespuestaRed login(String codigo, String contrasena) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.LOGIN)
                .codigo(codigo).contrasena(contrasena).build()));
    }

    @Override
    public RespuestaRed enviarTexto(String remitente, String destinatario, String contenido)
            throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO)
                .remitente(remitente).destinatario(destinatario).contenido(contenido).build()));
    }

    @Override
    public RespuestaRed enviarTexto(String id, String remitente, String destinatario, String contenido)
            throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id(id)
                .remitente(remitente).destinatario(destinatario).contenido(contenido).build()));
    }

    @Override
    public RespuestaRed enviarImagen(String remitente, String destinatario, String nombreArchivo,
                                String mime, String contenidoBase64) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.MENSAJE_IMAGEN)
                .remitente(remitente).destinatario(destinatario).nombreArchivo(nombreArchivo)
                .mime(mime).contenidoImagen(contenidoBase64).build()));
    }

    @Override
    public RespuestaRed enviarImagen(String id, String remitente, String destinatario,
                                String nombreArchivo, String mime, String contenidoBase64)
            throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.MENSAJE_IMAGEN).id(id)
                .remitente(remitente).destinatario(destinatario).nombreArchivo(nombreArchivo)
                .mime(mime).contenidoImagen(contenidoBase64).build()));
    }

    @Override
    public RespuestaRed enviarArchivo(String remitente, String destinatario, String nombreArchivo,
                                 String mime, String contenidoBase64) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.MENSAJE_ARCHIVO)
                .remitente(remitente).destinatario(destinatario).nombreArchivo(nombreArchivo)
                .mime(mime).contenidoImagen(contenidoBase64).build()));
    }

    @Override
    public RespuestaRed enviarArchivo(String id, String remitente, String destinatario,
                                 String nombreArchivo, String mime, String contenidoBase64)
            throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.MENSAJE_ARCHIVO).id(id)
                .remitente(remitente).destinatario(destinatario).nombreArchivo(nombreArchivo)
                .mime(mime).contenidoImagen(contenidoBase64).build()));
    }

    /**
     * Envío fragmentado de archivo genérico (PROTOCOLO.md §3, invariante 2).
     *
     * <p>Por qué existe: {@link TramaCodec} rechaza toda trama de más de 16 MiB y el Base64
     * infiere ×4/3, así que un archivo de 50 MiB necesita ~67 MiB y nunca cabría en una sola
     * trama. Se anuncia el total, se envían trozos de {@link #PARTE_MAX_BYTES} y se cierra con
     * el SHA-256 del archivo completo, que es lo que el servidor verifica.
     *
     * <p>Si el servidor rechaza cualquiera de las tres fases se devuelve esa respuesta tal cual
     * y se abandona: el llamante la trata como un rechazo más y marca ERROR, igual que hace con
     * las imágenes. Solo una caída real de red lanza {@link IOException}, que es lo que dispara
     * el registro de pendiente y el reintento posterior.
     */
    @Override
    public RespuestaRed enviarArchivoFragmentado(String id, String remitente, String destinatario,
                                             String nombreArchivo, String mime, byte[] contenido)
            throws IOException {
        byte[] datos = contenido == null ? new byte[0] : contenido;
        if (datos.length == 0) {
            throw new IOException("el archivo esta vacio");
        }
        String archivoId = id != null ? id : UUID.randomUUID().toString();
        String fecha = LocalDateTime.now().toString();
        int totalPartes = (datos.length + PARTE_MAX_BYTES - 1) / PARTE_MAX_BYTES;
        Base64.Encoder encoder = Base64.getEncoder();

        RespuestaRed inicio = adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.ARCHIVO_INICIO)
                .id(archivoId).remitente(remitente).destinatario(destinatario)
                .nombreArchivo(nombreArchivo).mime(mime).tamanoArchivo((long) datos.length)
                .totalPartes(totalPartes).fechaHora(fecha).build()));
        if (!aceptado(inicio)) {
            return inicio;
        }

        for (int indice = 0; indice < totalPartes; indice++) {
            int desde = indice * PARTE_MAX_BYTES;
            int hasta = Math.min(desde + PARTE_MAX_BYTES, datos.length);
            RespuestaRed parte = adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.ARCHIVO_PARTE)
                    .id(archivoId + "#p" + indice).remitente(remitente).archivoId(archivoId)
                    .indiceParte(indice)
                    .contenidoImagen(encoder.encodeToString(
                            Arrays.copyOfRange(datos, desde, hasta)))
                    .fechaHora(fecha).build()));
            if (!aceptado(parte)) {
                return parte;
            }
        }

        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.ARCHIVO_FIN)
                .id(archivoId + "#fin").remitente(remitente).archivoId(archivoId)
                .hashSha256(sha256Hex(datos)).fechaHora(fecha).build()));
    }

    private static boolean aceptado(RespuestaRed respuesta) {
        return Boolean.TRUE.equals(respuesta.exito());
    }

    /** SHA-256 en minúsculas, el mismo formato que el servidor compara en `finalizarArchivo`. */
    private static String sha256Hex(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException imposible) {
            // SHA-256 es obligatorio en toda JVM (JEP 247).
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", imposible);
        }
    }

    @Override
    public RespuestaRed difundir(String remitente, String contenido) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.BROADCAST)
                .remitente(remitente).contenido(contenido).build()));
    }

    @Override
    public RespuestaRed difundir(String id, String remitente, String contenido) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.BROADCAST).id(id)
                .remitente(remitente).contenido(contenido).build()));
    }

    @Override
    public RespuestaRed listarConectados() throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.LISTAR_CONECTADOS).build()));
    }

    @Override
    public RespuestaRed expulsar(String codigo) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.KICK).codigo(codigo).build()));
    }

    // FASE 6

    @Override
    public RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina)
            throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.HISTORIAL_REQ)
                .remitente(remitente).destinatario(destinatario).pagina(pagina).build()));
    }

    @Override
    public RespuestaRed descargarArchivo(String archivoId) throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.DESCARGAR_ARCHIVO)
                .archivoId(archivoId).build()));
    }

    @Override
    public RespuestaRed enviarLectura(String lector, String autor, String idOriginal) throws IOException {
        Socket actual = socket;
        if (actual == null || actual.isClosed()) {
            throw new EOFException("sin conexion con el servidor");
        }
        Mensaje aviso = Mensaje.builder().tipo(TipoMensaje.MENSAJE_LEIDO)
                .id(UUID.randomUUID().toString())
                .fechaHora(LocalDateTime.now().toString())
                .remitente(lector).destinatario(autor).contenido(idOriginal).build();
        synchronized (candadoSalida) {
            TramaCodec.escribir(actual.getOutputStream(), aviso);
        }
        return adaptar(Mensaje.builder().tipo(TipoMensaje.ACK).exito(true).build());
    }

    @Override
    public RespuestaRed listarUsuarios() throws IOException {
        return adaptar(pedir(Mensaje.builder().tipo(TipoMensaje.LISTAR_USUARIOS).build()));
    }

    @Override
    public void logout() throws IOException {
        Socket actual = socket;
        if (actual == null || actual.isClosed()) {
            return;
        }
        Mensaje aviso = Mensaje.builder().tipo(TipoMensaje.LOGOUT)
                .id(UUID.randomUUID().toString())
                .fechaHora(LocalDateTime.now().toString()).build();
        synchronized (candadoSalida) {
            TramaCodec.escribir(actual.getOutputStream(), aviso);
        }
    }

    /** Envía correlacionando por id y bloquea hasta la respuesta o el timeout. */
    Mensaje pedir(Mensaje solicitud) throws IOException {
        Socket actual = socket;
        if (actual == null || actual.isClosed()) {
            throw new EOFException("sin conexion con el servidor");
        }

        String id = solicitud.id() != null ? solicitud.id() : UUID.randomUUID().toString();
        Mensaje conId = clonarConId(solicitud, id);
        CompletableFuture<Mensaje> futuro = new CompletableFuture<>();
        pendientes.put(id, futuro);
        try {
            synchronized (candadoSalida) {
                TramaCodec.escribir(actual.getOutputStream(), conId);
            }
            return futuro.get(TIMEOUT_RESPUESTA_SEG, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendientes.remove(id);
            throw new IOException("sin respuesta del servidor en " + TIMEOUT_RESPUESTA_SEG + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendientes.remove(id);
            throw new IOException("espera interrumpida", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IOException("conexion cerrada esperando respuesta", e.getCause());
        }
    }

    private static RespuestaRed adaptar(Mensaje mensaje) {
        return new RespuestaRed(TipoMensajeCliente.valueOf(mensaje.tipo().name()),
                mensaje.id(), mensaje.fechaHora(), mensaje.codigo(), mensaje.nombres(),
                mensaje.apellidos(), mensaje.programa(), mensaje.remitente(),
                mensaje.destinatario(), mensaje.contenido(), mensaje.hashSha256(),
                mensaje.numCaracteres(), mensaje.numPalabras(), mensaje.etapasFiltrado(),
                mensaje.nombreArchivo(), mensaje.tamanoArchivo(), mensaje.archivoId(),
                mensaje.mime(), mensaje.contenidoImagen(), mensaje.pagina(),
                mensaje.totalPaginas(), mensaje.exito(), mensaje.mensajeError(),
                mensaje.usuariosConectados(), mensaje.tipoOriginal());
    }

    private void bucleLectura(long mia) {
        Socket actual = socket;
        try {
            while (actual != null && !actual.isClosed()) {
                Mensaje trama = TramaCodec.leer(actual.getInputStream());
                encaminar(trama);
            }
        } catch (IOException fin) {
            // corte de red o cierre: fallar pendientes y avisar una vez
        } finally {
            // PAT-03: solo el lector vigente reporta. Si reconectamos o
            // cerramos a proposito mientras este terminaba, su aviso es obsoleto.
            if (mia != generacion.get()) {
                return;
            }
            pendientes.forEach((id, futuro) ->
                    futuro.completeExceptionally(new EOFException("conexion cerrada")));
            pendientes.clear();
            ReceptorMensajes obs = receptor;
            if (obs != null) {
                obs.alDesconexion();
            }
        }
    }

    private void encaminar(Mensaje trama) {
        if (trama.tipo() == TipoMensaje.CLOSE_NOTICE) {
            ReceptorMensajes obs = receptor;
            if (obs != null) {
                obs.alCierre(adaptar(trama));
            }
            return;
        }
        if (trama.id() != null) {
            CompletableFuture<Mensaje> futuro = pendientes.remove(trama.id());
            if (futuro != null) {
                futuro.complete(trama);
                return;
            }
        }
        ReceptorMensajes obs = receptor;
        if (obs == null) {
            return;
        }
        switch (trama.tipo()) {
            case MENSAJE_TEXTO, MENSAJE_IMAGEN, MENSAJE_ARCHIVO, BROADCAST, SYNC_LOGIN,
                 IMAGE_FILTERED_RESULT, HISTORIAL_PAGE,
                 DESCARGAR_ARCHIVO_RESPUESTA, MENSAJE_ENTREGADO,
                 MENSAJE_LEIDO, PRESENCIA,
                 LISTAR_USUARIOS_RESPUESTA, LISTAR_CONECTADOS_RESPUESTA ->
                       obs.alRecibir(adaptar(trama));
            case ERROR -> obs.alError(trama.mensajeError());
            default -> obs.alError("trama inesperada: " + trama.tipo());
        }
    }

    private static Mensaje clonarConId(Mensaje base, String id) {
        // Copia los 29 componentes del record. Omitir uno lo pierde en el socket: `totalPartes`
        // e `indiceParte` son los que hacen válida una transferencia fragmentada, y el servidor
        // rechaza ARCHIVO_INICIO / ARCHIVO_PARTE si no llegan.
        return Mensaje.builder().tipo(base.tipo()).id(id)
                .fechaHora(LocalDateTime.now().toString())
                .codigo(base.codigo()).contrasena(base.contrasena())
                .nombres(base.nombres()).apellidos(base.apellidos()).programa(base.programa())
                .remitente(base.remitente()).destinatario(base.destinatario())
                .contenido(base.contenido()).hashSha256(base.hashSha256())
                .numCaracteres(base.numCaracteres()).numPalabras(base.numPalabras())
                .etapasFiltrado(base.etapasFiltrado()).nombreArchivo(base.nombreArchivo())
                .tamanoArchivo(base.tamanoArchivo()).archivoId(base.archivoId()).mime(base.mime())
                .contenidoImagen(base.contenidoImagen()).pagina(base.pagina())
                .totalPaginas(base.totalPaginas()).exito(base.exito())
                .mensajeError(base.mensajeError()).ipRemitente(base.ipRemitente())
                .ipDestinatario(base.ipDestinatario())
                .usuariosConectados(base.usuariosConectados())
                .totalPartes(base.totalPartes()).indiceParte(base.indiceParte())
                .tipoOriginal(base.tipoOriginal()).build();
    }
}
