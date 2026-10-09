package universidad.mensajeria.cliente.presentacion.celdas;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.image.Image;

/**
 * Miniaturas LRU de imágenes visibles (máx 100).
 * Extraído de MensajeCell para SRP: la celda pinta, este almacén cachea.
 */
public final class MiniaturasChat {

    private static final int TOPE = 100;
    private static final int MIN_BASE64 = 100;
    private static final int MIN_BYTES = 10;
    private static final double PREVIEW_ANCHO = 300;

    private static final Map<String, Image> MINIATURAS =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > TOPE;
                }
            });

    private MiniaturasChat() {
    }

    public static Image obtener(String idMensaje) {
        Image imagen = MINIATURAS.get(idMensaje);
        return imagen != null && !imagen.isError() ? imagen : null;
    }

    /** Se llama desde tareas de fondo; la escena FX nunca decodifica Base64. */
    public static void precalentar(String idMensaje, String base64) {
        if (idMensaje == null || base64 == null || base64.length() < MIN_BASE64
                || MINIATURAS.containsKey(idMensaje)) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length < MIN_BYTES) {
                return;
            }
            Image imagen = new Image(new ByteArrayInputStream(bytes), PREVIEW_ANCHO, 0, true, true);
            if (!imagen.isError()) {
                MINIATURAS.put(idMensaje, imagen);
            }
        } catch (IllegalArgumentException ignorada) {
            // Miniatura best-effort: la burbuja conserva el placeholder.
        }
    }

    static int tamano() {
        return MINIATURAS.size();
    }

    static void limpiar() {
        MINIATURAS.clear();
    }
}
