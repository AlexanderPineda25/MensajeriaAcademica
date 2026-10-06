package universidad.mensajeria.cliente.presentacion;

/** Traduce fallos técnicos conocidos a mensajes breves para la interfaz. */
final class FormatoErrorCliente {

    private FormatoErrorCliente() { }

    static String mensaje(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof java.net.SocketTimeoutException) {
                return "El servidor tardó demasiado en responder. Intenta de nuevo.";
            }
            if (actual instanceof java.io.IOException) {
                return "No fue posible conectar con el servidor. Verifica que esté activo.";
            }
            if (actual instanceof IllegalStateException
                    && "Máximo 50 MiB".equals(actual.getMessage())) {
                return "El archivo supera el límite de 50 MiB.";
            }
        }
        return "No se pudo completar la acción. Intenta de nuevo.";
    }
}
