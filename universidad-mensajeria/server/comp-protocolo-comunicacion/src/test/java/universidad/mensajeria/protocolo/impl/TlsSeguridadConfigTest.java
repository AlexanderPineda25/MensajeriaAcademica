package universidad.mensajeria.protocolo.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import universidad.mensajeria.protocolo.ConfigRed;
import universidad.mensajeria.protocolo.InterfazProtocoloComunicacion;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seguridad: TLS habilitado con secretos de ejemplo, almacen ausente o
 * colision de puertos falla rapido con mensaje accionable (que propiedad
 * tocar). TLS apagado nunca exige secretos.
 */
class TlsSeguridadConfigTest {

    @TempDir
    Path base;

    private static ConfigRed tlsHacia(String almacen, String clave, int plano, int tls) {
        return new ConfigRed(plano, 1, 2, 4, 1, 0, 10, true, tls, almacen, clave);
    }

    private static void iniciarEsperandoFallo(InterfazProtocoloComunicacion protocolo,
                                              String fragmentoMensaje) {
        try {
            IllegalStateException fallo = assertThrows(IllegalStateException.class,
                    protocolo::iniciar);
            assertTrue(fallo.getMessage().contains(fragmentoMensaje),
                    "mensaje sin pista accionable: " + fallo.getMessage());
        } finally {
            protocolo.detener();
        }
    }

    @Test
    void claveDeEjemploNoArranca() throws Exception {
        Path almacen = base.resolve("keystore.p12");
        Files.write(almacen, new byte[]{1, 2, 3});
        iniciarEsperandoFallo(
                new ProveedorProtocoloComunicacionImpl()
                        .crear(tlsHacia(almacen.toString(), "CAMBIAR", 0, 5001)),
                "red.tls.clave");
    }

    @Test
    void claveAusenteNoArranca() throws Exception {
        Path almacen = base.resolve("keystore.p12");
        Files.write(almacen, new byte[]{1, 2, 3});
        iniciarEsperandoFallo(
                new ProveedorProtocoloComunicacionImpl()
                        .crear(tlsHacia(almacen.toString(), null, 0, 5001)),
                "red.tls.clave");
    }

    @Test
    void almacenAusenteNoArranca() {
        iniciarEsperandoFallo(
                new ProveedorProtocoloComunicacionImpl().crear(
                        tlsHacia(base.resolve("no-existe.p12").toString(), "real123", 0, 5001)),
                "red.tls.almacen");
    }

    @Test
    void colisionDePuertosNoArranca() {
        // Validacion pura (sin bindear puertos fijos): mismo puerto fijo en ambos.
        ConfigRed colision = tlsHacia(base.resolve("keystore.p12").toString(),
                "real123", 5000, 5000);
        IllegalStateException fallo = assertThrows(IllegalStateException.class,
                () -> ProtocoloComunicacion.validarTls(colision));
        assertTrue(fallo.getMessage().contains("red.tls.puerto"),
                "mensaje sin pista accionable: " + fallo.getMessage());
    }

    @Test
    void dobleEfimeroNoEsColision() throws Exception {
        // 0/0 = ambos efimeros: el SO asigna puertos distintos, es valido.
        Path almacen = base.resolve("keystore.p12");
        Files.write(almacen, new byte[]{1, 2, 3});
        ConfigRed efimeros = tlsHacia(almacen.toString(), "real123", 0, 0);
        assertDoesNotThrow(() -> ProtocoloComunicacion.validarTls(efimeros));
    }

    @Test
    void tlsApagadoNoExigeSecretos() {
        InterfazProtocoloComunicacion protocolo =
                new ProveedorProtocoloComunicacionImpl().crear(new ConfigRed(0, 1, 2, 4, 1));
        try {
            assertDoesNotThrow(protocolo::iniciar);
            assertTrue(protocolo.estaActivo());
        } finally {
            protocolo.detener();
        }
    }
}
