package universidad.mensajeria.cliente.fachada.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

/**
 * La lista de recientes combina historial + presencia + directorio (SRP):
 * orden tipo WhatsApp (fecha del último mensaje primero; la presencia solo
 * desempata), nadie se repite y el yo queda fuera.
 */
class ConstructorRecientesTest {

    private static UsuarioLocal usuario(String codigo, boolean conectado) {
        return new UsuarioLocal(codigo, codigo, "", "", conectado, "2026-01-01", "2026-01-01");
    }

    private static RecienteChat reciente(String otro, String fecha) {
        return new RecienteChat(otro, "hola", "MENSAJE_TEXTO", fecha, 0, false);
    }

    @Test
    void elMasNuevoVaPrimeroAunqueEsteDesconectado() {
        // Orden WhatsApp (paridad .NET/Python): la presencia pinta el punto
        // pero no reordena; C003 está conectado pero su mensaje es más viejo.
        List<RecienteChat> base = List.of(
                reciente("C003", "2026-10-01T10:00"),
                reciente("B002", "2026-10-08T10:00"));
        List<UsuarioLocal> directorio = List.of(
                usuario("A001", true), usuario("B002", false), usuario("C003", true));

        List<RecienteChat> salida = ConstructorRecientes.armar(base, directorio, "A001");

        assertEquals("B002", salida.get(0).otro());
        assertFalse(salida.get(0).conectado());
        assertEquals("C003", salida.get(1).otro());
        assertTrue(salida.get(1).conectado());
    }

    @Test
    void aFechaIgualDesempataLaPresencia() {
        List<RecienteChat> base = List.of(
                reciente("B002", "2026-10-07T10:00"),
                reciente("C003", "2026-10-07T10:00"));
        List<UsuarioLocal> directorio = List.of(
                usuario("A001", true), usuario("B002", false), usuario("C003", true));

        List<RecienteChat> salida = ConstructorRecientes.armar(base, directorio, "A001");

        assertEquals("C003", salida.get(0).otro());
        assertTrue(salida.get(0).conectado());
    }

    @Test
    void agregaContactosSinConversacionYExcluyeAlYo() {
        List<UsuarioLocal> directorio = List.of(
                usuario("A001", true), usuario("B002", true), usuario("C003", false));

        List<RecienteChat> salida = ConstructorRecientes.armar(List.of(), directorio, "A001");

        assertEquals(2, salida.size());
        assertEquals("B002", salida.get(0).otro());
        assertTrue(salida.get(0).conectado());
        assertEquals("C003", salida.get(1).otro());
        assertFalse(salida.get(1).conectado());
    }

    @Test
    void noDuplicaAlQueYaTieneConversacion() {
        List<RecienteChat> base = List.of(reciente("B002", "2026-10-07T10:00"));
        List<UsuarioLocal> directorio = List.of(usuario("A001", true), usuario("B002", true));

        List<RecienteChat> salida = ConstructorRecientes.armar(base, directorio, "A001");

        assertEquals(1, salida.size());
        assertEquals("hola", salida.get(0).ultimoContenido());
    }
}
