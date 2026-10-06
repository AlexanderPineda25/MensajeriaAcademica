package universidad.mensajeria.cliente.presentacion.celdas;

import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

/**
 * UIX-04/UIX-05: una fila del historial puede ser un mensaje o un separador
 * de día. Antes la lista era `ListView&lt;MensajeLocal&gt;` plana: sin
 * cabeceras de día y con el remitente repetido en cada burbuja.
 *
 * La vista no decide nada: {@link FilasChat#armar} construye las filas con el
 * flag de remitente ya calculado, y la celda solo pinta.
 */
public sealed interface FilaChat permits FilaChat.MensajeFila, FilaChat.DiaFila {

    /** Un mensaje, con indicación de si debe mostrar el remitente (UIX-05). */
    record MensajeFila(MensajeLocal mensaje, boolean mostrarRemitente) implements FilaChat {
    }

    /** Separador de día (UIX-04): clave ISO para comparar, etiqueta para pintar. */
    record DiaFila(String claveDia, String etiqueta) implements FilaChat {
    }
}
