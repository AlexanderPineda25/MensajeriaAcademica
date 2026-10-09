using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>
    /// Cuatro callbacks por los que el transporte notifica eventos al cliente.
    /// El contrato de aplicación es <see cref="RespuestaRed"/>, no JSON crudo.
    /// </summary>
    public interface IReceptorMensajes
    {
        void AlRecibir(RespuestaRed mensaje);
        void AlCierre(RespuestaRed aviso);
        void AlError(string motivo);
        void AlDesconexion();
    }
}
