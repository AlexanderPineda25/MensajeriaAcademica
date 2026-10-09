namespace Mensajeria.Transversal
{
    /// <summary>Calcula el desplazamiento que mantiene una fila en su coordenada vertical.</summary>
    internal static class AnclajeScroll
    {
        public static double AjustarDesplazamiento(double desplazamientoActual,
            double coordenadaActual, double coordenadaOriginal) =>
            System.Math.Max(0, desplazamientoActual + coordenadaActual - coordenadaOriginal);
    }
}
