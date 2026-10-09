package universidad.mensajeria.cliente.fachada.servicios;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.fachada.InterfazCoordinadorMensajeria;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionValidacion;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PendienteEnvio;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.cliente.transversal.utilerias.TextoCliente;
import universidad.mensajeria.cliente.transversal.utilerias.ValidacionCliente;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Servicio enfocado en el envío de mensajes 1-a-1 (texto, imagen, archivo) y broadcast (Fase 4).
 * <p>
 * Implementa el patrón "guardar primero — intentar por red — guardar pendiente si hay fallo de red"
 * y queda separado de las responsabilidades de sesión y reconexión de {@code FachadaCliente}.
 * No conoce JavaFX ni la UI.
 * </p>
 */
public final class CoordinadorMensajeriaCliente implements InterfazCoordinadorMensajeria {

    private final InterfazConexionRed red;
    private final InterfazHistorialLocal historial;

    public CoordinadorMensajeriaCliente(InterfazConexionRed red,
                                        InterfazHistorialLocal historial) {
        this.red = red;
        this.historial = historial;
    }

    /**
     * Envía un mensaje de texto 1-a-1.
     * Guarda localmente primero; si la red falla, registra pendiente.
     *
     * @return {@code true} si salió por red, {@code false} si quedó pendiente
     */
    @Override
    public boolean enviarTexto(String yo, String destinatario, String contenido) throws ExcepcionCliente {
        ValidacionCliente.destinatario(destinatario);
        ValidacionCliente.contenido(contenido);
        String id = UUID.randomUUID().toString();
        String ahora = LocalDateTime.now().toString();
        historial.guardar(new MensajeLocal(id, yo, destinatario,
                TipoMensajeCliente.MENSAJE_TEXTO.name(), contenido, null, contenido.length(),
                TextoCliente.contarPalabras(contenido), null, null, null, ahora, true, true,
                EstadoMensaje.PENDIENTE.name()));
        try {
            RespuestaRed ack = red.enviarTexto(id, yo, destinatario, contenido);
            exigirAceptado(ack, "envio rechazado");
            historial.marcarEstado(id, EstadoMensaje.ENVIADO.name());
            return true;
        } catch (IOException sinRed) {
            historial.marcarEstado(id, EstadoMensaje.PENDIENTE.name());
            historial.registrarPendiente(new PendienteEnvio(id,
                    TipoMensajeCliente.MENSAJE_TEXTO.name(), yo, destinatario,
                    contenido, null, null, ahora, 0, sinRed.getMessage()));
            return false;
        } catch (RuntimeException rechazo) {
            historial.marcarEstado(id, EstadoMensaje.ERROR.name());
            throw rechazo;
        }
    }

    /**
     * Envía una imagen 1-a-1 (bytes ya elegidos por el usuario).
     * Guarda el blob en Base64 localmente antes de intentar la red.
     *
     * @return {@code true} si salió por red, {@code false} si quedó pendiente
     */
    @Override
    public boolean enviarImagen(String yo, String destinatario, byte[] bytes,
                                String nombreArchivo, String mime) throws ExcepcionCliente {
        ValidacionCliente.destinatario(destinatario);
        ValidacionCliente.imagen(bytes);
        String id = UUID.randomUUID().toString();
        String ahora = LocalDateTime.now().toString();
        String base64 = Base64.getEncoder().encodeToString(bytes);
        historial.guardar(new MensajeLocal(id, yo, destinatario,
                TipoMensajeCliente.MENSAJE_IMAGEN.name(), base64, null, null, null,
                nombreArchivo, null, (long) bytes.length, ahora, true, true,
                EstadoMensaje.PENDIENTE.name()));
        try {
            RespuestaRed resultado = red.enviarImagen(id, yo, destinatario,
                    nombreArchivo, mime, base64);
            if (!Boolean.TRUE.equals(resultado.exito())
                    && resultado.tipo() != TipoMensajeCliente.IMAGE_FILTERED_RESULT) {
                throw new ExcepcionSesion(resultado.mensajeError() != null
                        ? resultado.mensajeError() : "envio rechazado");
            }
            if (resultado.archivoId() != null) {
                historial.actualizarArchivoId(id, resultado.archivoId());
            }
            if (resultado.etapasFiltrado() != null) {
                historial.actualizarEtapasFiltrado(id, resultado.etapasFiltrado());
            }
            historial.marcarEstado(id, EstadoMensaje.ENVIADO.name());
            return true;
        } catch (IOException sinRed) {
            historial.marcarEstado(id, EstadoMensaje.PENDIENTE.name());
            historial.registrarPendiente(new PendienteEnvio(id,
                    TipoMensajeCliente.MENSAJE_IMAGEN.name(), yo, destinatario,
                    null, nombreArchivo, bytes, ahora, 0, sinRed.getMessage()));
            return false;
        } catch (RuntimeException rechazo) {
            historial.marcarEstado(id, EstadoMensaje.ERROR.name());
            throw rechazo;
        }
    }

    /**
     * Envía un archivo genérico (PDF, docs, zip) 1-a-1.
     *
     * @return {@code true} si salió por red, {@code false} si quedó pendiente
     */
    @Override
    public boolean enviarArchivo(String yo, String destinatario, byte[] bytes,
                                 String nombreArchivo, String mime) throws ExcepcionCliente {
        ValidacionCliente.destinatario(destinatario);
        if (bytes == null || bytes.length == 0) {
            throw new ExcepcionValidacion("el archivo esta vacio");
        }
        String id = UUID.randomUUID().toString();
        String ahora = LocalDateTime.now().toString();
        // Un archivo de 50 MiB en Base64 ocuparía ~67 MiB en la columna `contenido` y el
        // preview del sidebar saldría con basura. Se guarda el marcador, como el cliente Python:
        // el bytes va por `archivoId` y la celda pinta "📎 nombre (N bytes)".
        historial.guardar(new MensajeLocal(id, yo, destinatario,
                TipoMensajeCliente.MENSAJE_ARCHIVO.name(), MARCA_ARCHIVO, null, null, null,
                nombreArchivo, null, (long) bytes.length, ahora, true, true,
                EstadoMensaje.PENDIENTE.name()));
        try {
            RespuestaRed resultado = red.enviarArchivoFragmentado(id, yo, destinatario,
                    nombreArchivo, mime == null ? MIME_POR_DEFECTO : mime, bytes);
            exigirAceptado(resultado, "envio rechazado");
            if (resultado.archivoId() != null) {
                historial.actualizarArchivoId(id, resultado.archivoId());
            }
            historial.marcarEstado(id, EstadoMensaje.ENVIADO.name());
            return true;
        } catch (IOException sinRed) {
            historial.marcarEstado(id, EstadoMensaje.PENDIENTE.name());
            historial.registrarPendiente(new PendienteEnvio(id,
                    TipoMensajeCliente.MENSAJE_ARCHIVO.name(), yo, destinatario,
                    null, nombreArchivo, bytes, ahora, 0, sinRed.getMessage()));
            return false;
        } catch (RuntimeException rechazo) {
            historial.marcarEstado(id, EstadoMensaje.ERROR.name());
            throw rechazo;
        }
    }

    private static void exigirAceptado(RespuestaRed respuesta, String error) {
        if (!Boolean.TRUE.equals(respuesta.exito())) {
            throw new ExcepcionSesion(respuesta.mensajeError() != null
                    ? respuesta.mensajeError() : error);
        }
    }

    /** Texto en la columna `contenido` de un archivo. Debe coincidir con el cliente Python. */
    static final String MARCA_ARCHIVO = "[Archivo]";
    private static final String MIME_POR_DEFECTO = "application/octet-stream";
}
