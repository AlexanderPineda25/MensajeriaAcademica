package universidad.mensajeria.clienteservidor.commands;

import universidad.mensajeria.clienteservidor.SalidaClienteServidor.SalidaConsultas;
import universidad.mensajeria.clienteservidor.SalidaClienteServidor.SalidaRespuestas;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.clienteservidor.impl.CommandHandler;

/** LISTAR_CONECTADOS: exige auth y delega el listado de sesiones vivas. */
public final class ListarCommand implements CommandHandler {

    private final SalidaConsultas salida;
    private final SalidaRespuestas respuestas;

    public ListarCommand(SalidaConsultas salida, SalidaRespuestas respuestas) {
        this.salida = salida;
        this.respuestas = respuestas;
    }

    @Override
    public void handle(Mensaje mensaje, IdSesion sesion) {
        // SEC-05: la presencia no se publica a un socket sin LOGIN.
        if (!Comandos.exigirAutenticacion(mensaje, sesion, respuestas)) {
            return;
        }
        salida.listarConectados(sesion, mensaje.id());
    }

    @Override
    public String tipo() {
        return "LISTAR_CONECTADOS";
    }
}
