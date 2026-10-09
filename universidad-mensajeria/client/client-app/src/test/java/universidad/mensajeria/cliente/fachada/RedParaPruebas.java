package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.conexion.ReceptorMensajes;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

import java.io.IOException;
import java.util.List;

/**
 * Doble de red para pruebas de servicios de fachada: solo responde lo que cada
 * prueba configura; lo demás falla explícito en vez de devolver null.
 */
public final class RedParaPruebas implements InterfazConexionRed {

    private boolean conectada = true;
    private List<String> conectados = List.of();
    private RespuestaRed respuestaUsuarios;
    private String paginaJson = "[]";
    private int totalPaginas = 1;
    private boolean fallaDescarga;

    public void conectada(boolean valor) {
        conectada = valor;
    }

    public void conectados(List<String> codigos) {
        conectados = codigos;
    }

    public void respuestaUsuarios(RespuestaRed respuesta) {
        respuestaUsuarios = respuesta;
    }

    public void paginaHistorial(String json, int total) {
        paginaJson = json;
        totalPaginas = total;
    }

    public void fallaDescarga(boolean valor) {
        fallaDescarga = valor;
    }

    @Override
    public void conectar() {
        conectada = true;
    }

    @Override
    public void desconectar() {
        conectada = false;
    }

    @Override
    public boolean conectado() {
        return conectada;
    }

    @Override
    public void suscribir(ReceptorMensajes receptor) {
    }

    @Override
    public RespuestaRed login(String codigo, String contrasena) {
        throw new UnsupportedOperationException("fuera de alcance");
    }

    @Override
    public RespuestaRed enviarTexto(String remitente, String destinatario, String contenido) {
        throw new UnsupportedOperationException("fuera de alcance");
    }

    @Override
    public RespuestaRed enviarImagen(String remitente, String destinatario, String nombreArchivo,
                                     String mime, String contenidoBase64) {
        throw new UnsupportedOperationException("fuera de alcance");
    }

    @Override
    public RespuestaRed difundir(String remitente, String contenido) {
        throw new UnsupportedOperationException("fuera de alcance");
    }

    @Override
    public RespuestaRed listarConectados() {
        return new RespuestaRed(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                Boolean.TRUE, null, conectados, null);
    }

    @Override
    public RespuestaRed expulsar(String codigo) {
        throw new UnsupportedOperationException("fuera de alcance");
    }

    @Override
    public RespuestaRed listarUsuarios() {
        if (respuestaUsuarios != null) {
            return respuestaUsuarios;
        }
        throw new UnsupportedOperationException("sin respuesta configurada");
    }

    @Override
    public RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina) {
        return RespuestaRedPrueba.desde(Mensaje.builder().tipo(TipoMensaje.HISTORIAL_PAGE)
                .pagina(pagina).totalPaginas(totalPaginas).exito(true)
                .contenido(paginaJson).build());
    }

    @Override
    public RespuestaRed descargarArchivo(String archivoId) throws IOException {
        if (fallaDescarga) {
            throw new IOException("descarga interrumpida");
        }
        return RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.DESCARGAR_ARCHIVO_RESPUESTA).archivoId(archivoId)
                .exito(true).contenidoImagen("AAAA").build());
    }

    @Override
    public void logout() {
    }
}
