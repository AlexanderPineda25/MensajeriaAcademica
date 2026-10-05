package universidad.mensajeria.cliente.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** UI-09: modo oscuro por defecto y hojas de estilo por modo. */
final class TemaTest {

    @Test
    void oscuroPorDefecto() {
        assertEquals(Tema.OSCURO, Tema.normalizar(null));
        assertEquals(Tema.OSCURO, Tema.normalizar("raro"));
        assertEquals(Tema.CLARO, Tema.normalizar("claro"));
    }

    @Test
    void cssPorModo() {
        assertEquals("/estilos/login.css", Tema.cssLogin(Tema.OSCURO));
        assertEquals("/estilos/login-claro.css", Tema.cssLogin(Tema.CLARO));
        assertEquals("/estilos/chat.css", Tema.cssChat(Tema.OSCURO));
        assertEquals("/estilos/chat-claro.css", Tema.cssChat(Tema.CLARO));
    }

    @Test
    void recursosExisten() {
        assertNotNull(Tema.class.getResource("/estilos/login.css"));
        assertNotNull(Tema.class.getResource("/estilos/login-claro.css"));
        assertNotNull(Tema.class.getResource("/estilos/chat.css"));
        assertNotNull(Tema.class.getResource("/estilos/chat-claro.css"));
    }
}
