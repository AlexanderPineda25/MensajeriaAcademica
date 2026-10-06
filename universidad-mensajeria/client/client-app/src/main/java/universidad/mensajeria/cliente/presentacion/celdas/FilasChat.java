package universidad.mensajeria.cliente.presentacion.celdas;

import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.utilerias.FormatoFecha;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UIX-04/UIX-05: construye las filas del historial a partir de los mensajes en
 * orden cronológico. Inserta un {@link FilaChat.DiaFila} cada vez que cambia el
 * día y marca {@code mostrarRemitente=false} en las rachas del mismo autor.
 *
 * Regla de agrupación (la misma en los 3 clientes, VIS-02): mismo
 * {@code origen}, mismo día y menos de 5 minutos entre mensajes. Con fecha
 * ilegible no se agrupa (se muestra el remitente: el defecto seguro).
 */
public final class FilasChat {

    private static final long VENTANA_RACHA_MINUTOS = 5;

    private FilasChat() {
    }

    public static List<FilaChat> armar(List<MensajeLocal> mensajes) {
        List<FilaChat> filas = new ArrayList<>();
        String ultimoDia = null;
        MensajeLocal anterior = null;
        for (MensajeLocal actual : mensajes) {
            String dia = FormatoFecha.claveDia(actual.fechaEnvio());
            if (!dia.isEmpty() && !dia.equals(ultimoDia)) {
                filas.add(new FilaChat.DiaFila(dia, FormatoFecha.etiquetaDia(actual.fechaEnvio())));
                ultimoDia = dia;
                anterior = null;
            }
            filas.add(new FilaChat.MensajeFila(actual, !agrupar(anterior, actual)));
            anterior = actual;
        }
        return filas;
    }

    static boolean agrupar(MensajeLocal anterior, MensajeLocal actual) {
        if (anterior == null || actual == null) {
            return false;
        }
        if (anterior.origen() == null || !anterior.origen().equals(actual.origen())) {
            return false;
        }
        LocalDateTime a = FormatoFecha.leer(anterior.fechaEnvio());
        LocalDateTime b = FormatoFecha.leer(actual.fechaEnvio());
        if (a == null || b == null) {
            return false;
        }
        if (!a.toLocalDate().equals(b.toLocalDate())) {
            return false;
        }
        long minutos = Math.abs(Duration.between(a, b).toMinutes());
        return minutos < VENTANA_RACHA_MINUTOS;
    }
}
