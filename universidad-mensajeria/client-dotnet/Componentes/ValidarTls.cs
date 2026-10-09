using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;

namespace Mensajeria.Componentes
{
    /// <summary>
    /// TLS *fail-fast* del cliente .NET: espejo de <c>client-python/transversal/tls.py</c>
    /// (Fase 1.3 de PLAN_PARIDAD_CLIENTES.md, cierra SEG-01).
    ///
    /// <para><b>POR QUÉ falla antes de abrir el socket:</b> un
    /// <c>client.properties</c> mal escrito (dos servicios en el mismo puerto, un
    /// almacén de CA que no está en este PC) tiene que verse al arrancar y con un
    /// mensaje accionable, no a mitad de una conversación ya empezada.</para>
    ///
    /// <para><b>Los tres rechazos</b>, en el mismo orden y con el mismo criterio que
    /// Python:</para>
    /// <list type="number">
    /// <item><c>tls.habilitado</c> con un valor que no sea <c>true</c>/<c>false</c>.</item>
    /// <item>TLS y plano en el MISMO puerto fijo. <c>0</c> significa efímero (sigue al
    /// puerto plano) y por eso no colisiona.</item>
    /// <item><c>tls.confianza</c> indicado pero el fichero no existe.</item>
    /// </list>
    ///
    /// <para><b>Camino por defecto:</b> sin <c>tls.confianza</c> se valida la cadena
    /// con el almacén del sistema, que es lo correcto. Este tipo NUNCA devuelve un
    /// callback que acepte cualquier certificado: eso desactivaría TLS sin avisar.</para>
    /// </summary>
    public static class ValidarTls
    {
        /// <summary>Puerto TLS usado cuando <c>tls.puerto</c> no es un entero.</summary>
        public const int PuertoPorDefecto = 5001;

        /// <summary>Valor de <c>tls.puerto</c> que significa "sigue al puerto plano".</summary>
        public const int PuertoEfimero = 0;

        /// <summary>Texto aceptado como "TLS activo" en <c>client.properties</c>.</summary>
        public const string ValorHabilitado = "true";

        /// <summary>Texto aceptado como "TLS inactivo" en <c>client.properties</c>.</summary>
        public const string ValorDeshabilitado = "false";

        /// <summary>
        /// Rechazo 1: <c>tls.habilitado</c> mal formado. Acepta <c>true</c>/<c>false</c>
        /// sin distinguir mayúsculas ni espacios; ausente o vacío significa apagado.
        /// </summary>
        public static bool HabilitadoDesdeTexto(string? texto)
        {
            if (texto == null) return false;
            string t = texto.Trim();
            if (t.Length == 0) return false;
            if (t.Equals(ValorHabilitado, StringComparison.OrdinalIgnoreCase)) return true;
            if (t.Equals(ValorDeshabilitado, StringComparison.OrdinalIgnoreCase)) return false;
            throw new ArgumentException(
                $"tls.habilitado mal formado: '{texto}'. Admite solo '{ValorHabilitado}' o "
                + $"'{ValorDeshabilitado}' en client.properties", nameof(texto));
        }

        /// <summary>
        /// <c>tls.puerto</c> como entero. Un valor que no sea un número cae al
        /// defecto en vez de tumbar el arranque (mismo criterio que Python).
        /// </summary>
        public static int PuertoTlsDesdeTexto(string? texto)
        {
            if (string.IsNullOrWhiteSpace(texto)) return PuertoPorDefecto;
            return int.TryParse(texto.Trim(), out int puerto) ? puerto : PuertoPorDefecto;
        }

        /// <summary>
        /// Falla rápido ANTES de abrir ningún socket. Con TLS apagado no comprueba
        /// nada: un almacén inexistente es irrelevante si no se usa TLS.
        /// </summary>
        public static void Validar(bool habilitado, int puertoPlano, int puertoTls, string? confianza)
        {
            if (!habilitado) return;

            // Rechazo 2: dos sockets en el mismo puerto fijo. No se puede hablar
            // "plano o cifrado" en el mismo endpoint.
            if (puertoTls == puertoPlano && puertoTls != PuertoEfimero)
                throw new ArgumentException(
                    $"TLS y plano comparten el puerto {puertoPlano}: cambie tls.puerto a uno "
                    + "distinto de puerto en client.properties", nameof(puertoTls));

            if (string.IsNullOrWhiteSpace(confianza)) return;

            // Rechazo 3: almacén indicado pero ausente. Es mejor un arranque roto y
            // con nombre de fichero que una cadena que no valida nada.
            if (!File.Exists(confianza))
                throw new FileNotFoundException(
                    $"TLS habilitado pero el almacen de confianza no existe: {confianza} "
                    + "(revise tls.confianza: ruta a un fichero PEM con la CA; el almacen real "
                    + "nunca se versiona)", confianza);
        }

        /// <summary>
        /// Entrada con la forma de <c>client.properties</c>: los tres rechazos más el
        /// parseo tolerante de los dos valores numéricos.
        /// </summary>
        public static void ValidarDesdeTexto(string? habilitado, int puertoPlano,
            string? puertoTls, string? confianza)
            => Validar(HabilitadoDesdeTexto(habilitado), puertoPlano,
                PuertoTlsDesdeTexto(puertoTls), confianza);

        /// <summary>
        /// Callback de validación para <see cref="ConexionRed.ValidarCertificado"/>.
        /// Devuelve <c>null</c> cuando no hay <c>tls.confianza</c>: en ese caso el
        /// handshake usa la validación de cadena del sistema.
        /// </summary>
        /// <param name="confianza">Ruta del PEM con la CA propia; vacío = almacén del sistema.</param>
        /// <param name="host">Host contra el que se valida el nombre del certificado.</param>
        public static RemoteCertificateValidationCallback? CrearValidador(string? confianza, string host)
        {
            if (string.IsNullOrWhiteSpace(confianza)) return null;
            X509Certificate2Collection almacen = CargarAlmacen(confianza);
            return (remitente, certificado, cadena, errores) =>
                ValidarConAlmacen(certificado, errores, almacen, host);
        }

        private static bool ValidarConAlmacen(X509Certificate? certificado, SslPolicyErrors errores,
            X509Certificate2Collection almacen, string host)
        {
            if (certificado == null) return false; // sin certificado no hay nada que validar

            X509Certificate2? prestado = certificado as X509Certificate2;
            X509Certificate2? propio = null;
            try
            {
                // Rama de reserva: la plataforma puede entregar un X509Certificate que
                // no sea X509Certificate2. X509CertificateLoader es .NET 9+ y la app es
                // net8, así que aquí el constructor es la única vía disponible.
#pragma warning disable SYSLIB0057
                X509Certificate2 efectivo = prestado ?? (propio = new X509Certificate2(certificado.GetRawCertData()));
#pragma warning restore SYSLIB0057

                // El nombre se comprueba SIEMPRE, incluso con almacén propio: validar la
                // cadena contra nuestra CA no autoriza a presentar su certificado para
                // otro host, y el name mismatch es el ataque más trivial de todos.
                if (!VerificarNombre(efectivo, host)) return false;

                // Sin errores del sistema, la cadena ya la validó la plataforma: no se
                // toca. Solo se rehace cuando lo que falla es la confianza.
                if (errores == SslPolicyErrors.None) return true;
                if (errores.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch)) return false;

                using X509Chain cadenaPropia = NuevaCadenaPropia(almacen);
                return cadenaPropia.Build(efectivo);
            }
            finally { propio?.Dispose(); }
        }

        /// <summary>
        /// Cadena con confianza propia. <see cref="X509RevocationMode.NoCheck"/> porque
        /// un cliente de escritorio no puede fiarse de que la CRL del servidor sea
        /// alcanzable: sin CRL solo se acepta lo que la propia CA firma.
        /// </summary>
        private static X509Chain NuevaCadenaPropia(X509Certificate2Collection almacen)
        {
            var cadena = new X509Chain();
            cadena.ChainPolicy.RevocationMode = X509RevocationMode.NoCheck;
            cadena.ChainPolicy.DisableCertificateDownloads = true;
            cadena.ChainPolicy.TrustMode = X509ChainTrustMode.CustomRootTrust;
            foreach (X509Certificate ca in almacen)
                cadena.ChainPolicy.CustomTrustStore.Add(ca);
            return cadena;
        }

        private static X509Certificate2Collection CargarAlmacen(string ruta)
        {
            var almacen = new X509Certificate2Collection();
            try
            {
                almacen.ImportFromPemFile(ruta);
            }
            catch (Exception ex)
            {
                throw new ArgumentException(
                    $"TLS habilitado y tls.confianza no es un PEM legible: {ruta}", nameof(ruta), ex);
            }
            if (almacen.Count == 0)
                throw new ArgumentException(
                    $"tls.confianza no contiene ningun certificado PEM: {ruta}", nameof(ruta));
            return almacen;
        }

        /// <summary>
        /// RFC 6125: si hay subjectAltName, el CN no se mira. Solo se cae al CN cuando
        /// el certificado no declara ninguna alternativa.
        /// </summary>
        private static bool VerificarNombre(X509Certificate2 certificado, string host)
        {
            string objetivo = NormalizarHost(host);
            if (objetivo.Length == 0) return false;

            var dns = new List<string>();
            var ips = new List<string>();
            bool haySan = RecogerNombresAlternativos(certificado, dns, ips);

            bool esIp = IPAddress.TryParse(objetivo, out IPAddress? ipObjetivo);
            if (haySan)
            {
                if (esIp)
                    return ips.Any(n => IPAddress.TryParse(n, out IPAddress? alt) && alt.Equals(ipObjetivo));
                return dns.Any(n => CoincideNombre(n, objetivo));
            }

            if (esIp) return false;
            return CoincideNombre(certificado.GetNameInfo(X509NameType.SimpleName, forIssuer: false), objetivo);
        }

        private static bool RecogerNombresAlternativos(X509Certificate2 certificado,
            List<string> dns, List<string> ips)
        {
            foreach (X509Extension extension in certificado.Extensions)
            {
                // Se acepta solo la extensión ya decodificada; una SAN cruta o crítica
                // sin decodificar hace que la verificación falle (cerrado, no abierto).
                if (extension is not X509SubjectAlternativeNameExtension san) continue;
                dns.AddRange(san.EnumerateDnsNames());
                ips.AddRange(san.EnumerateIPAddresses().Select(d => d.ToString()));
                return true;
            }
            return false;
        }

        private static bool CoincideNombre(string? patron, string objetivo)
        {
            if (string.IsNullOrEmpty(patron)) return false;
            string p = patron.Trim().TrimEnd('.').ToLowerInvariant();
            if (p.Length == 0) return false;
            if (p == objetivo) return true;

            // Comodín: solo en la etiqueta izquierda y solo uno. `*` a secas o
            // `*.*.com` no authorizes nada.
            if (p.IndexOf('*') != 0 || p.IndexOf('*', 1) >= 0) return false;
            int punto = p.IndexOf('.');
            if (punto < 1) return false;
            int puntoObjetivo = objetivo.IndexOf('.');
            if (puntoObjetivo < 1) return false;
            return p[(punto + 1)..] == objetivo[(puntoObjetivo + 1)..];
        }

        private static string NormalizarHost(string? host)
        {
            string h = (host ?? "").Trim().TrimEnd('.');
            if (h.StartsWith("[", StringComparison.Ordinal) && h.EndsWith("]", StringComparison.Ordinal))
                h = h[1..^1];
            return h.ToLowerInvariant();
        }
    }
}