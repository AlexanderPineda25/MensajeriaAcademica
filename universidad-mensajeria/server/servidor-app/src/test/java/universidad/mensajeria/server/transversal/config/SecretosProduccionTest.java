package universidad.mensajeria.server.transversal.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SEG-03: con MENSAJERIA_PROD=true los defectos de desarrollo no arrancan.
 */
class SecretosProduccionTest {

    private static DbProperties db(String motor, String pass) {
        DbProperties.Motor mysql = new DbProperties.Motor("mysql", "url", "root",
                "mysql".equals(motor) ? pass : "s3cr3t-mysql");
        DbProperties.Motor postgres = new DbProperties.Motor("postgres", "url", "postgres",
                "postgres".equals(motor) ? pass : "s3cr3t-pg");
        DbProperties.Motor oracle = new DbProperties.Motor("oracle", "url", "U",
                "oracle".equals(motor) ? pass : "s3cr3t-ora");
        return new DbProperties(motor, mysql, postgres, oracle);
    }

    private static RedProperties red(boolean tls, String clave) {
        return new RedProperties(5000,
                new RedProperties.Pool(10, 100, 100, 5, 10),
                new RedProperties.Limites(100, 3),
                "uploads",
                new RedProperties.Tls(tls, 5001, "config/keystore.p12", clave));
    }

    @Test
    void prodConSecretosRealesPasa() {
        assertDoesNotThrow(() ->
                SecretosProduccion.validar(db("mysql", "s3cr3t-real"), red(false, null)));
    }

    @Test
    void prodConPassDefaultFallaConVariableAccionable() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                SecretosProduccion.validar(db("mysql", "12345"), red(false, null)));
        assert e.getMessage().contains("DB_MYSQL_PASS");
    }

    @Test
    void prodConPassVacioFalla() {
        assertThrows(IllegalStateException.class, () ->
                SecretosProduccion.validar(db("postgres", "  "), red(false, null)));
    }

    @Test
    void prodEligeLaVariableDelMotorActivo() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                SecretosProduccion.validar(db("oracle", "12345"), red(false, null)));
        assert e.getMessage().contains("DB_ORACLE_PASS");
    }

    @Test
    void prodTlsConClaveEjemploFalla() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                SecretosProduccion.validar(db("mysql", "s3cr3t-real"), red(true, "CAMBIAR")));
        assert e.getMessage().contains("RED_TLS_CLAVE");
    }

    @Test
    void prodTlsConClaveRealPasa() {
        assertDoesNotThrow(() ->
                SecretosProduccion.validar(db("mysql", "s3cr3t-real"), red(true, "clave-real")));
    }
}
