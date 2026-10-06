using System;
using System.Globalization;
using System.Windows.Data;
using Mensajeria.Transversal;

namespace Mensajeria.Views
{
    /// <summary>
    /// UIX-10: ancho de burbuja relativo al de la lista (delegación fina al
    /// cálculo puro <see cref="AnchoBurbuja"/>).
    /// </summary>
    public class AnchoBurbujaConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            double ancho = value is double d ? d : 0;
            return AnchoBurbuja.Calcular(ancho);
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
            throw new NotSupportedException();
    }
}
