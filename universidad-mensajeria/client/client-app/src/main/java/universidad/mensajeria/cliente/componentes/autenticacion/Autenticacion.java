package universidad.mensajeria.cliente.componentes.autenticacion;

import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.utilerias.ValidacionCliente;

/** Autenticación contra el servidor vía la conexión (sin sockets aquí). */
public final class Autenticacion implements InterfazAutenticacion {

    private final InterfazConexionRed red;
    private volatile String codigoActual;

    public Autenticacion(InterfazConexionRed red) {
        this.red = red;
    }

    @Override
    public boolean login(String codigo, String contrasena) throws Exception {
        ValidacionCliente.codigo(codigo);
        if (contrasena == null || contrasena.isBlank()) {
            throw new IllegalArgumentException("la contrasena es obligatoria");
        }
        RespuestaRed respuesta = red.login(codigo, contrasena);
        if (Boolean.TRUE.equals(respuesta.exito())) {
            codigoActual = codigo;
            return true;
        }
        // El protocolo responde con un rechazo genérico. La vista también
        // muestra un mensaje genérico y no revela si el código existe.
        codigoActual = null;
        return false;
    }

    @Override
    public void logout() throws Exception {
        try {
            red.logout();
        } finally {
            codigoActual = null;
        }
    }

    @Override
    public void olvidarSesion() {
        codigoActual = null;
    }

    @Override
    public String codigoActual() {
        return codigoActual;
    }

    @Override
    public boolean sesionAbierta() {
        return codigoActual != null;
    }
}
