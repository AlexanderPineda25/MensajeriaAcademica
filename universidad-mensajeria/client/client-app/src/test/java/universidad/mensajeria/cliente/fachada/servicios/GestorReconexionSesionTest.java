package universidad.mensajeria.cliente.fachada.servicios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GestorReconexionSesion — política de backoff")
class GestorReconexionSesionTest {

    @ParameterizedTest(name = "intento={0} → {1}s")
    @CsvSource({
            "0,  1",
            "1,  2",
            "2,  4",
            "3,  8",
            "4, 16",
            "5, 30",
            "6, 30",   // clamped al máximo
            "99,30",   // muy grande → siempre 30
            "-1, 1"    // negativo → siempre mínimo
    })
    @DisplayName("calcula la espera correcta para cada intento")
    void esperaSegundos(int intento, int esperado) {
        assertEquals(esperado, GestorReconexionSesion.esperaSegundos(intento));
    }
}
