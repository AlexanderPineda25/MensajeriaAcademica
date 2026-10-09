package universidad.mensajeria.cliente.presentacion;

import universidad.mensajeria.cliente.transversal.records.RecienteChat;

import java.util.List;

import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

/**
 * Salida del refresco de contactos hacia la conversación visible (DIP).
 */
public interface FlujoConversacion {

    /** El usuario eligió una conversación: prepara estado y carga mensajes. */
    void alSeleccionar(RecienteChat actual);

    /** Pinta la página recién cargada en la lista de mensajes. */
    void pintar(String conversacion, List<MensajeLocal> pagina, int total, int offset);
}
