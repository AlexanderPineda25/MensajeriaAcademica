package universidad.mensajeria.cliente.fachada;

import java.util.function.Consumer;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;

/** Puerto de eventos de aplicación para las vistas del cliente. */
public interface InterfazEventosCliente {

    void suscribirEventos(Consumer<EventoCliente> observador);

    void desuscribirEventos(Consumer<EventoCliente> observador);

    int cantidadObservadoresEventos();
}
