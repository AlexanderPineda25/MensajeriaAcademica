package universidad.mensajeria.cliente.transversal.utilerias;

import java.util.Locale;

/**
 * Texto del dominio del chat (conteo, extensiones, MIME, citas).
 * Centraliza la lógica antes duplicada en fachada, coordinador y controlador.
 */
public final class TextoCliente {

    /** Tope de adjunto aceptado por la UI (50 MiB). */
    public static final long TOPE_ADJUNTO_BYTES = 50L * 1024 * 1024;

    /** Ancho del corte de cita de respuesta (150 + "…"). */
    public static final int CORTE_CITA = 150;

    private TextoCliente() {
    }

    public static int contarPalabras(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        return texto.trim().split("\\s+").length;
    }

    public static String extension(String nombre) {
        int punto = nombre == null ? -1 : nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    public static String mimeDe(String nombre) {
        return switch (extension(nombre)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "zip" -> "application/zip";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    public static String truncarCita(String cita) {
        if (cita == null) {
            return "";
        }
        String recorte = cita.trim();
        return recorte.length() > CORTE_CITA
                ? recorte.substring(0, CORTE_CITA - 3) + "…"
                : recorte;
    }
}
