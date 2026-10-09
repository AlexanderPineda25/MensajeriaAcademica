package universidad.mensajeria.cliente.presentacion;

import javafx.concurrent.Task;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Salida de sesión del chat: kick, salir simple/total y cierre remoto (Fase 3).
 * <p>
 * Extraído de {@code ChatController} por responsabilidad única: cerrar la
 * sesión local o remota y devolver al login, sin saber de contactos,
 * historial ni envíos. Todo lo externo llega por constructor (DIP).
 * </p>
 */
public final class GestorSalidaSesionChat {

    private static final Logger LOG = Logger.getLogger(GestorSalidaSesionChat.class.getName());

    private final InterfazChatViewModel viewModel;
    private final Executor tareasFondo;
    private final InterfazNavegacionCliente navegacion;
    private final Consumer<String> estado;
    private final Runnable detener;
    private final BooleanSupplier detenido;
    private boolean cierreRemotoProcesado;

    public GestorSalidaSesionChat(InterfazChatViewModel viewModel,
                                  Executor tareasFondo,
                                  InterfazNavegacionCliente navegacion,
                                  Consumer<String> estado,
                                  Runnable detener,
                                  BooleanSupplier detenido) {
        this.viewModel = viewModel;
        this.tareasFondo = tareasFondo;
        this.navegacion = navegacion;
        this.estado = estado;
        this.detener = detener;
        this.detenido = detenido;
    }

    public void expulsarOtras() {
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                viewModel.expulsarOtrasSesiones();
                return null;
            }
        };
        tarea.setOnSucceeded(e -> estado.accept("Se solicitó cerrar tus otras sesiones"));
        tarea.setOnFailed(e -> estado.accept(
                "Error: " + FormatoErrorCliente.mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    public void salir() {
        if (navegacion.confirmar("¿Deseas cerrar esta sesión y volver al inicio?")) {
            salirAlInicio(false);
        }
    }

    public void salirEnTodas() {
        if (navegacion.confirmar(
                "¿Deseas cerrar esta sesión y cerrar también las otras sesiones de tu cuenta?")) {
            salirAlInicio(true);
        }
    }

    void salirAlInicio() {
        salirAlInicio(false);
    }

    private void salirAlInicio(boolean todasLasSesiones) {
        detener.run();
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (todasLasSesiones) {
                    viewModel.expulsarOtrasSesiones();
                }
                try {
                    viewModel.logout();
                } catch (universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente
                        | java.io.IOException falloSesion) {
                    LOG.fine(() -> "logout best-effort: "
                            + FormatoErrorCliente.mensaje(falloSesion));
                    viewModel.desconectar();
                }
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            try {
                navegacion.mostrarLogin();
            } catch (universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente
                    | java.io.IOException err) {
                estado.accept(FormatoErrorCliente.mensaje(err));
            }
        });
        tareasFondo.execute(tarea);
    }

    /** Cierre ordenado por el servidor: una sola vez y de vuelta al login. */
    public void manejarCierreSesion() {
        if (cierreRemotoProcesado || detenido.getAsBoolean()) {
            return;
        }
        cierreRemotoProcesado = true;
        detener.run();
        viewModel.cerrarSesionRemota();
        navegacion.avisarProximoLogin("Tu sesión se cerró. Inicia sesión nuevamente.");
        try {
            navegacion.mostrarLogin();
        } catch (universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente
                | java.io.IOException falloNavegacion) {
            LOG.warning("No se pudo volver al inicio de sesión: "
                    + FormatoErrorCliente.mensaje(falloNavegacion));
        }
    }
}
