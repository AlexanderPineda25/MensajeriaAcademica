package universidad.mensajeria.cliente.transversal.utilerias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TLS fail-fast (Fase 1.3, SEG-01). Espejo de {@code client-python/tests/test_tls.py}:
 * los tres rechazos y el camino por defecto.
 *
 * <p>El motivo de que exista: sin esto, un {@code tls.confianza} mal escrito no sale
 * hasta el handshake y sale como una IOException genérica que no dice qué propiedad
 * tocar. Aquí el mensaje tiene que NOMBRAR la variable.
 */
class ValidarTlsTest {

    private static final int PUERTO_PLAÑO = 5000;
    private static final int PUERTO_TLS = 5001;

    @Test
    @DisplayName("colisión de puertos: el mensaje nombra tls.puerto")
    void rechazaColisionDePuertos() {
        IOException fallo = assertThrows(IOException.class,
                () -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_PLAÑO, ""));

        assertTrue(fallo.getMessage().contains("tls.puerto"),
                "el mensaje debe decir qué propiedad cambiar: " + fallo.getMessage());
        assertTrue(fallo.getMessage().contains(String.valueOf(PUERTO_PLAÑO)));
        assertTrue(fallo.getMessage().contains("client.properties"));
    }

    @Test
    @DisplayName("almacén de certificados ausente: el mensaje nombra tls.confianza")
    void rechazaAlmacenAusente() {
        String ruta = "C:/no/existe/confianza.jks";

        IOException fallo = assertThrows(IOException.class,
                () -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS, ruta));

        assertTrue(fallo.getMessage().contains("tls.confianza"),
                "el mensaje debe decir qué propiedad revisar: " + fallo.getMessage());
        assertTrue(fallo.getMessage().contains(ruta));
    }

    @Test
    @DisplayName("valores mal formados: puerto TLS fuera de rango y ruta no parseable")
    void rechazaValoresMalFormados() {
        IOException porPuerto = assertThrows(IOException.class,
                () -> ValidarTls.validar(true, PUERTO_PLAÑO, 0, ""));
        assertTrue(porPuerto.getMessage().contains("tls.puerto"), porPuerto.getMessage());

        IOException porRuta = assertThrows(IOException.class,
                () -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS, "C:\\mal<>formado.jks"));
        assertTrue(porRuta.getMessage().contains("tls.confianza"), porRuta.getMessage());
    }

    @Test
    @DisplayName("camino por defecto: tls.confianza vacío usa la validación de cadena estándar")
    void caminoPorDefectoSinAlmacen() {
        assertDoesNotThrow(() -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS, ""));
        assertDoesNotThrow(() -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS, "   "));
        assertDoesNotThrow(() -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS, null));
    }

    @Test
    @DisplayName("TLS deshabilitado no valida nada, ni siquiera un almacén inexistente")
    void sinTlsNoHayQueValidar() {
        assertDoesNotThrow(() -> ValidarTls.validar(false, PUERTO_PLAÑO, PUERTO_PLAÑO,
                "C:/no/existe/confianza.jks"));
    }

    @Test
    @DisplayName("un almacén existente se acepta: la validación no lo abre todavía")
    void aceptaAlmacenExistente(@TempDir Path temporal) throws IOException {
        Path almacen = Files.writeString(temporal.resolve("confianza.jks"), "contenido");

        assertDoesNotThrow(() -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS,
                almacen.toString()));
    }

    @Test
    @DisplayName("un directorio no vale como almacén: debe ser un fichero")
    void rechazaDirectorioComoAlmacen(@TempDir Path temporal) {
        IOException fallo = assertThrows(IOException.class,
                () -> ValidarTls.validar(true, PUERTO_PLAÑO, PUERTO_TLS, temporal.toString()));

        assertTrue(fallo.getMessage().contains("tls.confianza"), fallo.getMessage());
    }

    @Test
    @DisplayName("el puerto TLS por defecto es el mismo en los 3 clientes")
    void puertoPorDefectoCompartido() {
        assertEquals(5001, ValidarTls.TLS_PUERTO_POR_DEFECTO);
    }
}