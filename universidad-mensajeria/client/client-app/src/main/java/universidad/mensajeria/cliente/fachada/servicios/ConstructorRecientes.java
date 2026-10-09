package universidad.mensajeria.cliente.fachada.servicios;

import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Construcción pura de la lista de conversaciones recientes con presencia (Fase 4).
 * <p>
 * Extraído de {@code FachadaCliente} por responsabilidad única: combina el
 * historial local con la caché del directorio sin tocar red, H2 ni hilos.
 * Toda la entrada llega por parámetros, así que es comprobable sin fakes.
 * </p>
 */
public final class ConstructorRecientes {

    private ConstructorRecientes() {
    }

    /**
     * Base del historial + presencia de caché + contactos sin conversación.
     *
     * @param base historial reciente tal como lo devuelve el DAO
     * @param directorio caché local del directorio (para presencia y completado)
     * @param yo código de la sesión actual (se excluye a sí mismo)
     */
    public static List<RecienteChat> armar(List<RecienteChat> base,
                                           List<UsuarioLocal> directorio, String yo) {
        Set<String> conectados = new HashSet<>();
        for (UsuarioLocal u : directorio) {
            if (u.conectado()) {
                conectados.add(u.codigo());
            }
        }
        List<RecienteChat> salida = conPresencia(base, conectados);
        ordenar(salida);
        agregarSinConversacion(salida, directorio, yo, conectados);
        return salida;
    }

    private static List<RecienteChat> conPresencia(List<RecienteChat> base,
                                                  Set<String> conectados) {
        List<RecienteChat> salida = new ArrayList<>();
        for (RecienteChat r : base) {
            salida.add(new RecienteChat(r.otro(), r.ultimoContenido(), r.ultimoTipo(),
                    r.fechaUltimo(), r.noLeidos(), conectados.contains(r.otro())));
        }
        return salida;
    }

    private static void ordenar(List<RecienteChat> salida) {
        // Orden tipo WhatsApp (paridad con .NET y Python): la fecha del último
        // mensaje manda; la presencia solo desempata a fecha igual, así una
        // entrada/salida de línea no mueve conversaciones bajo el dedo.
        salida.sort((a, b) -> {
            int porFecha = b.fechaUltimo().compareTo(a.fechaUltimo());
            if (porFecha != 0) {
                return porFecha;
            }
            return Boolean.compare(b.conectado(), a.conectado());
        });
    }

    private static void agregarSinConversacion(List<RecienteChat> salida,
                                               List<UsuarioLocal> directorio,
                                               String yo, Set<String> conectados) {
        // Al final y determinista: conectados primero, luego por apellidos.
        List<UsuarioLocal> sinConversacion = new ArrayList<>();
        for (UsuarioLocal u : directorio) {
            if (u.codigo().equals(yo)) {
                continue;
            }
            boolean ya = salida.stream().anyMatch(r -> r.otro().equals(u.codigo()));
            if (!ya) {
                sinConversacion.add(u);
            }
        }
        sinConversacion.sort((a, b) -> {
            if (a.conectado() != b.conectado()) {
                return a.conectado() ? -1 : 1;
            }
            String apellidosA = a.apellidos() == null ? "" : a.apellidos();
            String apellidosB = b.apellidos() == null ? "" : b.apellidos();
            int porApellidos = apellidosA.compareToIgnoreCase(apellidosB);
            return porApellidos != 0 ? porApellidos
                    : a.codigo().compareTo(b.codigo());
        });
        for (UsuarioLocal u : sinConversacion) {
            salida.add(new RecienteChat(u.codigo(), "", "", "", 0,
                    conectados.contains(u.codigo())));
        }
    }
}
