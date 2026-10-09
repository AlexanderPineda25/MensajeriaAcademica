package universidad.mensajeria.cliente.componentes.cache;

import universidad.mensajeria.cliente.persistencia.InterfazAlmacenLocal;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionHistorial;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caché H2 de usuarios con lectura directa en memoria (patrón Cache real).
 * Escrituras atraviesan a H2 e invalidan la entrada; lecturas usan LRU.
 */
public final class CacheUsuarios implements InterfazCacheUsuarios {

    private static final int TOPE_MEMORIA = 500;

    // HISTORIAL_LOCAL.md §6
    private static final String BORRAR_USUARIO = "DELETE FROM cache_usuarios WHERE codigo = ?";
    private static final String INSERTAR_USUARIO = """
            INSERT INTO cache_usuarios (codigo, nombres, apellidos, programa,
                conectado, fecha_registro, actualizado)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

    // HISTORIAL_LOCAL.md §7
    private static final String LISTAR_CACHE = """
            SELECT codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado
            FROM cache_usuarios
            ORDER BY conectado DESC, apellidos ASC""";
    private static final String POR_CODIGO = """
            SELECT codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado
            FROM cache_usuarios
            WHERE codigo = ?""";
    private static final String MARCAR_CONECTADO = """
            UPDATE cache_usuarios SET conectado = 1 WHERE codigo = ?""";
    private static final String MARCAR_TODOS_DESCONECTADOS = """
            UPDATE cache_usuarios SET conectado = 0""";
    private static final String BORRAR_DIRECTORIO = "DELETE FROM cache_usuarios";

    private final InterfazAlmacenLocal store;
    private final Map<String, UsuarioLocal> memoria =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, UsuarioLocal> eldest) {
                    return size() > TOPE_MEMORIA;
                }
            });

    public CacheUsuarios(InterfazAlmacenLocal store) {
        this.store = store;
    }

    @Override
    public void guardar(UsuarioLocal usuario) throws ExcepcionCliente {
        try (Connection conn = store.abrir();
             PreparedStatement borrar = conn.prepareStatement(BORRAR_USUARIO);
             PreparedStatement insertar = conn.prepareStatement(INSERTAR_USUARIO)) {
            borrar.setString(1, usuario.codigo());
            borrar.executeUpdate();
            insertar.setString(1, usuario.codigo());
            insertar.setString(2, usuario.nombres());
            insertar.setString(3, usuario.apellidos());
            insertar.setString(4, usuario.programa());
            insertar.setInt(5, usuario.conectado() ? 1 : 0);
            insertar.setString(6, usuario.fechaRegistro());
            insertar.setString(7, usuario.actualizado());
            insertar.executeUpdate();
            memoria.put(usuario.codigo(), usuario);
        } catch (SQLException fallo) {
            throw new ExcepcionHistorial("no se pudo guardar el usuario local", fallo);
        }
    }

    @Override
    public void reemplazarDirectorio(List<UsuarioLocal> usuarios) throws ExcepcionCliente {
        try (Connection conn = store.abrir();
             PreparedStatement borrar = conn.prepareStatement(BORRAR_DIRECTORIO);
             PreparedStatement insertar = conn.prepareStatement(INSERTAR_USUARIO)) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                borrar.executeUpdate();
                for (UsuarioLocal usuario : usuarios) {
                    insertar.setString(1, usuario.codigo());
                    insertar.setString(2, usuario.nombres());
                    insertar.setString(3, usuario.apellidos());
                    insertar.setString(4, usuario.programa());
                    insertar.setInt(5, usuario.conectado() ? 1 : 0);
                    insertar.setString(6, usuario.fechaRegistro());
                    insertar.setString(7, usuario.actualizado());
                    insertar.addBatch();
                }
                insertar.executeBatch();
                conn.commit();
                memoria.clear();
                for (UsuarioLocal usuario : usuarios) {
                    memoria.put(usuario.codigo(), usuario);
                }
            } catch (SQLException fallo) {
                conn.rollback();
                throw new ExcepcionHistorial("no se pudo reemplazar el directorio", fallo);
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException fallo) {
            throw new ExcepcionHistorial("no se pudo reemplazar el directorio", fallo);
        }
    }

    @Override
    public List<UsuarioLocal> listar() throws ExcepcionCliente {
        try (Connection conn = store.abrir();
             PreparedStatement q = conn.prepareStatement(LISTAR_CACHE);
             ResultSet rs = q.executeQuery()) {
            List<UsuarioLocal> filas = new ArrayList<>();
            while (rs.next()) {
                filas.add(leer(rs));
            }
            return filas;
        } catch (SQLException fallo) {
            throw new ExcepcionHistorial("no se pudo listar el directorio local", fallo);
        }
    }

    @Override
    public Optional<UsuarioLocal> porCodigo(String codigo) throws ExcepcionCliente {
        UsuarioLocal enMemoria = memoria.get(codigo);
        if (enMemoria != null) {
            return Optional.of(enMemoria);
        }
        try (Connection conn = store.abrir();
             PreparedStatement q = conn.prepareStatement(POR_CODIGO)) {
            q.setString(1, codigo);
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UsuarioLocal usuario = leer(rs);
                memoria.put(codigo, usuario);
                return Optional.of(usuario);
            }
        } catch (SQLException fallo) {
            throw new ExcepcionHistorial("no se pudo buscar el usuario local", fallo);
        }
    }

    @Override
    public void marcarConectados(List<String> codigos) throws ExcepcionCliente {
        try (Connection conn = store.abrir();
             PreparedStatement todos = conn.prepareStatement(MARCAR_TODOS_DESCONECTADOS);
             PreparedStatement uno = conn.prepareStatement(MARCAR_CONECTADO)) {
            todos.executeUpdate();
            for (String codigo : codigos) {
                uno.setString(1, codigo);
                uno.executeUpdate();
            }
            memoria.clear();
        } catch (SQLException fallo) {
            throw new ExcepcionHistorial("no se pudo marcar presencia local", fallo);
        }
    }

    private static UsuarioLocal leer(ResultSet rs) throws SQLException {
        return new UsuarioLocal(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5) == 1, rs.getString(6), rs.getString(7));
    }
}
