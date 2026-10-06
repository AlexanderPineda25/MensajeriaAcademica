package universidad.mensajeria.cliente.presentacion.celdas;

import org.junit.jupiter.api.Test;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UIX-04/UIX-05: mismo contrato que `agrupar` (Python) y `DebeMostrarEncabezado` (.NET). */
class FilasChatTest {

    private static MensajeLocal mensaje(String id, String origen, String fecha) {
        return new MensajeLocal(id, origen, "YO", "MENSAJE_TEXTO", "hola", null,
                null, null, null, null, null, fecha, true, false, "ENVIADO", null);
    }

    @Test
    void insertaSeparadorAlCambiarDeDia() {
        String hoy = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).toString();
        String ayer = LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).toString();

        List<FilaChat> filas = FilasChat.armar(List.of(
                mensaje("1", "B", ayer), mensaje("2", "B", hoy)));

        assertEquals(4, filas.size());
        assertTrue(filas.get(0) instanceof FilaChat.DiaFila);
        assertEquals("Ayer", ((FilaChat.DiaFila) filas.get(0)).etiqueta());
        assertTrue(filas.get(2) instanceof FilaChat.DiaFila);
        assertEquals("Hoy", ((FilaChat.DiaFila) filas.get(2)).etiqueta());
    }

    @Test
    void laRachaDelMismoAutorOcultaElRemitente() {
        String base = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).toString();
        String dosMin = LocalDateTime.now().withHour(10).withMinute(2).withSecond(0).toString();

        List<FilaChat> filas = FilasChat.armar(List.of(
                mensaje("1", "B", base), mensaje("2", "B", dosMin)));

        // 1 separador + 2 mensajes.
        assertEquals(3, filas.size());
        assertTrue(((FilaChat.MensajeFila) filas.get(1)).mostrarRemitente());
        assertFalse(((FilaChat.MensajeFila) filas.get(2)).mostrarRemitente());
    }

    @Test
    void cambioDeAutorOCorteDeDiaRompeLaRacha() {
        String base = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).toString();
        String dosMin = LocalDateTime.now().withHour(10).withMinute(2).withSecond(0).toString();

        assertFalse(FilasChat.agrupar(mensaje("1", "B", base), mensaje("2", "C", dosMin)));
        assertFalse(FilasChat.agrupar(mensaje("1", "B", base), mensaje("2", "B",
                LocalDateTime.now().minusDays(1).withHour(10).withMinute(2).toString())));
        assertFalse(FilasChat.agrupar(mensaje("1", "B", base), mensaje("2", "B",
                LocalDateTime.now().withHour(11).withMinute(0).toString())));
        assertFalse(FilasChat.agrupar(null, mensaje("2", "B", base)));
    }

    @Test
    void fechaIlegibleNoAgrupaNiRompe() {
        List<FilaChat> filas = FilasChat.armar(List.of(
                mensaje("1", "B", "basura"), mensaje("2", "B", "basura")));

        // Sin cabecera (sin día legible) y con remitente en ambos.
        assertEquals(2, filas.size());
        assertTrue(((FilaChat.MensajeFila) filas.get(0)).mostrarRemitente());
        assertTrue(((FilaChat.MensajeFila) filas.get(1)).mostrarRemitente());
    }
}
