package universidad.mensajeria.cliente.componentes.protocolo;

import java.util.List;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

/** Puerto para traducir respuestas JSON del servidor a modelos del cliente. */
public interface InterfazMapeadorDatosServidor {

    List<UsuarioLocal> mapearDirectorio(String json);

    List<MensajeLocal> mapearPaginaHistorial(String json, String codigoSesion);

    String normalizarTipoMensaje(String tipo);
}
