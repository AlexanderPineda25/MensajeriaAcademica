package universidad.mensajeria.server.carga;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import universidad.mensajeria.common.codec.TramaCodec;
import universidad.mensajeria.common.interno.EstadoServidor;
import universidad.mensajeria.common.interno.LimitesDTO;
import universidad.mensajeria.common.interno.UsuarioDTO;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;
import universidad.mensajeria.protocolo.ConfigRed;
import universidad.mensajeria.server.almacenarinformacion.PersistenciaPort;
import universidad.mensajeria.server.inicio.Fabrica;
import universidad.mensajeria.server.inicio.SemillaUsuarios;
import universidad.mensajeria.server.transversal.config.FiltrosProperties;
import universidad.mensajeria.server.transversal.config.RedProperties;
import universidad.mensajeria.server.transversal.config.SesionProperties;
import universidad.mensajeria.server.transversal.fachada.Fachada;
import universidad.mensajeria.usuarios.InterfazUsuariosDisponibles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PERF-01: carga realista con mezcla, no solo texto.
 *
 * La base medida antes (SimuladorCargaTest) era 50 sesiones x 2 textos = 100 tramas
 * de texto y 11,8 tramas/s. Eso no dice nada del camino de imagen (5 derivados
 * generados en el servidor) ni de la difusion masiva (poolEntrega). Aqui se mide:
 *
 * <ul>
 *   <li>100 sesiones simultaneas (el tope declarado en red.limites.max-conexiones),
 *       cada una con textos e imagenes en mezcla.</li>
 *   <li>Latencia p95 del ACK por trama, no solo el promedio: el promedio oculta al
 *       usuario lento.</li>
 *   <li>Cero rechazos: si el servidor devolviera ERROR por saturacion, el ACK no
 *       llegaria y el test falla.</li>
 *   <li>Una difusion administrativa con 100 destinatarios en vuelo, que es lo que
 *       realmente ejercita poolEntrega (PERF-03).</li>
 * </ul>
 *
 * Es una medicion, no un test de correccion: los umbrales son Loose a proposito
 * (ver assertAlFinal) y lo que se fija es la ausencia de rechazo y de perdida.
 * Los numeros se leen de la salida estandar y se transcriben a ESTADO_ROADMAP.
 */
@SpringBootTest(properties = {"app.inicio=false", "app.consola=false", "app.escritorio=false",
        "app.salir=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargaMixtaTest {

    private static final int SESIONES = 100;
    private static final int TEXTOS_POR_SESION = 3;
    private static final int IMAGENES_POR_SESION = 1;
    private static final int TIMEOUT_MS = 60000;
    /** Latencia de ACK por debajo de la cual el p95 se considera sano. */
    private static final long P95_ACEPTABLE_MS = 5000;

    @Autowired
    private PersistenciaPort persistencia;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RedProperties red;
    @Autowired
    private SesionProperties sesion;
    @Autowired
    private FiltrosProperties filtros;
    @Autowired
    private ResourceLoader resources;

    private Fabrica.SistemaMontado sistema;
    private Fachada fachada;
    private int puerto;
    private String prefijo;
    private String base64Imagen;

    @TempDir
    static java.nio.file.Path baseTemporal;

    @BeforeAll
    void subirServidor() throws Exception {
        List<UsuarioDTO> semilla = SemillaUsuarios.leer(
                resources.getResource("classpath:usuarios_iniciales.csv").getInputStream(),
                passwordEncoder::encode);
        java.nio.file.Path dirUploads =
                java.nio.file.Files.createDirectories(baseTemporal.resolve("uploads"));
        java.nio.file.Path dirWork =
                java.nio.file.Files.createDirectories(baseTemporal.resolve("work"));
        sistema = Fabrica.montar(new Fabrica.Parametros(
                new ConfigRed(red.puerto(), red.pool().core(), red.pool().max(),
                        red.pool().queue(), sesion.timeoutSegundos()),
                new LimitesDTO(0, 0, 52_428_800L),
                filtros.hilos(), filtros.giroGrados(), filtros.brilloFactor(),
                filtros.reduccionFactor(),
                dirUploads,
                dirWork,
                persistencia,
                new InterfazUsuariosDisponibles.VerificadorCredenciales() {
                    @Override
                    public boolean verificar(String plano, String hash) {
                        return passwordEncoder.matches(plano, hash);
                    }

                    @Override
                    public String cifrar(String plano) {
                        return passwordEncoder.encode(plano);
                    }
                },
                semilla));
        fachada = sistema.fachada();
        fachada.iniciarServidor();
        puerto = red.puerto();
        prefijo = "M" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        for (int i = 0; i < SESIONES; i++) {
            String codigo = prefijo + i;
            UsuarioDTO creado = sistema.usuarios().registrar(codigo,
                    passwordEncoder.encode("Secreta123"), "Carga", "Mixta", "Sistemas");
            if (!sistema.almacenar().existeUsuarioPorCodigo(creado.codigo())) {
                sistema.almacenar().registrarUsuarioNuevo(creado.codigo(),
                        creado.hashContrasena(), creado.nombres(), creado.apellidos(),
                        creado.programa());
            }
        }
        base64Imagen = imagenPngEnBase64(64, 64);
    }

    @AfterAll
    void bajarServidor() {
        fachada.detenerServidor();
        sistema.apagarPoolFiltros();
    }

    private static String imagenPngEnBase64(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                imagen.setRGB(x, y, ((x * 4) << 16) | ((y * 4) << 8) | 200);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", png);
        return Base64.getEncoder().encodeToString(png.toByteArray());
    }

    @Test
    void cienSesionesConTextosEImagenesSinRechazosYConP95DeAckMedido() throws Exception {
        EstadoServidor antes = fachada.estadoServidor();
        AtomicLong tramas = new AtomicLong();
        List<Long> latenciasAck = Collections.synchronizedList(new ArrayList<>());
        List<String> errores = Collections.synchronizedList(new ArrayList<>());

        ExecutorService clientes = Executors.newFixedThreadPool(SESIONES);
        try {
            List<Callable<Void>> tareas = new ArrayList<>();
            for (int i = 0; i < SESIONES; i++) {
                final String codigo = prefijo + i;
                tareas.add(() -> {
                    try (Socket socket = new Socket("127.0.0.1", puerto)) {
                        socket.setSoTimeout(TIMEOUT_MS);
                        socket.setTcpNoDelay(true);
                        enviar(socket, Mensaje.builder().tipo(TipoMensaje.LOGIN)
                                .id("login-" + UUID.randomUUID())
                                .fechaHora(LocalDateTime.now().toString())
                                .codigo(codigo).contrasena("Secreta123").build());
                        Mensaje login = leerSalvoSync(socket);
                        if (login.tipo() != TipoMensaje.LOGIN_RESPUESTA) {
                            errores.add(codigo + ": login -> " + login.tipo());
                            return null;
                        }
                        for (int m = 0; m < TEXTOS_POR_SESION; m++) {
                            medir(socket, errores, latenciasAck, tramas, codigo, m, false);
                        }
                        for (int m = 0; m < IMAGENES_POR_SESION; m++) {
                            medir(socket, errores, latenciasAck, tramas, codigo, m, true);
                        }
                    }
                    return null;
                });
            }
            long inicio = System.nanoTime();
            for (Future<Void> f : clientes.invokeAll(tareas, 10, TimeUnit.MINUTES)) {
                f.get();
            }
            double segundos = (System.nanoTime() - inicio) / 1_000_000_000.0;
            EstadoServidor despues = fachada.estadoServidor();

            long p95 = percentil(latenciasAck, 95);
            long p50 = percentil(latenciasAck, 50);
            long maximo = Collections.max(latenciasAck);
            System.out.printf("[carga-mixta] %d sesiones, %d tramas (texto+imagen) en %.1fs "
                            + "= %.1f tramas/s; ACK p50 %d ms, p95 %d ms, max %d ms; "
                            + "pool %d/%d; procesadas +%d; en cola %d%n",
                    SESIONES, tramas.get(), segundos, tramas.get() / Math.max(segundos, 0.001),
                    p50, p95, maximo,
                    despues.trabajadoresOcupados(), despues.trabajadoresTotal(),
                    despues.mensajesProcesados() - antes.mensajesProcesados(),
                    despues.mensajesEnCola());

            assertTrue(errores.isEmpty(),
                    "el servidor rechazo tramas bajo carga: " + errores.size()
                            + " casos, p.ej. " + errores.stream().limit(3).toList());
            assertEquals(SESIONES * (TEXTOS_POR_SESION + IMAGENES_POR_SESION), tramas.get(),
                    "toda trama debe recibir ACK");
            // Umbral holgado: esto es medicion. Si un dia falla, el numero ya esta
            // registrado arriba y la decision de subir pool/hilos es de PERF-02.
            assertTrue(p95 < P95_ACEPTABLE_MS,
                    "p95 de ACK " + p95 + " ms por encima de " + P95_ACEPTABLE_MS);
        } finally {
            clientes.shutdownNow();
        }
    }

    /**
     * PERF-03: la difusion masiva con un destinatario lento. Un cliente que no lee
     * no puede tapar al resto: se mide cuanto tarda el resto en recibir, y que el
     * progreso y el resumen de fallidos cuadren con lo entregado.
     */
    @Test
    void difusionMasivaConDestinatarioLentoNoBloqueaAlResto() throws Exception {
        AtomicLong recibidos = new AtomicLong();
        List<String> errores = Collections.synchronizedList(new ArrayList<>());
        long[] instanteInicio = new long[1];
        long[] instanteUltimo = new long[1];
        // El lento es el indice 0 y nunca lee: los que deben recibir son los otros.
        final int lectoresEsperados = SESIONES - 1;

        ExecutorService lectores = Executors.newFixedThreadPool(lectoresEsperados);
        Socket lento = new Socket("127.0.0.1", puerto);
        try {
            lento.setSoTimeout(2000);
            lento.setTcpNoDelay(true);
            List<Socket> sockets = new ArrayList<>();
            List<Callable<Void>> tareas = new ArrayList<>();
            for (int i = 1; i < SESIONES; i++) {
                String codigo = prefijo + i;
                Socket socket = new Socket("127.0.0.1", puerto);
                sockets.add(socket);
                socket.setSoTimeout(TIMEOUT_MS);
                socket.setTcpNoDelay(true);
                enviar(socket, Mensaje.builder().tipo(TipoMensaje.LOGIN)
                        .id("login-" + UUID.randomUUID())
                        .fechaHora(LocalDateTime.now().toString())
                        .codigo(codigo).contrasena("Secreta123").build());
                leerSalvoSync(socket);
                tareas.add(() -> {
                    // Cada lector espera SU propio BROADCAST. La condicion de
                    // salida va con un contador global, no con el compartido: si
                    // otro lector ya sumo, este pasaria a comprobar y saldría
                    // sin haber leido nada, o al reves se quedaria bloqueado
                    // en leer() esperando un frame que ya se entrego.
                    while (!Thread.currentThread().isInterrupted()) {
                        Mensaje trama = TramaCodec.leer(socket.getInputStream());
                        if (trama.tipo() == TipoMensaje.BROADCAST) {
                            if ("aviso masivo".equals(trama.contenido())) {
                                recibidos.incrementAndGet();
                                return null;
                            }
                        } else if (trama.tipo() == TipoMensaje.CLOSE_NOTICE) {
                            errores.add(codigo + ": cierre antes del BROADCAST");
                            return null;
                        }
                    }
                    return null;
                });
            }
            // El lento entra ultimo y nunca lee: su socket se queda con el
            // frame en el buffer del kernel sin que nadie lo lea.
            enviar(lento, Mensaje.builder().tipo(TipoMensaje.LOGIN)
                    .id("login-" + UUID.randomUUID())
                    .fechaHora(LocalDateTime.now().toString())
                    .codigo(prefijo + "0").contrasena("Secreta123").build());

            // Los lectores se lanzan antes de difundir y quedan bloqueados en
            // TramaCodec.leer esperando su BROADCAST. Ojo: invokeAll() ESPERA a
            // que terminen, y aqui terminaran justo DESPUES de la difusion que
            // viene a continuacion: seria un deadlock. Por eso submit() uno a
            // uno guardando el Future, en vez de invokeAll().
            List<Future<Void>> futuros = new ArrayList<>();
            for (Callable<Void> tarea : tareas) {
                futuros.add(lectores.submit(tarea));
            }
            instanteInicio[0] = System.nanoTime();
            fachada.difundirAdministrativo("aviso masivo");
            long tope = System.currentTimeMillis() + 60000;
            while (recibidos.get() < lectoresEsperados && System.currentTimeMillis() < tope) {
                Thread.sleep(20);
            }
            instanteUltimo[0] = System.nanoTime();
            // Los lectores que ya recibieron su BROADCAST estan listos; los que
            // no, se cancelan en vez de esperar: el timeout debe fallar la
            // asercion de cobertura, no colgar el test.
            for (Future<Void> f : futuros) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException todavia) {
                    f.cancel(true);
                }
            }
            for (Socket socket : sockets) {
                socket.close();
            }
            double segundos = (instanteUltimo[0] - instanteInicio[0]) / 1_000_000_000.0;
            System.out.printf("[difusion] %d de %d deliveries en %.2fs con 1 cliente lento "
                            + "y su socket sin leer%n",
                    recibidos.get(), lectoresEsperados, segundos);

            assertTrue(errores.isEmpty(), "errores: " + errores);
            assertEquals(lectoresEsperados, recibidos.get(),
                    "el cliente lento no debe impedir la entrega al resto");
            assertTrue(segundos < 30, "el resto recibio en " + segundos + " s: demasiado lento");
        } finally {
            lento.close();
            lectores.shutdownNow();
        }
    }

    /** Envia una trama (texto o imagen) y cronometra hasta su ACK. */
    private void medir(Socket socket, List<String> errores, List<Long> latencias,
                       AtomicLong tramas, String codigo, int indice, boolean imagen)
            throws Exception {
        long t0 = System.nanoTime();
        if (imagen) {
            enviar(socket, Mensaje.builder().tipo(TipoMensaje.MENSAJE_IMAGEN)
                    .id("img-" + UUID.randomUUID())
                    .fechaHora(LocalDateTime.now().toString())
                    .remitente(codigo).destinatario(prefijo + "0")
                    .nombreArchivo("carga.png").mime("image/png")
                    .contenidoImagen(base64Imagen).build());
        } else {
            enviar(socket, Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO)
                    .id("txt-" + UUID.randomUUID())
                    .fechaHora(LocalDateTime.now().toString())
                    .remitente(codigo).destinatario(prefijo + "0")
                    .contenido("carga mixta " + indice + " de " + codigo).build());
        }
        Mensaje ack = leerHastaConfirmacion(socket);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (ack.tipo() == TipoMensaje.ACK
                || ack.tipo() == TipoMensaje.IMAGE_FILTERED_RESULT) {
            latencias.add(ms);
            tramas.incrementAndGet();
        } else {
            errores.add(codigo + " trama " + indice + (imagen ? " imagen" : " texto")
                    + " -> " + ack.tipo() + " (" + ack.mensajeError() + ")");
        }
    }

    private static long percentil(List<Long> valores, int percentil) {
        if (valores.isEmpty()) {
            return 0;
        }
        long[] copia = valores.stream().mapToLong(Long::longValue).sorted().toArray();
        int indice = (int) Math.ceil(percentil / 100.0 * copia.length) - 1;
        return copia[Math.max(0, Math.min(copia.length - 1, indice))];
    }

    private static void enviar(Socket socket, Mensaje mensaje) throws Exception {
        TramaCodec.escribir(socket.getOutputStream(), mensaje);
    }

    private static Mensaje leerSalvoSync(Socket socket) throws Exception {
        while (true) {
            Mensaje trama = TramaCodec.leer(socket.getInputStream());
            if (trama.tipo() != TipoMensaje.SYNC_LOGIN
                    && trama.tipo() != TipoMensaje.PRESENCIA
                    && trama.tipo() != TipoMensaje.MENSAJE_ENTREGADO
                    && trama.tipo() != TipoMensaje.MENSAJE_LEIDO) {
                return trama;
            }
        }
    }

    /**
     * Espera la confirmacion de la trama enviada. No es solo ACK: el servidor
     * confirma una imagen con IMAGE_FILTERED_RESULT (lleva hash, etapas y
     * archivoId), no con un ACK generico. Ignorar ese tipo hace que la prueba
     * expire esperando un ACK que nunca llega.
     */
    private static Mensaje leerHastaConfirmacion(Socket socket) throws Exception {
        while (true) {
            Mensaje trama = TramaCodec.leer(socket.getInputStream());
            if (trama.tipo() == TipoMensaje.ACK
                    || trama.tipo() == TipoMensaje.IMAGE_FILTERED_RESULT
                    || trama.tipo() == TipoMensaje.ERROR) {
                return trama;
            }
        }
    }
}
