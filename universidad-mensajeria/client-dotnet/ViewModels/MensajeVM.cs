using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Mensajeria.Transversal;

namespace Mensajeria.ViewModels
{
    public class MensajeVM : INotifyPropertyChanged
    {
        private BitmapImage? _imagen;
        private string _estadoImagen = "";
        private bool _cargando;
        private bool _solicitada;

        public string Id { get; init; } = "";
        private string _encabezado = "";
        /// <summary>UIX-05: vacío en rachas del mismo autor (se recalcula).</summary>
        public string Encabezado
        {
            get => _encabezado;
            set { _encabezado = value; OnPropertyChanged(); OnPropertyChanged(nameof(MostrarEncabezado)); }
        }
        public bool MostrarEncabezado => !EsCabeceraDia && !string.IsNullOrEmpty(Encabezado);
        public string Autor { get; init; } = "";
        /// <summary>UIX-05: código del remitente (agrupar por código, no por
        /// nombre visible, que puede cambiar entre páginas).</summary>
        public string AutorCrudo { get; init; } = "";
        public string Fecha { get; init; } = "";
        /// <summary>UIX-04/UIX-05: fecha cruda para agrupar (la visible ya va formateada).</summary>
        public string FechaCruda { get; init; } = "";
        /// <summary>UIX-04: true en filas de cabecera de día (sin burbuja).</summary>
        public bool EsCabeceraDia { get; init; }
        public string TextoCabecera { get; init; } = "";
        public Visibility MostrarCabecera => EsCabeceraDia ? Visibility.Visible : Visibility.Collapsed;
        public Visibility MostrarBurbuja => BurbujaChat.DebeMostrar(EsCabeceraDia)
            ? Visibility.Visible : Visibility.Collapsed;
        public string Contenido { get; init; } = "";
        public string Estado { get; init; } = "";
        public string ArchivoId { get; init; } = "";
        /// <summary>Traza JSON de filtros recibida del servidor, sin mostrar sus rutas.</summary>
        public string? EtapasFiltrado { get; init; }
        public string LeyendaEtapasFiltrado =>
            Transversal.FormatoEtapasFiltrado.Resumir(EtapasFiltrado);
        public Visibility MostrarEtapasFiltrado => EsImagen
            && !string.IsNullOrEmpty(LeyendaEtapasFiltrado)
                ? Visibility.Visible : Visibility.Collapsed;
        public string RutaArchivo { get; set; } = "";
        public string NombreArchivo { get; init; } = "";
        public bool EsImagen { get; init; }
        public bool EsArchivo { get; init; }
        private string _patronResaltado = "";
        /// <summary>UIX-06: consulta activa para resaltar (vacía = sin marcas).</summary>
        public string PatronResaltado
        {
            get => _patronResaltado;
            set { _patronResaltado = value; OnPropertyChanged(); }
        }
        public Visibility MostrarDescarga => EsArchivo ? Visibility.Visible : Visibility.Collapsed;
        /// <summary>True si el remitente es la sesión activa: burbuja a la derecha.</summary>
        public bool EsPropio { get; init; }
        public bool EsLeido { get; init; }
        public HorizontalAlignment Alineacion => BurbujaChat.Alineacion(EsPropio)
            == LadoBurbuja.Derecha ? HorizontalAlignment.Right : HorizontalAlignment.Left;
        public Brush Fondo => Pincel(EsPropio ? "BubbleOwnBrush" : "BubbleInBrush");
        public Brush ColorEstado => Pincel(EsLeido ? "VistoLeidoBrush" : "VistoOtroBrush");
        public Brush ColorContenido => Pincel(EsPropio ? "BubbleOwnFgBrush" : "BubbleInFgBrush");

        private static Brush Pincel(string clave)
        {
            try
            {
                if (Application.Current?.TryFindResource(clave) is Brush b) return b;
            }
            catch { }
            return EsPropioFallback(clave);
        }

        private static Brush EsPropioFallback(string clave) => clave switch
        {
            "BubbleOwnBrush" => new SolidColorBrush(Color.FromRgb(0x7B, 0x61, 0xB8)),
            "BubbleInBrush" => new SolidColorBrush(Color.FromRgb(0x18, 0x25, 0x33)),
            "VistoLeidoBrush" => new SolidColorBrush(Color.FromRgb(0x5E, 0xB1, 0xFF)),
            "VistoOtroBrush" => new SolidColorBrush(Color.FromRgb(0xE8, 0xEE, 0xF2)),
            "BubbleOwnFgBrush" => Brushes.White,
            _ => new SolidColorBrush(Color.FromRgb(0xE8, 0xEE, 0xF2)),
        };
        public BitmapImage? Imagen { get => _imagen; set { _imagen = value; OnPropertyChanged(); OnPropertyChanged(nameof(MostrarReintento)); } }
        public string EstadoImagen { get => _estadoImagen; set { _estadoImagen = value; OnPropertyChanged(); } }
        public bool Cargando { get => _cargando; set { _cargando = value; OnPropertyChanged(); OnPropertyChanged(nameof(MostrarReintento)); OnPropertyChanged(nameof(MostrarCancelar)); } }
        public bool Solicitada { get => _solicitada; set { _solicitada = value; OnPropertyChanged(); OnPropertyChanged(nameof(MostrarReintento)); } }
        public Visibility MostrarTexto => string.IsNullOrEmpty(Contenido) ? Visibility.Collapsed : Visibility.Visible;
        public Visibility MostrarImagen => EsImagen ? Visibility.Visible : Visibility.Collapsed;
        public Visibility MostrarReintento => EsImagen && !Cargando && Imagen == null && Solicitada
            ? Visibility.Visible : Visibility.Collapsed;
        /// <summary>UIX-09: cancelar la carga en vuelo de esta imagen.</summary>
        public Visibility MostrarCancelar => EsImagen && Cargando && !EsCabeceraDia
            ? Visibility.Visible : Visibility.Collapsed;
        public event PropertyChangedEventHandler? PropertyChanged;
        private void OnPropertyChanged([CallerMemberName] string? name = null) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
    }
}
