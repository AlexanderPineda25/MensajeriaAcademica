package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.componentes.cache.InterfazCacheUsuarios;
import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.componentes.protocolo.InterfazMapeadorDatosServidor;
import universidad.mensajeria.cliente.fachada.InterfazSincronizadorDirectorio;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Servicio enfocado en directorio y presencia (Fase 4).
 * <p>
 * Extraído de {@code FachadaCliente} por responsabilidad única: la red se
 * consulta por acciones explícitas (login, "Actualizar", arranque), nunca por
 * sondeo; el resto del tiempo la UI lee la caché. No conoce JavaFX ni la UI.
 * </p>
 */
public final class SincronizadorDirectorioCliente implements InterfazSincronizadorDirectorio {

    private final InterfazConexionRed red;
    private final InterfazCacheUsuarios cache;
    private final InterfazMapeadorDatosServidor mapeadorDatosServidor;
    private final Supplier<String> codigoActual;

    public SincronizadorDirectorioCliente(InterfazConexionRed red,
                                          InterfazCacheUsuarios cache,
                                          InterfazMapeadorDatosServidor mapeadorDatosServidor,
                                          Supplier<String> codigoActual) {
        this.red = red;
        this.cache = cache;
        this.mapeadorDatosServidor = mapeadorDatosServidor;
        this.codigoActual = codigoActual;
    }

    /** Conectados del servidor + refresco de la caché local. */
    @Override
    public List<String> listarConectados() throws ExcepcionCliente, IOException {
        exigirSesion();
        RespuestaRed respuesta = red.listarConectados();
        List<String> conectados = respuesta.usuariosConectados() != null
                ? respuesta.usuariosConectados() : List.of();
        cache.marcarConectados(conectados);
        for (String codigo : conectados) {
            asegurarEnCache(codigo);
        }
        return conectados;
    }

    /** Directorio local (offline): conectados primero. */
    @Override
    public List<UsuarioLocal> directorioLocal() throws ExcepcionCliente {
        return cache.listar();
    }

    /** Carga el directorio completo solo al entrar o bajo actualización manual. */
    @Override
    public List<UsuarioLocal> sincronizarDirectorio() throws ExcepcionCliente, IOException {
        exigirSesion();
        RespuestaRed respuesta = red.listarUsuarios();
        if (respuesta.tipo() != TipoMensajeCliente.LISTAR_USUARIOS_RESPUESTA
                || respuesta.contenido() == null || respuesta.contenido().isBlank()) {
            throw new ExcepcionSesion("directorio de usuarios no disponible");
        }
        List<UsuarioLocal> usuarios = mapeadorDatosServidor
                .mapearDirectorio(respuesta.contenido());
        boolean incluyeSesion = usuarios.stream()
                .anyMatch(usuario -> usuario.codigo().equals(codigoActual.get()));
        if (usuarios.isEmpty() || !incluyeSesion) {
            throw new ExcepcionSesion("directorio incompleto; se conserva la caché local");
        }
        cache.reemplazarDirectorio(usuarios);
        return cache.listar();
    }

    /**
     * Presencia fresca de red (bloquea hasta 15 s). Solo para login,
     * "Actualizar" manual y arranque: NUNCA en el auto-refresco.
     */
    @Override
    public List<String> refrescarPresencia() throws ExcepcionCliente, IOException {
        return listarConectados();
    }

    @Override
    public void asegurarEnCache(String codigo) throws ExcepcionCliente, IOException {
        if (cache.porCodigo(codigo).isEmpty()) {
            String ahora = LocalDateTime.now().toString();
            cache.guardar(new UsuarioLocal(codigo, codigo, "", "", false, ahora, ahora));
        }
    }

    private void exigirSesion() {
        if (codigoActual.get() == null) {
            throw new ExcepcionSesion("inicia sesion primero");
        }
    }
}
