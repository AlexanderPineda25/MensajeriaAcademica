package universidad.mensajeria.server.fachadadeservicios.casosdeuso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.interno.TipoAccion;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;
import universidad.mensajeria.server.conexionesclientes.InterfazConexionesClientes;
import universidad.mensajeria.server.fachadadeservicios.FachadaDeServicios;
import universidad.mensajeria.server.logdeeventos.InterfazLogDeEventos;
import universidad.mensajeria.server.mensajes.InterfazMensajes;
import universidad.mensajeria.usuarios.InterfazUsuariosDisponibles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abanico de difusión (BROADCAST administrativo y de usuario). Movido verbatim
 * desde FachadaDeServicios para que la Fachada solo coordine: la entrega por
 * destinatario vive en {@link EntregaCaso} y el envio por E10 en Mensajes.
 * El pool de entrega lo aporta Fabrica (un cliente lento retiene como maximo
 * un hilo de entrega, nunca el pool comun ni el de filtros).
 */
public final class DifusionCaso {

    private static final Logger LOG = LoggerFactory.getLogger(DifusionCaso.class);

    private final InterfazUsuariosDisponibles usuarios;
    private final InterfazMensajes mensajes;
    private final InterfazConexionesClientes conexiones;
    private final InterfazLogDeEventos eventos;
    private final universidad.mensajeria.protocolo.InterfazProtocoloComunicacion protocolo;
    private final Executor poolEntrega;
    private final EntregaCaso entrega;

    public DifusionCaso(InterfazUsuariosDisponibles usuarios,
                        InterfazMensajes mensajes,
                        InterfazConexionesClientes conexiones,
                        InterfazLogDeEventos eventos,
                        universidad.mensajeria.protocolo.InterfazProtocoloComunicacion protocolo,
                        Executor poolEntrega,
                        EntregaCaso entrega) {
        this.usuarios = usuarios;
        this.mensajes = mensajes;
        this.conexiones = conexiones;
        this.eventos = eventos;
        this.protocolo = protocolo;
        this.poolEntrega = poolEntrega;
        this.entrega = entrega;
    }

    public void difundir(Mensaje trama, IdSesion origen) {
        if (FachadaDeServicios.CUENTA_SISTEMA.equals(trama.remitente())
                || "ADMINISTRADOR".equals(trama.remitente())) {
            // Difusión del servidor A TODOS los registrados (no solo conectados):
            // se persiste una copia por usuario (los desconectados la reciben
            // por pendientes/SYNC_LOGIN) y se entrega en vivo a las sesiones vivas.
            List<String> destinos = usuarios.listar().stream()
                    .map(u -> u.codigo())
                    .filter(codigo -> !FachadaDeServicios.CUENTA_SISTEMA.equalsIgnoreCase(codigo))
                    .sorted()
                    .toList();
            List<CompletableFuture<?>> futuros = new ArrayList<>();
            List<String> fallidos = new ArrayList<>();
            AtomicInteger entregados = new AtomicInteger();
            int hito = Math.max(1, destinos.size() / 4);
            for (String codigo : destinos) {
                futuros.add(mensajes.procesarTexto(FachadaDeServicios.CUENTA_SISTEMA, codigo,
                                trama.contenido(), trama.ipRemitente())
                        .thenAcceptAsync(dto -> {
                            Mensaje entregaMsg = Mensaje.builder()
                                    .tipo(TipoMensaje.BROADCAST)
                                    .id(trama.id())
                                    .fechaHora(LocalDateTime.now().toString())
                                    .remitente(FachadaDeServicios.CUENTA_SISTEMA)
                                    .contenido(trama.contenido())
                                    .ipRemitente(trama.ipRemitente())
                                    .build();
                            entrega.entregar(codigo, entregaMsg);
                            int hechos = entregados.incrementAndGet();
                            if (hechos % hito == 0 || hechos == destinos.size()) {
                                eventos.registrar(TipoAccion.BROADCAST,
                                        "Difusión " + hechos + "/" + destinos.size() + " usuarios",
                                        null, trama.ipRemitente(), trama.id());
                            }
                        }, poolEntrega)
                        .exceptionally(fallo -> {
                            synchronized (fallidos) {
                                fallidos.add(codigo);
                            }
                            LOG.warn("No se pudo persistir difusion {} -> {}: {}",
                                    FachadaDeServicios.CUENTA_SISTEMA, codigo, fallo.getMessage());
                            return null;
                        }));
            }
            CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        int sesiones = conexiones.totalSesiones();
                        eventos.registrar(TipoAccion.BROADCAST,
                                "Difusion administrativa a " + destinos.size()
                                        + " usuarios (" + sesiones + " sesiones vivas"
                                        + ", fallos persistencia: " + fallidos.size() + ")",
                                null, trama.ipRemitente(), trama.id());
                        LOG.info("Difusion administrativa {} a {} usuarios (fallos: {})",
                                trama.id(), destinos.size(), fallidos.size());
                    });
            return;
        }
        List<String> destinos = conexiones.codigosConectados();
        List<CompletableFuture<?>> futuros = new ArrayList<>();
        List<String> fallidos = new ArrayList<>();
        AtomicInteger entregados = new AtomicInteger();
        int hito = Math.max(1, destinos.size() / 4);
        for (String codigo : destinos) {
            String ipDestinatario = entrega.fotoIpDestinatario(codigo);
            futuros.add(mensajes.procesarTexto(trama.remitente(), codigo,
                            trama.contenido(), trama.ipRemitente(), ipDestinatario, trama.id())
                    .thenAcceptAsync(dto -> {
                        Mensaje entregaMsg = Mensaje.builder()
                                .tipo(TipoMensaje.BROADCAST)
                                .id(trama.id())
                                .fechaHora(LocalDateTime.now().toString())
                                .remitente(trama.remitente())
                                .contenido(trama.contenido())
                                .ipRemitente(trama.ipRemitente())
                                .build();
                        for (IdSesion s : conexiones.sesionesDe(codigo)) {
                            if (origen != null && s.id().equals(origen.id())) {
                                continue;
                            }
                            try {
                                protocolo.enviar(s, entregaMsg);
                            } catch (Exception e) {
                                LOG.warn("No se pudo difundir a {}: {}", codigo, e.getMessage());
                            }
                        }
                        if (entregados.incrementAndGet() % hito == 0) {
                            eventos.registrar(TipoAccion.BROADCAST,
                                    "Broadcast " + entregados.get() + "/" + destinos.size()
                                            + " usuarios",
                                    null, trama.ipRemitente(), trama.id());
                        }
                    }, poolEntrega)
                    .exceptionally(fallo -> {
                        synchronized (fallidos) {
                            fallidos.add(codigo);
                        }
                        LOG.warn("No se pudo persistir broadcast {} -> {}: {}",
                                trama.remitente(), codigo, fallo.getMessage());
                        return null;
                    }));
        }
        CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    LOG.info("Broadcast de {} (fallos persistencia: {})",
                            trama.remitente(), fallidos.size());
                    entrega.responder(origen, trama.id(), TipoMensaje.ACK);
                });
    }
}
