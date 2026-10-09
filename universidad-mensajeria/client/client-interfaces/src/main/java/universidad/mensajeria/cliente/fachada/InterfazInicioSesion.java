package universidad.mensajeria.cliente.fachada;

import java.io.IOException;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/** Operaciones que necesita la pantalla de inicio de sesión. */
public interface InterfazInicioSesion {

    boolean login(String codigo, String contrasena) throws ExcepcionCliente, IOException;

    void desconectar();
}
