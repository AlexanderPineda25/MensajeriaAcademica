package universidad.mensajeria.cliente.fachada;

/**
 * Política de reconexión con backoff y custodia temporal de credenciales.
 */
public interface InterfazGestorReconexion {

    void guardarSesion(String codigo, String contrasena);

    void olvidarSesion();

    void programarReconexion();

    void cerrar();

    static int esperaSegundos(int intento) {
        int[] tabla = {1, 2, 4, 8, 16, 30};
        return tabla[Math.min(Math.max(0, intento), tabla.length - 1)];
    }
}
