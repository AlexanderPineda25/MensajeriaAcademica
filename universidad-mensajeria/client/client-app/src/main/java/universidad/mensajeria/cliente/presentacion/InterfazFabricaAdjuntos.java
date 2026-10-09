package universidad.mensajeria.cliente.presentacion;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.ListView;
import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;

/**
 * Fábrica del gestor de adjuntos (DIP: el controlador no hace {@code new}).
 */
public interface InterfazFabricaAdjuntos {

    InterfazGestorAdjuntos crear(InterfazChatViewModel viewModel, Executor fondo,
                                ListView<FilaChat> listaMensajes,
                                Supplier<String> conversacionActual,
                                Consumer<String> mostrarEstado);
}
