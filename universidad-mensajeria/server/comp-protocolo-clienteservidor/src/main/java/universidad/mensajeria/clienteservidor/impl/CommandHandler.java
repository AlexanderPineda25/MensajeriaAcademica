package universidad.mensajeria.clienteservidor.impl;

import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.tipos.Mensaje;

/**
 * Command del patron Command (PLAN2 §2.5): un tipo de trama = una clase.
 * Valida y delega en la salida que implementa la Fachada; no conoce la red,
 * la persistencia ni otros componentes.
 */
public interface CommandHandler {

    void handle(Mensaje mensaje, IdSesion sesion);

    /** Nombre del bean/tipo del catalogo que atiende (p.ej. "LOGIN"). */
    String tipo();

    /**
     * SEC-05: el default es {@code true} (fallo cerrado) porque casi toda trama
     * pertenece a una sesion autenticada; solo LOGIN es publica. Un comando
     * nuevo nace protegido: si olvida la guarda de
     * {@code Comandos.exigirAutenticacion}, el test parametrizado de
     * DespachadorTest lo detecta porque el tipo sigue marcando true. Quien
     * quiera abrirlo debe sobreescribir esto, y el mismo test exige que el
     * conjunto de tipos abiertos sea exactamente el declarado como publico.
     */
    default boolean requiereAutenticacion() {
        return true;
    }
}
