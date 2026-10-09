package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import java.io.IOException;
import java.util.List;

/**
 * Lecturas del directorio y la presencia (DIP).
 */
public interface InterfazSincronizadorDirectorio {

    /** Conectados del servidor + refresco de la caché local. */
    List<String> listarConectados() throws ExcepcionCliente, IOException;

    /** Directorio local (offline): conectados primero. */
    List<UsuarioLocal> directorioLocal() throws ExcepcionCliente;

    /** Carga el directorio completo solo al entrar o bajo actualización manual. */
    List<UsuarioLocal> sincronizarDirectorio() throws ExcepcionCliente, IOException;

    /** Presencia fresca de red. Solo para login, "Actualizar" manual y arranque. */
    List<String> refrescarPresencia() throws ExcepcionCliente, IOException;

    /** Asegura una fila mínima en caché para un código visto en red. */
    void asegurarEnCache(String codigo) throws ExcepcionCliente, IOException;
}
