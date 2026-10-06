package universidad.mensajeria.cliente.composicion;

import universidad.mensajeria.cliente.fachada.InterfazFachadaCliente;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

/** Recursos de vida de aplicación construidos una sola vez por la composición. */
public final class ClienteRuntime implements AutoCloseable {

    private final ClienteConfig config;
    private final int sentenciasInicializacion;
    private final LocalStore store;
    private final InterfazFachadaCliente fachada;
    private boolean cerrado;

    ClienteRuntime(ClienteConfig config, int sentenciasInicializacion, LocalStore store,
                   InterfazFachadaCliente fachada) {
        this.config = config;
        this.sentenciasInicializacion = sentenciasInicializacion;
        this.store = store;
        this.fachada = fachada;
    }

    public ClienteConfig config() {
        return config;
    }

    public int sentenciasInicializacion() {
        return sentenciasInicializacion;
    }

    public InterfazFachadaCliente fachada() {
        return fachada;
    }

    @Override
    public synchronized void close() {
        if (cerrado) {
            return;
        }
        cerrado = true;
        try {
            fachada.desconectar();
        } finally {
            try {
                fachada.cerrar();
            } finally {
                store.cerrar();
            }
        }
    }
}
