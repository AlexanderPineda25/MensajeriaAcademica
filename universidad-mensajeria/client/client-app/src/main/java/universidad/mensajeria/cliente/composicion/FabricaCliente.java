package universidad.mensajeria.cliente.composicion;

import universidad.mensajeria.cliente.componentes.autenticacion.Autenticacion;
import universidad.mensajeria.cliente.componentes.cache.CacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.ConexionCliente;
import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.fachada.FachadaCliente;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

/** Composition root y fábrica del grafo concreto del cliente Java. */
public final class FabricaCliente {

    public ClienteRuntime crear() throws Exception {
        ClienteConfig config = ClienteConfig.cargar();
        LocalStore store = new LocalStore(config);
        try {
            int sentencias = store.inicializar();
            ConexionCliente red = new ConexionCliente(config.host(), config.puerto(),
                    config.tlsHabilitado(), config.tlsPuerto(), config.tlsConfianza());
            HistorialLocal historial = new HistorialLocal(store);
            CacheUsuarios cache = new CacheUsuarios(store);
            FachadaCliente fachada = new FachadaCliente(red,
                    new Autenticacion(red), historial, cache,
                    new MapeadorDatosServidor());
            return new ClienteRuntime(config, sentencias, store, fachada);
        } catch (Exception | Error fallo) {
            store.cerrar();
            throw fallo;
        }
    }
}
