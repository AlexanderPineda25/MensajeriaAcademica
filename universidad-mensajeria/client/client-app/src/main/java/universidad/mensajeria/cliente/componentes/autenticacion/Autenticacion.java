package universidad.mensajeria.cliente.componentes.autenticacion;

import universidad.mensajeria.cliente.componentes.conexion.InterfazConexionRed;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionValidacion;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.utilerias.ValidacionCliente;

import java.io.IOException;

/** Autenticación contra el servidor vía la conexión (sin sockets aquí). */
public final class Autenticacion implements InterfazAutenticacion {

    private final InterfazConexionRed red;
    private volatile String codigoActual;

    public Autenticacion(InterfazConexionRed red) {
        this.red = red;
    }

    @Override
    public boolean login(String codigo, String contrasena) throws ExcepcionCliente {
        ValidacionCliente.codigo(codigo);
        if (contrasena == null || contrasena.isBlank()) {
            throw new ExcepcionValidacion("la contrasena es obligatoria");
        }
        try {
            RespuestaRed respuesta = red.login(codigo, contrasena);
            if (Boolean.TRUE.equals(respuesta.exito())) {
                codigoActual = codigo;
                return true;
            }
            codigoActual = null;
            return false;
        } catch (IOException falloRed) {
            throw new ExcepcionCliente("no se pudo autenticar con el servidor", falloRed);
        }
    }

    @Override
    public void logout() throws ExcepcionCliente {
        try {
            red.logout();
        } catch (IOException falloRed) {
            throw new ExcepcionCliente("no se pudo cerrar la sesión remota", falloRed);
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
