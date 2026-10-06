package universidad.mensajeria.cliente.componentes.conexion;

import universidad.mensajeria.cliente.transversal.records.RespuestaRed;

/**
 * Retrollamadas del puerto de comunicación con el servidor.
 */
public interface ReceptorMensajes {

    /** Texto, imagen o broadcast entrante (ya persistido por la fachada). */
    void alRecibir(RespuestaRed mensaje);

    /** El servidor cerró o expulsó esta sesión. */
    void alCierre(RespuestaRed aviso);

    /** Error asíncrono (sin solicitud pendiente que lo reclame). */
    void alError(String motivo);

    /** El socket se rompió (corte de red): operar offline. */
    void alDesconexion();
}
