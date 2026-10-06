namespace Mensajeria.Transversal
{
    /// <summary>Clasifica los avisos visibles dentro de la conversación.</summary>
    public enum TipoAvisoInline
    {
        Error,
        Advertencia,
        Informacion,
        Exito
    }

    /// <summary>
    /// Estado simple del banner inline del chat. Mantiene la transición visible/oculto
    /// fuera del code-behind para que el comportamiento pueda probarse sin abrir WPF.
    /// </summary>
    public sealed class EstadoAvisoInline
    {
        public string Mensaje { get; private set; } = "";
        public TipoAvisoInline Tipo { get; private set; } = TipoAvisoInline.Informacion;
        public bool Visible { get; private set; }

        public void Mostrar(string? mensaje, TipoAvisoInline tipo)
        {
            if (string.IsNullOrWhiteSpace(mensaje))
            {
                Limpiar();
                return;
            }

            Mensaje = mensaje.Trim();
            Tipo = tipo;
            Visible = true;
        }

        public void Limpiar()
        {
            Mensaje = "";
            Tipo = TipoAvisoInline.Informacion;
            Visible = false;
        }
    }
}
