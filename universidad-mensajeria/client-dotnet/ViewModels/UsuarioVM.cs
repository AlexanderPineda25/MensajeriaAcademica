using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Media;

namespace Mensajeria.ViewModels
{
    public class UsuarioVM : INotifyPropertyChanged
    {
        public string Codigo { get; init; } = "";
        public string Nombres { get; init; } = "";
        public string Apellidos { get; init; } = "";
        public bool Conectado { get; init; }
        public string Etiqueta => string.IsNullOrWhiteSpace((Nombres + " " + Apellidos).Trim())
            ? Codigo : $"{Nombres} {Apellidos} [{Codigo}]";
        public string Presencia => Conectado ? "en línea" : "desconectado";
        public Brush ColorPresencia => Conectado ? Brushes.SeaGreen : Brushes.LightGray;

        private int _noLeidos;
        /// <summary>UIX-02: distintivo de no leídos (lo único que notifica).</summary>
        public int NoLeidos
        {
            get => _noLeidos;
            set { _noLeidos = value; OnPropertyChanged(); OnPropertyChanged(nameof(TextoInsignia)); OnPropertyChanged(nameof(MostrarInsignia)); }
        }
        public string TextoInsignia => NoLeidos > 0 ? $"({NoLeidos})" : "";
        public Visibility MostrarInsignia => NoLeidos > 0 ? Visibility.Visible : Visibility.Collapsed;

        public event PropertyChangedEventHandler? PropertyChanged;
        private void OnPropertyChanged([CallerMemberName] string? name = null) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
    }
}
