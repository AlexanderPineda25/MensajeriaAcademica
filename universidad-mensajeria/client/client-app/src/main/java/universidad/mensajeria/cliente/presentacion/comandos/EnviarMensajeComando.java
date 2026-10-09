package universidad.mensajeria.cliente.presentacion.comandos;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import universidad.mensajeria.cliente.presentacion.InterfazChatViewModel;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.utilerias.TextoCliente;

/**
 * Envía texto y/o adjunto de la conversación actual.
 */
public final class EnviarMensajeComando implements ComandoChat {

    private final InterfazChatViewModel viewModel;
    private final Executor fondo;
    private final String destino;
    private final String texto;
    private final byte[] adjunto;
    private final String adjuntoNombre;
    private final MensajeLocal respuesta;
    private final Consumer<Boolean> alTerminar;
    private final Consumer<String> alError;

    public EnviarMensajeComando(InterfazChatViewModel viewModel, Executor fondo,
                               String destino, String texto, byte[] adjunto,
                               String adjuntoNombre, MensajeLocal respuesta,
                               Consumer<Boolean> alTerminar, Consumer<String> alError) {
        this.viewModel = viewModel;
        this.fondo = fondo;
        this.destino = destino;
        this.texto = texto == null ? "" : texto.trim();
        this.adjunto = adjunto == null ? null : adjunto.clone();
        this.adjuntoNombre = adjuntoNombre;
        this.respuesta = respuesta;
        this.alTerminar = alTerminar;
        this.alError = alError;
    }

    @Override
    public void ejecutar() {
        Task<Boolean> tarea = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                boolean enRed = true;
                if (!texto.isBlank()) {
                    enRed = viewModel.enviarTexto(destino, texto, respuesta);
                }
                if (adjunto != null) {
                    String ext = TextoCliente.extension(adjuntoNombre);
                    if (esImagen(ext)) {
                        enRed = viewModel.enviarImagen(destino, adjunto, adjuntoNombre,
                                "image/" + ext) && enRed;
                    } else {
                        enRed = viewModel.enviarArchivo(destino, adjunto, adjuntoNombre,
                                TextoCliente.mimeDe(adjuntoNombre)) && enRed;
                    }
                }
                return enRed;
            }
        };
        tarea.setOnSucceeded(e -> alTerminar.accept(tarea.getValue()));
        tarea.setOnFailed(e -> alError.accept(tarea.getException() == null
                ? "envío fallido" : tarea.getException().getMessage()));
        fondo.execute(tarea);
    }

    private static boolean esImagen(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) || "gif".equals(ext);
    }
}
