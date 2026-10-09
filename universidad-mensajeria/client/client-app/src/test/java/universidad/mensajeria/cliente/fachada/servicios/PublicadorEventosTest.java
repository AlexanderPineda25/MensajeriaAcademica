package universidad.mensajeria.cliente.fachada.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

/**
 * El bus de eventos vive en su propio servicio (SRP): suscribe sin duplicar,
 * adapta tramas a eventos neutrales y un observador roto no tumba a los demás.
 */
class PublicadorEventosTest {

    @Test
    void suscribirDosVecesNoDuplica() {
        PublicadorEventosCliente bus = new PublicadorEventosCliente();
        List<EventoCliente> recibidos = new ArrayList<>();
        java.util.function.Consumer<EventoCliente> observador = recibidos::add;
        bus.suscribirEventos(observador);
        bus.suscribirEventos(observador);

        assertEquals(1, bus.cantidadObservadoresEventos());
    }

    @Test
    void publicaCadaTipoAdaptadoASuEvento() {
        PublicadorEventosCliente bus = new PublicadorEventosCliente();
        List<EventoCliente> recibidos = new ArrayList<>();
        bus.suscribirEventos(recibidos::add);

        bus.publicar(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_TEXTO).remitente("B002")
                .destinatario("A001").build()));
        bus.publicar(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.PRESENCIA).build()));

        assertEquals(2, recibidos.size());
        assertEquals(EventoCliente.Tipo.MENSAJE, recibidos.get(0).tipo());
        assertEquals(EventoCliente.Tipo.PRESENCIA, recibidos.get(1).tipo());
        assertEquals("B002", recibidos.get(0).remitente());
    }

    @Test
    void observadorRotoNoBloqueaALosDemas() {
        PublicadorEventosCliente bus = new PublicadorEventosCliente();
        List<EventoCliente> sanos = new ArrayList<>();
        bus.suscribirEventos(e -> { throw new IllegalStateException("roto"); });
        bus.suscribirEventos(sanos::add);

        bus.publicar(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_TEXTO).build()));

        assertEquals(1, sanos.size());
    }

    @Test
    void desuscribirDejaDeAvisar() {
        PublicadorEventosCliente bus = new PublicadorEventosCliente();
        List<EventoCliente> recibidos = new ArrayList<>();
        java.util.function.Consumer<EventoCliente> observador = recibidos::add;
        bus.suscribirEventos(observador);
        bus.desuscribirEventos(observador);

        bus.publicar(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.MENSAJE_TEXTO).build()));

        assertEquals(0, recibidos.size());
        assertEquals(0, bus.cantidadObservadoresEventos());
    }
}
