package universidad.mensajeria.cliente.presentacion;

import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

/**
 * Carga y descarga de adjuntos de la conversación visible (DIP).
 */
public interface InterfazGestorAdjuntos {

    boolean imagenFallida(String idMensaje);

    int paginaRemota(String conversacion);

    Integer totalPaginasRemotas(String conversacion);

    void registrarPaginaRemota(String conversacion, int siguiente, int total);

    boolean hayMasHistorialRemoto(String conversacion);

    void registrarPaginaDeMensaje(String idMensaje, int pagina);

    void cambiarConversacion();

    void detener();

    void cargarImagenVisible(MensajeLocal item);

    void reintentarImagen(MensajeLocal item);

    void descargarArchivo(MensajeLocal item);
}
