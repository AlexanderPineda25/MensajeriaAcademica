package universidad.mensajeria.server.mensajes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import universidad.mensajeria.common.interno.ArchivoDTO;
import universidad.mensajeria.common.interno.ArchivoGuardarDTO;
import universidad.mensajeria.common.interno.LimitesDTO;
import universidad.mensajeria.common.interno.MensajeGuardarDTO;
import universidad.mensajeria.common.interno.MensajeResumenDTO;
import universidad.mensajeria.common.interno.ResultadoMensajeDTO;
import universidad.mensajeria.common.interno.UsuarioDTO;
import universidad.mensajeria.server.almacenarinformacion.AlmacenarInformacion;
import universidad.mensajeria.server.almacenarinformacion.InterfazAlmacenarInformacion;
import universidad.mensajeria.server.almacenarinformacion.PersistenciaPort;
import universidad.mensajeria.utilerias.TuberiaFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El texto tambien queda en uploads/ (subcarpeta "textos" por remitente) y los
 * archivos genericos en carpeta por remitente (sin subcarpeta).
 */
class ProcesarTextoArchivoUploadsTest {

    @TempDir
    Path base;

    private static UsuarioDTO usuario(String codigo) {
        return new UsuarioDTO(codigo, "Ana", "Lopez", "Sistemas", "hash", true);
    }

    private InterfazAlmacenarInformacion almacenar(Path work, Path uploads,
                                                   PersistenciaPort persistencia) {
        return new AlmacenarInformacion(persistencia, work, uploads);
    }

    private PersistenciaPort persistencia() {
        PersistenciaPort persistencia = mock(PersistenciaPort.class);
        when(persistencia.buscarUsuarioPorCodigo("A001")).thenReturn(Optional.of(usuario("A001")));
        when(persistencia.buscarUsuarioPorCodigo("B002")).thenReturn(Optional.of(usuario("B002")));
        when(persistencia.leerLimites()).thenReturn(new LimitesDTO(100, 3, 52_428_800L));
        when(persistencia.guardarMensaje(any(MensajeGuardarDTO.class)))
                .thenAnswer(inv -> {
                    MensajeGuardarDTO dto = inv.getArgument(0);
                    return new MensajeResumenDTO(7L, dto.remitente(), dto.destinatario(),
                            "TEXTO", dto.contenido(), dto.hashSha256(),
                            dto.numCaracteres(), dto.numPalabras(), null, null, null, null,
                            dto.etapasFiltrado(), "2026-10-02T00:00:00");
                });
        when(persistencia.guardarArchivo(any(ArchivoGuardarDTO.class)))
                .thenAnswer(inv -> {
                    ArchivoGuardarDTO dto = inv.getArgument(0);
                    return new ArchivoDTO(11L, dto.nombre(), dto.ruta(), dto.tamano(),
                            dto.tipo(), dto.mime(), dto.hashSha256());
                });
        return persistencia;
    }

    private InterfazMensajes mensajes(InterfazAlmacenarInformacion almacenar) {
        return new Mensajes(almacenar, new TuberiaFactory(), Runnable::run, etapa -> {
        });
    }

    @Test
    void textoQuedaEnSubcarpetaTextosPorRemitente() throws Exception {
        Path work = Files.createDirectories(base.resolve("work"));
        Path uploads = Files.createDirectories(base.resolve("uploads"));
        InterfazMensajes mensajes = mensajes(almacenar(work, uploads, persistencia()));

        ResultadoMensajeDTO dto = mensajes
                .procesarTexto("A001", "B002", "hola mundo", "127.0.0.1").get();

        assertEquals(1, dto.rutasDerivadas().size());
        assertTrue(dto.rutasDerivadas().get(0).startsWith("Ana Lopez [A001]/textos/"),
                dto.rutasDerivadas().toString());
        assertTrue(dto.rutasDerivadas().get(0).endsWith("00_original.txt"));
        assertTrue(Files.isRegularFile(uploads.resolve(dto.rutasDerivadas().get(0))));
        assertEquals("hola mundo", Files.readString(
                uploads.resolve(dto.rutasDerivadas().get(0)), StandardCharsets.UTF_8));
    }

    /**
     * Difusion administrativa: el MISMO texto desde el MISMO remitente (SERVIDOR)
     * se procesa en paralelo para N destinatarios. Los N comparten hash y por tanto
     * la misma ruta final en uploads/. Sin serializar esa promocion, los Files.move
     * chocan en Windows y las copias perdedoras se pierden (fallo real visto en
     * MensajeriaE2ETest con 11 de 65).
     */
    @Test
    void difusionDelMismoTextoEnParaleloNoPierdeCopias() throws Exception {
        Path work = Files.createDirectories(base.resolve("w3"));
        Path uploads = Files.createDirectories(base.resolve("u3"));
        int destinatarios = 24;
        PersistenciaPort persistencia = persistencia();
        when(persistencia.buscarUsuarioPorCodigo("SERVIDOR"))
                .thenReturn(Optional.of(new UsuarioDTO("SERVIDOR", "Sistema", "Mensajeria",
                        "Ninguno", "hash", true)));
        // poolFiltros real: sin paralelismo el choque de promotion no se reproduce.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            InterfazMensajes mensajes = new Mensajes(almacenar(work, uploads, persistencia),
                    new TuberiaFactory(), pool, etapa -> {
                    });

            List<java.util.concurrent.CompletableFuture<ResultadoMensajeDTO>> futuros =
                    new java.util.ArrayList<>();
            for (int i = 0; i < destinatarios; i++) {
                futuros.add(mensajes.procesarTexto("SERVIDOR", "B002",
                        "Mantenimiento programado", "127.0.0.1"));
            }
            List<ResultadoMensajeDTO> resultados = new java.util.ArrayList<>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }

            // Nadie fallo: las 24 copias se persistieron en la BD.
            verify(persistencia, times(destinatarios)).guardarMensaje(any(MensajeGuardarDTO.class));
            // Y el original unico quedo en disco con el contenido correcto.
            String ruta = resultados.get(0).rutasDerivadas().get(0);
            for (ResultadoMensajeDTO dto : resultados) {
                assertEquals(ruta, dto.rutasDerivadas().get(0),
                        "todas las copias comparten hash y ruta");
            }
            assertEquals("Mantenimiento programado",
                    Files.readString(uploads.resolve(ruta), StandardCharsets.UTF_8));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void archivoGenericoQuedaEnCarpetaPorRemitente() throws Exception {
        Path work = Files.createDirectories(base.resolve("w2"));
        Path uploads = Files.createDirectories(base.resolve("u2"));
        InterfazMensajes mensajes = mensajes(almacenar(work, uploads, persistencia()));

        ResultadoMensajeDTO dto = mensajes.procesarArchivo("A001", "B002",
                new byte[]{1, 2, 3}, "doc.pdf", "application/pdf", "127.0.0.1").get();

        assertEquals(1, dto.rutasDerivadas().size());
        assertTrue(dto.rutasDerivadas().get(0).startsWith("Ana Lopez [A001]/"),
                dto.rutasDerivadas().toString());
        assertTrue(Files.isRegularFile(uploads.resolve(dto.rutasDerivadas().get(0))));
    }
}
