package universidad.mensajeria.cliente.transversal.records;

/** Estado local del envío y confirmaciones remotas (referencia canónica). */
public enum EstadoMensaje {
    PENDIENTE,
    ERROR,
    ENVIADO,
    ENTREGADO,
    LEIDO;

    public static EstadoMensaje desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return ENVIADO;
        }
        try {
            return EstadoMensaje.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return ENVIADO;
        }
    }

    /** Orden monótono de la máquina PENDIENTE → ENVIADO → ENTREGADO → LEIDO. */
    public int orden() {
        return switch (this) {
            case LEIDO -> 3;
            case ENTREGADO -> 2;
            case ENVIADO -> 1;
            default -> 0;
        };
    }

    /**
     * Conserva el estado más avanzado al reguardar (upsert idempotente).
     * Única implementación canónica: SQL, DAO y fachada delegan aquí.
     */
    public static String conservar(String anteriorTexto, String nuevoTexto) {
        EstadoMensaje anterior = desde(anteriorTexto);
        EstadoMensaje nuevo = desde(nuevoTexto);
        if (anterior == LEIDO || nuevo == LEIDO) {
            return LEIDO.name();
        }
        return nuevo.orden() >= anterior.orden() ? nuevo.name() : anterior.name();
    }

    /** Tick literal de la burbuja (igual que Python UI-06). */
    public String tick() {
        return switch (this) {
            case LEIDO -> "✓✓ Leído";
            case ENTREGADO -> "✓✓ Entregado";
            case ERROR -> "! No enviado";
            case PENDIENTE -> "⏳ Pendiente";
            default -> "✓ Enviado";
        };
    }

    /** Detalle del tooltip del tick. */
    public String detalle(String nombreDestino) {
        return switch (this) {
            case LEIDO -> "Visto por " + (nombreDestino == null ? "" : nombreDestino);
            case ENTREGADO -> "Entregado";
            case PENDIENTE -> "Pendiente de envío";
            case ERROR -> "No se pudo enviar";
            default -> "Enviado";
        };
    }

    public static String tickDe(String estadoTexto) {
        return desde(estadoTexto).tick();
    }

    public static String detalleDe(String estadoTexto, String nombreDestino) {
        return desde(estadoTexto).detalle(nombreDestino);
    }
}
