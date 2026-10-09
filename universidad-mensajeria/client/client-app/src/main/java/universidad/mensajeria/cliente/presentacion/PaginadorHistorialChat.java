package universidad.mensajeria.cliente.presentacion;

import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;
import universidad.mensajeria.cliente.presentacion.celdas.FilasChat;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;

import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Paginación del historial de la conversación visible (Fase 3).
 * <p>
 * Extraído de {@code ChatController} por responsabilidad única: traer páginas
 * locales/remotas, pintar filas con anclaje de scroll (UIX-16) y avisar de lo
 * nuevo que llega mientras se lee historial viejo. No decide contactos,
 * envíos ni salidas; todo lo externo llega por constructor (DIP).
 * </p>
 */
public final class PaginadorHistorialChat {

    private final ListView<FilaChat> listaMensajes;
    private final Button botonMas;
    private final Button botonNuevos;
    private final ProgressIndicator ruedaHistorial;
    private final InterfazGestorAdjuntos gestorAdjuntos;
    private final InterfazChatViewModel viewModel;
    private final Executor tareasFondo;
    private final Supplier<String> seleccionado;
    private final Consumer<String> estado;
    private final SaltoBusquedaPendiente salto;
    private final BiConsumer<String, Integer> alSaltar;
    private final int limitePagina;
    // UIX-16: mensajes que llegaron mientras se lee historial viejo.
    private int nuevosPendientes;

    public PaginadorHistorialChat(ListView<FilaChat> listaMensajes,
                                  Button botonMas,
                                  Button botonNuevos,
                                  ProgressIndicator ruedaHistorial,
                                  InterfazGestorAdjuntos gestorAdjuntos,
                                  InterfazChatViewModel viewModel,
                                  Executor tareasFondo,
                                  Supplier<String> seleccionado,
                                  Consumer<String> estado,
                                  SaltoBusquedaPendiente salto,
                                  BiConsumer<String, Integer> alSaltar,
                                  int limitePagina) {
        this.listaMensajes = listaMensajes;
        this.botonMas = botonMas;
        this.botonNuevos = botonNuevos;
        this.ruedaHistorial = ruedaHistorial;
        this.gestorAdjuntos = gestorAdjuntos;
        this.viewModel = viewModel;
        this.tareasFondo = tareasFondo;
        this.seleccionado = seleccionado;
        this.estado = estado;
        this.salto = salto;
        this.alSaltar = alSaltar;
        this.limitePagina = limitePagina;
    }

    /** Baja a lo nuevo sin perder el punto de lectura anterior. */
    public void irANuevos() {
        nuevosPendientes = 0;
        botonNuevos.setVisible(false);
        botonNuevos.setManaged(false);
        viewModel.setOffsetActual(0);
        cargarConversacion();
    }

    public void cargarMas() {
        String otro = seleccionado.get();
        if (otro == null) {
            return;
        }
        int mostrados = listaMensajes.getItems().size();
        Task<Boolean> tarea = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                int totalLocal = viewModel.contarConversacion(otro);
                if (viewModel.offsetActual() + limitePagina < totalLocal) {
                    viewModel.setOffsetActual(viewModel.offsetActual() + limitePagina);
                    return false;
                }
                int siguiente = gestorAdjuntos.paginaRemota(otro);
                Integer total = gestorAdjuntos.totalPaginasRemotas(otro);
                if (total != null && siguiente >= total) {
                    return null;
                }
                PaginaHistorialRemota remota = viewModel.traerHistorial(otro, siguiente);
                gestorAdjuntos.registrarPaginaRemota(otro, siguiente + 1,
                        remota.totalPaginas());
                viewModel.setOffsetActual(mostrados);
                return true;
            }
        };
        tarea.setOnSucceeded(e -> {
            Boolean trajo = tarea.getValue();
            if (trajo == null) {
                estado.accept("No hay más historial");
            } else {
                cargarConversacion();
            }
        });
        tarea.setOnFailed(e -> estado.accept(
                "Error al cargar historial: " + mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    /**
     * Avisa de lo nuevo que llega mientras se lee historial viejo: no arrastra
     * el scroll, solo arma el botón de bajada (UIX-16).
     */
    public void notificarEntrante(String afectado, boolean esAcuse) {
        String sel = seleccionado.get();
        if (sel != null && sel.equals(afectado) && !esAcuse
                && viewModel.offsetActual() > 0) {
            nuevosPendientes++;
            botonNuevos.setText("↓ " + nuevosPendientes + " nuevos");
            botonNuevos.setVisible(true);
            botonNuevos.setManaged(true);
        }
    }

    void pintarPagina(String otro, List<MensajeLocal> pagina, int total, int off) {
        if (otro == null) {
            return;
        }
        for (int i = 0; i < pagina.size(); i++) {
            MensajeLocal m = pagina.get(i);
            int rankDesdeMasNuevo = off + pagina.size() - 1 - i;
            gestorAdjuntos.registrarPaginaDeMensaje(
                    m.idMensaje(), rankDesdeMasNuevo / limitePagina);
        }
        // UIX-04/UIX-05: la lista muestra filas (mensajes + separadores de día),
        // no mensajes sueltos. Se reconstruye desde todos los mensajes cargados
        // para que las cabeceras y las rachas queden bien también al anteponer
        // páginas viejas (el anclaje fino del scroll es UIX-16).
        List<MensajeLocal> mensajes = new ArrayList<>(pagina);
        int filasAntes = listaMensajes.getItems().size();
        if (off > 0) {
            Set<String> existentes = new HashSet<>();
            List<MensajeLocal> actuales = new ArrayList<>();
            for (FilaChat fila : listaMensajes.getItems()) {
                if (fila instanceof FilaChat.MensajeFila mf) {
                    existentes.add(mf.mensaje().idMensaje());
                    actuales.add(mf.mensaje());
                }
            }
            mensajes.removeIf(m -> existentes.contains(m.idMensaje()));
            mensajes.addAll(actuales);
        }
        listaMensajes.getItems().setAll(FilasChat.armar(mensajes));
        if (off == 0 && !listaMensajes.getItems().isEmpty()) {
            listaMensajes.scrollTo(listaMensajes.getItems().size() - 1);
            nuevosPendientes = 0;
            botonNuevos.setVisible(false);
            botonNuevos.setManaged(false);
        } else if (off > 0) {
            // UIX-16: ancla en el primer mensaje que ya se veía: las filas
            // nuevas van todas encima, así que su índice sube justo eso.
            int nuevas = listaMensajes.getItems().size() - filasAntes;
            if (nuevas > 0) {
                listaMensajes.scrollTo(Math.min(nuevas, listaMensajes.getItems().size() - 1));
            }
        }
        boolean mas = total > listaMensajes.getItems().size()
                || gestorAdjuntos.hayMasHistorialRemoto(otro);
        botonMas.setVisible(mas);
        botonMas.setManaged(mas);
    }

    void cargarConversacion() {
        String otro = seleccionado.get();
        if (otro == null) {
            return;
        }
        int off = viewModel.offsetActual();
        // UIX-14: la rueda gira mientras el historial se trae en fondo.
        ruedaHistorial.setVisible(true);
        ruedaHistorial.setManaged(true);
        Task<CargaPagina> tarea = new Task<>() {
            @Override
            protected CargaPagina call() throws Exception {
                List<MensajeLocal> pag = viewModel.cargarConversacion(otro, limitePagina, off);
                return new CargaPagina(otro, pag, viewModel.contarConversacion(otro), off);
            }
        };
        tarea.setOnSucceeded(e -> {
            ruedaHistorial.setVisible(false);
            ruedaHistorial.setManaged(false);
            pintarPagina(otro, tarea.getValue().pagina(), tarea.getValue().total(), off);
            // UIX-06: si hay un salto pendiente (búsqueda), se reintenta ahora
            // que hay más mensajes cargados.
            if (salto.haySalto()) {
                String id = salto.id();
                int intentos = salto.intentos();
                salto.consumir();
                alSaltar.accept(id, intentos);
            }
        });
        tarea.setOnFailed(e -> {
            ruedaHistorial.setVisible(false);
            ruedaHistorial.setManaged(false);
            estado.accept("Error: " + mensaje(tarea.getException()));
        });
        tareasFondo.execute(tarea);
    }

    private record CargaPagina(String conversacion, List<MensajeLocal> pagina,
                               int total, int offset) {
    }

    private static String mensaje(Throwable e) {
        return FormatoErrorCliente.mensaje(e);
    }
}
