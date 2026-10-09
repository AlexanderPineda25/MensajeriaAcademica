package universidad.mensajeria.cliente.fachada.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import universidad.mensajeria.cliente.RespuestaRedPrueba;
import universidad.mensajeria.cliente.componentes.cache.CacheUsuarios;
import universidad.mensajeria.cliente.componentes.protocolo.MapeadorDatosServidor;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.fachada.RedParaPruebas;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionSesion;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

/**
 * El directorio y la presencia viven en su propio servicio (SRP):
 * la red solo se consulta por acción explícita y la caché queda al día.
 */
class SincronizadorDirectorioTest {

    private CacheUsuarios cache;
    private RedParaPruebas red;
    private String sesion = "A001";

    @BeforeEach
    void montar() throws Exception {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:h2:mem:sincroniza"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(props));
        store.inicializar();
        cache = new CacheUsuarios(store);
        red = new RedParaPruebas();
    }

    @Test
    void listarConectadosMarcaLaCacheYDevuelveLaLista() throws Exception {
        red.conectados(List.of("A001", "B002"));

        List<String> conectados = servicio().listarConectados();

        assertEquals(List.of("A001", "B002"), conectados);
        assertTrue(cache.porCodigo("B002").isPresent());
    }

    @Test
    void sincronizarDirectorioCargaIncluyendoLaSesion() throws Exception {
        red.respuestaUsuarios(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.LISTAR_USUARIOS_RESPUESTA).exito(true).contenido("""
                        [{"codigo":"A001","nombres":"Ana","apellidos":"Pérez","programa":"Sistemas","conectado":true},
                         {"codigo":"B002","nombres":"Luis","apellidos":"Díaz","programa":"Matemáticas","conectado":false}]
                        """).build()));

        var usuarios = servicio().sincronizarDirectorio();

        assertEquals(2, usuarios.size());
        assertEquals(2, servicio().directorioLocal().size());
    }

    @Test
    void sinSesionRechazaLaSincronizacion() {
        sesion = null;

        assertThrows(ExcepcionSesion.class, () -> servicio().sincronizarDirectorio());
    }

    @Test
    void directorioSinLaSesionSeDescarta() {
        sesion = "Z999";
        red.respuestaUsuarios(RespuestaRedPrueba.desde(Mensaje.builder()
                .tipo(TipoMensaje.LISTAR_USUARIOS_RESPUESTA).exito(true).contenido("""
                        [{"codigo":"A001","nombres":"Ana","apellidos":"Pérez","programa":"Sistemas","conectado":true}]
                        """).build()));

        assertThrows(ExcepcionSesion.class, () -> servicio().sincronizarDirectorio());
    }

    private SincronizadorDirectorioCliente servicio() {
        return new SincronizadorDirectorioCliente(red, cache,
                new MapeadorDatosServidor(), () -> sesion);
    }
}
