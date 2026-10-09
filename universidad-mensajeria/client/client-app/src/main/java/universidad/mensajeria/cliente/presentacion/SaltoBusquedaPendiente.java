package universidad.mensajeria.cliente.presentacion;

/**
 * Salto diferido a una coincidencia de búsqueda que aún no está cargada.
 * <p>
 * La búsqueda ({@code BusquedaHistorialChat}) lo programa y la paginación
 * ({@code PaginadorHistorialChat}) lo consume tras traer más historial:
 * un único dueño del estado mutable compartido entre ambos.
 * </p>
 */
public final class SaltoBusquedaPendiente {

    private String id;
    private int intentos;

    public void programar(String id, int intentos) {
        this.id = id;
        this.intentos = intentos;
    }

    public boolean haySalto() {
        return id != null;
    }

    public String id() {
        return id;
    }

    public int intentos() {
        return intentos;
    }

    public void consumir() {
        id = null;
        intentos = 0;
    }
}
