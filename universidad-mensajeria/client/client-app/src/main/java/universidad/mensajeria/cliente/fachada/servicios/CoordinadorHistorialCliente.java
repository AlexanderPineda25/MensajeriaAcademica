package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.fachada.InterfazCoordinadorHistorial;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

import java.util.List;
import java.util.Optional;

/**
 * Servicio enfocado en consultas de historial, paginación y búsqueda local (Fase 4).
 */
public final class CoordinadorHistorialCliente implements InterfazCoordinadorHistorial {

    private final InterfazHistorialLocal historial;

    public CoordinadorHistorialCliente(InterfazHistorialLocal historial) {
        this.historial = historial;
    }

    @Override
    public List<MensajeLocal> conversacion(String yo, String otro, int limite, int offset) throws ExcepcionCliente {
        return historial.pagina(yo, otro, limite, offset);
    }

    @Override
    public int contarConversacion(String yo, String otro) throws ExcepcionCliente {
        return historial.contar(yo, otro);
    }

    @Override
    public List<String> buscarEnHistorial(String yo, String otro, String patron, int limite) throws ExcepcionCliente {
        List<MensajeLocal> encontrados = historial.buscar(yo, otro, patron, limite);
        return encontrados.stream().map(MensajeLocal::idMensaje).toList();
    }

    @Override
    public Optional<String> blobMensaje(String idMensaje) throws ExcepcionCliente {
        return historial.blobPorId(idMensaje);
    }

    public Optional<String> archivoIdMensaje(String idMensaje) throws ExcepcionCliente {
        return historial.archivoIdPorId(idMensaje);
    }

    public List<String> marcarLeidos(String yo, String otro) throws ExcepcionCliente {
        List<String> noLeidos = historial.idsNoLeidos(yo, otro);
        historial.marcarLeidosDe(yo, otro);
        return noLeidos;
    }
}
