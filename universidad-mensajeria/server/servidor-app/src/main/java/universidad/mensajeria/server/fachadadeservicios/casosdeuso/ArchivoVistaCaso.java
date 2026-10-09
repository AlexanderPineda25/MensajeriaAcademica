package universidad.mensajeria.server.fachadadeservicios.casosdeuso;

import universidad.mensajeria.server.mensajes.InterfazMensajes;

import java.io.IOException;

/**
 * Lecturas de archivo para las vistas (consola/escritorio), fuera del flujo
 * de protocolo: bytes via Mensajes (E10). La Fachada delega aquí en vez
 * de tocar persistencia directa (diagrama: Fachada solo alcanza
 * persistencia via Mensajes/LogDeEventos).
 */
public final class ArchivoVistaCaso {

    private final InterfazMensajes mensajes;

    public ArchivoVistaCaso(InterfazMensajes mensajes) {
        this.mensajes = mensajes;
    }

    public byte[] bytes(long archivoId) {
        try {
            return mensajes.leerBytesArchivo(archivoId);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer el archivo " + archivoId
                    + ": " + e.getMessage(), e);
        }
    }
}
