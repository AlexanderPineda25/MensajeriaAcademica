package universidad.mensajeria.cliente.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.Scene;

/**
 * La salida de sesión vive en su propio colaborador (SRP): confirma antes de
 * salir, detiene el controlador y el cierre remoto solo se procesa una vez.
 */
class SalidaSesionChatTest {

    private List<String> estados;
    private AtomicBoolean detenido;
    private NavegacionFalsa navegacion;
    private List<String> llamadas;
    private final Executor fondo = Runnable::run;

    @BeforeEach
    void montar() throws Exception {
        iniciarJavaFx();
        estados = new ArrayList<>();
        detenido = new AtomicBoolean();
        navegacion = new NavegacionFalsa();
        llamadas = new ArrayList<>();
    }

    @Test
    void salirConfirmaDetieneYVuelveAlLogin() throws Exception {
        navegar(true);

        gestor().salir();
        vaciarColaFx();

        assertTrue(detenido.get());
        assertTrue(llamadas.contains("logout"));
        assertEquals(1, navegacion.vecesLogin);
    }

    @Test
    void salirCanceladaNoHaceNada() throws Exception {
        navegar(false);

        gestor().salir();
        vaciarColaFx();

        assertFalse(detenido.get());
        assertEquals(0, navegacion.vecesLogin);
    }

    @Test
    void expulsarOtrasAvisaElEstado() throws Exception {
        gestor().expulsarOtras();
        vaciarColaFx();

        assertEquals(List.of("Se solicitó cerrar tus otras sesiones"), estados);
    }

    @Test
    void cierreRemotoSoloSeProcesaUnaVez() {
        GestorSalidaSesionChat gestor = gestor();

        gestor.manejarCierreSesion();
        gestor.manejarCierreSesion();

        assertTrue(detenido.get());
        assertEquals(1, navegacion.vecesLogin);
        assertEquals(1, navegacion.avisos.size());
    }

    @Test
    void cierreRemotoConControladorDetenidoNoActua() {
        detenido.set(true);

        gestor().manejarCierreSesion();

        assertEquals(0, navegacion.vecesLogin);
    }

    private void navegar(boolean confirma) {
        navegacion.confirma = confirma;
    }

    private GestorSalidaSesionChat gestor() {
        InterfazChatViewModel vista = (InterfazChatViewModel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{InterfazChatViewModel.class},
                (proxy, method, args) -> {
                    llamadas.add(method.getName());
                    return null;
                });
        return new GestorSalidaSesionChat(vista, fondo, navegacion, estados::add,
                () -> detenido.set(true), detenido::get);
    }

    private static final class NavegacionFalsa implements InterfazNavegacionCliente {
        private boolean confirma;
        private int vecesLogin;
        private final List<String> avisos = new ArrayList<>();

        @Override
        public void mostrarLogin() {
            vecesLogin++;
        }

        @Override
        public void mostrarChat() {
        }

        @Override
        public boolean confirmar(String pregunta) {
            return confirma;
        }

        @Override
        public void avisarProximoLogin(String motivo) {
            avisos.add(motivo);
        }

        @Override
        public String consumirAviso() {
            return avisos.isEmpty() ? null : avisos.remove(0);
        }

        @Override
        public void aplicarTema(Scene escena, boolean login) {
        }
    }

    private static void iniciarJavaFx() throws InterruptedException {
        CountDownLatch iniciado = new CountDownLatch(1);
        try {
            Platform.startup(iniciado::countDown);
            if (!iniciado.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX no inició dentro del tiempo esperado.");
            }
        } catch (IllegalStateException yaIniciado) {
            if (!"Toolkit already initialized".equals(yaIniciado.getMessage())) {
                throw yaIniciado;
            }
        }
    }

    private static <T> T ejecutarEnJavaFx(Callable<T> accion) throws Exception {
        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(20, TimeUnit.SECONDS);
    }

    private static void vaciarColaFx() throws Exception {
        ejecutarEnJavaFx(() -> null);
        ejecutarEnJavaFx(() -> null);
    }
}
