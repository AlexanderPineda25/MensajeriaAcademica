package universidad.mensajeria.common.interno;

/**
 * Conteo de conexiones por usuario (FASE 9, Informe 2): salida del
 * GROUP BY sobre registro_acciones. "Veces conectado" cuenta solo
 * TipoAccion.LOGIN (los accesos exitosos), no un CONECTADO aparte.
 */
public record LoginConteoDTO(String codigo, long veces) {
}
