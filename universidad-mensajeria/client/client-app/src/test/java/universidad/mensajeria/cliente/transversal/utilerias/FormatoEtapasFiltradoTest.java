package universidad.mensajeria.cliente.transversal.utilerias;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FormatoEtapasFiltradoTest {

    @Test
    void resumeEnOrdenConDuracionesYOmiteRutas() {
        String json = "[{\"etapa\":\"01_grises.png\",\"ms\":12,\"ruta\":\"C:/privado/01.png\"},"
                + "{\"etapa\":\"02_sepia.png\",\"ms\":3.5,\"ruta\":\"D:/otra.png\"},"
                + "{\"etapa\":\"C:/privado/03_giro180.png\",\"ms\":4,"
                + "\"ruta\":\"C:/privado/03_giro180.png\"}]";

        assertEquals("Filtros: Grises (12 ms) → Sepia (3.5 ms) → Giro 180° (4 ms)",
                FormatoEtapasFiltrado.resumir(json));
    }

    @Test
    void omiteTrazaVaciaONoValida() {
        assertEquals("", FormatoEtapasFiltrado.resumir(null));
        assertEquals("", FormatoEtapasFiltrado.resumir("[]"));
        assertEquals("", FormatoEtapasFiltrado.resumir("no es json"));
    }
}
