package universidad.mensajeria.cliente.presentacion;

import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;
import universidad.mensajeria.cliente.transversal.utilerias.TextoCliente;

import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Adjunto de entrada del compositor: elegir, previsualizar y quitar (Fase 3).
 * <p>
 * Extraído de {@code ChatController} por responsabilidad única: lee el archivo
 * en fondo (tope 50 MiB), arma la miniatura y limpia al enviar. No decide
 * envíos ni conversaciones; todo lo externo llega por constructor (DIP).
 * </p>
 */
public final class CargadorAdjuntoEntradaChat {

    private static final Set<String> EXT_IMAGEN = Set.of("png", "jpg", "jpeg", "gif");
    private static final int PREVIEW_ANCHO = 120;

    private final ImageView vistaPrevia;
    private final ListView<FilaChat> listaMensajes;
    private final Label etiquetaAdjunto;
    private final InterfazChatViewModel viewModel;
    private final Executor tareasFondo;
    private final Consumer<String> estado;
    private int versionAdjunto;

    public CargadorAdjuntoEntradaChat(ImageView vistaPrevia,
                                      ListView<FilaChat> listaMensajes,
                                      Label etiquetaAdjunto,
                                      InterfazChatViewModel viewModel,
                                      Executor tareasFondo,
                                      Consumer<String> estado) {
        this.vistaPrevia = vistaPrevia;
        this.listaMensajes = listaMensajes;
        this.etiquetaAdjunto = etiquetaAdjunto;
        this.viewModel = viewModel;
        this.tareasFondo = tareasFondo;
        this.estado = estado;
    }

    /** Arrastra-y-suelta sobre el historial como vía alterna de adjuntar. */
    public void configurarArrastre() {
        listaMensajes.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        listaMensajes.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasFiles()) {
                for (File f : db.getFiles()) {
                    if (f.isFile()) {
                        cargarArchivo(f);
                        ok = true;
                        break;
                    }
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    public void elegir() {
        FileChooser elegidor = new FileChooser();
        elegidor.setTitle("Elegir archivo (imagen, PDF, documento...)");
        elegidor.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Todos", "*.*"),
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("Documentos", "*.pdf", "*.txt", "*.docx", "*.xlsx", "*.pptx", "*.zip"));
        cargarArchivo(elegidor.showOpenDialog(listaMensajes.getScene().getWindow()));
    }

    public void limpiar() {
        versionAdjunto++;
        viewModel.limpiarAdjunto();
        vistaPrevia.setImage(null);
    }

    /** Carga un archivo elegido o arrastrado (costura de prueba: RNF-19). */
    void cargarArchivo(File archivo) {
        if (archivo == null) {
            return;
        }
        int version = ++versionAdjunto;
        Task<byte[]> tarea = new Task<>() {
            @Override
            protected byte[] call() throws Exception {
                byte[] bytes = Files.readAllBytes(archivo.toPath());
                if (bytes.length > TextoCliente.TOPE_ADJUNTO_BYTES) {
                    throw new universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion(
                            "Máximo 50 MiB");
                }
                return bytes;
            }
        };
        tarea.setOnSucceeded(e -> alArchivoLeido(archivo, version, tarea.getValue()));
        tarea.setOnFailed(e -> estado.accept("No se pudo leer el archivo: "
                + FormatoErrorCliente.mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    private void alArchivoLeido(File archivo, int version, byte[] bytesArchivo) {
        if (version != versionAdjunto) {
            return;
        }
        viewModel.prepararAdjunto(bytesArchivo, archivo.getName(),
                TextoCliente.mimeDe(archivo.getName()));
        etiquetaAdjunto.setText(viewModel.adjuntoNombre());
        boolean esImagen = EXT_IMAGEN.contains(TextoCliente.extension(archivo.getName()));
        Task<Image> preview = new Task<>() {
            @Override
            protected Image call() {
                if (!esImagen) {
                    return null;
                }
                return new Image(new java.io.ByteArrayInputStream(bytesArchivo),
                        PREVIEW_ANCHO, 0, true, true);
            }
        };
        preview.setOnSucceeded(done -> {
            if (version != versionAdjunto) {
                return;
            }
            vistaPrevia.setImage(preview.getValue());
            etiquetaAdjunto.setText(viewModel.adjuntoNombre()
                    + (esImagen ? "" : " (archivo)"));
        });
        tareasFondo.execute(preview);
    }
}
