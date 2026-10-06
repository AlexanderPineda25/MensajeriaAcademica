package universidad.mensajeria.cliente.componentes.conexion;

import universidad.mensajeria.cliente.transversal.records.RespuestaRed;

import java.io.IOException;

/**
 * Puerto de comunicación con el servidor. Operaciones síncronas correlacionadas
 * por id; las entregas asíncronas van al {@code ReceptorMensajes}.
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
        throw new IOException("envio de archivos no soportado");
    }

    default RespuestaRed enviarArchivo(String id, String remitente, String destinatario,
                                  String nombreArchivo, String mime, String contenidoBase64)
            throws IOException {
        return enviarArchivo(remitente, destinatario, nombreArchivo, mime, contenidoBase64);
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
        throw new IOException("lectura no soportada");
    }

    /** Directorio con presencia (LISTAR_USUARIOS_RESPUESTA trae JSON). */
    default RespuestaRed listarUsuarios() throws IOException {
        throw new IOException("listar usuarios no soportado");
    }
}
