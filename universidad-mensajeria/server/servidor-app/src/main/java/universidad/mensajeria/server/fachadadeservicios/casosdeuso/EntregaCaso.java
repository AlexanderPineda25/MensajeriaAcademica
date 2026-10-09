package universidad.mensajeria.server.fachadadeservicios.casosdeuso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import universidad.mensajeria.common.interno.EventoEtapa;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.interno.ResultadoMensajeDTO;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;
import universidad.mensajeria.server.conexionesclientes.InterfazConexionesClientes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrega TCP y acuses del procesamiento (E10 hacia la red via protocolo).
 * Movido verbatim desde FachadaDeServicios para que la Fachada solo
 * coordine: entrega a sesiones vivas (+acuse al remitente), presencia y
 * respuestas ACK/IMAGEN/ERROR del camino de procesamiento.
 */
public final class EntregaCaso {

    private static final Logger LOG = LoggerFactory.getLogger(EntregaCaso.class);

    private final universidad.mensajeria.protocolo.InterfazProtocoloComunicacion protocolo;
    private final InterfazConexionesClientes conexiones;

    public EntregaCaso(universidad.mensajeria.protocolo.InterfazProtocoloComunicacion protocolo,
                       InterfazConexionesClientes conexiones) {
        this.protocolo = protocolo;
        this.conexiones = conexiones;
    }

    public void entregar(String codigo, Mensaje entrega) {
        boolean alMenosUna = false;
        for (IdSesion s : conexiones.sesionesDe(codigo)) {
            try {
                protocolo.enviar(s, entrega);
                alMenosUna = true;
            } catch (Exception e) {
                LOG.warn("No se pudo entregar a {}: {}", codigo, e.getMessage());
                conexiones.remover(codigo, s);
            }
        }
        // Acuse de entrega al remitente (aunque esté offline queda persistido).
        if (entrega.remitente() != null && entrega.id() != null) {
            Mensaje acuse = Mensaje.builder()
                    .tipo(TipoMensaje.MENSAJE_ENTREGADO)
                    .fechaHora(LocalDateTime.now().toString())
                    .remitente(codigo)
                    .destinatario(entrega.remitente())
                    .contenido(entrega.id())
                    .build();
            for (IdSesion s : conexiones.sesionesDe(entrega.remitente())) {
                try {
                    protocolo.enviar(s, acuse);
                } catch (Exception e) {
                    LOG.debug("No se pudo acusar entrega a {}: {}",
                            entrega.remitente(), e.getMessage());
                }
            }
            if (!alMenosUna) {
                LOG.debug("Entrega offline a {} (id {}): igual se acusa", codigo, entrega.id());
            }
        }
    }

    /** Broadcast de presencia a todas las sesiones vivas. */
    public void emitirPresencia(String codigo, boolean conectado) {
        List<String> todos = conexiones.codigosConectados();
        Mensaje aviso = Mensaje.builder()
                .tipo(TipoMensaje.PRESENCIA)
                .fechaHora(LocalDateTime.now().toString())
                .codigo(codigo)
                .contenido(conectado ? "conectado" : "desconectado")
                .usuariosConectados(todos)
                .build();
        for (String destino : todos) {
            for (IdSesion s : conexiones.sesionesDe(destino)) {
                try {
                    protocolo.enviar(s, aviso);
                } catch (Exception e) {
                    LOG.debug("No se pudo avisar presencia a {}: {}", destino, e.getMessage());
                }
            }
        }
    }

    public void responder(IdSesion origen, String idSolicitud, TipoMensaje tipo) {
        if (origen == null) {
            return;
        }
        try {
            protocolo.enviar(origen, Mensaje.builder()
                    .tipo(tipo)
                    .id(idSolicitud)
                    .fechaHora(LocalDateTime.now().toString())
                    .exito(true)
                    .build());
        } catch (Exception e) {
            LOG.warn("No se pudo responder a {}: {}", origen, e.getMessage());
        }
    }

    public void responderImagen(IdSesion origen, Mensaje trama, ResultadoMensajeDTO dto) {
        if (origen == null) {
            return;
        }
        try {
            protocolo.enviar(origen, Mensaje.builder()
                    .tipo(TipoMensaje.IMAGE_FILTERED_RESULT)
                    .id(trama.id())
                    .fechaHora(LocalDateTime.now().toString())
                    .exito(true)
                    .hashSha256(dto.hashSha256())
                    .etapasFiltrado(etapasJson(dto))
                    .archivoId(dto.archivoId() == null ? null : String.valueOf(dto.archivoId()))
                    .build());
        } catch (Exception e) {
            LOG.warn("No se pudo responder a {}: {}", origen, e.getMessage());
        }
    }

    public void responderError(IdSesion origen, Mensaje trama, Throwable fallo) {
        if (origen == null) {
            return;
        }
        Throwable causa = fallo instanceof java.util.concurrent.CompletionException
                && fallo.getCause() != null ? fallo.getCause() : fallo;
        String motivo = causa.getMessage() != null ? causa.getMessage() : "error interno";
        if (causa instanceof universidad.mensajeria.common.interno.UsuarioNoEncontradoException
                || causa instanceof IllegalArgumentException
                || causa instanceof IllegalStateException) {
            try {
                protocolo.enviar(origen, Mensaje.builder()
                        .tipo(TipoMensaje.ERROR)
                        .id(trama.id())
                        .fechaHora(LocalDateTime.now().toString())
                        .exito(false)
                        .mensajeError(motivo)
                        .build());
            } catch (Exception e) {
                LOG.warn("No se pudo avisar error a {}: {}", origen, e.getMessage());
            }
        } else {
            LOG.error("Error procesando {} de {}: {}", trama.tipo(), origen, motivo);
            try {
                protocolo.enviar(origen, Mensaje.builder()
                        .tipo(TipoMensaje.ERROR)
                        .id(trama.id())
                        .fechaHora(LocalDateTime.now().toString())
                        .exito(false)
                        .mensajeError("error interno procesando " + trama.tipo())
                        .build());
            } catch (Exception e) {
                LOG.warn("No se pudo avisar error a {}: {}", origen, e.getMessage());
            }
        }
    }

    /** Foto de IPs vivas del destinatario (null si offline): no es historica. */
    public String fotoIpDestinatario(String codigoDestinatario) {
        if (codigoDestinatario == null || codigoDestinatario.isBlank()) {
            return null;
        }
        String ips = conexiones.sesionesDe(codigoDestinatario.trim()).stream()
                .map(IdSesion::direccionIp)
                .filter(ip -> ip != null && !ip.isBlank())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        return ips.isBlank() ? null : ips;
    }

    public static String etapasJson(ResultadoMensajeDTO dto) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < dto.traza().size(); i++) {
            EventoEtapa etapa = dto.traza().get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"etapa\":\"").append(etapa.etapa())
                    .append("\",\"ms\":").append(etapa.ms())
                    .append(",\"ruta\":\"").append(etapa.rutaSalida()).append("\"}");
        }
        return json.append("]").toString();
    }
}
