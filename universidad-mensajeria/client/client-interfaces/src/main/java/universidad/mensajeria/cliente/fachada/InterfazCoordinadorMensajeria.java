package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/**
 * Envíos 1-a-1 con política guardar-primero → red → pendiente (OCP: nuevas
 * políticas se añaden con otra implementación, sin editar la fachada).
 */
public interface InterfazCoordinadorMensajeria {

    boolean enviarTexto(String yo, String destinatario, String contenido) throws ExcepcionCliente;

    boolean enviarImagen(String yo, String destinatario, byte[] bytes,
                         String nombreArchivo, String mime) throws ExcepcionCliente;

    boolean enviarArchivo(String yo, String destinatario, byte[] bytes,
                          String nombreArchivo, String mime) throws ExcepcionCliente;
}
