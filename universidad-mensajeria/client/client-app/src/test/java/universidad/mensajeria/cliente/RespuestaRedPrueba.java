package universidad.mensajeria.cliente;

import universidad.mensajeria.cliente.transversal.records.RespuestaRed;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;
import universidad.mensajeria.common.tipos.Mensaje;

public final class RespuestaRedPrueba {

    private RespuestaRedPrueba() {
    }

    public static RespuestaRed desde(Mensaje mensaje) {
        return new RespuestaRed(TipoMensajeCliente.valueOf(mensaje.tipo().name()),
                mensaje.id(), mensaje.fechaHora(), mensaje.codigo(), mensaje.nombres(),
                mensaje.apellidos(), mensaje.programa(), mensaje.remitente(),
                mensaje.destinatario(), mensaje.contenido(), mensaje.hashSha256(),
                mensaje.numCaracteres(), mensaje.numPalabras(), mensaje.etapasFiltrado(),
                mensaje.nombreArchivo(), mensaje.tamanoArchivo(), mensaje.archivoId(),
                mensaje.mime(), mensaje.contenidoImagen(), mensaje.pagina(),
                mensaje.totalPaginas(), mensaje.exito(), mensaje.mensajeError(),
                mensaje.usuariosConectados(), mensaje.tipoOriginal());
    }
}
