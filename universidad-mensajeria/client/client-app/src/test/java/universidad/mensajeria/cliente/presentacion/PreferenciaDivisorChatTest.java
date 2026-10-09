package universidad.mensajeria.cliente.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

class PreferenciaDivisorChatTest {

    @Test
    void conservaPosicionSeparadaPorCuenta() throws Exception {
        Preferences nodo = Preferences.userRoot().node(
                "prueba" + UUID.randomUUID().toString().replace("-", ""));
        try {
            PreferenciaDivisorChat preferencia = new PreferenciaDivisorChat(nodo);
            preferencia.guardar("A001", 0.42);

            assertEquals(0.42, preferencia.leer("A001"), 0.0001);
            assertEquals(0.30, preferencia.leer("B002"), 0.0001);
        } finally {
            nodo.removeNode();
        }
    }

    @Test
    void ignoraPosicionesFueraDelRangoYCodigosVacios() throws Exception {
        Preferences nodo = Preferences.userRoot().node(
                "prueba" + UUID.randomUUID().toString().replace("-", ""));
        try {
            PreferenciaDivisorChat preferencia = new PreferenciaDivisorChat(nodo);
            preferencia.guardar("A001", 0.90);
            preferencia.guardar("", 0.40);

            assertEquals(0.30, preferencia.leer("A001"), 0.0001);
            assertEquals(0.30, preferencia.leer(""), 0.0001);
        } finally {
            nodo.removeNode();
        }
    }
}
