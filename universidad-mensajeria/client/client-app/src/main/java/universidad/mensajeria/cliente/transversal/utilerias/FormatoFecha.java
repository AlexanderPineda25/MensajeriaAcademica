package universidad.mensajeria.cliente.transversal.utilerias;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UIX-03: formato de fecha unificado y relativo en los 3 clientes.
 *
 * Antes la burbuja hacía `substring(11,16)`: mostraba la hora pero descartaba
 * el día, y un mensaje de ayer parecía de hoy. Ahora la burbuja muestra hora
 * corta y el separador de día (UIX-04) pone el contexto. Los textos son los
 * mismos en Java, Python y .NET (contrato compartido, VIS-02):
 * "Hoy 10:00", "Ayer 10:00", "02-01 10:00", "02-01-2025 10:00".
 *
 * Acepta `yyyy-MM-ddTHH:mm:ss` (red/Java) y `yyyy-MM-dd HH:mm:ss` (SQLite).
 * Todo lo que no se pueda leer devuelve "" en vez de romper la celda.
 */
public final class FormatoFecha {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd-MM");
    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private FormatoFecha() {
    }

    /** Hora corta para el pie de la burbuja. */
    public static String horaCorta(String fechaEnvio) {
        LocalDateTime fecha = leer(fechaEnvio);
        if (fecha == null) {
            return "";
        }
        LocalDate hoy = LocalDate.now();
        LocalDate dia = fecha.toLocalDate();
        String hora = fecha.format(HORA);
        if (dia.equals(hoy)) {
            return hora;
        }
        if (dia.getYear() == hoy.getYear()) {
            return fecha.format(DIA_MES) + " " + hora;
        }
        return fecha.format(DIA_MES_ANO) + " " + hora;
    }

    /** Etiqueta para el separador de día (UIX-04). */
    public static String etiquetaDia(String fechaEnvio) {
        LocalDateTime fecha = leer(fechaEnvio);
        if (fecha == null) {
            return "";
        }
        LocalDate hoy = LocalDate.now();
        LocalDate dia = fecha.toLocalDate();
        if (dia.equals(hoy)) {
            return "Hoy";
        }
        if (dia.equals(hoy.minusDays(1))) {
            return "Ayer";
        }
        if (dia.getYear() == hoy.getYear()) {
            return fecha.format(DIA_MES);
        }
        return fecha.format(DIA_MES_ANO);
    }

    /** Clave de agrupación por día (UIX-04): "" si ilegible. */
    public static String claveDia(String fechaEnvio) {
        LocalDateTime fecha = leer(fechaEnvio);
        return fecha == null ? "" : fecha.toLocalDate().toString();
    }

    /** Lee `yyyy-MM-ddTHH:mm:ss` o `yyyy-MM-dd HH:mm:ss`; null si ilegible. */
    public static LocalDateTime leer(String fechaEnvio) {
        if (fechaEnvio == null || fechaEnvio.isBlank()) {
            return null;
        }
        String normalizada = fechaEnvio.strip().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalizada);
        } catch (Exception e) {
            try {
                return LocalDate.parse(normalizada).atStartOfDay();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
