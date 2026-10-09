package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.fachada.InterfazEventosCliente;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bus de eventos hacia la UI, patrón Observer (Fase 4).
 * <p>
 * Extraído de {@code FachadaCliente} por responsabilidad única: suscribir
 * observadores y publicar tramas adaptadas a eventos neutrales, sin saber de
 * sesiones, red ni persistencia. No conoce JavaFX ni la UI.
 * </p>
 */
public final class PublicadorEventosCliente implements InterfazEventosCliente {

    private static final Logger LOG = Logger.getLogger(PublicadorEventosCliente.class.getName());

    private final List<Consumer<EventoCliente>> observadores =
            new CopyOnWriteArrayList<>();

    @Override
    public void suscribirEventos(Consumer<EventoCliente> observador) {
        if (!observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    @Override
    public void desuscribirEventos(Consumer<EventoCliente> observador) {
        observadores.remove(observador);
    }

    @Override
    public int cantidadObservadoresEventos() {
        return observadores.size();
    }

    /** Adapta la trama a evento neutral y la entrega a cada observador. */
    public void publicar(RespuestaRed mensaje) {
        EventoCliente evento = adaptar(mensaje);
        for (Consumer<EventoCliente> observador : observadores) {
            try {
                observador.accept(evento);
            } catch (RuntimeException error) {
                LOG.log(Level.WARNING, "observador de eventos del cliente falló", error);
            }
        }
    }

    static EventoCliente adaptar(RespuestaRed mensaje) {
        EventoCliente.Tipo tipo = switch (mensaje.tipo()) {
            case PRESENCIA -> EventoCliente.Tipo.PRESENCIA;
            case CLOSE_NOTICE -> EventoCliente.Tipo.SESION_CERRADA;
            case MENSAJE_ENTREGADO -> EventoCliente.Tipo.ACUSE_ENTREGA;
            case MENSAJE_LEIDO -> EventoCliente.Tipo.ACUSE_LECTURA;
            default -> EventoCliente.Tipo.MENSAJE;
        };
        return new EventoCliente(tipo, mensaje.remitente(), mensaje.destinatario());
    }
}
