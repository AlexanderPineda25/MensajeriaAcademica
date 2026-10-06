namespace Mensajeria.Transversal
{
    public enum LadoBurbuja { Izquierda, Derecha }

    /// <summary>Decisiones de presentación que comparten las filas del chat.</summary>
    public static class BurbujaChat
    {
        public static bool DebeMostrar(bool esCabeceraDia) => !esCabeceraDia;

        public static LadoBurbuja Alineacion(bool esPropio) =>
            esPropio ? LadoBurbuja.Derecha : LadoBurbuja.Izquierda;
    }
}
