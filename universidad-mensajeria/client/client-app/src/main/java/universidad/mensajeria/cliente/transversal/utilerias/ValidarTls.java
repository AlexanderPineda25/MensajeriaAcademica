package universidad.mensajeria.cliente.transversal.utilerias;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Falla rápido ANTES de abrir ningún socket TLS (Fase 1.3, SEG-01).
 *
 * <p>Espejo de {@code transversal/tls.py::validar_tls} del cliente Python, que es la
 * referencia de paridad (PLAN_PARIDAD_CLIENTES.md §0.4). Sin esto, un
 * {@code tls.confianza} mal escrito no sale hasta el handshake, y sale como una
 * IOException genérica que no dice qué propiedad tocar.
 *
 * <p>Decisión: {@code tls.confianza} vacío NO es un error. Se usa
 * {@code SSLSocketFactory.getDefault()}, o sea la validación de cadena estándar
 * del propio JDK, que es el camino por defecto. El almacén JKS con contraseña
 * vacía es solo la vía de una CA propia.
 *
 * <p>Vive en {@code client-app} y no en {@code client-interfaces} porque este módulo
 * no puede tener dependencias (contrato/puertos.json) y la regla se aplica a
 * superficie de puertos, no a utilidades de un adaptador.
 */
public final class ValidarTls {

    /** Puerto TLS por defecto cuando {@code tls.puerto} no es usable. Mismo valor en los 3 clientes. */
    public static final int TLS_PUERTO_POR_DEFECTO = 5001;

    private static final int PUERTO_MINIMO = 1;
    private static final int PUERTO_MAXIMO = 65535;

    private ValidarTls() {
    }

    /**
     * Interpreta {@code tls.habilitado} sin convertir silenciosamente un error de escritura
     * en TLS deshabilitado. Una propiedad ausente o vacía conserva el valor seguro por defecto.
     *
     * @param texto valor crudo de {@code client.properties}
     * @return si se solicitó TLS
     * @throws IllegalArgumentException si el valor no es {@code true} ni {@code false}
     */
    public static boolean habilitadoDesdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String valor = texto.trim();
        if ("true".equalsIgnoreCase(valor)) {
            return true;
        }
        if ("false".equalsIgnoreCase(valor)) {
            return false;
        }
        throw new IllegalArgumentException("tls.habilitado mal formado: '" + texto
                + "'. Admite solo true o false en client.properties");
    }

    /**
     * Valida la configuración TLS. Si el TLS no está habilitado no hace nada: en ese caso
     * no hay nada que validar y rechazar sería lo incorrecto.
     *
     * @param habilitado lo que dice {@code tls.habilitado}
     * @param puerto    {@code puerto} en claro
     * @param puertoTls {@code tls.puerto}
     * @param confianza {@code tls.confianza}; vacío = validación de cadena estándar
     * @throws IOException con el mensaje nombrando la propiedad que hay que corregir
     */
    public static void validar(boolean habilitado, int puerto, int puertoTls, String confianza)
            throws IOException {
        if (!habilitado) {
            return;
        }
        if (puertoTls < PUERTO_MINIMO || puertoTls > PUERTO_MAXIMO) {
            throw new IOException("TLS habilitado pero tls.puerto = " + puertoTls
                    + " no es un puerto valido (debe estar entre " + PUERTO_MINIMO + " y "
                    + PUERTO_MAXIMO + ")");
        }
        // Dos puertos fijos iguales: el cliente no sabría cuál de los dos speakar.
        if (puertoTls == puerto) {
            throw new IOException("TLS y plano comparten el puerto " + puerto
                    + ": cambie tls.puerto a uno distinto de puerto en client.properties");
        }
        if (confianza == null || confianza.isBlank()) {
            // Camino por defecto: SSLSocketFactory.getDefault() y se verifica la cadena del JDK.
            return;
        }
        try {
            if (!Files.isRegularFile(Path.of(confianza))) {
                throw new IOException("TLS habilitado pero el almacen de confianza no existe: " + confianza
                        + " (revise tls.confianza: debe apuntar a un almacen JKS con la CA; "
                        + "el almacen real nunca se versiona)");
            }
        } catch (InvalidPathException rutaInvalida) {
            // Una ruta con caracteres no válidos en Windows: se nombra igual la propiedad.
            throw new IOException("TLS habilitado pero tls.confianza no es una ruta valida: " + confianza
                    + " (revise tls.confianza en client.properties)", rutaInvalida);
        }
    }
}
