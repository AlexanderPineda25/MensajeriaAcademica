package universidad.mensajeria.cliente.presentacion.celdas;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.utilerias.FormatoFecha;

/** Celda virtualizada del historial: pinta metadatos y solo pide la imagen visible. */
public final class MensajeCell extends ListCell<FilaChat> {

    private static final Map<String, Image> MINIATURAS =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > 100;
                }
            });

    private final Consumer<MensajeLocal> alVerImagen;
    private final Consumer<MensajeLocal> alReintentarImagen;
    private final Consumer<MensajeLocal> alResponder;
    private final Function<String, String> nombreVisible;
    private final Predicate<String> imagenFallida;
    private final Consumer<MensajeLocal> alDescargar;
    private final java.util.function.Supplier<String> consultaResaltada;

    public MensajeCell(Consumer<MensajeLocal> alVerImagen,
                       Consumer<MensajeLocal> alReintentarImagen,
                       Consumer<MensajeLocal> alResponder,
                       Function<String, String> nombreVisible,
                       Predicate<String> imagenFallida) {
        this(alVerImagen, alReintentarImagen, alResponder, nombreVisible, imagenFallida, m -> { }, () -> "");
    }

    public MensajeCell(Consumer<MensajeLocal> alVerImagen,
                       Consumer<MensajeLocal> alReintentarImagen,
                       Consumer<MensajeLocal> alResponder,
                       Function<String, String> nombreVisible,
                       Predicate<String> imagenFallida,
                       Consumer<MensajeLocal> alDescargar) {
        this(alVerImagen, alReintentarImagen, alResponder, nombreVisible,
                imagenFallida, alDescargar, () -> "");
    }

    public MensajeCell(Consumer<MensajeLocal> alVerImagen,
                       Consumer<MensajeLocal> alReintentarImagen,
                       Consumer<MensajeLocal> alResponder,
                       Function<String, String> nombreVisible,
                       Predicate<String> imagenFallida,
                       Consumer<MensajeLocal> alDescargar,
                       java.util.function.Supplier<String> consultaResaltada) {
        this.alVerImagen = alVerImagen;
        this.alReintentarImagen = alReintentarImagen;
        this.alResponder = alResponder;
        this.nombreVisible = nombreVisible;
        this.imagenFallida = imagenFallida;
        this.alDescargar = alDescargar == null ? m -> { } : alDescargar;
        this.consultaResaltada = consultaResaltada == null ? () -> "" : consultaResaltada;
        getStyleClass().add("chat-message-cell");
    }

    @Override
    protected void updateItem(FilaChat filaChat, boolean empty) {
        super.updateItem(filaChat, empty);
        if (empty || filaChat == null) {
            setGraphic(null);
            setText(null);
            return;
        }
        if (filaChat instanceof FilaChat.DiaFila dia) {
            Label cabecera = new Label(dia.etiqueta());
            cabecera.getStyleClass().add("day-separator");
            cabecera.setMaxWidth(Double.MAX_VALUE);
            cabecera.setTooltip(new Tooltip(dia.claveDia()));
            setGraphic(cabecera);
            return;
        }
        MensajeLocal item = ((FilaChat.MensajeFila) filaChat).mensaje();
        boolean mostrarRemitente = ((FilaChat.MensajeFila) filaChat).mostrarRemitente();

        boolean mio = item.enviado();
        VBox burbuja = new VBox(5);
        burbuja.getStyleClass().addAll("message-bubble", mio ? "message-out" : "message-in");
        burbuja.setPadding(new Insets(8, 10, 6, 10));
        // UIX-10: el ancho sigue a la ventana (65% de la lista, tope 580) en
        // vez de 460 fijos. El controlador repinta al redimensionar.
        double ancho = anchoBurbuja();
        burbuja.setMaxWidth(ancho);
        double interior = Math.max(200, ancho - 30);

        // UIX-05: en una racha del mismo autor solo el primer mensaje lleva
        // remitente; el flag ya viene calculado en la fila.
        if (!mio && mostrarRemitente) {
            Label remitente = new Label(nombreVisible.apply(item.origen()));
            remitente.getStyleClass().add("message-sender");
            remitente.setTooltip(new Tooltip(remitente.getText()));
            burbuja.getChildren().add(remitente);
        }

        if ("MENSAJE_IMAGEN".equals(item.tipo())) {
            String detalle = item.nombreArchivo() == null ? "Imagen" : item.nombreArchivo();
            Label titulo = new Label(detalle);
            titulo.getStyleClass().add("image-caption");
            burbuja.getChildren().add(titulo);
            Image miniatura = MINIATURAS.get(item.idMensaje());
            if (miniatura != null && !miniatura.isError()) {
                ImageView vista = new ImageView(miniatura);
                vista.setFitWidth(300);
                vista.setFitHeight(240);
                vista.setPreserveRatio(true);
                vista.setSmooth(true);
                vista.setOnMouseClicked(e -> {
                    if (e.getClickCount() >= 2) {
                        alResponder.accept(item);
                        e.consume();
                    } else {
                        abrirGrande(item, miniatura);
                    }
                });
                burbuja.getChildren().add(vista);
            } else {
                boolean fallo = imagenFallida.test(item.idMensaje());
                Label espera = new Label(fallo
                        ? "No se pudo cargar · reintentar" : "Cargando imagen…");
                espera.getStyleClass().add("image-placeholder");
                espera.setOnMouseClicked(e -> {
                    if (e.getClickCount() >= 2) {
                        alResponder.accept(item);
                        e.consume();
                    } else {
                        alReintentarImagen.accept(item);
                    }
                });
                espera.setTooltip(new Tooltip("Cargar o reintentar imagen"));
                burbuja.getChildren().add(espera);
                if (!fallo) alVerImagen.accept(item);
            }
        } else if ("MENSAJE_ARCHIVO".equals(item.tipo())) {
            String nombre = item.nombreArchivo() == null ? "Archivo" : item.nombreArchivo();
            String tam = item.tamanoArchivo() != null ? " (" + item.tamanoArchivo() + " bytes)" : "";
            Label titulo = new Label("📎 " + nombre + tam);
            titulo.getStyleClass().add("image-caption");
            titulo.setWrapText(true);
            burbuja.getChildren().add(titulo);
            Button descargar = new Button("⬇ Descargar");
            descargar.getStyleClass().add("reply-button");
            descargar.setTooltip(new Tooltip("Descargar archivo"));
            descargar.setOnAction(e -> alDescargar.accept(item));
            burbuja.getChildren().add(descargar);
        } else {
            String crudo = item.contenido() == null ? "" : item.contenido();
            // Cita estilo WhatsApp: "Respuesta a X: ..."\n\ntexto. Se separa
            // en bloque propio para distinguirla del mensaje (misma
            // convención que emiten los 3 clientes al responder).
            String cita = null;
            String cuerpo = crudo;
            int corte = crudo.indexOf("\n\n");
            if (crudo.startsWith("Respuesta a ") && corte > 0 && corte < 300) {
                cita = crudo.substring(0, corte);
                cuerpo = crudo.substring(corte + 2);
            }
            if (cita != null) {
                Label quote = new Label(cita);
                quote.setWrapText(true);
                quote.setMaxWidth(interior);
                quote.getStyleClass().add("message-quote");
                burbuja.getChildren().add(quote);
            }
Label texto = new Label(cuerpo);
        texto.setWrapText(true);
        texto.setMaxWidth(interior);
        texto.getStyleClass().add("message-text");
        // UIX-06: si hay búsqueda activa y el cuerpo la contiene, se pinta con
        // las coincidencias resaltadas en vez del Label plano.
        String consulta = consultaResaltada.get();
        if (consulta != null && !consulta.isBlank() && cuerpo.toLowerCase(Locale.ROOT)
                .contains(consulta.toLowerCase(Locale.ROOT))) {
            burbuja.getChildren().add(textoResaltado(cuerpo, consulta.trim(), interior));
        } else {
            burbuja.getChildren().add(texto);
        }
    }

        String hora = FormatoFecha.horaCorta(item.fechaEnvio());
        HBox pie = new HBox(7);
        pie.setAlignment(Pos.CENTER_RIGHT);
        pie.getStyleClass().add("message-footer");
        Button responder = new Button("↩");
        responder.getStyleClass().add("reply-button");
        responder.setTooltip(new Tooltip("Responder a este mensaje"));
        responder.setAccessibleText("Responder a este mensaje");
        responder.setOnAction(e -> alResponder.accept(item));
        Label lHora = new Label(hora);
        lHora.getStyleClass().add("message-time");
        // UIX-03: la hora corta ya no dice de qué día es; el tooltip sí.
        String dia = FormatoFecha.etiquetaDia(item.fechaEnvio());
        if (!dia.isEmpty()) {
            lHora.setTooltip(new Tooltip(dia + " " + hora));
        }
        pie.getChildren().addAll(responder, lHora);
        if (mio) {
            Label tick = new Label(tickDe(item.estado()));
            tick.getStyleClass().addAll("message-tick",
                    "LEIDO".equals(item.estado()) ? "message-read" : "message-unread");
            String detalle = switch (item.estado() == null ? "" : item.estado()) {
                case "LEIDO" -> "Visto por " + nombreVisible.apply(item.destino());
                case "ENTREGADO" -> "Entregado";
                case "PENDIENTE" -> "Pendiente de envío";
                case "ERROR" -> "No se pudo enviar";
                default -> "Enviado";
            };
            tick.setTooltip(new Tooltip(detalle));
            pie.getChildren().add(tick);
        }
        burbuja.getChildren().add(pie);

        HBox fila = new HBox(burbuja);
        fila.setAlignment(mio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        fila.setPadding(new Insets(3, 12, 3, 12));
        HBox.setHgrow(burbuja, Priority.NEVER);
        // Telegram Web: doble-click en cualquier mensaje = responder.
        // Un click en imagen conserva abrir-grande/reintentar (handlers propios con consume).
        fila.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                alResponder.accept(item);
            }
        });
        setGraphic(fila);
        setText(null);
    }

    /**
     * UIX-06: cuerpo con las coincidencias resaltadas. Parte el texto por la
     * consulta (insensible a mayúsculas) y marca esos tramos con la clase
     * `message-highlight`; el resto conserva `message-text`.
     */
    static TextFlow textoResaltado(String cuerpo, String consulta, double maxWidth) {
        TextFlow flujo = new TextFlow();
        flujo.setMaxWidth(maxWidth);
        String minusCuerpo = cuerpo.toLowerCase(Locale.ROOT);
        String minusConsulta = consulta.toLowerCase(Locale.ROOT);
        int pos = 0;
        int hallado;
        while ((hallado = minusCuerpo.indexOf(minusConsulta, pos)) >= 0) {
            if (hallado > pos) {
                flujo.getChildren().add(textoPlano(cuerpo.substring(pos, hallado)));
            }
            Text marca = new Text(cuerpo.substring(hallado, hallado + consulta.length()));
            marca.getStyleClass().add("message-highlight");
            flujo.getChildren().add(marca);
            pos = hallado + consulta.length();
        }
        if (pos < cuerpo.length()) {
            flujo.getChildren().add(textoPlano(cuerpo.substring(pos)));
        }
        return flujo;
    }

    private static Text textoPlano(String trozo) {
        Text texto = new Text(trozo);
        texto.getStyleClass().add("message-text");
        return texto;
    }

    /**
     * UIX-10: ancho de burbuja relativo a la lista (65%, tope 580, suelo 280).
     * Si la lista aún no tiene ancho (primer pintado), 460 como antes.
     */
    private double anchoBurbuja() {
        try {
            javafx.scene.control.ListView<?> lista = getListView();
            if (lista != null && lista.getWidth() > 100) {
                return Math.min(580, Math.max(280, lista.getWidth() * 0.65));
            }
        } catch (Exception ignorada) {
            // sin lista a mano: valor clásico
        }
        return 460;
    }

    static String tickDe(String estado) {
        // Visto literal con palabra, igual que el cliente Python (UI-06).
        if ("LEIDO".equals(estado)) {
            return "✓✓ Leído";
        }
        if ("ENTREGADO".equals(estado)) {
            return "✓✓ Entregado";
        }
        if ("ERROR".equals(estado)) {
            return "! No enviado";
        }
        if ("PENDIENTE".equals(estado)) {
            return "⏳ Pendiente";
        }
        return "✓ Enviado";
    }

    /** Se llama desde tareas de fondo; la escena FX nunca decodifica Base64. */
    public static void precalentar(String idMensaje, String base64) {
        if (idMensaje == null || base64 == null || base64.length() < 100
                || MINIATURAS.containsKey(idMensaje)) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length < 10) {
                return;
            }
            Image imagen = new Image(new ByteArrayInputStream(bytes), 300, 0, true, true);
            if (!imagen.isError()) {
                MINIATURAS.put(idMensaje, imagen);
            }
        } catch (Exception ignorada) {
            // La miniatura es best-effort; la burbuja conserva el placeholder.
        }
    }

    /**
     * UIX-08: visor con zoom y pan en vez de ventana fija. Rueda = zoom,
     * arrastrar = pan (pannable nativo), `+`/`-`/`0`/`F` y `Esc` por teclado.
     */
    private static void abrirGrande(MensajeLocal item, Image miniatura) {
        ImageView grande = new ImageView(miniatura);
        grande.setPreserveRatio(true);
        grande.setFitWidth(900);
        grande.setFitHeight(720);
        javafx.scene.layout.StackPane lienzo = new javafx.scene.layout.StackPane(grande);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(lienzo);
        scroll.setPannable(true);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        javafx.beans.property.DoubleProperty zoom =
                new javafx.beans.property.SimpleDoubleProperty(1.0);
        zoom.addListener((o, a, b) -> {
            double z = Math.min(8.0, Math.max(0.2, b.doubleValue()));
            grande.setFitWidth(900 * z);
            grande.setFitHeight(720 * z);
            boolean encaja = z <= 1.0;
            scroll.setFitToWidth(encaja);
            scroll.setFitToHeight(encaja);
        });
        scroll.setOnScroll(e -> {
            zoom.set(zoom.get() * (e.getDeltaY() > 0 ? 1.1 : 1 / 1.1));
            e.consume();
        });
        javafx.scene.control.Button mas = new javafx.scene.control.Button("+");
        mas.setOnAction(e -> zoom.set(zoom.get() * 1.25));
        mas.setAccessibleText("Acercar");
        javafx.scene.control.Button menos = new javafx.scene.control.Button("−");
        menos.setOnAction(e -> zoom.set(zoom.get() / 1.25));
        menos.setAccessibleText("Alejar");
        javafx.scene.control.Button encajar = new javafx.scene.control.Button("0");
        encajar.setOnAction(e -> zoom.set(1.0));
        encajar.setAccessibleText("Tamaño original");
        javafx.scene.layout.HBox barra = new javafx.scene.layout.HBox(6, menos, encajar, mas);
        barra.setAlignment(Pos.CENTER);
        barra.setPadding(new Insets(6));
        javafx.scene.layout.BorderPane raiz = new javafx.scene.layout.BorderPane();
        raiz.setCenter(scroll);
        raiz.setBottom(barra);
        javafx.scene.Scene escena = new javafx.scene.Scene(raiz, 940, 800);
        escena.setOnKeyPressed(e -> {
            javafx.scene.input.KeyCode codigo = e.getCode();
            if (codigo == javafx.scene.input.KeyCode.ADD
                    || codigo == javafx.scene.input.KeyCode.PLUS
                    || codigo == javafx.scene.input.KeyCode.EQUALS) {
                zoom.set(zoom.get() * 1.25);
            } else if (codigo == javafx.scene.input.KeyCode.SUBTRACT
                    || codigo == javafx.scene.input.KeyCode.MINUS) {
                zoom.set(zoom.get() / 1.25);
            } else if (codigo == javafx.scene.input.KeyCode.DIGIT0
                    || codigo == javafx.scene.input.KeyCode.NUMPAD0
                    || codigo == javafx.scene.input.KeyCode.F) {
                zoom.set(1.0);
            } else if (codigo == javafx.scene.input.KeyCode.ESCAPE) {
                ((Stage) escena.getWindow()).close();
            } else {
                return;
            }
            e.consume();
        });
        Stage ventana = new Stage();
        ventana.setTitle(item.nombreArchivo() == null ? "Imagen" : item.nombreArchivo());
        ventana.setScene(escena);
        ventana.show();
    }
}
