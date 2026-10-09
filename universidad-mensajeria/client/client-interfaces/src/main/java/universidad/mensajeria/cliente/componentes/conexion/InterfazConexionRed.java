package universidad.mensajeria.cliente.componentes.conexion;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionOperacionNoSoportada;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;

import java.io.IOException;
import java.util.Base64;

/**
 * Puerto de comunicación con el servidor. Operaciones síncronas correlacionadas
 * por id; las entregas asíncronas van al {@code ReceptorMensajes}.
 * Las operaciones opcionales documentan {@link ExcepcionOperacionNoSoportada}:
 * el llamante ya trata {@link IOException}, así se conserva la sustitución (LSP).
 */
public interface InterfazConexionRed {

    void conectar() throws IOException;

    void desconectar();

    boolean conectado();

    void suscribir(ReceptorMensajes receptor);

    RespuestaRed login(String codigo, String contrasena) throws IOException;

    RespuestaRed enviarTexto(String remitente, String destinatario, String contenido) throws IOException;

    default RespuestaRed enviarTexto(String id, String remitente, String destinatario,
                                String contenido) throws IOException {
        return enviarTexto(remitente, destinatario, contenido);
    }

    RespuestaRed enviarImagen(String remitente, String destinatario, String nombreArchivo,
                         String mime, String contenidoBase64) throws IOException;

    default RespuestaRed enviarImagen(String id, String remitente, String destinatario,
                                 String nombreArchivo, String mime, String contenidoBase64)
            throws IOException {
        return enviarImagen(remitente, destinatario, nombreArchivo, mime, contenidoBase64);
    }

    /** Archivo generico (PDF, docs, zip): mismo framing que imagen, otro tipo. */
    default RespuestaRed enviarArchivo(String remitente, String destinatario, String nombreArchivo,
                                  String mime, String contenidoBase64) throws IOException {
        throw new ExcepcionOperacionNoSoportada("envio de archivos");
    }

    default RespuestaRed enviarArchivo(String id, String remitente, String destinatario,
                                  String nombreArchivo, String mime, String contenidoBase64)
            throws IOException {
        return enviarArchivo(remitente, destinatario, nombreArchivo, mime, contenidoBase64);
    }

    /**
     * Archivo generico grande por el protocolo fragmentado del servidor:
     * {@code ARCHIVO_INICIO} → N×{@code ARCHIVO_PARTE} → {@code ARCHIVO_FIN}.
     * <p>
     * Recibe bytes <em>crudos</em> porque el corte en trozos solo tiene sentido sobre el
     * archivo, no sobre su Base64. Es la unica via que supera el tope de 16 MiB por
     * trama: un Base64 de 50 MiB ocupa ~67 MiB y el codec lo rechaza.
     * </p>
     * <p>
     * La implementacion por defecto degrada al envio en trama unica para que los dobles
     * de prueba y los adaptadores sin fragmentacion sigan funcionando con archivos
     * pequenos. Un rechazo del servidor en cualquiera de las tres fases se devuelve tal
     * cual (no se sigue enviando) y lo interpreta el llamante igual que una imagen.
     * </p>
     */
    default RespuestaRed enviarArchivoFragmentado(String id, String remitente, String destinatario,
                                             String nombreArchivo, String mime, byte[] contenido)
            throws IOException {
        return enviarArchivo(id, remitente, destinatario, nombreArchivo, mime,
                Base64.getEncoder().encodeToString(contenido));
    }

    RespuestaRed difundir(String remitente, String contenido) throws IOException;

    default RespuestaRed difundir(String id, String remitente, String contenido) throws IOException {
        return difundir(remitente, contenido);
    }

    RespuestaRed listarConectados() throws IOException;

    RespuestaRed expulsar(String codigo) throws IOException;

    // FASE 6 — historial paginado y descarga bajo demanda

    RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina) throws IOException;

    RespuestaRed descargarArchivo(String archivoId) throws IOException;

    /** LOGOUT sin respuesta esperada (el servidor avisa CLOSE_NOTICE). */
    void logout() throws IOException;

    /** Acuse de lectura: el lector avisa al autor que abrió el chat. */
    default RespuestaRed enviarLectura(String lector, String autor, String idOriginal) throws IOException {
        throw new ExcepcionOperacionNoSoportada("acuse de lectura");
    }

    /** Directorio con presencia (LISTAR_USUARIOS_RESPUESTA trae JSON). */
    default RespuestaRed listarUsuarios() throws IOException {
        throw new ExcepcionOperacionNoSoportada("listar usuarios");
    }
}
