package universidad.mensajeria.server.vistaescritorio;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import universidad.mensajeria.common.interno.MensajeResumenDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Previsualización de filtros de la pestaña Mensajes: núcleo puro
 * (sin toolkit JavaFX) sobre una imagen generada en memoria.
 */
class VistaEscritorioFiltrosTest {

    private static BufferedImage imagenPrueba() {
        BufferedImage imagen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                imagen.setRGB(x, y, ((x * 32) << 16) | ((y * 32) << 8) | 128);
            }
        }
        return imagen;
    }

    @Test
    void cadaFiltroDevuelveImagenNuevaDelTamanoEsperado() {
        BufferedImage base = imagenPrueba();
        assertEquals(8, VistaEscritorio.aplicarFiltroVista(base, "Original").getWidth());
        assertEquals(8, VistaEscritorio.aplicarFiltroVista(base, "Grises").getHeight());
        assertEquals(8, VistaEscritorio.aplicarFiltroVista(base, "Sepia").getWidth());
        assertEquals(8, VistaEscritorio.aplicarFiltroVista(base, "Giro 180").getHeight());
        assertEquals(8, VistaEscritorio.aplicarFiltroVista(base, "Brillo x1.25").getWidth());
        BufferedImage reducida = VistaEscritorio.aplicarFiltroVista(base, "Reducida 50%");
        assertEquals(4, reducida.getWidth());
        assertEquals(4, reducida.getHeight());
    }

    @Test
    void losFiltrosTransformanPixelesSinTocarLaBase() {
        BufferedImage base = imagenPrueba();
        int antes = base.getRGB(1, 1);
        BufferedImage grises = VistaEscritorio.aplicarFiltroVista(base, "Grises");
        assertNotSame(base, grises);
        int rgb = grises.getRGB(1, 1);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        assertEquals(r, g, "grises: canales iguales");
        assertEquals(g, b, "grises: canales iguales");
        assertEquals(antes, base.getRGB(1, 1), "la base no se muta");
    }

    @Test
    void opcionDesconocidaDevuelveCopia() {
        BufferedImage base = imagenPrueba();
        BufferedImage copia = VistaEscritorio.aplicarFiltroVista(base, "Inexistente");
        assertNotSame(base, copia);
        assertEquals(base.getRGB(3, 3), copia.getRGB(3, 3));
    }

    @Test
    void baseVaciaLanzaErrorClaro() {
        assertThrows(IllegalArgumentException.class,
                () -> VistaEscritorio.aplicarFiltroVista(null, "Grises"));
        assertTrue(VistaEscritorio.aplicarFiltroVista(imagenPrueba(), null).getWidth() > 0,
                "opcion nula equivale a Original");
    }

    // Sesión B: Conectados muestra "Nombre Apellidos [código]" y el mapa
    // inverso usa el visible completo (incluye código → único).

    @Test
    void conectadosResuelveNombreVisible() {
        Map<String, String> directorio = Map.of("A001", "Ana Pérez [A001]");
        assertEquals("Ana Pérez [A001]",
                VistaEscritorio.resolverVisible("A001", directorio));
        assertEquals("X999", VistaEscritorio.resolverVisible("X999", directorio));
        assertEquals("SERVIDOR (sistema)", VistaEscritorio.resolverVisible("SERVIDOR", directorio));
        assertEquals("-", VistaEscritorio.resolverVisible("  ", directorio));
    }

    @Test
    void conectadosMapeaVisiblesYCodigoPorVisible() {
        Map<String, String> directorio = Map.of(
                "A001", "Ana Pérez [A001]", "B002", "Luis Díaz [B002]");
        List<String> visibles = VistaEscritorio.visiblesDe(List.of("A001", "B002"), directorio);
        assertEquals(List.of("Ana Pérez [A001]", "Luis Díaz [B002]"), visibles);
        Map<String, String> inverso =
                VistaEscritorio.indiceCodigoPorVisible(List.of("A001", "B002"), directorio);
        assertEquals("A001", inverso.get("Ana Pérez [A001]"));
        assertEquals("B002", inverso.get("Luis Díaz [B002]"));
    }

    @Test
    void confirmacionMencionaElVisible() {
        assertEquals("¿Desconectar a Ana Pérez [A001]? Se cerrarán sus sesiones activas.",
                VistaEscritorio.mensajeConfirmacionDesconexion("Ana Pérez [A001]"));
    }

    // Sesión B: filtro local por tipo + conservar selección por id.

    private static MensajeResumenDTO mensaje(long id, String tipo) {
        return new MensajeResumenDTO(id, "A001", "B002", tipo, "hola", "hash",
                4, 1, null, null, null, null, null, "2026-10-07T10:00:00");
    }

    @Test
    void fichaFiltraPorTipoLocalSinPedirAlServidor() {
        List<MensajeResumenDTO> pagina = List.of(
                mensaje(1, "TEXTO"), mensaje(2, "IMAGEN"), mensaje(3, "texto"));
        assertEquals(3, VistaEscritorio.filtrarPorTipo(pagina, "Todos").size());
        assertEquals(List.of(1L, 3L), VistaEscritorio.filtrarPorTipo(pagina, "TEXTO")
                .stream().map(MensajeResumenDTO::id).toList());
        assertEquals(1, VistaEscritorio.filtrarPorTipo(pagina, "IMAGEN").size());
    }

    @Test
    void seleccionSeRestauraPorId() {
        List<MensajeResumenDTO> lista = List.of(mensaje(10, "TEXTO"), mensaje(20, "IMAGEN"));
        assertEquals(1, VistaEscritorio.indicePorId(lista, 20));
        assertEquals(-1, VistaEscritorio.indicePorId(lista, 999));
    }

    // RF-S53: IP viva del destinatario o "No disponible".

    @Test
    void ipVivaMuestraSesionesActivas() {
        Map<String, String> ips = Map.of("B002", "192.168.1.20");
        assertEquals("192.168.1.20", VistaEscritorio.ipVivaDe(ips, "B002"));
        assertEquals("192.168.1.20", VistaEscritorio.ipVivaDe(ips, "b002"));
    }

    @Test
    void ipVivaSinSesionEsNoDisponible() {
        Map<String, String> ips = Map.of("B002", "192.168.1.20");
        assertEquals("No disponible", VistaEscritorio.ipVivaDe(ips, "C003"));
        assertEquals("No disponible", VistaEscritorio.ipVivaDe(ips, null));
        assertEquals("No disponible", VistaEscritorio.ipVivaDe(ips, "  "));
        assertEquals("No disponible", VistaEscritorio.ipVivaDe(null, "B002"));
    }
}
