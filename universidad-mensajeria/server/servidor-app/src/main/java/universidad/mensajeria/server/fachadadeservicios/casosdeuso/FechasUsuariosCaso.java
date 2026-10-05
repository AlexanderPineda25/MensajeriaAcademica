package universidad.mensajeria.server.fachadadeservicios.casosdeuso;

import universidad.mensajeria.common.interno.FechasUsuarioDTO;
import universidad.mensajeria.server.almacenarinformacion.InterfazAlmacenarInformacion;

import java.util.HashMap;
import java.util.Map;

/**
 * Fechas por codigo para los informes (los usuarios en memoria no traen
 * fechas: viven en la tabla usuarios). La Fachada delega aquí en vez de
 * tocar persistencia directa (ver ComponentesServidor).
 */
public final class FechasUsuariosCaso {

    private final InterfazAlmacenarInformacion almacenar;

    public FechasUsuariosCaso(InterfazAlmacenarInformacion almacenar) {
        this.almacenar = almacenar;
    }

    public Map<String, FechasUsuarioDTO> porCodigo() {
        Map<String, FechasUsuarioDTO> fechas = new HashMap<>();
        for (FechasUsuarioDTO fecha : almacenar.fechasUsuarios()) {
            fechas.put(fecha.codigo(), fecha);
        }
        return fechas;
    }
}
