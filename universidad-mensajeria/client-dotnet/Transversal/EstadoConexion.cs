namespace Mensajeria.Transversal
{
    /// <summary>
    /// PAT-03: los 4 estados de la conexión, observables por la vista.
    ///
    /// Réplica exacta de <c>EstadoConexion.java</c> y <c>estado_conexion.py</c>.
    /// Antes .NET no se enteraba de la caída: <c>OnDesconectado</c> existía en
    /// el transporte y en la fachada, pero ninguna vista se suscribía y el
    /// banner ámbar del XAML tenía <c>Visibility="Collapsed"</c> fijo (UIX-00a).
    /// Los textos son los mismos en los 3 clientes (contrato compartido, VIS-02).
    /// </summary>
    public enum EstadoConexion
    {
        /// <summary>Hay un intento de conexión en curso.</summary>
        Conectando,

        /// <summary>Socket vivo y sesión iniciada.</summary>
        Conectado,

        /// <summary>Se perdió la conexión o falló una operación. Recuperable.</summary>
        SinRed,

        /// <summary>El servidor cerró la sesión (CLOSE_NOTICE) o el usuario salió.</summary>
        SesionCerrada
    }

    /// <summary>Textos y semántica de <see cref="EstadoConexion"/>, compartidos.</summary>
    public static class EstadoConexionTexto
    {
        /// <summary>Texto para la etiqueta de estado de la cabecera.</summary>
        public static string Texto(EstadoConexion estado) => estado switch
        {
            EstadoConexion.Conectando => "Conectando…",
            EstadoConexion.SinRed => "Sin conexión — mensajes en cola local, se reintentan solos",
            EstadoConexion.SesionCerrada => "Sesión cerrada",
            _ => "En línea"
        };

        /// <summary>
        /// true si el estado no permite trabajar en red (banner ámbar + aviso).
        /// Un cierre de sesión también degrada: hay que volver a entrar.
        /// </summary>
        public static bool MuestraBanner(EstadoConexion estado) =>
            estado == EstadoConexion.SinRed || estado == EstadoConexion.SesionCerrada;
    }
}