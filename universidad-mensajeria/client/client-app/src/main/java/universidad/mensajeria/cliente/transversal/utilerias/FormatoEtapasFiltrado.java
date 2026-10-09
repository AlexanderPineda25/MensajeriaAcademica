package universidad.mensajeria.cliente.transversal.utilerias;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convierte la traza JSON del filtro en la leyenda compartida, sin rutas. */
public final class FormatoEtapasFiltrado {

    private static final Pattern GIRO = Pattern.compile("^giro(\\d+)$");
    private static final Pattern PREFIJO_NUMERICO = Pattern.compile("^\\d+[_\\-\\s.]*");
    private static final Pattern CLAVE_SEGURA = Pattern.compile("[^a-z0-9áéíóúñ]");
    private static final Pattern CAMEL_CASE = Pattern.compile("(?<=[a-záéíóúñ])(?=[A-Z])");

    private FormatoEtapasFiltrado() { }

    public static String resumir(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonElement raiz = JsonParser.parseString(json);
            if (!raiz.isJsonArray()) {
                return "";
            }
            JsonArray etapas = raiz.getAsJsonArray();
            List<String> resumen = new ArrayList<>();
            for (JsonElement elemento : etapas) {
                if (!elemento.isJsonObject()) {
                    continue;
                }
                JsonObject etapa = elemento.getAsJsonObject();
                String nombre = nombreLegible(texto(etapa, "etapa"));
                if (nombre == null) {
                    continue;
                }
                String duracion = duracion(etapa.get("ms"));
                resumen.add(nombre + duracion);
            }
            return resumen.isEmpty() ? "" : "Filtros: " + String.join(" → ", resumen);
        } catch (RuntimeException jsonInvalido) {
            return "";
        }
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        return elemento != null && elemento.isJsonPrimitive()
                && elemento.getAsJsonPrimitive().isString()
                ? elemento.getAsString() : null;
    }

    private static String duracion(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonPrimitive()
                || !elemento.getAsJsonPrimitive().isNumber()) {
            return "";
        }
        try {
            BigDecimal milisegundos = elemento.getAsBigDecimal();
            if (milisegundos.signum() < 0) {
                return "";
            }
            return " (" + milisegundos.stripTrailingZeros().toPlainString() + " ms)";
        } catch (NumberFormatException invalido) {
            return "";
        }
    }

    private static String nombreLegible(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String nombre = valor.trim().replace('\\', '/');
        int separador = nombre.lastIndexOf('/');
        if (separador >= 0) {
            nombre = nombre.substring(separador + 1);
        }
        int extension = nombre.lastIndexOf('.');
        if (extension > 0) {
            nombre = nombre.substring(0, extension);
        }
        nombre = PREFIJO_NUMERICO.matcher(nombre).replaceFirst("");
        String clave = CLAVE_SEGURA.matcher(nombre.toLowerCase(Locale.ROOT)).replaceAll("");
        if (clave.equals("sha256") || clave.equals("sha256imagen")) {
            return "Verificación SHA-256";
        }
        if (List.of("grises", "escalagris", "escalagrises", "escaladegrises")
                .contains(clave)) {
            return "Grises";
        }
        if (clave.equals("sepia")) {
            return "Sepia";
        }
        if (clave.equals("reduccion") || clave.equals("reducción")) {
            return "Reducción";
        }
        if (clave.equals("brillo")) {
            return "Brillo";
        }
        Matcher giro = GIRO.matcher(clave);
        if (giro.matches()) {
            return "Giro " + giro.group(1) + "°";
        }
        if (clave.equals("giro")) {
            return "Giro";
        }

        nombre = nombre.replace('_', ' ').replace('-', ' ');
        nombre = CAMEL_CASE.matcher(nombre).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return nombre.isEmpty() ? null
                : nombre.substring(0, 1).toUpperCase(Locale.ROOT) + nombre.substring(1);
    }
}
