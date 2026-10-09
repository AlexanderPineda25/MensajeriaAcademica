package universidad.mensajeria.cliente.fachada;

import java.util.List;
import java.util.concurrent.Executor;
import java.io.IOException;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/**
 * Fachada agregada que conserva el contrato interno de la aplicación.
 * Las vistas reciben puertos más pequeños adecuados a cada pantalla.
 * Completamente neutral: no expone DTOs de protocolo.
 */
public interface InterfazFachadaCliente extends InterfazInicioSesion, InterfazChatCliente {

    Executor fondo();

    void cerrar();

    List<String> listarConectados() throws ExcepcionCliente, IOException;
}
