package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.componentes.autenticacion.InterfazAutenticacion;
import universidad.mensajeria.cliente.componentes.cache.InterfazCacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
import universidad.mensajeria.cliente.componentes.historial.InterfazHistorialLocal;
import universidad.mensajeria.cliente.componentes.protocolo.InterfazMapeadorDatosServidor;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Receptor de tramas asíncronas del servidor (Fase 4).
 * <p>
 * Extraído de {@code FachadaCliente} por responsabilidad única: persiste lo
 * entrante (mensajes, acuses, presencia) y traduce estados de conexión, sin
 * decidir políticas de sesión ni reintentos. La fachada solo lo suscribe a la
 * red y le entrega las salidas observables.
 * </p>
 */
public final class ReceptorEntranteCliente implements ReceptorMensajes {

    private static final Logger LOG = Logger.getLogger(ReceptorEntranteCliente.class.getName());

    /** Salidas hacia la fachada: estado observable y eventos de UI. */
    public interface Salidas {
        EstadoConexion estadoConexion();

        void cambiarEstado(EstadoConexion nuevo);

        void publicarEvento(RespuestaRed mensaje);

        void programarReconexion();

        void olvidarCredenciales();
    }

    private final InterfazConexionRed red;
    private final InterfazAutenticacion autenticacion;
    private final InterfazHistorialLocal historial;
    private final InterfazCacheUsuarios cache;
    private final InterfazMapeadorDatosServidor mapeadorDatosServidor;
    private final Salidas salidas;

    public ReceptorEntranteCliente(InterfazConexionRed red,
                                   InterfazAutenticacion autenticacion,
                                   InterfazHistorialLocal historial,
                                   InterfazCacheUsuarios cache,
                                   InterfazMapeadorDatosServidor mapeadorDatosServidor,
                                   Salidas salidas) {
        this.red = red;
        this.autenticacion = autenticacion;
        this.historial = historial;
        this.cache = cache;
        this.mapeadorDatosServidor = mapeadorDatosServidor;
        this.salidas = salidas;
    }

    /** Persiste lo entrante y lo reenvía a la UI. */
    @Override
    public void alRecibir(RespuestaRed mensaje) {
        try {
            encaminarPersistencia(mensaje);
        } catch (ExcepcionCliente | java.io.IOException | RuntimeException e) {
            LOG.warning("no se pudo persistir la trama " + mensaje.tipo()
                    + ": " + e.getMessage());
        }
        salidas.publicarEvento(mensaje);
    }

    private void encaminarPersistencia(RespuestaRed mensaje) throws ExcepcionCliente, IOException {
        if (mensaje.tipo() == TipoMensajeCliente.MENSAJE_ENTREGADO) {
            marcarAcuse(mensaje.contenido(), EstadoMensaje.ENTREGADO);
        } else if (mensaje.tipo() == TipoMensajeCliente.MENSAJE_LEIDO) {
            marcarAcuse(mensaje.contenido(), EstadoMensaje.LEIDO);
        } else if (mensaje.tipo() == TipoMensajeCliente.PRESENCIA) {
            aplicarPresencia(mensaje);
        } else if (esMensajeGuardable(mensaje.tipo())) {
            guardarEntrante(mensaje);
        }
    }

    private void marcarAcuse(String idMensaje, EstadoMensaje estado) throws ExcepcionCliente, IOException {
        if (idMensaje != null) {
            historial.marcarEstado(idMensaje, estado.name());
        }
    }

    private void aplicarPresencia(RespuestaRed mensaje) throws ExcepcionCliente, IOException {
        if (mensaje.codigo() != null) {
            asegurarEnCache(mensaje.codigo());
        }
        if (mensaje.usuariosConectados() != null) {
            try {
                cache.marcarConectados(mensaje.usuariosConectados());
            } catch (ExcepcionCliente | RuntimeException presenciaParcial) {
                LOG.fine(() -> "presencia best-effort omitida: " + presenciaParcial.getMessage());
            }
        }
    }

    private static boolean esMensajeGuardable(TipoMensajeCliente tipo) {
        return tipo == TipoMensajeCliente.MENSAJE_TEXTO
                || tipo == TipoMensajeCliente.MENSAJE_IMAGEN
                || tipo == TipoMensajeCliente.MENSAJE_ARCHIVO
                || tipo == TipoMensajeCliente.BROADCAST
                || tipo == TipoMensajeCliente.SYNC_LOGIN;
    }

    private void guardarEntrante(RespuestaRed mensaje) throws ExcepcionCliente, IOException {
        String yo = autenticacion.codigoActual();
        String destino = yo != null ? yo : mensaje.destinatario();
        String cuerpo = cuerpoEntrante(mensaje);
        String tipo = mensaje.tipo() == TipoMensajeCliente.SYNC_LOGIN
                ? mapeadorDatosServidor.normalizarTipoMensaje(mensaje.tipoOriginal())
                : mensaje.tipo().name();
        historial.guardar(new MensajeLocal(
                mensaje.id() != null ? mensaje.id() : UUID.randomUUID().toString(),
                mensaje.remitente(), destino, tipo, cuerpo, mensaje.hashSha256(),
                mensaje.numCaracteres(), mensaje.numPalabras(),
                mensaje.nombreArchivo(), null, mensaje.tamanoArchivo(),
                mensaje.fechaHora() != null ? mensaje.fechaHora()
                        : LocalDateTime.now().toString(),
                yo != null && yo.equals(mensaje.remitente()), true,
                EstadoMensaje.ENTREGADO.name(), mensaje.archivoId(), mensaje.etapasFiltrado()));
        if (mensaje.remitente() != null) {
            asegurarEnCache(mensaje.remitente());
        }
    }

    private static String cuerpoEntrante(RespuestaRed mensaje) {
        if ((mensaje.tipo() == TipoMensajeCliente.MENSAJE_IMAGEN
                || mensaje.tipo() == TipoMensajeCliente.MENSAJE_ARCHIVO)
                && mensaje.contenidoImagen() != null) {
            return mensaje.contenidoImagen();
        }
        return mensaje.contenido();
    }

    private void asegurarEnCache(String codigo) throws ExcepcionCliente, IOException {
        if (cache.porCodigo(codigo).isEmpty()) {
            String ahora = LocalDateTime.now().toString();
            cache.guardar(new UsuarioLocal(codigo, codigo, "", "", false, ahora, ahora));
        }
    }

    @Override
    public void alCierre(RespuestaRed aviso) {
        // PAT-03: CLOSE_NOTICE es "el servidor cerró mi sesión", no una caída
        // de red. Distinguirlo permite que la UI no ofrezca "reintentar" en un
        // sitio donde lo que hace falta es volver a entrar.
        salidas.olvidarCredenciales();
        salidas.cambiarEstado(EstadoConexion.SESION_CERRADA);
        salidas.publicarEvento(aviso);
    }

    @Override
    public void alError(String motivo) {
        // PAT-03/UIX-00c: esto ya no es un comentario. Un ERROR de trama es un
        // fallo de una petición, no del socket: se registra y se propaga a la UI
        // como estado degradado solo si además no hay socket vivo.
        LOG.warning("error del servidor: " + motivo);
        if (!red.conectado()) {
            salidas.cambiarEstado(EstadoConexion.SIN_RED);
        }
    }

    @Override
    public void alDesconexion() {
        // PAT-03/UIX-00c: el hilo lector murió. Aquí se enteraba la UI por
        // sondeo cada 30 s; ahora es un evento.
        LOG.info("conexión con el servidor perdida");
        if (salidas.estadoConexion() != EstadoConexion.SESION_CERRADA) {
            salidas.cambiarEstado(EstadoConexion.SIN_RED);
            salidas.programarReconexion();
        }
    }
}
