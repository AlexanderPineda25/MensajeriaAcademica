package universidad.mensajeria.cliente.persistencia;

import java.sql.Connection;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/**
 * Puerto de la base local (H2 por cuenta).
 * Los DAOs dependen de este puerto, nunca del {@code LocalStore} concreto (DIP).
 * Vive en client-app porque expone {@code java.sql} (los puertos neutrales
 * de client-interfaces no pueden).
 */
public interface InterfazAlmacenLocal {

    Connection abrir() throws ExcepcionCliente;

    void prepararCuenta(String codigo) throws ExcepcionCliente;

    int inicializar() throws ExcepcionCliente;

    void cerrar();
}
