using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Threading.Tasks;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>Contrato de red (espejo de InterfazConexionRed Java).</summary>
    public interface IConexionRed
    {
        event Action<Dictionary<string, JsonElement>>? OnMensajeRecibido;
        event Action<Dictionary<string, JsonElement>>? OnCierre;
        event Action? OnDesconectado;

        /// <summary>
        /// Suscribe un consumidor con respuestas neutrales. Implementadores
        /// antiguos heredan el puente de compatibilidad basado en sus eventos.
        /// </summary>
        void Suscribir(IReceptorMensajes receptor) =>
            SuscriptorReceptorMensajes.Suscribir(this, receptor);

        Task ConectarAsync(string host, int puerto);
        void Desconectar();
        void Enviar(Dictionary<string, object?> msg);
        Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg);
        Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg, TimeSpan timeout);
    }
}
