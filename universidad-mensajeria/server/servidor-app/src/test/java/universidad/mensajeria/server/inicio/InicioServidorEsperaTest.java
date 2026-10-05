package universidad.mensajeria.server.inicio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DIST-01: el arranque headless real (contenedor) debe quedarse esperando a
 * SIGTERM en vez de salir con 0 (loop de reinicios). En pruebas nunca espera
 * y con codigo de salida != 0 sale para que actue el restart-policy.
 */
class InicioServidorEsperaTest {

    @Test
    void servicioRealEsperaConCodigoCero() {
        assertTrue(InicioServidor.requiereEsperaServicio(true, 0));
    }

    @Test
    void pruebasNuncaEsperan() {
        assertFalse(InicioServidor.requiereEsperaServicio(false, 0));
    }

    @Test
    void codigoDistintoDeCeroSaleParaReiniciar() {
        assertFalse(InicioServidor.requiereEsperaServicio(true, 1));
    }
}
