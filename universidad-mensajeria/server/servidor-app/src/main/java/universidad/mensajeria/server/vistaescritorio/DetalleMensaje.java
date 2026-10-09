package universidad.mensajeria.server.vistaescritorio;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import universidad.mensajeria.common.interno.MensajeResumenDTO;
import universidad.mensajeria.server.transversal.fachada.Fachada;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Panel maestro-detalle reutilizable (pestaña Mensajes, Informes cuando el
 * tipo es mensajes, y ficha de usuario). Extraido de VistaEscritorio sin
 * cambiar comportamiento: solo presenta, previsualiza con filtros locales y
 * descarga; nunca persiste ni reordena la tuberia del servidor.
 */
final class DetalleMensaje {
    private final Label titulo = new Label("Detalle: selecciona un mensaje");
    private final TextArea contenido = new TextArea();
    private final Label meta = new Label(" ");
    private final ImageView vista = new ImageView();
    private final ComboBox<String> filtros = new ComboBox<>();
    private final Label estado =
            new Label("Filtros de previsualizacion (no reescriben uploads).");
    private final VBox cajaFiltros;
    private final VBox caja;
    private final ScrollPane desplazamiento;
    private final Stage escenario;
    private final Supplier<Fachada> fachada;
    private final Function<String, String> nombres;
    private final Function<String, String> ips;
    private MensajeResumenDTO seleccionado;
    private byte[] bytesImagen;
    private String nombreImagen = "";

    DetalleMensaje(Stage escenario, Supplier<Fachada> fachada,
                   Function<String, String> nombres, Function<String, String> ips) {
        this.escenario = escenario;
        this.fachada = fachada;
        this.nombres = nombres;
        this.ips = ips;
        titulo.setStyle("-fx-font-weight: bold;");
        contenido.setEditable(false);
        contenido.setWrapText(true);
        contenido.setPrefRowCount(3);
        contenido.setPromptText("El contenido del texto aparece aqui (no solo hash/metricas).");
        meta.setWrapText(true);
        meta.getStyleClass().add("message-meta-list");
        vista.setFitWidth(380);
        vista.setFitHeight(200);
        vista.setPreserveRatio(true);
        vista.setSmooth(true);
        filtros.getItems().setAll(
                "Original", "Grises", "Sepia", "Giro 180", "Brillo x1.25", "Reducida 50%");
        filtros.setValue("Original");
        estado.setWrapText(true);
        Button aplicar = VistaEscritorio.boton("Previsualizar filtro",
                () -> VistaEscritorio.ejecutarSeguro(this::aplicarFiltro));
        Button descargar = VistaEscritorio.boton("Descargar archivo",
                () -> VistaEscritorio.ejecutarSeguro(this::descargar));
        HBox fila = new HBox(8, new Label("Filtro:"), filtros, aplicar, descargar);
        cajaFiltros = new VBox(6, vista, fila, estado);
        cajaFiltros.setMinWidth(260);
        cajaFiltros.widthProperty().addListener((obs, anterior, actual) ->
                vista.setFitWidth(Math.max(120, Math.min(560, actual.doubleValue() - 12))));
        cajaFiltros.setVisible(false);
        cajaFiltros.setManaged(false);
        VBox detalle = new VBox(8, titulo, cajaFiltros, contenido, meta);
        caja = new VBox(detalle);
        caja.setFillWidth(true);
        desplazamiento = new ScrollPane(caja);
        desplazamiento.setFitToWidth(true);
        desplazamiento.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazamiento.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        desplazamiento.setMinHeight(150);
    }

    ScrollPane vista() {
        return desplazamiento;
    }

    void mostrar(MensajeResumenDTO m) {
        seleccionado = m;
        bytesImagen = null;
        nombreImagen = "";
        vista.setImage(null);
        if (m == null) {
            titulo.setText("Detalle: selecciona un mensaje");
            contenido.clear();
            meta.setText(" ");
            cajaFiltros.setVisible(false);
            cajaFiltros.setManaged(false);
            estado.setText("Filtros de previsualizacion (no reescriben uploads).");
            return;
        }
        titulo.setText("Detalle #" + m.id() + " " + VistaEscritorio.txt(m.tipo())
                + " " + nombres.apply(m.remitente()) + " -> " + nombres.apply(m.destinatario()));
        String tipo = m.tipo() == null ? "" : m.tipo().toUpperCase();
        actualizarMeta(m);

        if ("TEXTO".equals(tipo) || "BROADCAST".equals(tipo)) {
            cajaFiltros.setVisible(false);
            cajaFiltros.setManaged(false);
            contenido.setText(m.contenido() == null ? "(sin contenido)" : m.contenido());
            estado.setText("El texto no admite filtros visuales.");
            return;
        }
        cajaFiltros.setVisible(true);
        cajaFiltros.setManaged(true);
        filtros.setDisable(!"IMAGEN".equals(tipo));
        // IMAGEN / ARCHIVO: contenido es null en BD; se muestra nombre + preview si hay bytes.
        StringBuilder cuerpo = new StringBuilder();
        if (m.nombreArchivo() != null) {
            cuerpo.append(m.nombreArchivo());
        }
        if (m.mime() != null) {
            cuerpo.append(" [").append(m.mime()).append("]");
        }
        cuerpo.append("\n(hash ").append(VistaEscritorio.txt(m.hashSha256())).append(")");
        contenido.setText(cuerpo.toString());
        if (m.archivoId() == null) {
            estado.setText("No hay archivo disponible para mostrar. Intenta actualizar el mensaje.");
            return;
        }
        Fachada f = fachada.get();
        if (f == null) return;
        long idSeleccionado = m.id();
        nombreImagen = m.nombreArchivo() == null ? "archivo" : m.nombreArchivo();
        String nombreParaCarga = nombreImagen;
        estado.setText("Cargando archivo para la vista previa…");
        Thread carga = new Thread(() -> {
            byte[] bytes = null;
            Image imagenCargada = null;
            RuntimeException fallo = null;
            try {
                bytes = f.bytesArchivoParaVista(m.archivoId());
                if (bytes != null && VistaEscritorio.esImagen(bytes, m.mime(), nombreParaCarga)) {
                    imagenCargada = new Image(new ByteArrayInputStream(bytes),
                            560, 360, true, true);
                }
            } catch (RuntimeException e) {
                fallo = e;
            }
            byte[] resultado = bytes;
            RuntimeException error = fallo;
            Image imagenLista = imagenCargada;
            Platform.runLater(() -> {
                if (seleccionado == null || seleccionado.id() != idSeleccionado) return;
                if (error != null) {
                    estado.setText("No se pudo cargar el archivo. Intenta actualizar o descargarlo.");
                    return;
                }
                if (resultado == null || resultado.length == 0) {
                    estado.setText("El archivo no está disponible para la vista previa.");
                    return;
                }
                if (!VistaEscritorio.esImagen(resultado, m.mime(), nombreImagen)) {
                    estado.setText("Archivo no visualizable como imagen (" + resultado.length
                            + " bytes): puedes descargarlo.");
                    return;
                }
                bytesImagen = resultado.clone();
                // JavaFX decodifica y escala en segundo plano; el tamaño es
                // un límite de presentación y siempre conserva la proporción.
                if (imagenLista == null || imagenLista.isError()) {
                    estado.setText("No se pudo decodificar esta imagen; puedes descargar el archivo.");
                    return;
                }
                vista.setImage(imagenLista);
                estado.setText(VistaEscritorio.decodificableParaFiltros(bytesImagen)
                        ? "Imagen cargada. Selecciona un filtro para previsualizarlo."
                        : "Vista previa cargada; este formato no admite filtros visuales.");
            });
        }, "vista-previa-mensaje-" + idSeleccionado);
        carga.setDaemon(true);
        carga.start();
    }

    private void actualizarMeta(MensajeResumenDTO m) {
        StringBuilder metaTxt = new StringBuilder();
        metaTxt.append("Hash: ").append(VistaEscritorio.txt(m.hashSha256()))
                .append("\nCaracteres: ").append(m.numCaracteres() == null
                        ? "No aplica" : m.numCaracteres())
                .append("\nPalabras: ").append(m.numPalabras() == null
                        ? "No aplica" : m.numPalabras())
                .append("\nIP remitente (histórica): ")
                .append(VistaEscritorio.txt(m.ipRemitente()))
                .append("\nIP destinatario (sesiones activas): ")
                .append(ips.apply(m.destinatario()));
        if (m.nombreArchivo() != null) {
            metaTxt.append("\nArchivo: ").append(m.nombreArchivo());
        }
        if (m.tamanoArchivo() != null) {
            metaTxt.append(" · ").append(m.tamanoArchivo()).append(" bytes");
        }
        if (m.etapasFiltrado() != null && !m.etapasFiltrado().isBlank()) {
            metaTxt.append("\nFiltros aplicados: ")
                    .append(VistaEscritorio.resumirEtapas(m.etapasFiltrado()));
        }
        meta.setText(metaTxt.toString());
    }

    void actualizarIpDestinatario() {
        if (seleccionado != null) actualizarMeta(seleccionado);
    }

    /** Descarga lo mostrado: bytes del archivo o .txt con el contenido. */
    void descargar() {
        MensajeResumenDTO m = seleccionado;
        if (m == null) {
            estado.setText("Selecciona primero un mensaje para descargar.");
            return;
        }
        String tipo = m.tipo() == null ? "" : m.tipo().toUpperCase();
        try {
            if ("TEXTO".equals(tipo) || "BROADCAST".equals(tipo)) {
                guardarBytes(("mensaje-" + m.id() + ".txt").replaceAll("[^A-Za-z0-9._-]", "_"),
                        (m.contenido() == null ? "" : m.contenido())
                                .getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (m.archivoId() == null) {
                estado.setText("Este mensaje no tiene archivo asociado.");
                return;
            }
            Fachada f = fachada.get();
            if (f == null) {
                return;
            }
            byte[] bytes = f.bytesArchivoParaVista(m.archivoId());
            String nombre = (m.nombreArchivo() == null || m.nombreArchivo().isBlank())
                    ? ("archivo-" + m.id()) : m.nombreArchivo();
            String filtro = filtros == null ? "Original" : filtros.getValue();
            if (!"Original".equals(filtro) && bytesImagen != null && bytesImagen.length > 0) {
                // PLAN_MEJORAS §3: la descarga respeta el filtro elegido en vista.
                try {
                    java.awt.image.BufferedImage base = javax.imageio.ImageIO.read(
                            new ByteArrayInputStream(bytesImagen));
                    if (base == null) {
                        estado.setText("Este formato se puede ver pero no admite"
                                + " previsualizar filtros; se descarga el original.");
                    } else {
                        java.awt.image.BufferedImage filtrada =
                                VistaEscritorio.aplicarFiltroVista(base, filtro);
                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        if (!javax.imageio.ImageIO.write(filtrada, "png", buf)) {
                            estado.setText("No se pudo codificar el resultado del filtro.");
                            return;
                        }
                        guardarBytes(VistaEscritorio.baseSinExtension(nombre) + "-"
                                        + filtro.replaceAll("[^A-Za-z0-9]+", "") + ".png",
                                buf.toByteArray());
                        return;
                    }
                } catch (Exception ex) {
                    estado.setText("No se pudo aplicar el filtro: " + ex.getMessage());
                    return;
                }
            }
            guardarBytes(nombre, bytes);
        } catch (RuntimeException e) {
            estado.setText("No se pudo descargar: " + e.getMessage());
        }
    }

    private void guardarBytes(String sugerido, byte[] bytes) {
        FileChooser elegidor = new FileChooser();
        elegidor.setTitle("Guardar archivo del mensaje");
        elegidor.setInitialFileName(sugerido);
        File destino = elegidor.showSaveDialog(escenario);
        if (destino == null) {
            return;
        }
        try {
            Files.write(destino.toPath(), bytes);
            estado.setText("Guardado: " + destino.getName()
                    + " (" + bytes.length + " bytes).");
        } catch (Exception e) {
            estado.setText("No se pudo guardar: " + e.getMessage());
        }
    }

    private void aplicarFiltro() {
        if (bytesImagen == null || bytesImagen.length == 0) {
            estado.setText("Selecciona primero un mensaje de tipo IMAGEN.");
            return;
        }
        String opcion = filtros.getValue();
        try {
            java.awt.image.BufferedImage base = javax.imageio.ImageIO.read(
                    new ByteArrayInputStream(bytesImagen));
            if (base == null) {
                estado.setText("Este formato se puede ver pero no admite"
                        + " previsualizar filtros; usa Descargar archivo.");
                return;
            }
            java.awt.image.BufferedImage out =
                    VistaEscritorio.aplicarFiltroVista(base, opcion);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(out, "png", buf)) {
                estado.setText("No se pudo codificar el resultado del filtro.");
                return;
            }
            vista.setImage(new Image(new ByteArrayInputStream(buf.toByteArray())));
            estado.setText("Filtro aplicado en vista: " + opcion + " (no persiste).");
        } catch (Exception e) {
            estado.setText("No se pudo aplicar el filtro: " + e.getMessage());
        }
    }
}
