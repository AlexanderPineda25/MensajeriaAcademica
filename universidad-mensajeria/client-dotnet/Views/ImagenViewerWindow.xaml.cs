using System;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;

namespace Mensajeria.Views
{
    /// <summary>
    /// UIX-08: visor de imagen con zoom y pan en vez de ventana fija.
    /// Rueda = zoom, arrastrar = pan, `+`/`-`/`0`/`F` por teclado, `Esc` cierra.
    /// </summary>
    public partial class ImagenViewerWindow : Window
    {
        private readonly ScaleTransform _zoom = new(1, 1);
        private Point _arrastre;
        private bool _arrastrando;

        public ImagenViewerWindow(System.Windows.Media.ImageSource? imagen, string titulo)
        {
            InitializeComponent();
            Title = string.IsNullOrWhiteSpace(titulo) ? "Imagen" : titulo;
            imgGrande.Source = imagen;
            imgGrande.RenderTransform = _zoom;
            ActualizarEtiqueta();
            KeyDown += (_, e) =>
            {
                switch (e.Key)
                {
                    case Key.Add:
                    case Key.OemPlus: AplicarZoom(1.25); e.Handled = true; break;
                    case Key.Subtract:
                    case Key.OemMinus: AplicarZoom(1 / 1.25); e.Handled = true; break;
                    case Key.D0:
                    case Key.NumPad0:
                    case Key.F: AplicarZoom(null); e.Handled = true; break;
                    case Key.Escape: Close(); e.Handled = true; break;
                }
            };
        }

        private void AplicarZoom(double? factor)
        {
            double nuevo = factor == null ? 1.0
                : Math.Min(8.0, Math.Max(0.2, _zoom.ScaleX * factor.Value));
            _zoom.ScaleX = nuevo;
            _zoom.ScaleY = nuevo;
            ActualizarEtiqueta();
        }

        private void ActualizarEtiqueta() =>
            txtZoom.Text = $"{(int)Math.Round(_zoom.ScaleX * 100)} %";

        private void BtnAcercar_Click(object sender, RoutedEventArgs e) => AplicarZoom(1.25);

        private void BtnAlejar_Click(object sender, RoutedEventArgs e) => AplicarZoom(1 / 1.25);

        private void BtnEncajar_Click(object sender, RoutedEventArgs e) => AplicarZoom(null);

        private void Visor_PreviewMouseWheel(object sender, MouseWheelEventArgs e)
        {
            AplicarZoom(e.Delta > 0 ? 1.15 : 1 / 1.15);
            e.Handled = true;
        }

        private void Visor_PreviewMouseDown(object sender, MouseButtonEventArgs e)
        {
            _arrastrando = true;
            _arrastre = e.GetPosition(visorScroll);
            visorScroll.CaptureMouse();
        }

        private void Visor_PreviewMouseMove(object sender, MouseEventArgs e)
        {
            if (!_arrastrando) return;
            Point actual = e.GetPosition(visorScroll);
            visorScroll.ScrollToHorizontalOffset(visorScroll.HorizontalOffset - (actual.X - _arrastre.X));
            visorScroll.ScrollToVerticalOffset(visorScroll.VerticalOffset - (actual.Y - _arrastre.Y));
            _arrastre = actual;
        }

        private void Visor_PreviewMouseUp(object sender, MouseButtonEventArgs e)
        {
            _arrastrando = false;
            visorScroll.ReleaseMouseCapture();
        }
    }
}
