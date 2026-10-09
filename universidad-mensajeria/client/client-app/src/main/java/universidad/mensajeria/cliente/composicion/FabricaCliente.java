package universidad.mensajeria.cliente.composicion;

import universidad.mensajeria.cliente.componentes.autenticacion.Autenticacion;
import universidad.mensajeria.cliente.componentes.autenticacion.InterfazAutenticacion;
import universidad.mensajeria.cliente.componentes.cache.CacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.ConexionCliente;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.historial.HistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.fachada.FachadaCliente;
import universidad.mensajeria.cliente.fachada.InterfazCoordinadorHistorial;
import universidad.mensajeria.cliente.fachada.InterfazCoordinadorMensajeria;
import universidad.mensajeria.cliente.fachada.InterfazReintentadorPendientes;
import universidad.mensajeria.cliente.fachada.InterfazLectorHistorialRemoto;
import universidad.mensajeria.cliente.fachada.InterfazSincronizadorDirectorio;
import universidad.mensajeria.cliente.fachada.servicios.CoordinadorHistorialCliente;
import universidad.mensajeria.cliente.fachada.servicios.CoordinadorMensajeriaCliente;
import universidad.mensajeria.cliente.fachada.servicios.LectorHistorialRemotoCliente;
import universidad.mensajeria.cliente.fachada.servicios.ReintentadorPendientesCliente;
import universidad.mensajeria.cliente.fachada.servicios.SincronizadorDirectorioCliente;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/** Composition root y fábrica del grafo concreto del cliente Java. */
public final class FabricaCliente {

    public ClienteRuntime crear() throws ExcepcionCliente {
        ClienteConfig config = ClienteConfig.cargar();
        LocalStore store = new LocalStore(config);
        try {
            int sentencias = store.inicializar();
            InterfazConexionRed red = new ConexionCliente(config.host(), config.puerto(),
                    config.tlsHabilitado(), config.tlsPuerto(), config.tlsConfianza());
            HistorialLocal historial = new HistorialLocal(store);
            CacheUsuarios cache = new CacheUsuarios(store);
            InterfazAutenticacion autenticacion = new Autenticacion(red);
            InterfazCoordinadorHistorial coordinadorHistorial =
                    new CoordinadorHistorialCliente(historial);
            InterfazCoordinadorMensajeria coordinadorMensajeria =
                    new CoordinadorMensajeriaCliente(red, historial);
            MapeadorDatosServidor mapeador = new MapeadorDatosServidor();
            InterfazReintentadorPendientes reintentador =
                    new ReintentadorPendientesCliente(red, historial, autenticacion::codigoActual);
            InterfazSincronizadorDirectorio sincronizador =
                    new SincronizadorDirectorioCliente(red, cache, mapeador,
                            autenticacion::codigoActual);
            InterfazLectorHistorialRemoto lector = new LectorHistorialRemotoCliente(red,
                    historial, mapeador, coordinadorHistorial, autenticacion::codigoActual);
            FachadaCliente fachada = new FachadaCliente(red, autenticacion, historial, cache,
                    mapeador, coordinadorHistorial, coordinadorMensajeria, null,
                    reintentador, sincronizador, lector);
            return new ClienteRuntime(config, sentencias, store, fachada);
        } catch (ExcepcionCliente | RuntimeException | Error fallo) {
            store.cerrar();
            throw fallo;
        }
    }
}
