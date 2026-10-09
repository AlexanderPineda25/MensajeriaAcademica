package universidad.mensajeria.cliente.presentacion;

import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Búsqueda dentro del historial de la conversación visible, UIX-06 (Fase 3).
 * <p>
 * Extraído de {@code ChatController} por responsabilidad única: patrón con
 * debounce, navegación entre coincidencias y salto con carga diferida. Todo lo
 * externo llega por constructor (DIP); no decide contactos, envíos ni salidas.
 * </p>
 */
public final class BusquedaHistorialChat {

    private final ListView<FilaChat> listaMensajes;
    private final Button botonMas;
    private final HBox barraBuscar;
    private final TextField campoBuscarHistorial;
    private final Label etiquetaBuscarContador;
    private final InterfazChatViewModel viewModel;
    private final Executor tareasFondo;
    private final Supplier<String> seleccionado;
    private final Consumer<String> estado;
    private final Runnable cargarMas;
    private final SaltoBusquedaPendiente salto;
    private final PauseTransition pausaBuscar;
    // UIX-06: búsqueda en el historial con debounce.
    private final List<String> idsBusqueda = new ArrayList<>();
    private int indiceBusqueda = -1;
    private String patronBusqueda = "";

    public BusquedaHistorialChat(ListView<FilaChat> listaMensajes,
                                 Button botonMas,
                                 HBox barraBuscar,
                                 TextField campoBuscarHistorial,
                                 Label etiquetaBuscarContador,
                                 InterfazChatViewModel viewModel,
                                 Executor tareasFondo,
                                 Supplier<String> seleccionado,
                                 Consumer<String> estado,
                                 Runnable cargarMas,
                                 SaltoBusquedaPendiente salto,
                                 long debounceMs) {
        this.listaMensajes = listaMensajes;
        this.botonMas = botonMas;
        this.barraBuscar = barraBuscar;
        this.campoBuscarHistorial = campoBuscarHistorial;
        this.etiquetaBuscarContador = etiquetaBuscarContador;
        this.viewModel = viewModel;
        this.tareasFondo = tareasFondo;
        this.seleccionado = seleccionado;
        this.estado = estado;
        this.cargarMas = cargarMas;
        this.salto = salto;
        this.pausaBuscar = new PauseTransition(javafx.util.Duration.millis(debounceMs));
        this.pausaBuscar.setOnFinished(e -> buscarHistorialFondo());
    }

    /** Conecta el campo de búsqueda (debounce, Esc, Enter/Shift+Enter). */
    public void configurar() {
        campoBuscarHistorial.textProperty().addListener((o, a, b) -> {
            pausaBuscar.stop();
            pausaBuscar.playFromStart();
        });
        campoBuscarHistorial.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cerrar();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    anterior();
                } else {
                    siguiente();
                }
                e.consume();
            }
        });
    }

    /** Patrón activo para el resaltado de las celdas. */
    public String patronActual() {
        return patronBusqueda;
    }

    public void mostrar() {
        if (seleccionado.get() == null) {
            estado.accept("Elige una conversación para buscar en su historial.");
            return;
        }
        barraBuscar.setVisible(true);
        barraBuscar.setManaged(true);
        campoBuscarHistorial.requestFocus();
        campoBuscarHistorial.selectAll();
    }

    public void cerrar() {
        barraBuscar.setVisible(false);
        barraBuscar.setManaged(false);
        campoBuscarHistorial.clear();
        patronBusqueda = "";
        idsBusqueda.clear();
        indiceBusqueda = -1;
        salto.consumir();
        etiquetaBuscarContador.setText("");
        listaMensajes.refresh();
    }

    public void siguiente() {
        if (idsBusqueda.isEmpty()) {
            return;
        }
        indiceBusqueda = (indiceBusqueda + 1) % idsBusqueda.size();
        actualizarContadorBusqueda();
        saltarA(idsBusqueda.get(indiceBusqueda), 5);
    }

    public void anterior() {
        if (idsBusqueda.isEmpty()) {
            return;
        }
        indiceBusqueda = (indiceBusqueda - 1 + idsBusqueda.size()) % idsBusqueda.size();
        actualizarContadorBusqueda();
        saltarA(idsBusqueda.get(indiceBusqueda), 5);
    }

    private void actualizarContadorBusqueda() {
        if (idsBusqueda.isEmpty()) {
            etiquetaBuscarContador.setText(patronBusqueda.isBlank() ? "" : "Sin resultados");
        } else {
            etiquetaBuscarContador.setText((indiceBusqueda + 1) + "/" + idsBusqueda.size());
        }
    }

    void buscarHistorialFondo() {
        String otro = seleccionado.get();
        String patron = campoBuscarHistorial.getText() == null
                ? "" : campoBuscarHistorial.getText().trim();
        if (otro == null || patron.isEmpty()) {
            patronBusqueda = "";
            idsBusqueda.clear();
            indiceBusqueda = -1;
            actualizarContadorBusqueda();
            listaMensajes.refresh();
            return;
        }
        Task<List<String>> tarea = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return viewModel.buscarHistorial(otro, patron);
            }
        };
        tarea.setOnSucceeded(e -> {
            if (!patron.equals(campoBuscarHistorial.getText() == null
                    ? "" : campoBuscarHistorial.getText().trim())) {
                return;
            }
            patronBusqueda = patron;
            idsBusqueda.clear();
            idsBusqueda.addAll(tarea.getValue());
            indiceBusqueda = idsBusqueda.isEmpty() ? -1 : 0;
            actualizarContadorBusqueda();
            listaMensajes.refresh();
            if (!idsBusqueda.isEmpty()) {
                saltarA(idsBusqueda.get(0), 5);
            }
        });
        tarea.setOnFailed(e -> estado.accept(
                "No se pudo buscar: " + mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    /**
     * Salta al mensaje: si está cargado, scroll + selección; si no, carga una
     * página más y reintenta (acotado). El resaltado lo pinta la celda con el
     * patrón activo.
     */
    public void saltarA(String id, int intentos) {
        String otro = seleccionado.get();
        if (otro == null || id == null) {
            return;
        }
        for (int i = 0; i < listaMensajes.getItems().size(); i++) {
            FilaChat fila = listaMensajes.getItems().get(i);
            if (fila instanceof FilaChat.MensajeFila mf && id.equals(mf.mensaje().idMensaje())) {
                listaMensajes.scrollTo(i);
                listaMensajes.getSelectionModel().select(i);
                return;
            }
        }
        if (intentos <= 0 || !botonMas.isVisible()) {
            estado.accept("La coincidencia no está en el historial cargado.");
            return;
        }
        salto.programar(id, intentos - 1);
        cargarMas.run();
    }

    private static String mensaje(Throwable e) {
        return FormatoErrorCliente.mensaje(e);
    }
}
