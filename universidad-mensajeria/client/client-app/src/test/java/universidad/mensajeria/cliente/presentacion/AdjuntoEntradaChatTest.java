package universidad.mensajeria.cliente.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;
import universidad.mensajeria.cliente.transversal.utilerias.TextoCliente;

/**
 * El adjunto de entrada vive en su propio colaborador (SRP): lee en fondo,
 * prepara el adjunto en el ViewModel y limpia al enviar o quitar.
 */
class AdjuntoEntradaChatTest {

    private ImageView vistaPrevia;
    private ListView<FilaChat> lista;
    private Label etiqueta;
    private List<String> estados;
    private List<String> llamadas;
    private byte[] preparado;
    private final Executor fondo = Runnable::run;

    @BeforeEach
    void montar() throws Exception {
        iniciarJavaFx();
        ejecutarEnJavaFx(() -> {
            vistaPrevia = new ImageView();
            lista = new ListView<>();
            etiqueta = new Label();
            return null;
        });
        estados = new ArrayList<>();
        llamadas = new ArrayList<>();
    }

    @Test
    void cargarArchivoPreparaElAdjunto() throws Exception {
        Path tmp = Files.createTempFile("adjunto", ".txt");
        Files.writeString(tmp, "contenido");

        ejecutarEnJavaFx(() -> {
            cargador().cargarArchivo(tmp.toFile());
            return null;
        });
        vaciarColaFx();

        assertTrue(llamadas.contains("prepararAdjunto"));
        assertEquals(tmp.getFileName().toString(), nombrePreparado());
        assertEquals("contenido", new String(preparado()));
        Files.deleteIfExists(tmp);
    }

    @Test
    void archivoGiganteSeRechazaConEstado() throws Exception {
        Path tmp = Files.createTempFile("grande", ".bin");
        try (var raf = new java.io.RandomAccessFile(tmp.toFile(), "rw")) {
            raf.setLength(TextoCliente.TOPE_ADJUNTO_BYTES + 1);
        }

        ejecutarEnJavaFx(() -> {
            cargador().cargarArchivo(tmp.toFile());
            return null;
        });
        vaciarColaFx();

        assertEquals(1, estados.size());
        assertTrue(estados.get(0).startsWith("No se pudo leer el archivo"));
        Files.deleteIfExists(tmp);
    }

    @Test
    void limpiarVacíaElViewModelYLaVista() throws Exception {
        ejecutarEnJavaFx(() -> {
            cargador().limpiar();
            return null;
        });

        assertTrue(llamadas.contains("limpiarAdjunto"));
        assertNull(vistaPrevia.getImage());
    }

    private String nombrePreparado() {
        return (String) llamadas.stream().filter(s -> s.startsWith("nombre:"))
                .map(s -> s.substring("nombre:".length())).findFirst().orElseThrow();
    }

    private byte[] preparado() {
        return preparado;
    }

    private CargadorAdjuntoEntradaChat cargador() {
        InterfazChatViewModel vista = (InterfazChatViewModel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{InterfazChatViewModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepararAdjunto" -> {
                        llamadas.add("prepararAdjunto");
                        llamadas.add("nombre:" + args[1]);
                        preparado = ((byte[]) args[0]).clone();
                        yield null;
                    }
                    case "limpiarAdjunto" -> { llamadas.add("limpiarAdjunto"); yield null; }
                    case "adjuntoNombre" -> "f.txt";
                    default -> null;
                });
        return new CargadorAdjuntoEntradaChat(vistaPrevia, lista, etiqueta, vista,
                fondo, estados::add);
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
