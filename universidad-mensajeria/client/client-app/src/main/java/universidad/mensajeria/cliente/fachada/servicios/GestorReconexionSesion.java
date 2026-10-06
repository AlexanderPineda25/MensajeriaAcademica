package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.componentes.autenticacion.InterfazAutenticacion;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.transversal.EstadoConexion;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administra credenciales temporales y la restauración automática de sesión.
 */
public final class GestorReconexionSesion {

    private static final Logger LOG = Logger.getLogger(GestorReconexionSesion.class.getName());
    private static final int[] ESPERA_RECONEXION_SEGUNDOS = {1, 2, 4, 8, 16, 30};

    private final InterfazConexionRed red;
    private final InterfazAutenticacion autenticacion;
    private final Consumer<EstadoConexion> publicarEstado;
    private final Consumer<String> alRestaurarSesion;
    private final ScheduledExecutorService reconector;
    private final AtomicBoolean reconexionEnCurso = new AtomicBoolean();
    private final AtomicLong generacionSesion = new AtomicLong();
    private volatile ScheduledFuture<?> reintentoProgramado;
    private volatile String codigoSesion;
    private volatile String contrasenaSesion;
    private volatile boolean cierreIntencional = true;
    private volatile int intentosReconexion;

    public GestorReconexionSesion(InterfazConexionRed red,
                                  InterfazAutenticacion autenticacion,
                                  Consumer<EstadoConexion> publicarEstado,
                                  Consumer<String> alRestaurarSesion,
                                  ScheduledExecutorService reconector) {
        this.red = red;
        this.autenticacion = autenticacion;
        this.publicarEstado = publicarEstado;
        this.alRestaurarSesion = alRestaurarSesion;
        this.reconector = reconector;
    }

    public synchronized void guardarSesion(String codigo, String contrasena) {
        generacionSesion.incrementAndGet();
        codigoSesion = codigo;
        contrasenaSesion = contrasena;
        cierreIntencional = false;
        intentosReconexion = 0;
        cancelarReintentoProgramado();
    }

    public synchronized void olvidarSesion() {
        cierreIntencional = true;
        generacionSesion.incrementAndGet();
        codigoSesion = null;
        contrasenaSesion = null;
        intentosReconexion = 0;
        cancelarReintentoProgramado();
    }

    public synchronized void programarReconexion() {
        if (cierreIntencional || codigoSesion == null || contrasenaSesion == null
                || reconector.isShutdown()) {
            return;
        }
        if (reintentoProgramado != null && !reintentoProgramado.isDone()) {
            return;
        }
        int intento = intentosReconexion++;
        long demora = esperaSegundos(intento);
        LOG.info(() -> "Reintento de conexión programado en " + demora + " s");
        reintentoProgramado = reconector.schedule(this::intentarReconexion,
                demora, TimeUnit.SECONDS);
    }

    public void cerrar() {
        olvidarSesion();
        reconector.shutdownNow();
    }

    private void intentarReconexion() {
        reintentoProgramado = null;
        if (cierreIntencional || codigoSesion == null || contrasenaSesion == null
                || !reconexionEnCurso.compareAndSet(false, true)) {
            return;
        }
        long generacion = generacionSesion.get();
        try {
            String codigo = codigoSesion;
            String clave = contrasenaSesion;
            red.desconectar();
            if (cierreIntencional || generacion != generacionSesion.get()) {
                return;
            }
            red.conectar();
            if (cierreIntencional || generacion != generacionSesion.get()) {
                red.desconectar();
                return;
            }
            if (!autenticacion.login(codigo, clave)) {
                LOG.warning("La reautenticación fue rechazada; se requiere iniciar sesión de nuevo.");
                olvidarSesion();
                red.desconectar();
                publicarEstado.accept(EstadoConexion.SESION_CERRADA);
                return;
            }
            if (cierreIntencional || generacion != generacionSesion.get()) {
                red.desconectar();
                return;
            }
            intentosReconexion = 0;
            publicarEstado.accept(EstadoConexion.CONECTADO);
            alRestaurarSesion.accept(codigo);
            LOG.info("Conexión y sesión restauradas automáticamente.");
        } catch (Exception e) {
            LOG.log(Level.INFO, "Falló el intento de reconexión automática", e);
            red.desconectar();
            publicarEstado.accept(EstadoConexion.SIN_RED);
            programarReconexion();
        } finally {
            reconexionEnCurso.set(false);
        }
    }

    private synchronized void cancelarReintentoProgramado() {
        if (reintentoProgramado != null) {
            reintentoProgramado.cancel(false);
            reintentoProgramado = null;
        }
    }

    /**
     * Calcula los segundos de espera exponencial acotada a un máximo de 30 segundos.
     */
    public static int esperaSegundos(int intento) {
        return ESPERA_RECONEXION_SEGUNDOS[Math.min(Math.max(0, intento),
                ESPERA_RECONEXION_SEGUNDOS.length - 1)];
    }
}
