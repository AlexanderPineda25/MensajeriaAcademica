using System;
using System.Collections.Generic;
using System.IO;
using Microsoft.Data.Sqlite;

namespace Mensajeria.Store
{
    public class LocalStore : IDisposable
    {
        private readonly SqliteConnection _conn;
        public string DbPath { get; }
        // SqliteConnection se comparte entre la UI y el lector de red; los DAO
        // usan este candado común para serializar cualquier acceso al handle.
        public object SyncRoot { get; } = new();

        public LocalStore(string dbPath)
        {
            string expandedPath = ExpandirRuta(dbPath);
            DbPath = expandedPath;

            string? dir = Path.GetDirectoryName(expandedPath);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);

            // Concurrencia multi-proceso (dos sesiones misma cuenta): WAL + espera acotada.
            _conn = new SqliteConnection($"Data Source={expandedPath};Cache=Shared;Mode=ReadWriteCreate;Default Timeout=30");
            _conn.Open();
            AplicarConcurrencia();
            Inicializar();
        }

        public SqliteConnection Connection => _conn;

        // ---------- Resolución por cuenta (convención compartida Python/.NET) ----------
        // Base "<base>.db" + cuenta "A001" => "<base>_A001.db". Misma ruta para los
        // dos clientes dada la misma db.url base y la misma cuenta.
        public static string ExpandirRuta(string dbPath)
        {
            if (dbPath.StartsWith("sqlite:///")) dbPath = dbPath["sqlite:///".Length..];
            return dbPath.Replace("~", Environment.GetFolderPath(Environment.SpecialFolder.UserProfile));
        }

        public static string SanitizarCuenta(string cuenta)
        {
            if (string.IsNullOrWhiteSpace(cuenta)) throw new ArgumentException("cuenta vacía");
            var sb = new System.Text.StringBuilder();
            foreach (char c in cuenta.Trim())
                sb.Append(char.IsLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
            string s = sb.ToString().Trim('_');
            if (s.Length == 0) throw new ArgumentException("cuenta sin caracteres válidos");
            return s;
        }

        public static string RutaParaCuenta(string dbUrlOCamino, string cuenta)
        {
            string base_ = ExpandirRuta(dbUrlOCamino);
            string limpia = SanitizarCuenta(cuenta);
            string dir = Path.GetDirectoryName(base_) ?? "";
            string nombre = Path.GetFileName(base_);
            string stem = Path.GetFileNameWithoutExtension(nombre);
            string ext = Path.GetExtension(nombre);
            if (string.IsNullOrEmpty(ext)) ext = ".db";
            // Evita doble sufijo si ya termina en _<cuenta>.
            if (stem.EndsWith("_" + limpia, StringComparison.OrdinalIgnoreCase))
                return Path.Combine(dir, stem + ext);
            return Path.Combine(dir, stem + "_" + limpia + ext);
        }

        public static string RutaLegada(string dbUrlOCamino) => ExpandirRuta(dbUrlOCamino);

        /// <summary>
        /// Migración idempotente legado -&gt; archivo por cuenta. Copia mensajes a la
        /// cuenta, recalcula enviado según la cuenta (origen==cuenta?1:0), reparte
        /// pendientes según su origen y copia caché sin duplicar. Mantiene el
        /// archivo legado intacto como respaldo. Nunca hace retroceder estado.
        /// </summary>
        public static void MigrarLegadoACuenta(string rutaLegada, string rutaCuenta, string cuenta)
        {
            string leg = ExpandirRuta(rutaLegada);
            string cta = ExpandirRuta(rutaCuenta);
            if (string.Equals(leg, cta, StringComparison.OrdinalIgnoreCase)) return;
            if (!File.Exists(leg)) return;
            string? dir = Path.GetDirectoryName(cta);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);

            using var connLeg = new SqliteConnection($"Data Source={leg};Mode=ReadOnly;Default Timeout=30");
            connLeg.Open();
            if (!TablaExiste(connLeg, "historial_local")) return;

            using var connCta = new SqliteConnection($"Data Source={cta};Cache=Shared;Mode=ReadWriteCreate;Default Timeout=30");
            connCta.Open();
            using (var cmd = connCta.CreateCommand())
            {
                cmd.CommandText = "PRAGMA journal_mode=WAL;"; cmd.ExecuteNonQuery();
            }
            using (var cmd = connCta.CreateCommand())
            {
                cmd.CommandText = "PRAGMA busy_timeout=5000;"; cmd.ExecuteNonQuery();
            }
            // Asegura esquema en destino (usa LocalStore para el fallback si falta el .sql).
            connCta.Close();
            try { using var init = new LocalStore(cta) { }; } catch { }
            connCta.Open();
            using (var cmd = connCta.CreateCommand())
            {
                cmd.CommandText = "PRAGMA journal_mode=WAL;"; try { cmd.ExecuteNonQuery(); } catch { }
            }
            using (var cmd = connCta.CreateCommand())
            {
                cmd.CommandText = "PRAGMA busy_timeout=5000;"; try { cmd.ExecuteNonQuery(); } catch { }
            }
            // Una sola migracion para las dos rutas (MUERTE-5): antes habia
            // dos copias del mismo "anade estado + archivo_id si falta". Aqui
            // se mantiene el silencio que tenia la ruta de legado.
            try { MigrarHistorial(connCta); } catch { }

            // Lee legado.
            var filas = new List<Dictionary<string, object?>>();
            var columnasLegado = ColumnasTabla(connLeg, "historial_local");
            string archivoIdSql = columnasLegado.Contains("archivo_id")
                ? "archivo_id" : "NULL AS archivo_id";
            string estadoSql = columnasLegado.Contains("estado")
                ? "estado" : "'ENVIADO' AS estado";
            string etapasSql = columnasLegado.Contains("etapas_filtrado")
                ? "etapas_filtrado" : "NULL AS etapas_filtrado";
            using (var cmd = connLeg.CreateCommand())
            {
                cmd.CommandText = $@"SELECT id_mensaje, origen, destino, tipo, contenido, hash_sha256,
                    num_caracteres, num_palabras, nombre_archivo, {archivoIdSql}, ruta_archivo,
                    tamano_archivo, fecha_envio, enviado, descargado, {estadoSql}, {etapasSql}
                    FROM historial_local";
                try
                {
                    using var r = cmd.ExecuteReader();
                    while (r.Read())
                    {
                        var d = new Dictionary<string, object?>();
                        for (int i = 0; i < r.FieldCount; i++)
                            d[r.GetName(i)] = r.IsDBNull(i) ? null : r.GetValue(i);
                        filas.Add(d);
                    }
                }
                catch { return; }
            }
            // Inserta idempotente con enviado recalculado y estado monotónico.
            foreach (var f in filas)
            {
                string? id = f["id_mensaje"]?.ToString(); if (id == null) continue;
                string origen = f["origen"]?.ToString() ?? "";
                int enviado = origen == cuenta ? 1 : 0;
                using var cmd = connCta.CreateCommand();
                cmd.CommandText = @"
INSERT INTO historial_local (id_mensaje, origen, destino, tipo, contenido, hash_sha256,
    num_caracteres, num_palabras, nombre_archivo, ruta_archivo, tamano_archivo,
    fecha_envio, enviado, descargado, estado, archivo_id, etapas_filtrado)
VALUES (@id,@ori,@des,@tip,@con,@has,@nca,@npa,@nar,@rar,@tar,@fen,@env,@desca,@est,@aid,@etapas)
ON CONFLICT(id_mensaje) DO UPDATE SET
    descargado = MAX(excluded.descargado, historial_local.descargado),
    archivo_id = COALESCE(excluded.archivo_id, historial_local.archivo_id),
    etapas_filtrado = COALESCE(excluded.etapas_filtrado, historial_local.etapas_filtrado),
    ruta_archivo = COALESCE(excluded.ruta_archivo, historial_local.ruta_archivo),
    estado = CASE
        WHEN historial_local.estado = 'LEIDO' OR excluded.estado = 'LEIDO' THEN 'LEIDO'
        WHEN historial_local.estado = 'ENTREGADO' AND excluded.estado IN ('PENDIENTE','ENVIADO') THEN 'ENTREGADO'
        WHEN historial_local.estado = 'ENVIADO' AND excluded.estado = 'PENDIENTE' THEN 'ENVIADO'
        ELSE excluded.estado END";
                cmd.Parameters.AddWithValue("@id", id);
                cmd.Parameters.AddWithValue("@ori", origen);
                cmd.Parameters.AddWithValue("@des", f["destino"]?.ToString() ?? "");
                cmd.Parameters.AddWithValue("@tip", f["tipo"]?.ToString() ?? "MENSAJE_TEXTO");
                cmd.Parameters.AddWithValue("@con", f["contenido"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@has", f["hash_sha256"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@nca", f["num_caracteres"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@npa", f["num_palabras"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@nar", f["nombre_archivo"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@rar", f["ruta_archivo"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@tar", f["tamano_archivo"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@fen", f["fecha_envio"]?.ToString() ?? DateTime.UtcNow.ToString("O"));
                cmd.Parameters.AddWithValue("@env", enviado);
                cmd.Parameters.AddWithValue("@desca", f["descargado"] ?? 0);
                cmd.Parameters.AddWithValue("@est", f["estado"]?.ToString() ?? "ENVIADO");
                cmd.Parameters.AddWithValue("@aid", f["archivo_id"] ?? DBNull.Value);
                cmd.Parameters.AddWithValue("@etapas", f["etapas_filtrado"] ?? DBNull.Value);
                try { cmd.ExecuteNonQuery(); } catch { }
            }
            // Pendientes: solo los originados por esta cuenta.
            try
            {
                if (TablaExiste(connLeg, "pendientes_envio"))
                {
                    using var cmd = connLeg.CreateCommand();
                    cmd.CommandText = @"SELECT id, tipo, origen, destino, contenido, nombre_archivo,
                        payload, fecha_creado, intentos, ultimo_error FROM pendientes_envio WHERE origen = @o";
                    cmd.Parameters.AddWithValue("@o", cuenta);
                    using var r = cmd.ExecuteReader();
                    var pends = new List<Dictionary<string, object?>>();
                    while (r.Read())
                    {
                        var d = new Dictionary<string, object?>();
                        for (int i = 0; i < r.FieldCount; i++)
                            d[r.GetName(i)] = r.IsDBNull(i) ? null : r.GetValue(i);
                        pends.Add(d);
                    }
                    r.Close();
                    foreach (var p in pends)
                    {
                        using var ins = connCta.CreateCommand();
                        ins.CommandText = @"INSERT OR IGNORE INTO pendientes_envio
                            (id, tipo, origen, destino, contenido, nombre_archivo, payload, fecha_creado, intentos, ultimo_error)
                            VALUES (@id,@tip,@ori,@des,@con,@nar,@pay,@fec,@int,@err)";
                        ins.Parameters.AddWithValue("@id", p["id"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@tip", p["tipo"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@ori", p["origen"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@des", p["destino"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@con", p["contenido"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@nar", p["nombre_archivo"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@pay", p["payload"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@fec", p["fecha_creado"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@int", p["intentos"] ?? 0);
                        ins.Parameters.AddWithValue("@err", p["ultimo_error"] ?? DBNull.Value);
                        try { ins.ExecuteNonQuery(); } catch { }
                    }
                }
            }
            catch { }
            // Caché: copia sin duplicar.
            try
            {
                if (TablaExiste(connLeg, "cache_usuarios"))
                {
                    using var cmd = connLeg.CreateCommand();
                    cmd.CommandText = @"SELECT codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado FROM cache_usuarios";
                    using var r = cmd.ExecuteReader();
                    var users = new List<Dictionary<string, object?>>();
                    while (r.Read())
                    {
                        var d = new Dictionary<string, object?>();
                        for (int i = 0; i < r.FieldCount; i++)
                            d[r.GetName(i)] = r.IsDBNull(i) ? null : r.GetValue(i);
                        users.Add(d);
                    }
                    r.Close();
                    foreach (var u in users)
                    {
                        using var ins = connCta.CreateCommand();
                        ins.CommandText = @"INSERT OR IGNORE INTO cache_usuarios
                            (codigo, nombres, apellidos, programa, conectado, fecha_registro, actualizado)
                            VALUES (@c,@n,@a,@p,@con,@fr,@act)";
                        ins.Parameters.AddWithValue("@c", u["codigo"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@n", u["nombres"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@a", u["apellidos"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@p", u["programa"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@con", u["conectado"] ?? 0);
                        ins.Parameters.AddWithValue("@fr", u["fecha_registro"] ?? DBNull.Value);
                        ins.Parameters.AddWithValue("@act", u["actualizado"] ?? DateTime.UtcNow.ToString("O"));
                        try { ins.ExecuteNonQuery(); } catch { }
                    }
                }
            }
            catch { }
        }

        private static bool TablaExiste(SqliteConnection conn, string tabla)
        {
            using var cmd = conn.CreateCommand();
            cmd.CommandText = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=@t";
            cmd.Parameters.AddWithValue("@t", tabla);
            try { return Convert.ToInt32(cmd.ExecuteScalar()) > 0; } catch { return false; }
        }

        private static HashSet<string> ColumnasTabla(SqliteConnection conn, string tabla,
            SqliteTransaction? tx = null)
        {
            var columnas = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using var cmd = conn.CreateCommand();
            cmd.Transaction = tx;
            cmd.CommandText = $"PRAGMA table_info({tabla})";
            using var reader = cmd.ExecuteReader();
            while (reader.Read()) columnas.Add(reader.GetString(1));
            return columnas;
        }

        /// Migracion idempotente de columnas de historial.
        /// Antes existian dos copias: AsegurarColumnas (migracion de legado,
        /// sin transaccion y con los errores tragados) y MigrarHistorial
        /// (arranque, con transaccion). Mismo SQL, dos fuentes de verdad.
        private static void MigrarHistorial(SqliteConnection conn, SqliteTransaction? tx = null)
        {
            var columnas = ColumnasTabla(conn, "historial_local", tx);
            if (!columnas.Contains("estado"))
            {
                using var cmd = conn.CreateCommand();
                cmd.Transaction = tx;
                cmd.CommandText = "ALTER TABLE historial_local ADD COLUMN estado TEXT NOT NULL DEFAULT 'ENVIADO'";
                cmd.ExecuteNonQuery();
            }
            if (!columnas.Contains("archivo_id"))
            {
                using var cmd = conn.CreateCommand();
                cmd.Transaction = tx;
                cmd.CommandText = "ALTER TABLE historial_local ADD COLUMN archivo_id TEXT";
                cmd.ExecuteNonQuery();
            }
            if (!columnas.Contains("etapas_filtrado"))
            {
                using var cmd = conn.CreateCommand();
                cmd.Transaction = tx;
                cmd.CommandText = "ALTER TABLE historial_local ADD COLUMN etapas_filtrado TEXT";
                cmd.ExecuteNonQuery();
            }
        }

        private void AplicarConcurrencia()
        {
            foreach (string pragma in new[] { "PRAGMA journal_mode=WAL;", "PRAGMA busy_timeout=5000;", "PRAGMA synchronous=NORMAL;" })
            {
                try
                {
                    using var cmd = _conn.CreateCommand();
                    cmd.CommandText = pragma; cmd.ExecuteNonQuery();
                }
                catch { }
            }
        }

        private void Inicializar()
        {
            // El DDL tiene UNA sola copia: Store/schema-local.sql, que
            // Mensajeria.csproj copia al salida (MUERTE-5). Antes existia una
            // tercera copia incrustada aqui como "fallback" que era
            // inalcanzable y, si se hubiera alcanzado, habria creado un
            // esquema divergent sin avisar. Ahora, si falta, se dice.
            string schemaPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Store", "schema-local.sql");
            if (!File.Exists(schemaPath))
            {
                throw new FileNotFoundException(
                    "Falta schema-local.sql junto al ejecutable; no se puede crear el historial local",
                    schemaPath);
            }
            string schema = File.ReadAllText(schemaPath);
            using (var cmd = _conn.CreateCommand())
            {
                cmd.CommandText = schema;
                cmd.ExecuteNonQuery();
            }
            using (var tx = _conn.BeginTransaction())
            {
                MigrarHistorial(_conn, tx);
                tx.Commit();
            }
        }

        public void Dispose()
        {
            lock (SyncRoot)
            {
                _conn.Close();
                _conn.Dispose();
            }
        }
    }
}
