package universidad.mensajeria.server.transversal.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Seguridad (limite de frecuencia): 0 = sin limite y llega intacto hasta
 * ManejadorConexion; solo los negativos caen al defecto 10/s.
 */
class RedPropertiesTest {

    private static RedProperties conTramas(int tramasPorSegundo) {
        return new RedProperties(5000,
                new RedProperties.Pool(10, 100, 100, 5, tramasPorSegundo),
                new RedProperties.Limites(100, 3),
                "uploads",
                new RedProperties.Tls(false, 5001, null, null));
    }

    @Test
    void ceroEsSinLimite() {
        assertEquals(0, conTramas(0).pool().tramasPorSegundo());
    }

    @Test
    void negativosCaenEnDiezPorSegundo() {
        assertEquals(10, conTramas(-1).pool().tramasPorSegundo());
    }

    @Test
    void defectoEsDiezPorSegundo() {
        RedProperties red = new RedProperties(5000, null, null, null, null);
        assertEquals(10, red.pool().tramasPorSegundo());
    }
}
