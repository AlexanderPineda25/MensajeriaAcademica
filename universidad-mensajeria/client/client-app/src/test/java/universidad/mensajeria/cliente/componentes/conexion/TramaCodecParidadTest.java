package universidad.mensajeria.cliente.componentes.conexion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import universidad.mensajeria.common.codec.CodecException;
import universidad.mensajeria.common.codec.TramaCodec;
import universidad.mensajeria.common.tipos.Mensaje;
import universidad.mensajeria.common.tipos.TipoMensaje;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Paridad de framing entre los 3 clientes (Fase 1.6, vectores de {@code contrato/framing.json}).
 *
 * <p>Ejercita el {@link TramaCodec} de {@code common-protocol}, que es el que el cliente
 * usa de verdad: no hay un codec propio que probar.
 *
 * <p>Por qué compara contra {@code trama_hex} y no solo ida y vuelta: una ida y vuelta con
 * el mismo bug se cancela y no detecta nada. El caso crítico es {@code multibyte_acentos}:
 * 53 caracteres pero 56 bytes en UTF-8. Con {@code String.length()} en vez de
 * {@code byte[].length} la cabecera queda 3 bytes corta, el lector se descuadra y se pierden
 * todas las tramas siguientes. En Java {@code payload.length} ya son bytes porque
 * {@code getBytes(UTF_8)} devuelve bytes; este test lo fija como invariante.
 */
class TramaCodecParidadTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final String MULTIBYTE = "Señor café año";

    @Test
    @DisplayName("ack_minimo: 0000000e7b227469706f223a2241434b227d")
    void ackMinimo() {
        byte[] trama = TramaCodec.codificar(Mensaje.de(TipoMensaje.ACK));
        assertArrayEquals(HEX.parseHex("0000000e7b227469706f223a2241434b227d"), trama);
    }

    @Test
    @DisplayName("logout_minimo: 000000117b227469706f223a224c4f474f5554227d")
    void logoutMinimo() {
        byte[] trama = TramaCodec.codificar(Mensaje.de(TipoMensaje.LOGOUT));
        assertArrayEquals(HEX.parseHex("000000117b227469706f223a224c4f474f5554227d"), trama);
    }

    @Test
    @DisplayName("error_minimo: 000000107b227469706f223a224552524f52227d")
    void errorMinimo() {
        byte[] trama = TramaCodec.codificar(Mensaje.de(TipoMensaje.ERROR));
        assertArrayEquals(HEX.parseHex("000000107b227469706f223a224552524f52227d"), trama);
    }

    @Test
    @DisplayName("login_real: 69 bytes, con clave y contraseña de ejemplo")
    void loginReal() {
        Mensaje login = Mensaje.builder().tipo(TipoMensaje.LOGIN).id("v-1")
                .codigo("A001").contrasena("Secreta123").build();

        byte[] trama = TramaCodec.codificar(login);

        assertEquals(69, longitudAnunciada(trama));
        assertArrayEquals(HEX.parseHex(
                "000000457b227469706f223a224c4f47494e222c226964223a22762d31222c"
                        + "22636f6469676f223a2241303031222c22636f6e74726173656e61223a"
                        + "2253656372657461313233227d"), trama);
    }

    @Test
    @DisplayName("mensaje_texto: 95 bytes, los 5 campos que el servidor exige")
    void mensajeTexto() {
        Mensaje texto = Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO).id("m-1")
                .remitente("A001").destinatario("B002").contenido("Hola").build();

        byte[] trama = TramaCodec.codificar(texto);

        assertEquals(95, longitudAnunciada(trama));
        assertArrayEquals(HEX.parseHex(
                "0000005f7b227469706f223a224d454e53414a455f544558544f222c226964223a226d2d31"
                        + "222c2272656d6974656e7465223a2241303031222c2264657374696e6174617269"
                        + "6f223a2242303032222c22636f6e74656e69646f223a22486f6c61227d"), trama);
    }

    @Test
    @DisplayName("multibyte_acentos: 53 caracteres, 56 BYTES. El caso que mata.")
    void multibyteAcentos() {
        String payload = "{\"tipo\":\"MENSAJE_TEXTO\",\"contenido\":\"" + MULTIBYTE + "\"}";

        assertEquals(53, payload.length(), "el vector declara 53 caracteres");
        byte[] esperado = HEX.parseHex(
                "000000387b227469706f223a224d454e53414a455f544558544f222c22636f6e74656e"
                        + "69646f223a225365c3b16f7220636166c3a92061c3b16f227d");
        assertEquals(56, payload.getBytes(StandardCharsets.UTF_8).length,
                "en UTF-8 son 56 bytes aunque elijamos por error payload.length()");

        byte[] trama = TramaCodec.codificar(Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO)
                .contenido(MULTIBYTE).build());

        assertArrayEquals(esperado, trama,
                "la cabecera debe anunciar BYTES UTF-8, no caracteres");
        assertEquals(56, longitudAnunciada(trama));
    }

    @Test
    @DisplayName("multibyte_acentos también sobre el stream: escribir y leer")
    void multibyteAcentosSobreElStream() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        TramaCodec.escribir(salida, Mensaje.builder().tipo(TipoMensaje.MENSAJE_TEXTO)
                .contenido(MULTIBYTE).build());

        byte[] escrito = salida.toByteArray();
        assertArrayEquals(HEX.parseHex(
                "000000387b227469706f223a224d454e53414a455f544558544f222c22636f6e74656e"
                        + "69646f223a225365c3b16f7220636166c3a92061c3b16f227d"), escrito);

        Mensaje leido = TramaCodec.leer(new ByteArrayInputStream(escrito));
        assertEquals(TipoMensaje.MENSAJE_TEXTO, leido.tipo());
        assertEquals(MULTIBYTE, leido.contenido());
    }

    @Test
    @DisplayName("trama_incompleta: anuncia 255 bytes pero solo hay 2")
    void rechazaTramaIncompleta() {
        assertThrows(CodecException.class,
                () -> TramaCodec.decodificar(HEX.parseHex("000000ff4142")));
    }

    @Test
    @DisplayName("json_roto: payload truncado")
    void rechazaJsonRoto() {
        assertThrows(CodecException.class,
                () -> TramaCodec.decodificar(HEX.parseHex("000000057b226e")));
    }

    @Test
    @DisplayName("sin_tipo: el JSON no tiene la clave obligatoria")
    void rechazaJsonSinTipo() {
        assertThrows(CodecException.class,
                () -> TramaCodec.decodificar(HEX.parseHex("000000117b22746578746f223a22786f736861227d")));
    }

    @Test
    @DisplayName("longitud_cero: 0 no es una trama válida")
    void rechazaLongitudCero() {
        assertThrows(CodecException.class,
                () -> TramaCodec.decodificar(HEX.parseHex("00000000")));
    }

    @Test
    @DisplayName("longitud_cero también se rechaza al leer del stream")
    void rechazaLongitudCeroEnElStream() {
        assertThrows(CodecException.class,
                () -> TramaCodec.leer(new ByteArrayInputStream(HEX.parseHex("00000000"))));
    }

    @Test
    @DisplayName("longitud_sobre_el_tope: 32 MiB no debe reservar memoria")
    void rechazaLongitudSobreElTope() {
        assertThrows(CodecException.class,
                () -> TramaCodec.decodificar(HEX.parseHex("02000000")));
        assertThrows(CodecException.class,
                () -> TramaCodec.leer(new ByteArrayInputStream(HEX.parseHex("02000000"))));
    }

    @Test
    @DisplayName("trama_incompleta también se rechaza al leer del stream")
    void rechazaTramaIncompletaEnElStream() {
        assertThrows(IOException.class,
                () -> TramaCodec.leer(new ByteArrayInputStream(HEX.parseHex("000000ff4142"))));
    }

    @Test
    @DisplayName("el tope de trama es el del contrato: 16 MiB")
    void topeDeTramaDelContrato() {
        assertEquals(16777216, TramaCodec.TAMANO_MAXIMO_TRAMA);
    }

    /** Longitud que anuncian los 4 primeros bytes, en big-endian. */
    private static int longitudAnunciada(byte[] trama) {
        return ((trama[0] & 0xFF) << 24) | ((trama[1] & 0xFF) << 16)
                | ((trama[2] & 0xFF) << 8) | (trama[3] & 0xFF);
    }
}