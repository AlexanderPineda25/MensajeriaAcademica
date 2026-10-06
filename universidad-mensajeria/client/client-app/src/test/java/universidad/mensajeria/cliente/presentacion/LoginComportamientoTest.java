package universidad.mensajeria.cliente.presentacion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión para el comportamiento de errores y validación en la pantalla de inicio de sesión.
 */
class LoginComportamientoTest {

    @Test
    void timeoutDelServidorMuestraAvisoAmigable() {
        Throwable error = new SocketTimeoutException("Read timed out");
        String mensaje = FormatoErrorCliente.mensaje(error);

        assertEquals("El servidor tardó demasiado en responder. Intenta de nuevo.", mensaje);
        assertFalse(mensaje.contains("SocketTimeoutException"));
    }

    @Test
    void falloDeConexionIoMuestraAvisoVerificarServidor() {
        Throwable error = new IOException("Connection refused: connect");
        String mensaje = FormatoErrorCliente.mensaje(error);

        assertEquals("No fue posible conectar con el servidor. Verifica que esté activo.", mensaje);
        assertFalse(mensaje.contains("IOException"));
        assertFalse(mensaje.contains("Connection refused"));
    }

    @Test
    void archivoSuperaLimiteMuestraMensajeEspecifico() {
        Throwable error = new IllegalStateException("Máximo 50 MiB");
        String mensaje = FormatoErrorCliente.mensaje(error);

        assertEquals("El archivo supera el límite de 50 MiB.", mensaje);
    }

    @Test
    void excepcionAnidadaResuelveCausaRaiz() {
        Throwable raiz = new IOException("Broken pipe");
        Throwable envuelta = new RuntimeException("Error en capa superior", raiz);
        String mensaje = FormatoErrorCliente.mensaje(envuelta);

        assertEquals("No fue posible conectar con el servidor. Verifica que esté activo.", mensaje);
    }

    @Test
    void errorDesconocidoNoExponeDetallesTecnicos() {
        Throwable error = new NullPointerException("puntero nulo inesperado");
        String mensaje = FormatoErrorCliente.mensaje(error);

        assertEquals("No se pudo completar la acción. Intenta de nuevo.", mensaje);
        assertFalse(mensaje.contains("NullPointerException"));
    }
}
