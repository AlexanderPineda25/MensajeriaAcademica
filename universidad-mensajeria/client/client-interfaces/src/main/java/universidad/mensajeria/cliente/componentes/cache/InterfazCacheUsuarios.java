package universidad.mensajeria.cliente.componentes.cache;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import java.util.List;
import java.util.Optional;

/** Caché local de usuarios (InterfazCacheUsuarios → adaptador H2). */
public interface InterfazCacheUsuarios {

    void guardar(UsuarioLocal usuario) throws ExcepcionCliente;

    default void reemplazarDirectorio(List<UsuarioLocal> usuarios) throws ExcepcionCliente {
        for (UsuarioLocal usuario : usuarios) {
            guardar(usuario);
        }
    }

    List<UsuarioLocal> listar() throws ExcepcionCliente;

    Optional<UsuarioLocal> porCodigo(String codigo) throws ExcepcionCliente;

    void marcarConectados(List<String> codigos) throws ExcepcionCliente;
}
