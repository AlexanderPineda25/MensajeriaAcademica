package universidad.mensajeria.cliente.presentacion;

import javafx.scene.Scene;

/** Acciones de navegación y ventana que los controladores solicitan al shell. */
public interface InterfazNavegacionCliente {

    void mostrarLogin() throws Exception;

    void mostrarChat() throws Exception;

    boolean confirmar(String pregunta);

    void avisarProximoLogin(String motivo);

    String consumirAviso();

    void aplicarTema(Scene escena, boolean login);
}
