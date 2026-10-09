package universidad.mensajeria.cliente.presentacion;

import java.io.IOException;
import javafx.scene.Scene;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/** Acciones de navegación y ventana que los controladores solicitan al shell. */
public interface InterfazNavegacionCliente {

    void mostrarLogin() throws ExcepcionCliente, IOException;

    void mostrarChat() throws ExcepcionCliente, IOException;

    boolean confirmar(String pregunta);

    void avisarProximoLogin(String motivo);

    String consumirAviso();

    void aplicarTema(Scene escena, boolean login);
}
