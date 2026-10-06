package universidad.mensajeria.cliente;

import javafx.application.Application;
import universidad.mensajeria.cliente.presentacion.ClienteApp;

/** Punto de entrada; el grafo de objetos se construye en la composición JavaFX. */
public final class ClienteMain {

    public static void main(String[] args) {
        Application.launch(ClienteApp.class, args);
    }

    private ClienteMain() {
    }
}
