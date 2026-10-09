package universidad.mensajeria.server.fachadadeservicios.casosdeuso;

import universidad.mensajeria.common.interno.FechasUsuarioDTO;
import universidad.mensajeria.server.mensajes.InterfazMensajes;

import java.util.HashMap;
import java.util.Map;

/**
 * Fechas por codigo para los informes (los usuarios en memoria no traen
 * fechas: viven en la tabla usuarios). La Fachada delega aquí en vez de
 * tocar persistencia directa (diagrama: solo via Mensajes/LogDeEventos).
 */
public final class FechasUsuariosCaso {

    private final InterfazMensajes mensajes;

    public FechasUsuariosCaso(InterfazMensajes mensajes) {
        this.mensajes = mensajes;
    }

    public Map<String, FechasUsuarioDTO> porCodigo() {
        Map<String, FechasUsuarioDTO> fechas = new HashMap<>();
        for (FechasUsuarioDTO fecha : mensajes.fechasUsuarios()) {
            fechas.put(fecha.codigo(), fecha);
        }
        return fechas;
    }
}
