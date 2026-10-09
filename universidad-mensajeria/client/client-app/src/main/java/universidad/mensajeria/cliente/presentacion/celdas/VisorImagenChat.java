package universidad.mensajeria.cliente.presentacion.celdas;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

/**
 * Visor de imagen grande con zoom y pan (extraído de MensajeCell).
 * Patrón Command implícito: cada botón/tecla es un comando de zoom.
 */
public final class VisorImagenChat {

    private static final double VENTANA_ANCHO = 940;
    private static final double VENTANA_ALTO = 800;
    private static final double IMAGEN_BASE_ANCHO = 900;
    private static final double IMAGEN_BASE_ALTO = 720;
    private static final double ZOOM_MIN = 0.2;
    private static final double ZOOM_MAX = 8.0;
    private static final double PASO_ZOOM = 1.25;
    private static final double PASO_RUEDA = 1.1;

    private VisorImagenChat() {
    }

    public static void abrir(MensajeLocal item, Image miniatura) {
        ImageView grande = nuevaVista(miniatura);
        DoubleProperty zoom = new SimpleDoubleProperty(1.0);
        ScrollPane scroll = nuevoLienzo(grande, zoom);
        BorderPane raiz = new BorderPane();
        raiz.setCenter(scroll);
        raiz.setBottom(barraZoom(zoom));
        Scene escena = new Scene(raiz, VENTANA_ANCHO, VENTANA_ALTO);
        atajosTeclado(escena, zoom);
        Stage ventana = new Stage();
        ventana.setTitle(item.nombreArchivo() == null ? "Imagen" : item.nombreArchivo());
        ventana.setScene(escena);
        ventana.show();
    }

    private static ImageView nuevaVista(Image miniatura) {
        ImageView grande = new ImageView(miniatura);
        grande.setPreserveRatio(true);
        grande.setFitWidth(IMAGEN_BASE_ANCHO);
        grande.setFitHeight(IMAGEN_BASE_ALTO);
        return grande;
    }

    private static ScrollPane nuevoLienzo(ImageView grande, DoubleProperty zoom) {
        StackPane lienzo = new StackPane(grande);
        ScrollPane scroll = new ScrollPane(lienzo);
        scroll.setPannable(true);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        zoom.addListener((o, a, b) -> aplicarZoom(grande, scroll, b.doubleValue()));
        scroll.setOnScroll(e -> {
            double actual = zoom.get();
            zoom.set(actual * (e.getDeltaY() > 0 ? PASO_RUEDA : 1 / PASO_RUEDA));
            e.consume();
        });
        return scroll;
    }

    private static void aplicarZoom(ImageView grande, ScrollPane scroll, double valor) {
        double z = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, valor));
        grande.setFitWidth(IMAGEN_BASE_ANCHO * z);
        grande.setFitHeight(IMAGEN_BASE_ALTO * z);
        boolean encaja = z <= 1.0;
        scroll.setFitToWidth(encaja);
        scroll.setFitToHeight(encaja);
    }

    private static HBox barraZoom(DoubleProperty zoom) {
        Button mas = new Button("+");
        mas.setOnAction(e -> zoom.set(zoom.get() * PASO_ZOOM));
        mas.setAccessibleText("Acercar");
        Button menos = new Button("−");
        menos.setOnAction(e -> zoom.set(zoom.get() / PASO_ZOOM));
        menos.setAccessibleText("Alejar");
        Button encajar = new Button("0");
        encajar.setOnAction(e -> zoom.set(1.0));
        encajar.setAccessibleText("Tamaño original");
        HBox barra = new HBox(6, menos, encajar, mas);
        barra.setAlignment(Pos.CENTER);
        barra.setPadding(new Insets(6));
        return barra;
    }

    private static void atajosTeclado(Scene escena, DoubleProperty zoom) {
        escena.setOnKeyPressed(e -> {
            KeyCode codigo = e.getCode();
            if (codigo == KeyCode.ADD || codigo == KeyCode.PLUS || codigo == KeyCode.EQUALS) {
                zoom.set(zoom.get() * PASO_ZOOM);
            } else if (codigo == KeyCode.SUBTRACT || codigo == KeyCode.MINUS) {
                zoom.set(zoom.get() / PASO_ZOOM);
            } else if (codigo == KeyCode.DIGIT0 || codigo == KeyCode.NUMPAD0 || codigo == KeyCode.F) {
                zoom.set(1.0);
            } else if (codigo == KeyCode.ESCAPE) {
                ((Stage) escena.getWindow()).close();
            } else {
                return;
            }
            e.consume();
        });
    }
}
