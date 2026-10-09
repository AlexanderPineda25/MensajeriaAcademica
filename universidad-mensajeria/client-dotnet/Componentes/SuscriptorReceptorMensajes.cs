using System;
using System.Collections.Generic;
using System.Text.Json;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>
    /// Puente de compatibilidad para implementaciones de IConexionRed que aún
    /// publican eventos crudos. ConexionRed usa el mismo mapeador directamente
    /// para poder entregar también errores de lectura.
    /// </summary>
    internal static class SuscriptorReceptorMensajes
    {
        internal static void Suscribir(IConexionRed conexion, IReceptorMensajes receptor)
        {
            ArgumentNullException.ThrowIfNull(conexion);
            ArgumentNullException.ThrowIfNull(receptor);
            conexion.OnMensajeRecibido += mensaje => Notificar(mensaje, receptor.AlRecibir, receptor);
            conexion.OnCierre += aviso => Notificar(aviso, receptor.AlCierre, receptor);
            conexion.OnDesconectado += receptor.AlDesconexion;
        }

        private static void Notificar(Dictionary<string, JsonElement> crudo,
            Action<RespuestaRed> callback, IReceptorMensajes receptor)
        {
            try
            {
                callback(MapeadorRespuestaRed.Mapear(crudo));
            }
            catch (Exception error)
            {
                try { receptor.AlError(error.Message); }
                catch { /* El callback de error no debe interrumpir el lector. */ }
            }
        }
    }
}
