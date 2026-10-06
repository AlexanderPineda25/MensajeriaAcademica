package universidad.mensajeria.server.fachadadeservicios.casosdeuso;

import universidad.mensajeria.server.almacenarinformacion.InterfazAlmacenarInformacion;

import java.io.IOException;

/**
 * Lecturas de archivo para las vistas (consola/escritorio), fuera del flujo
 * de protocolo: bytes via AlmacenarInformacion. La Fachada delega aquí en vez
 * de tocar persistencia directa (ver ComponentesServidor: Fachada solo alcanza
 * persistencia via Mensajes/LogDeEventos/casos).
 */
public final class ArchivoVistaCaso {

    private final InterfazAlmacenarInformacion almacenar;

    public ArchivoVistaCaso(InterfazAlmacenarInformacion almacenar) {
        this.almacenar = almacenar;
    }

    public byte[] bytes(long archivoId) {
        try {
            return almacenar.leerBytesArchivo(archivoId);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer el archivo " + archivoId
                    + ": " + e.getMessage(), e);
        }
    }
}
