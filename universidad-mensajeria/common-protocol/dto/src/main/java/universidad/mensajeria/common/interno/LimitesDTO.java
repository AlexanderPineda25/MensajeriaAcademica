package universidad.mensajeria.common.interno;

/**
 * Limites operativos (PLAN2 §2.5 Regla 6): `Servicios` es logica pura y los
 * recibe de `Fabrica` desde server.properties. Sin acceso a config ni a BD.
 */
public record LimitesDTO(
        int maxConexiones,
        int maxConexionesPorUsuario,
        long maxTamanoArchivo
) {
    /**
     * Defecto 50 MB cuando no hay configuracion (server.properties ni BD).
     * Unico dueño del literal: FragmentoArchivoCaso, InicioServidor y
     * ConfiguracionLimites lo referencian en vez de repetirlo.
     */
    public static final long TAMANO_MAXIMO_POR_DEFECTO = 52_428_800L;
}
