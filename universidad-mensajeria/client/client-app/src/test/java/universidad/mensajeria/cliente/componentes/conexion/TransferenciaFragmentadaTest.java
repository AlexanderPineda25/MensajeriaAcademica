package universidad.mensajeria.cliente.componentes.conexion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import universidad.mensajeria.common.codec.TramaCodec;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protocolo de transferencia fragmentada de archivos ({@code AGENTS.md} §5 invariante 2).
 *
 * <p>Cubre el motivo por el que existe: {@code TramaCodec} rechaza toda trama de más de
 * 16 MiB y el Base64 infiere ×4/3, así que en trama única un archivo de 13 MiB ya no cabe.
 * El stub usa el {@link TramaCodec} real, de modo que si la fragmentación desapareciera
 * esta prueba fallaría por la razón correcta.
 */
class TransferenciaFragmentadaTest {

    /** 13 MiB crudos: en Base64 son ~17,3 MiB, por encima del tope de trama. */
    private static final int TAMANO_QUE_NO_CABE_EN_UNA_TRAMA = 13 * 1024 * 1024;

    private ServerSocket escucha;
    private ServidorTransferencia stub;
    private ConexionCliente conexion;

    @BeforeEach
    void montar() throws IOException {
        escucha = new ServerSocket(0);
        stub = new ServidorTransferencia(escucha);
        stub.start();
        conexion = new ConexionCliente("127.0.0.1", escucha.getLocalPort());
        conexion.conectar();
    }

    @AfterEach
    void bajar() {
        conexion.desconectar();
        stub.cerrar();
    }

    @Test
    @DisplayName("archivo de 13 MiB viaja fragmentado y se reensambla idéntico")
    void archivoGrandeSeFragmenta() throws Exception {
        byte[] original = new byte[TAMANO_QUE_NO_CABE_EN_UNA_TRAMA];
        new java.util.Random(7).nextBytes(original);

        RespuestaRed ack = conexion.enviarArchivoFragmentado(
                "m-grande", "A001", "B002", "video.mp4", "video/mp4", original);

        assertTrue(Boolean.TRUE.equals(ack.exito()), "el servidor debe aceptar la transferencia");

        List<Mensaje> tramas = esperarTramas(3);
        assertEquals(TipoMensaje.ARCHIVO_INICIO, tramas.get(0).tipo());
        assertEquals("video.mp4", tramas.get(0).nombreArchivo());
        assertEquals((long) original.length, tramas.get(0).tamanoArchivo());
        int esperadoPartes = (original.length + (1024 * 1024) - 1) / (1024 * 1024);
        assertEquals(esperadoPartes, tramas.get(0).totalPartes());

        for (int i = 0; i < esperadoPartes; i++) {
            Mensaje parte = tramas.get(1 + i);
            assertEquals(TipoMensaje.ARCHIVO_PARTE, parte.tipo());
            assertEquals(i, parte.indiceParte());
            assertEquals("m-grande", parte.archivoId(), "la transferencia se identifica por id");
            assertEquals("A001", parte.remitente());
        }

        Mensaje fin = tramas.get(tramas.size() - 1);
        assertEquals(TipoMensaje.ARCHIVO_FIN, fin.tipo());
        assertEquals("m-grande", fin.archivoId());
        assertEquals(sha256(original), fin.hashSha256(),
                "el servidor verifica el SHA-256 del archivo completo");

        assertArrayEquals(original, reensamblar(tramas, esperadoPartes),
                "los bytes reensamblados deben ser los del archivo original");
    }

    @Test
    @DisplayName("ninguna trama del envío fragmentado supera el tope del codec")
    void ningunaParteSuperaElTopeDeTrama() throws Exception {
        byte[] original = new byte[TAMANO_QUE_NO_CABE_EN_UNA_TRAMA];

        conexion.enviarArchivoFragmentado("m-top", "A001", "B002", "datos.bin",
                "application/octet-stream", original);

        for (Mensaje trama : esperarTramas(15)) {
            int bytes = String.valueOf(trama.contenidoImagen()).length();
            assertTrue(bytes < TramaCodec.TAMANO_MAXIMO_TRAMA,
                    "una ARCHIVO_PARTE debe quedar holgadamente bajo el tope de trama");
        }
    }

    @Test
    @DisplayName("el archivo de 13 MiB NO entraría en una sola trama (por qué existe el fragmentado)")
    void elArchivoNoCabeEnTramaUnica() throws Exception {
        byte[] original = new byte[TAMANO_QUE_NO_CABE_EN_UNA_TRAMA];
        String base64 = Base64.getEncoder().encodeToString(original);

        assertTrue(base64.length() > TramaCodec.TAMANO_MAXIMO_TRAMA,
                "el Base64 de este archivo excedería el tope; la fragmentación es lo que lo salva");
    }

    @Test
    @DisplayName("un rechazo en ARCHIVO_INICIO abandona sin mandar ninguna parte")
    void rechazoEnInicioAbandona() throws Exception {
        stub.rechazarEnTipo = TipoMensaje.ARCHIVO_INICIO;
        byte[] original = new byte[3 * 1024 * 1024];

        RespuestaRed respuesta = conexion.enviarArchivoFragmentado(
                "m-rechazo", "A001", "B002", "roto.zip", "application/zip", original);

        assertFalse(Boolean.TRUE.equals(respuesta.exito()));
        assertEquals("destinatario invalido", respuesta.mensajeError());

        List<Mensaje> tramas = esperarTramas(1);
        assertEquals(1, tramas.size(), "no debe enviarse ninguna parte tras el rechazo");
        assertEquals(TipoMensaje.ARCHIVO_INICIO, tramas.get(0).tipo());
    }

    @Test
    @DisplayName("un rechazo a mitad de envío se propaga y abandona")
    void rechazoEnParteAbandona() throws Exception {
        stub.rechazarEnParte = 2;
        byte[] original = new byte[4 * 1024 * 1024];

        RespuestaRed respuesta = conexion.enviarArchivoFragmentado(
                "m-medio", "A001", "B002", "corto.zip", "application/zip", original);

        assertFalse(Boolean.TRUE.equals(respuesta.exito()));
        assertTrue(respuesta.mensajeError().contains("2 rechazada"), respuesta.mensajeError());

        List<Mensaje> tramas = esperarTramas(3);
        assertEquals(TipoMensaje.ARCHIVO_PARTE, tramas.get(2).tipo());
        assertEquals(1, tramas.get(2).indiceParte());
        assertFalse(stub.recibioFin(), "no debe enviarse ARCHIVO_FIN tras un rechazo");
    }

    @Test
    @DisplayName("archivo vacío se rechaza antes de tocar la red")
    void archivoVacioSeRechaza() {
        assertThrows(IOException.class, () -> conexion.enviarArchivoFragmentado(
                "m-vacio", "A001", "B002", "vacio.txt", "text/plain", new byte[0]));
        assertEquals(0, stub.recibidas.size());
    }

    @Test
    @DisplayName("el puerto degrada a trama única: los dobles de prueba siguen funcionando")
    void elPuertoDegradaATramaUnica() throws Exception {
        byte[] original = "contenido pequeno".getBytes("UTF-8");
        AtomicInteger llamadas = new AtomicInteger();
        StringBuilder recibido = new StringBuilder();

        InterfazConexionRed doble = new InterfazConexionRed() {
            @Override
            public void conectar() {
            }

            @Override
            public void desconectar() {
            }

            @Override
            public boolean conectado() {
                return true;
            }

            @Override
            public void suscribir(ReceptorMensajes receptor) {
            }

            @Override
            public RespuestaRed login(String codigo, String contrasena) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed enviarTexto(String remitente, String destinatario, String contenido) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed enviarImagen(String remitente, String destinatario,
                                             String nombreArchivo, String mime, String base64) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed enviarArchivo(String id, String remitente, String destinatario,
                                               String nombreArchivo, String mime, String base64) {
                llamadas.incrementAndGet();
                recibido.append(new String(Base64.getDecoder().decode(base64),
                        java.nio.charset.StandardCharsets.UTF_8));
                return null;
            }

            @Override
            public RespuestaRed difundir(String remitente, String contenido) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed listarConectados() {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed expulsar(String codigo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed solicitarHistorial(String remitente, String destinatario, int pagina) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RespuestaRed descargarArchivo(String archivoId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() {
            }
        };

        doble.enviarArchivoFragmentado("m-1", "A001", "B002", "nota.txt", "text/plain", original);

        assertEquals(1, llamadas.get(), "debe delegar al envío en trama única");
        assertEquals("contenido pequeno", recibido.toString());
    }

    private List<Mensaje> esperarTramas(int alMenos) throws InterruptedException {
        for (int intento = 0; intento < 100 && stub.recibidas.size() < alMenos; intento++) {
            Thread.sleep(20);
        }
        return new ArrayList<>(stub.recibidas);
    }

    private static byte[] reensamblar(List<Mensaje> tramas, int partes) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (int i = 0; i < partes; i++) {
            salida.writeBytes(Base64.getDecoder().decode(tramas.get(1 + i).contenidoImagen()));
        }
        return salida.toByteArray();
    }

    private static String sha256(byte[] datos) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
    }

    /**
     * Servidor que acepta las tres fases de la transferencia, opcionalmente rechaza en un
     * punto dado, y guarda todo lo recibido para que el test verifique las tramas.
     */
    private static final class ServidorTransferencia extends Thread {

        private final ServerSocket escucha;
        private final List<Mensaje> recibidas = new CopyOnWriteArrayList<>();
        private volatile TipoMensaje rechazarEnTipo;
        private volatile int rechazarEnParte = -1;
        private volatile boolean finRecibido;
        private Socket cliente;

        private ServidorTransferencia(ServerSocket escucha) {
            this.escucha = escucha;
            setDaemon(true);
        }

        private List<Mensaje> recibidas() {
            return recibidas;
        }

        private boolean recibioFin() {
            return finRecibido;
        }

        private void cerrar() {
            try {
                if (cliente != null) {
                    cliente.close();
                }
                escucha.close();
            } catch (IOException ignorada) {
                // cierre de test
            }
        }

        @Override
        public void run() {
            try {
                cliente = escucha.accept();
                while (!cliente.isClosed()) {
                    Mensaje solicitud = TramaCodec.leer(cliente.getInputStream());
                    recibidas.add(solicitud);
                    Mensaje respuesta = responder(solicitud);
                    if (respuesta != null) {
                        TramaCodec.escribir(cliente.getOutputStream(), respuesta);
                    }
                }
            } catch (Exception fin) {
                // fin del test
            }
        }

        private Mensaje responder(Mensaje solicitud) {
            if (solicitud.tipo() == TipoMensaje.ARCHIVO_FIN) {
                finRecibido = true;
                return Mensaje.builder().tipo(TipoMensaje.ACK).id(solicitud.id())
                        .archivoId("archivo-real-1").exito(true).build();
            }
            if (solicitud.tipo() == rechazarEnTipo) {
                return Mensaje.builder().tipo(TipoMensaje.ERROR).id(solicitud.id())
                        .exito(false).mensajeError("destinatario invalido").build();
            }
            if (solicitud.tipo() == TipoMensaje.ARCHIVO_PARTE
                    && solicitud.indiceParte() != null
                    && solicitud.indiceParte() + 1 == rechazarEnParte) {
                // `totalPartes` solo viaja en ARCHIVO_INICIO: aquí no está.
                return Mensaje.builder().tipo(TipoMensaje.ERROR).id(solicitud.id())
                        .exito(false).mensajeError("parte " + rechazarEnParte + " rechazada").build();
            }
            return Mensaje.builder().tipo(TipoMensaje.ACK).id(solicitud.id()).exito(true).build();
        }
    }

    @Test
    @DisplayName("1 MiB exacto cabe en una sola parte: el corte es 1 MiB como en Python y .NET")
    void elCorteDeParteEsUnMib() throws Exception {
        byte[] exacto = new byte[1024 * 1024];
        stub.recibidas.clear();

        conexion.enviarArchivoFragmentado("m-1mib", "A001", "B002", "x.bin",
                "application/octet-stream", exacto);

        List<Mensaje> tramas = esperarTramas(3);
        assertEquals(3, tramas.size(), "INICIO + 1 PARTE + FIN");
        assertEquals(1, tramas.get(0).totalPartes());
        assertArrayEquals(exacto, reensamblar(tramas, 1));
    }
}