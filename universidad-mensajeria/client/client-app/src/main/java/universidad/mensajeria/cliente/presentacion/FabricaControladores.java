package universidad.mensajeria.cliente.presentacion;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import universidad.mensajeria.cliente.fachada.InterfazChatCliente;
import universidad.mensajeria.cliente.fachada.InterfazInicioSesion;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

/** Fábrica de controladores FXML con sus dependencias explícitas. */
public final class FabricaControladores {

    private final Map<Class<?>, Supplier<?>> constructores;

    public FabricaControladores(InterfazInicioSesion inicioSesion, InterfazChatCliente chat,
                                Executor tareasFondo,
                                ClienteConfig config,
                                InterfazNavegacionCliente navegacion) {
        constructores = Map.of(
                LoginController.class,
                () -> new LoginController(inicioSesion, tareasFondo, config, navegacion),
                ChatController.class,
                () -> new ChatController(new ChatViewModel(chat, tareasFondo), navegacion));
    }

    public Object crear(Class<?> tipo) {
        Supplier<?> constructor = constructores.get(tipo);
        if (constructor == null) {
            throw new universidad.mensajeria.cliente.transversal.excepciones.ExcepcionValidacion(
                    "No hay fábrica para el controlador " + tipo.getName());
        }
        return constructor.get();
    }
}
