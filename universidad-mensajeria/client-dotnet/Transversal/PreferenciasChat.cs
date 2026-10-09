using System;
using System.Collections.Generic;

namespace Mensajeria.Transversal
{
    /// <summary>Preferencias del chat guardadas con una clave por cuenta.</summary>
    internal static class PreferenciasChat
    {
        public static string? UltimoChat(string? codigoCuenta)
        {
            if (string.IsNullOrWhiteSpace(codigoCuenta)) return null;
            string valor = ClienteConfig.Valor(ClaveUltimoChat(codigoCuenta), "");
            return string.IsNullOrWhiteSpace(valor) ? null : valor;
        }

        public static void GuardarUltimoChat(string? codigoCuenta, string? codigoChat)
        {
            if (string.IsNullOrWhiteSpace(codigoCuenta)
                || string.IsNullOrWhiteSpace(codigoChat)
                || string.Equals(codigoCuenta, codigoChat, StringComparison.OrdinalIgnoreCase))
                return;
            ClienteConfig.Guardar(ClaveUltimoChat(codigoCuenta), codigoChat);
        }

        public static string? ResolverSeleccion(string? ultimoChat,
            string? seleccionActual, IEnumerable<string> contactosDisponibles)
        {
            if (!string.IsNullOrWhiteSpace(seleccionActual)) return seleccionActual;
            if (string.IsNullOrWhiteSpace(ultimoChat)) return null;
            foreach (string codigo in contactosDisponibles)
            {
                if (string.Equals(codigo, ultimoChat, StringComparison.OrdinalIgnoreCase))
                    return codigo;
            }
            return null;
        }

        private static string ClaveUltimoChat(string codigoCuenta) =>
            $"ultimoChat_{codigoCuenta.Trim()}";
    }
}
