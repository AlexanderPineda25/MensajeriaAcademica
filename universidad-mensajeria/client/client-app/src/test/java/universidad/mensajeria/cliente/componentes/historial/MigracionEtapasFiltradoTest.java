package universidad.mensajeria.cliente.componentes.historial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import universidad.mensajeria.cliente.persistencia.LocalStore;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

class MigracionEtapasFiltradoTest {

    @Test
    void agregaLaColumnaAUnaBaseExistenteSinPerderFilasYEsIdempotente() throws Exception {
        Properties propiedades = new Properties();
        propiedades.setProperty("db.url", "jdbc:h2:mem:migracionEtapas"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        LocalStore store = new LocalStore(ClienteConfig.desde(propiedades));
        try (Connection conexion = store.abrir(); Statement ddl = conexion.createStatement()) {
            ddl.execute("""
                    CREATE TABLE historial_local (
                        id_mensaje TEXT PRIMARY KEY, origen TEXT NOT NULL, destino TEXT NOT NULL,
                        tipo TEXT NOT NULL, contenido TEXT, hash_sha256 TEXT,
                        num_caracteres INTEGER, num_palabras INTEGER, nombre_archivo TEXT,
                        archivo_id TEXT, ruta_archivo TEXT, tamano_archivo INTEGER,
                        fecha_envio TEXT NOT NULL, enviado INTEGER NOT NULL DEFAULT 0,
                        descargado INTEGER NOT NULL DEFAULT 0,
                        estado TEXT NOT NULL DEFAULT 'ENVIADO')
                    """);
            ddl.execute("INSERT INTO historial_local (id_mensaje, origen, destino, tipo, fecha_envio)"
                    + " VALUES ('anterior', 'A001', 'B002', 'MENSAJE_IMAGEN', '2026-01-01')");
        }

        store.inicializar();
        store.inicializar();

        try (Connection conexion = store.abrir();
             PreparedStatement consulta = conexion.prepareStatement(
                     "SELECT etapas_filtrado FROM historial_local WHERE id_mensaje = ?")) {
            consulta.setString(1, "anterior");
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                assertNull(fila.getString(1));
            }
        }
        assertEquals(1, new HistorialLocal(store).contar("A001", "B002"));
        store.cerrar();
    }
}
