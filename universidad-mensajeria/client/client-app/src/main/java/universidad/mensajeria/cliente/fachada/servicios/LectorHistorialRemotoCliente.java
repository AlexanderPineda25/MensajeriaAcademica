package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.InterfazMapeadorDatosServidor;
import universidad.mensajeria.cliente.fachada.InterfazCoordinadorHistorial;
import universidad.mensajeria.cliente.fachada.InterfazLectorHistorialRemoto;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Servicio enfocado en leer historial remoto y descargar bytes bajo demanda (Fase 4).
 * <p>
 * Extraído de {@code FachadaCliente} por responsabilidad única: traer páginas,
 * persistirlas (upsert por id) y servir miniaturas sin traer la lista entera.
 * No conoce JavaFX ni la UI.
 * </p>
 */
public final class LectorHistorialRemotoCliente implements InterfazLectorHistorialRemoto {

    private static final Logger LOG = Logger.getLogger(LectorHistorialRemotoCliente.class.getName());

    private final InterfazConexionRed red;
    private final InterfazHistorialLocal historial;
    private final InterfazMapeadorDatosServidor mapeadorDatosServidor;
    private final InterfazCoordinadorHistorial coordinadorHistorial;
    private final Supplier<String> codigoActual;

    public LectorHistorialRemotoCliente(InterfazConexionRed red,
                                        InterfazHistorialLocal historial,
                                        InterfazMapeadorDatosServidor mapeadorDatosServidor,
                                        InterfazCoordinadorHistorial coordinadorHistorial,
                                        Supplier<String> codigoActual) {
        this.red = red;
        this.historial = historial;
        this.mapeadorDatosServidor = mapeadorDatosServidor;
        this.coordinadorHistorial = coordinadorHistorial;
        this.codigoActual = codigoActual;
    }

    /** Historial offline paginado (más reciente primero, como el contrato). */
    @Override
    public List<MensajeLocal> conversacion(String otro, int limite, int offset)
            throws ExcepcionCliente, IOException {
        exigirSesion();
        long inicio = System.nanoTime();
        List<MensajeLocal> pagina = coordinadorHistorial.conversacion(
                codigoActual.get(), otro, limite, offset);
        List<MensajeLocal> cronologica = new ArrayList<>(pagina);
        java.util.Collections.reverse(cronologica);
        LOG.fine(() -> "[perf] conversacion=" + entre(System.nanoTime(), inicio)
                + "ms (" + cronologica.size() + " msgs)");
        return cronologica;
    }

    @Override
    public int contarConversacion(String otro) throws ExcepcionCliente, IOException {
        exigirSesion();
        return coordinadorHistorial.contarConversacion(codigoActual.get(), otro);
    }

    /**
     * Ids de mensajes con el patrón (de más nuevo a más viejo, tope 200).
     * La vista salta entre ellos sin traer el historial entero.
     */
    @Override
    public List<String> buscarEnHistorial(String otro, String patron)
            throws ExcepcionCliente, IOException {
        exigirSesion();
        return coordinadorHistorial.buscarEnHistorial(codigoActual.get(), otro, patron, 200);
    }

    /**
     * Trae UNA página remota (`HISTORIAL_PAGE`, 50 msg, sin bytes), la persiste
     * (upsert por id) y registra `archivoId` para descargas.
     * Llamar en hilo de fondo: `pedir()` bloquea hasta 15 s.
     */
    @Override
    public PaginaHistorialRemota traerHistorial(String otro, int pagina)
            throws ExcepcionCliente, IOException {
        exigirSesion();
        long inicio = System.nanoTime();
        RespuestaRed respuesta = red.solicitarHistorial(codigoActual.get(), otro, pagina);
        if (!Boolean.TRUE.equals(respuesta.exito())) {
            throw new ExcepcionSesion(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : "historial rechazado");
        }
        List<MensajeLocal> mensajes = mapeadorDatosServidor
                .mapearPaginaHistorial(respuesta.contenido(), codigoActual.get());
        for (MensajeLocal mensaje : mensajes) {
            historial.guardar(mensaje);
        }
        int recibidos = mensajes.size();
        int total = respuesta.totalPaginas() != null ? respuesta.totalPaginas() : 0;
        LOG.fine("[perf] historialRemoto pag=" + pagina + " recibidos=" + recibidos
                + " en " + entre(System.nanoTime(), inicio) + "ms");
        return new PaginaHistorialRemota(recibidos, pagina, total);
    }

    /** Bytes de UN mensaje (miniatura visible), nunca la lista. */
    @Override
    public Optional<String> blobMensaje(String idMensaje) throws ExcepcionCliente {
        return coordinadorHistorial.blobMensaje(idMensaje);
    }

    /**
     * Descarga los bytes de una imagen (`DESCARGAR_ARCHIVO`), los guarda en H2
     * y devuelve los bytes para la miniatura.
     */
    @Override
    public byte[] descargarImagen(String idMensaje) throws ExcepcionCliente, IOException {
        exigirSesion();
        java.util.Optional<String> local = historial.blobPorId(idMensaje);
        if (local.isPresent() && !local.get().isBlank()) {
            return Base64.getDecoder().decode(local.get());
        }
        String archivoId = historial.archivoIdPorId(idMensaje).orElse(null);
        if (archivoId == null) {
            throw new ExcepcionSesion(
                    "metadatos de imagen pendientes; actualiza esta página del historial");
        }
        RespuestaRed respuesta = red.descargarArchivo(archivoId);
        if (!Boolean.TRUE.equals(respuesta.exito()) || respuesta.contenidoImagen() == null) {
            throw new ExcepcionSesion(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : "descarga rechazada");
        }
        byte[] bytes = Base64.getDecoder().decode(respuesta.contenidoImagen());
        historial.actualizarContenido(idMensaje,
                Base64.getEncoder().encodeToString(bytes));
        return bytes;
    }

    /** Descarga un adjunto sin exponer el DTO de red a la presentación. */
    @Override
    public byte[] descargarContenidoArchivo(String archivoId) throws ExcepcionCliente, IOException {
        exigirSesion();
        RespuestaRed respuesta = red.descargarArchivo(archivoId);
        if (!Boolean.TRUE.equals(respuesta.exito()) || respuesta.contenidoImagen() == null) {
            throw new ExcepcionSesion(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : "descarga no disponible");
        }
        return Base64.getDecoder().decode(respuesta.contenidoImagen());
    }

    /** Descarga de archivo del servidor bajo demanda. */
    @Override
    public RespuestaRed descargarArchivo(String archivoId) throws ExcepcionCliente, IOException {
        exigirSesion();
        return red.descargarArchivo(archivoId);
    }

    private void exigirSesion() {
        if (codigoActual.get() == null) {
            throw new ExcepcionSesion("inicia sesion primero");
        }
    }

    /** Milisegundos entre dos `nanoTime`. */
    private static long entre(long finNanos, long inicioNanos) {
        return (finNanos - inicioNanos) / 1_000_000;
    }
}
