package universidad.mensajeria.cliente.presentacion;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import javafx.scene.control.ListView;
import javafx.stage.FileChooser;
import universidad.mensajeria.cliente.fachada.InterfazArchivosConversacion;
import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;
import universidad.mensajeria.cliente.presentacion.celdas.MensajeCell;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;

/** Carga, reintenta, cancela y descarga adjuntos de la conversación visible. */
public final class GestorAdjuntosChat implements InterfazGestorAdjuntos {

    private static final String METADATOS_PENDIENTES = "metadatos";
    private static final Logger LOG = Logger.getLogger(GestorAdjuntosChat.class.getName());

    private final InterfazArchivosConversacion archivos;
    private final Executor tareasFondo;
    private final ListView<FilaChat> listaMensajes;
    private final Supplier<String> conversacionActual;
    private final Consumer<String> mostrarEstado;
    private final Map<String, Integer> paginaPorMensaje = new ConcurrentHashMap<>();
    private final Map<String, Integer> siguientePaginaRemota = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalPaginasRemotas = new ConcurrentHashMap<>();
    private final Set<String> imagenesFallidas = ConcurrentHashMap.newKeySet();
    private final Map<String, Task<?>> cargasImagen = new ConcurrentHashMap<>();
    private final Map<String, Object> candadosMetadata = new ConcurrentHashMap<>();

    GestorAdjuntosChat(InterfazArchivosConversacion archivos, Executor tareasFondo,
                       ListView<FilaChat> listaMensajes,
                       Supplier<String> conversacionActual,
                       Consumer<String> mostrarEstado) {
        this.archivos = archivos;
        this.tareasFondo = tareasFondo;
        this.listaMensajes = listaMensajes;
        this.conversacionActual = conversacionActual;
        this.mostrarEstado = mostrarEstado;
    }

    @Override
    public boolean imagenFallida(String idMensaje) {
        return imagenesFallidas.contains(idMensaje);
    }

    @Override
    public int paginaRemota(String conversacion) {
        return siguientePaginaRemota.getOrDefault(conversacion, 0);
    }

    @Override
    public Integer totalPaginasRemotas(String conversacion) {
        return totalPaginasRemotas.get(conversacion);
    }

    @Override
    public void registrarPaginaRemota(String conversacion, int siguiente, int total) {
        siguientePaginaRemota.put(conversacion, siguiente);
        totalPaginasRemotas.put(conversacion, total);
    }

    @Override
    public boolean hayMasHistorialRemoto(String conversacion) {
        Integer total = totalPaginasRemotas(conversacion);
        return total != null && paginaRemota(conversacion) < total;
    }

    @Override
    public void registrarPaginaDeMensaje(String idMensaje, int pagina) {
        paginaPorMensaje.put(idMensaje, pagina);
    }

    @Override
    public void cambiarConversacion() {
        cancelarCargas();
        paginaPorMensaje.clear();
    }

    @Override
    public void detener() {
        cancelarCargas();
    }

    @Override
    public void cargarImagenVisible(MensajeLocal item) {
        if (item == null || cargasImagen.containsKey(item.idMensaje())) {
            return;
        }
        String chat = conversacionActual.get();
        if (chat == null) {
            return;
        }
        int paginaMetadata = paginaPorMensaje.getOrDefault(item.idMensaje(), 0);
        Task<byte[]> tarea = new Task<>() {
            @Override
            protected byte[] call() throws Exception {
                byte[] bytes;
                try {
                    bytes = archivos.descargarImagen(item.idMensaje());
                } catch (IllegalStateException faltaId) {
                    if (!esFaltaDeMetadatos(faltaId)) {
                        throw faltaId;
                    }
                    String clave = chat + ":" + paginaMetadata;
                    Object candado = candadosMetadata.computeIfAbsent(clave, k -> new Object());
                    synchronized (candado) {
                        try {
                            bytes = archivos.descargarImagen(item.idMensaje());
                        } catch (IllegalStateException aunSinId) {
                            if (!esFaltaDeMetadatos(aunSinId)) {
                                throw aunSinId;
                            }
                            PaginaHistorialRemota remota = archivos
                                    .traerHistorial(chat, paginaMetadata);
                            siguientePaginaRemota.merge(chat, paginaMetadata + 1, Math::max);
                            totalPaginasRemotas.put(chat, remota.totalPaginas());
                            bytes = archivos.descargarImagen(item.idMensaje());
                        }
                    }
                }
                MensajeCell.precalentar(item.idMensaje(),
                        Base64.getEncoder().encodeToString(bytes));
                return bytes;
            }
        };
        tarea.setOnSucceeded(e -> {
            cargasImagen.remove(item.idMensaje(), tarea);
            imagenesFallidas.remove(item.idMensaje());
            listaMensajes.refresh();
        });
        tarea.setOnFailed(e -> {
            cargasImagen.remove(item.idMensaje(), tarea);
            imagenesFallidas.add(item.idMensaje());
            LOG.fine(() -> "imagen no disponible " + item.idMensaje() + ": "
                    + FormatoErrorCliente.mensaje(tarea.getException()));
            listaMensajes.refresh();
        });
        tarea.setOnCancelled(e -> cargasImagen.remove(item.idMensaje(), tarea));
        cargasImagen.put(item.idMensaje(), tarea);
        tareasFondo.execute(tarea);
    }

    @Override
    public void reintentarImagen(MensajeLocal item) {
        if (item == null) {
            return;
        }
        imagenesFallidas.remove(item.idMensaje());
        cargarImagenVisible(item);
    }

    @Override
    public void descargarArchivo(MensajeLocal item) {
        if (item == null) {
            return;
        }
        FileChooser elegidor = new FileChooser();
        elegidor.setTitle("Guardar archivo");
        elegidor.setInitialFileName(item.nombreArchivo() == null
                ? "archivo.bin" : item.nombreArchivo());
        File destino = elegidor.showSaveDialog(listaMensajes.getScene().getWindow());
        if (destino == null) {
            return;
        }
        mostrarEstado.accept("Descargando " + item.nombreArchivo() + "…");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                byte[] bytes;
                try {
                    bytes = archivos.descargarImagen(item.idMensaje());
                } catch (universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente
                        | java.io.IOException noDisponibleLocalmente) {
                    bytes = archivos.descargarContenidoArchivo(item.archivoId());
                }
                Files.write(destino.toPath(), bytes);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> mostrarEstado.accept(
                "Archivo guardado: " + destino.getName()));
        tarea.setOnFailed(e -> mostrarEstado.accept("No se pudo descargar: "
                + FormatoErrorCliente.mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    private void cancelarCargas() {
        for (Task<?> tarea : cargasImagen.values()) {
            tarea.cancel(true);
        }
        cargasImagen.clear();
    }

    private static boolean esFaltaDeMetadatos(IllegalStateException error) {
        return error.getMessage() != null
                && error.getMessage().contains(METADATOS_PENDIENTES);
    }
}
