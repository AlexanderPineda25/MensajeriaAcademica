using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Media;
using Mensajeria.Transversal;

namespace Mensajeria.Views
{
    /// <summary>
    /// UIX-06: TextBlock que resalta las coincidencias de <see cref="Patron"/>
    /// dentro de <see cref="Texto"/> usando <see cref="Resaltado.Dividir"/>.
    /// Virtualization-safe: los Inlines se reconstruyen cada vez que cambia
    /// cualquiera de las dos propiedades enlazadas.
    /// </summary>
    public class ResaltadoTextBlock : TextBlock
    {
        public static readonly DependencyProperty TextoProperty =
            DependencyProperty.Register(nameof(Texto), typeof(string),
                typeof(ResaltadoTextBlock),
                new FrameworkPropertyMetadata("",
                    FrameworkPropertyMetadataOptions.AffectsRender,
                    (d, _) => ((ResaltadoTextBlock)d).Repintar()));

        public static readonly DependencyProperty PatronProperty =
            DependencyProperty.Register(nameof(Patron), typeof(string),
                typeof(ResaltadoTextBlock),
                new FrameworkPropertyMetadata("",
                    FrameworkPropertyMetadataOptions.AffectsRender,
                    (d, _) => ((ResaltadoTextBlock)d).Repintar()));

        public string Texto
        {
            get => (string)GetValue(TextoProperty);
            set => SetValue(TextoProperty, value);
        }

        public string Patron
        {
            get => (string)GetValue(PatronProperty);
            set => SetValue(PatronProperty, value);
        }

        public ResaltadoTextBlock()
        {
            Loaded += (_, _) => Repintar();
        }

        // NOTA: DependencyObject.OnPropertyChanged está sellado en WPF y no se
        // puede invalidar; por eso el repintado va en los callbacks de Texto y
        // Patron (arriba) y en Loaded.

        private void Repintar()
        {
            Inlines.Clear();
            foreach (var tramo in Resaltado.Dividir(Texto, Patron))
            {
                var run = new Run(tramo.Texto);
                if (tramo.EsMatch)
                {
                    run.FontWeight = FontWeights.Bold;
                    run.TextDecorations = System.Windows.TextDecorations.Underline;
                    run.Foreground = (Brush?)TryFindResource("RemitenteBrush")
                        ?? new SolidColorBrush(Color.FromRgb(0xC4, 0xB5, 0xFD));
                }
                Inlines.Add(run);
            }
        }
    }
}
