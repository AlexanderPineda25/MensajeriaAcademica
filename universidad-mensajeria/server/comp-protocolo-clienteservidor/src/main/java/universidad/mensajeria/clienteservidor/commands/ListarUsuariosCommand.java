package universidad.mensajeria.clienteservidor.commands;

import universidad.mensajeria.clienteservidor.SalidaClienteServidor.SalidaConsultas;
import universidad.mensajeria.clienteservidor.SalidaClienteServidor.SalidaRespuestas;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.clienteservidor.impl.CommandHandler;

/** LISTAR_USUARIOS (§13.15): exige auth y devuelve el directorio completo con estado. */
public final class ListarUsuariosCommand implements CommandHandler {

    private final SalidaConsultas salida;
    private final SalidaRespuestas respuestas;

    public ListarUsuariosCommand(SalidaConsultas salida, SalidaRespuestas respuestas) {
        this.salida = salida;
        this.respuestas = respuestas;
    }

    @Override
    public void handle(Mensaje mensaje, IdSesion sesion) {
        // SEC-05: sin LOGIN no se entrega el directorio (codigo, nombres, apellidos, programa).
        if (!Comandos.exigirAutenticacion(mensaje, sesion, respuestas)) {
            return;
        }
        salida.listarUsuarios(sesion, mensaje.id());
    }

    @Override
    public String tipo() {
        return "LISTAR_USUARIOS";
    }
}
