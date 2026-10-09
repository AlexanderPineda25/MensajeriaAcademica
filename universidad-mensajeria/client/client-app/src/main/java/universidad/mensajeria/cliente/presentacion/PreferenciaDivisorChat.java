package universidad.mensajeria.cliente.presentacion;

import java.util.prefs.Preferences;

/** Guarda la posición del divisor principal por cuenta, sin exponer persistencia a la vista. */
final class PreferenciaDivisorChat {

    private static final double POSICION_PREDETERMINADA = 0.30;
    private static final double POSICION_MINIMA = 0.15;
    private static final double POSICION_MAXIMA = 0.60;
    private static final String PREFIJO = "anchoSidebar_";

    private final Preferences preferencias;

    PreferenciaDivisorChat(Preferences preferencias) {
        this.preferencias = preferencias;
    }

    double leer(String codigoCuenta) {
        if (codigoCuenta == null || codigoCuenta.isBlank()) {
            return POSICION_PREDETERMINADA;
        }
        double posicion = preferencias.getDouble(PREFIJO + codigoCuenta, POSICION_PREDETERMINADA);
        return valida(posicion) ? posicion : POSICION_PREDETERMINADA;
    }

    void guardar(String codigoCuenta, double posicion) {
        if (codigoCuenta != null && !codigoCuenta.isBlank() && valida(posicion)) {
            preferencias.putDouble(PREFIJO + codigoCuenta, posicion);
        }
    }

    private static boolean valida(double posicion) {
        return Double.isFinite(posicion)
                && posicion >= POSICION_MINIMA
                && posicion <= POSICION_MAXIMA;
    }
}
