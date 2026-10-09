package universidad.mensajeria.cliente.presentacion;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Modo Normal (claro) / Oscuro del cliente (UI-09). Oscuro por defecto.
 * La elección persiste en {@code client.properties} (clave {@code tema}),
 * el mismo formato compartido de §5.2. No toca {@code ClienteConfig}.
 */
public final class Tema {

    public static final String OSCURO = "oscuro";
    public static final String CLARO = "claro";

    private static final String CLAVE = "tema";

    private Tema() {
    }

    public static String normalizar(String modo) {
        return CLARO.equals(modo) ? CLARO : OSCURO;
    }

    public static String cssLogin(String modo) {
        return CLARO.equals(normalizar(modo))
                ? "/estilos/login-claro.css" : "/estilos/login.css";
    }

    public static String cssChat(String modo) {
        return CLARO.equals(normalizar(modo))
                ? "/estilos/chat-claro.css" : "/estilos/chat.css";
    }

    /** Lee el modo guardado (externo manda, classpath de respaldo). */
    public static String cargar() {
        Properties props = new Properties();
        try (InputStream in = Tema.class.getResourceAsStream("/client.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (java.io.IOException lecturaOmitida) {
            // Respaldo por defecto: sin properties se usa oscuro.
        }
        Path externo = externo();
        if (Files.exists(externo)) {
            try (Reader reader = Files.newBufferedReader(externo, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (java.io.IOException lecturaOmitida) {
                // Respaldo por defecto.
            }
        }
        return normalizar(props.getProperty(CLAVE, OSCURO));
    }

    /** Guarda el modo en el client.properties externo (conserva el resto). */
    public static void guardar(String modo) {
        String valor = normalizar(modo);
        Properties props = new Properties();
        Path externo = externo();
        if (Files.exists(externo)) {
            try (Reader reader = Files.newBufferedReader(externo, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (java.io.IOException lecturaOmitida) {
                // Se reescribe igual con lo que haya en memoria.
            }
        }
        props.setProperty(CLAVE, valor);
        try (Writer writer = Files.newBufferedWriter(externo, StandardCharsets.UTF_8)) {
            props.store(writer, "tema del cliente (oscuro|claro)");
        } catch (java.io.IOException escrituraOpcional) {
            // Preferencia opcional: no interrumpe la UI.
        }
    }

    private static Path externo() {
        String ruta = System.getProperty("mensajeria.config");
        return ruta == null || ruta.isBlank()
                ? Path.of("client.properties") : Path.of(ruta);
    }
}
