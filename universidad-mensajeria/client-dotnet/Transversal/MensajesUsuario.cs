using System;
using System.IO;
using System.Net.Sockets;
using System.Threading.Tasks;

namespace Mensajeria.Transversal
{
    /// <summary>Traduce fallos de transporte y del servidor a mensajes de interfaz.</summary>
    public static class MensajesUsuario
    {
        public const string CredencialesInvalidas = "Código o contraseña incorrectos.";

        public static string Conexion(Exception? error)
        {
            if (EsTiempoAgotado(error))
                return "El servidor tardó demasiado en responder. Intenta de nuevo.";
            if (EsTransporte(error))
                return "No fue posible conectar con el servidor. Verifica que esté activo e inténtalo de nuevo.";
            return "No fue posible iniciar sesión. Verifica tus datos e inténtalo de nuevo.";
        }

        public static string Accion(Exception? error, string accion)
        {
            if (EsTiempoAgotado(error))
                return $"El servidor tardó demasiado en {accion}. Intenta de nuevo.";
            if (EsTransporte(error))
                return $"Se perdió la conexión. No se pudo {accion}; inténtalo de nuevo cuando vuelva el servidor.";
            return $"No se pudo {accion}. Revisa los datos e inténtalo de nuevo.";
        }

        public static string Archivo(Exception? error) => EsTransporte(error)
            ? "No se pudo acceder al archivo. Comprueba tu conexión e inténtalo de nuevo."
            : "No se pudo leer el archivo. Comprueba que siga disponible y que tengas acceso.";

        public static string SesionCerrada => "Tu sesión se cerró. Inicia sesión nuevamente.";

        private static bool EsTiempoAgotado(Exception? error)
        {
            for (var actual = error; actual != null; actual = actual.InnerException)
                if (actual is TimeoutException or TaskCanceledException) return true;
            return false;
        }

        private static bool EsTransporte(Exception? error)
        {
            for (var actual = error; actual != null; actual = actual.InnerException)
                if (actual is IOException or SocketException) return true;
            return false;
        }
    }
}
