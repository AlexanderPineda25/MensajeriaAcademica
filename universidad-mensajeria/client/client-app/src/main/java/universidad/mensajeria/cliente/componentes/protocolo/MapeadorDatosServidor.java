package universidad.mensajeria.cliente.componentes.protocolo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;
import universidad.mensajeria.common.tipos.TipoMensaje;

/** Adaptador de formato: traduce payloads del protocolo al modelo interno del cliente. */
public final class MapeadorDatosServidor implements InterfazMapeadorDatosServidor {

    @Override
    public List<UsuarioLocal> mapearDirectorio(String json) {
        JsonArray filas = analizarArray(json, "directorio de usuarios ilegible");
        String ahora = LocalDateTime.now().toString();
        List<UsuarioLocal> usuarios = new ArrayList<>();
        for (JsonElement fila : filas) {
            if (!fila.isJsonObject()) {
                continue;
            }
            JsonObject usuario = fila.getAsJsonObject();
            String codigo = texto(usuario, "codigo");
            if (codigo == null || codigo.isBlank()) {
                continue;
            }
            usuarios.add(new UsuarioLocal(codigo,
                    valorONada(texto(usuario, "nombres")),
                    valorONada(texto(usuario, "apellidos")),
                    valorONada(texto(usuario, "programa")),
                    booleano(usuario, "conectado"), ahora, ahora));
        }
        return List.copyOf(usuarios);
    }

    @Override
    public List<MensajeLocal> mapearPaginaHistorial(String json, String codigoSesion) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonArray filas = analizarArray(json, "pagina remota ilegible");
        List<MensajeLocal> mensajes = new ArrayList<>();
        for (JsonElement fila : filas) {
            JsonObject mensaje = fila.getAsJsonObject();
            String id = texto(mensaje, "id");
            if (id == null) {
                continue;
            }
            String remitente = texto(mensaje, "remitente");
            String fecha = texto(mensaje, "fechaEnvio");
            mensajes.add(new MensajeLocal(id, remitente,
                    texto(mensaje, "destinatario"),
                    normalizarTipoMensaje(texto(mensaje, "tipo")),
                    texto(mensaje, "contenido"), texto(mensaje, "hashSha256"),
                    entero(mensaje, "numCaracteres"), entero(mensaje, "numPalabras"),
                    texto(mensaje, "nombreArchivo"), null, largo(mensaje, "tamanoArchivo"),
                    fecha != null ? fecha : LocalDateTime.now().toString(),
                    Objects.equals(codigoSesion, remitente), false,
                    EstadoMensaje.ENTREGADO.name(), texto(mensaje, "archivoId"),
                    texto(mensaje, "etapasFiltrado")));
        }
        return List.copyOf(mensajes);
    }

    private static JsonArray analizarArray(String json, String mensajeError) {
        try {
            return JsonParser.parseString(json).getAsJsonArray();
        } catch (RuntimeException error) {
            throw new universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion(
                    mensajeError, error);
        }
    }

    private static String texto(JsonObject objeto, String campo) {
        return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                ? objeto.get(campo).getAsString() : null;
    }

    @Override
    public String normalizarTipoMensaje(String tipo) {
        if (tipo == null) {
            return TipoMensaje.SYNC_LOGIN.name();
        }
        return switch (tipo.toUpperCase(Locale.ROOT)) {
            case "TEXTO", "MENSAJE_TEXTO" -> TipoMensaje.MENSAJE_TEXTO.name();
            case "IMAGEN", "MENSAJE_IMAGEN" -> TipoMensaje.MENSAJE_IMAGEN.name();
            case "ARCHIVO", "MENSAJE_ARCHIVO" -> TipoMensaje.MENSAJE_ARCHIVO.name();
            default -> tipo;
        };
    }

    private static String valorONada(String valor) {
        return valor == null ? "" : valor;
    }

    private static boolean booleano(JsonObject objeto, String campo) {
        try {
            return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                    && objeto.get(campo).getAsBoolean();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static Integer entero(JsonObject objeto, String campo) {
        try {
            return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                    ? objeto.get(campo).getAsInt() : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Long largo(JsonObject objeto, String campo) {
        try {
            return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                    ? objeto.get(campo).getAsLong() : null;
        } catch (RuntimeException error) {
            return null;
        }
    }
}
