package universidad.mensajeria.server.fachadadeservicios;

import org.junit.jupiter.api.Test;
import universidad.mensajeria.common.interno.ResultadoMensajeDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF-S36: bloque uniforme por mensaje (fecha, remitente, destinatario, tipo,
 * hash, resultado) en multilinea para consola y log; en IMAGEN el resultado
 * nombra las rutas derivadas guardadas.
 */
class BloqueMensajeTest {

    @Test
    void bloqueTextoTraeLasSeisEtiquetasEnMultilinea() {
        String bloque = FachadaDeServicios.bloque("TEXTO", "2026-10-08T10:00:00",
                "A001", "B002", "abc123", "10 caracteres, 2 palabras");
        for (String etiqueta : List.of("fecha=", "remitente=", "destinatario=",
                "tipo=", "hash=", "resultado=")) {
            assertTrue(bloque.contains(etiqueta), "falta " + etiqueta);
        }
        assertTrue(bloque.contains("A001") && bloque.contains("B002"));
        assertTrue(bloque.lines().count() >= 6, "bloque multilinea");
    }

    @Test
    void hashNuloSeMuestraComoGuion() {
        String bloque = FachadaDeServicios.bloque("TEXTO", "f", "A001", "B002",
                null, "r");
        assertTrue(bloque.contains("hash=-"));
    }

    @Test
    void rutasResultadoNombraLasDerivadas() {
        ResultadoMensajeDTO dto = new ResultadoMensajeDTO(7L, "hash", 10, 2,
                List.of("00_original.png", "01_grises.png"), 11L, List.of());
        String resultado = FachadaDeServicios.rutasResultado(dto);
        assertTrue(resultado.contains("00_original.png"));
        assertTrue(resultado.contains("01_grises.png"));
    }

    @Test
    void rutasVaciasConservanElLiteral() {
        ResultadoMensajeDTO dto = new ResultadoMensajeDTO(7L, "hash", 10, 2,
                List.of(), 11L, List.of());
        assertEquals("5 filtros aplicados", FachadaDeServicios.rutasResultado(dto));
        assertEquals("5 filtros aplicados", FachadaDeServicios.rutasResultado(null));
    }
}
