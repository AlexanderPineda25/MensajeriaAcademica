package universidad.mensajeria.server.transversal.config;

/**
 * SEG-03: guardia de secretos en produccion.
 *
 * Los properties traen defectos SOLO de desarrollo ({@code 12345},
 * {@code CAMBIAR}) via {@code ${VAR:defecto}}. Con
 * {@code MENSAJERIA_PROD=true} el arranque falla rapido si el motor activo
 * o la clave TLS siguen con esos defectos: rotar secretos en produccion es
 * obligatorio, no opcional. En desarrollo (variable ausente) no cambia nada.
 *
 * La rotacion periodica en si es operativa (ver DESPLIEGUE.md "Rotacion de
 * secretos"): esta clase garantiza que nunca se opere con los de ejemplo.
 *
 * ORDEN IMPORTANTE: se invoca desde {@code DataSourceAdapter.dataSource}, no
 * desde un runner. Si se valida despues, Hikari ya intento conectar con la
 * clave de ejemplo y el unico sintoma es un {@code Access denied} de MySQL que
 * no dice que variable hay que fijar.
 */
public final class SecretosProduccion {

    static final String ENV_PROD = "MENSAJERIA_PROD";

    private SecretosProduccion() {
    }

    public static boolean esProduccion() {
        return "true".equalsIgnoreCase(System.getenv(ENV_PROD));
    }

    public static void validarSiProduccion(DbProperties db, RedProperties red) {
        if (!esProduccion()) {
            return;
        }
        validar(db, red);
    }

    static void validar(DbProperties db, RedProperties red) {
        DbProperties.Motor motor = db.motorActivo();
        if (motor.pass() == null || motor.pass().isBlank() || "12345".equals(motor.pass())) {
            throw new IllegalStateException(
                    "MENSAJERIA_PROD=true pero db." + db.nombreActivo() + ".pass sigue "
                            + "siendo el default de desarrollo: fije "
                            + variablePara(db.nombreActivo()) + " con la clave real "
                            + "y reinicie (ver DESPLIEGUE.md \"Rotacion de secretos\")");
        }
        if (red.tls() != null && red.tls().habilitado()
                && (red.tls().clave() == null || red.tls().clave().isBlank()
                || "CAMBIAR".equals(red.tls().clave()))) {
            throw new IllegalStateException(
                    "MENSAJERIA_PROD=true con TLS habilitado y clave de ejemplo: "
                            + "fije RED_TLS_CLAVE con la clave real del almacen y reinicie");
        }
    }

    private static String variablePara(String motor) {
        return switch (motor) {
            case "postgres" -> "DB_POSTGRES_PASS";
            case "oracle" -> "DB_ORACLE_PASS";
            default -> "DB_MYSQL_PASS";
        };
    }
}
