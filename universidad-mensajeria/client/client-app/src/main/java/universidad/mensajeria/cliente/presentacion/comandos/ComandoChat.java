package universidad.mensajeria.cliente.presentacion.comandos;

/**
 * Patrón Command para acciones del chat (enviar, paginar, buscar).
 * Cada acción encapsula su ejecución en fondo y su resultado en UI.
 */
public interface ComandoChat {

    void ejecutar();
}
