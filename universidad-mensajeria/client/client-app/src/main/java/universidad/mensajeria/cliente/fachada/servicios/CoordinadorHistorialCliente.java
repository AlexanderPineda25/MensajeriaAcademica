package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

import java.util.List;
import java.util.Optional;

/**
 * Servicio enfocado en consultas de historial, paginación y búsqueda local (Fase 4).
 */
public final class CoordinadorHistorialCliente {

    private final InterfazHistorialLocal historial;

    public CoordinadorHistorialCliente(InterfazHistorialLocal historial) {
        this.historial = historial;
    }

    public List<MensajeLocal> conversacion(String yo, String otro, int limite, int offset) throws Exception {
        return historial.pagina(yo, otro, limite, offset);
    }

    public int contarConversacion(String yo, String otro) throws Exception {
        return historial.contar(yo, otro);
    }

    public List<String> buscarEnHistorial(String yo, String otro, String patron, int limite) throws Exception {
        List<MensajeLocal> encontrados = historial.buscar(yo, otro, patron, limite);
        return encontrados.stream().map(MensajeLocal::idMensaje).toList();
    }

    public Optional<String> blobMensaje(String idMensaje) throws Exception {
        return historial.blobPorId(idMensaje);
    }

    public Optional<String> archivoIdMensaje(String idMensaje) throws Exception {
        return historial.archivoIdPorId(idMensaje);
    }

    public List<String> marcarLeidos(String yo, String otro) throws Exception {
        List<String> noLeidos = historial.idsNoLeidos(yo, otro);
        historial.marcarLeidosDe(yo, otro);
        return noLeidos;
    }
}
