package universidad.mensajeria.clienteservidor.commands;

import universidad.mensajeria.clienteservidor.SalidaClienteServidor.SalidaAutenticacion;
import universidad.mensajeria.clienteservidor.SalidaClienteServidor.SalidaRespuestas;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.clienteservidor.impl.CommandHandler;

/** LOGOUT: exige auth y delega el cierre en la Fachada (avisa CLOSE_NOTICE a sus sockets). */
public final class LogoutCommand implements CommandHandler {

    private final SalidaAutenticacion salida;
    private final SalidaRespuestas respuestas;

    public LogoutCommand(SalidaAutenticacion salida, SalidaRespuestas respuestas) {
        this.salida = salida;
        this.respuestas = respuestas;
    }

    @Override
    public void handle(Mensaje mensaje, IdSesion sesion) {
        // SEC-05: sin LOGIN no hay sesion que cerrar; responder ERROR evita tocar la del atacante.
        if (!Comandos.exigirAutenticacion(mensaje, sesion, respuestas)) {
            return;
        }
        salida.cerrar(sesion, mensaje.id());
    }

    @Override
    public String tipo() {
        return "LOGOUT";
    }
}
