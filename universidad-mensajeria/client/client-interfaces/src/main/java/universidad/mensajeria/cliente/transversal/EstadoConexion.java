package universidad.mensajeria.cliente.transversal;

/** Estado de conexión que publica el puerto de la fachada a la presentación. */
public enum EstadoConexion {

    CONECTANDO("Conectando…", false),
    CONECTADO("En línea", false),
    SIN_RED("Sin conexión — mensajes en cola local, se reintentan solos", true),
    SESION_CERRADA("Sesión cerrada", true);

    private final String texto;
    private final boolean degradado;

    EstadoConexion(String texto, boolean degradado) {
        this.texto = texto;
        this.degradado = degradado;
    }

    public String texto() {
        return texto;
    }

    public boolean degradado() {
        return degradado;
    }

    public boolean muestraBanner() {
        return degradado;
    }
}
