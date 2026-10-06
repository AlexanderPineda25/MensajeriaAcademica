package universidad.mensajeria.cliente.transversal.utilerias;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UIX-03: el mismo contrato que `formatear_fecha` (Python) y `FormatoFecha` (.NET). */
class FormatoFechaTest {

    @Test
    void hoyMuestraSoloLaHora() {
        String hoy = LocalDateTime.now().withHour(10).withMinute(5).withSecond(0).toString();

        assertEquals("10:05", FormatoFecha.horaCorta(hoy));
        assertEquals("Hoy", FormatoFecha.etiquetaDia(hoy));
    }

    @Test
    void ayerSeMarcaComoAyer() {
        String ayer = LocalDateTime.now().minusDays(1).withHour(9).withMinute(30).toString();

        assertEquals("Ayer", FormatoFecha.etiquetaDia(ayer));
        assertTrue(FormatoFecha.horaCorta(ayer).contains("09:30"));
    }

    @Test
    void haceUnaSemanaMuestraDiaYMes() {
        String hace = LocalDateTime.now().minusDays(7).withHour(8).withMinute(0).toString();

        assertTrue(FormatoFecha.horaCorta(hace).matches("\\d{2}-\\d{2} 08:00"));
        assertTrue(FormatoFecha.etiquetaDia(hace).matches("\\d{2}-\\d{2}"));
    }

    @Test
    void aceptaElFormatoConEspacioDeSqlite() {
        String conEspacio = LocalDate.now() + " 10:00:00";

        assertEquals("10:00", FormatoFecha.horaCorta(conEspacio));
        assertEquals("Hoy", FormatoFecha.etiquetaDia(conEspacio));
    }

    @Test
    void loIlegibleNoRompeLaCelda() {
        assertEquals("", FormatoFecha.horaCorta(null));
        assertEquals("", FormatoFecha.horaCorta(""));
        assertEquals("", FormatoFecha.horaCorta("ayer a las tres"));
        assertEquals("", FormatoFecha.etiquetaDia("basura"));
        assertEquals("", FormatoFecha.claveDia(null));
    }

    @Test
    void claveDiaAgrupaPorFecha() {
        assertEquals(LocalDate.now().toString(),
                FormatoFecha.claveDia(LocalDateTime.now().toString()));
        assertEquals(LocalDate.now().minusDays(1).toString(),
                FormatoFecha.claveDia(LocalDate.now().minusDays(1) + " 10:00:00"));
    }
}
