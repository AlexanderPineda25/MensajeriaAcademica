package universidad.mensajeria.cliente.fachada;

/** Operaciones que necesita la pantalla de inicio de sesión. */
public interface InterfazInicioSesion {

    boolean login(String codigo, String contrasena) throws Exception;

    void desconectar();
}
