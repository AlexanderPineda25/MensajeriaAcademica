package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.fachada.InterfazReintentadorPendientes;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.PendienteEnvio;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;

import java.io.IOException;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Servicio enfocado en reenviar la cola local de pendientes en orden (Fase 4).
 * <p>
 * Extraído de {@code FachadaCliente} por responsabilidad única: la fachada
 * decide <em>cuándo</em> reintentar (login, reconexión, flush), este servicio
 * sabe <em>cómo</em> reenviar cada tipo. No conoce JavaFX ni la UI.
 * </p>
 */
public final class ReintentadorPendientesCliente implements InterfazReintentadorPendientes {

    private static final Logger LOG = Logger.getLogger(ReintentadorPendientesCliente.class.getName());

    private final InterfazConexionRed red;
    private final InterfazHistorialLocal historial;
    private final Supplier<String> codigoActual;

    public ReintentadorPendientesCliente(InterfazConexionRed red,
                                         InterfazHistorialLocal historial,
                                         Supplier<String> codigoActual) {
        this.red = red;
        this.historial = historial;
        this.codigoActual = codigoActual;
    }

    /** Reenvía pendientes en orden; devuelve cuántos salieron. */
    @Override
    public int reintentarPendientes() throws ExcepcionCliente, IOException {
        if (codigoActual.get() == null || !red.conectado()) {
            return 0;
        }
        int enviados = 0;
        for (PendienteEnvio pendiente : historial.pendientes()) {
            if (!reintentarUno(pendiente)) {
                break;
            }
            enviados++;
        }
        return enviados;
    }

    private boolean reintentarUno(PendienteEnvio pendiente) throws ExcepcionCliente, IOException {
        try {
            if (TipoMensajeCliente.MENSAJE_LEIDO.name().equals(pendiente.tipo())) {
                reintentarLectura(pendiente);
            } else if (TipoMensajeCliente.MENSAJE_IMAGEN.name().equals(pendiente.tipo())) {
                reintentarImagen(pendiente);
            } else if (TipoMensajeCliente.MENSAJE_ARCHIVO.name().equals(pendiente.tipo())) {
                reintentarArchivo(pendiente);
            } else {
                reintentarTexto(pendiente);
            }
            historial.eliminarPendiente(pendiente.id());
            return true;
        } catch (ExcepcionSesion rechazo) {
            historial.marcarEstado(pendiente.id(), EstadoMensaje.ERROR.name());
            historial.eliminarPendiente(pendiente.id());
            LOG.fine(() -> "[reintento] pendiente descartado por rechazo: "
                    + pendiente.id() + " (" + rechazo.getMessage() + ")");
            return true;
        } catch (IOException | RuntimeException e) {
            historial.incrementarIntento(pendiente.id(), e.getMessage());
            return false;
        }
    }

    private void reintentarLectura(PendienteEnvio pendiente) throws ExcepcionCliente, IOException {
        RespuestaRed ack = red.enviarLectura(
                pendiente.origen(), pendiente.destino(), pendiente.contenido());
        exigirExito(ack, "acuse de lectura rechazado");
    }

    private void reintentarImagen(PendienteEnvio pendiente) throws ExcepcionCliente, IOException {
        String base64 = pendiente.payload() == null ? ""
                : Base64.getEncoder().encodeToString(pendiente.payload());
        RespuestaRed ack = red.enviarImagen(pendiente.id(), pendiente.origen(), pendiente.destino(),
                pendiente.nombreArchivo(), "application/octet-stream", base64);
        exigirExito(ack, "reenvio de imagen rechazado");
        if (ack.archivoId() != null) {
            historial.actualizarArchivoId(pendiente.id(), ack.archivoId());
        }
        historial.marcarEstado(pendiente.id(), EstadoMensaje.ENVIADO.name());
    }

    private void reintentarArchivo(PendienteEnvio pendiente) throws ExcepcionCliente, IOException {
        // El pendiente guarda los bytes crudos, así que el reintento también va fragmentado:
        // un archivo grande que quedó en cola sin red no debe fallar al reenviarse.
        byte[] datos = pendiente.payload() == null ? new byte[0] : pendiente.payload();
        RespuestaRed ack = red.enviarArchivoFragmentado(pendiente.id(), pendiente.origen(),
                pendiente.destino(), pendiente.nombreArchivo(), "application/octet-stream", datos);
        exigirExito(ack, "reenvio de archivo rechazado");
        if (ack.archivoId() != null) {
            historial.actualizarArchivoId(pendiente.id(), ack.archivoId());
        }
        historial.marcarEstado(pendiente.id(), EstadoMensaje.ENVIADO.name());
    }

    private void reintentarTexto(PendienteEnvio pendiente) throws ExcepcionCliente, IOException {
        RespuestaRed ack = red.enviarTexto(pendiente.id(), pendiente.origen(),
                pendiente.destino(), pendiente.contenido());
        exigirExito(ack, "reenvio de mensaje rechazado");
        historial.marcarEstado(pendiente.id(), EstadoMensaje.ENVIADO.name());
    }

    private static void exigirExito(RespuestaRed respuesta, String error) {
        if (!Boolean.TRUE.equals(respuesta.exito())
                && respuesta.tipo() != TipoMensajeCliente.IMAGE_FILTERED_RESULT) {
            throw new ExcepcionSesion(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : error);
        }
    }
}
