using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;
using Mensajeria.Fachada;
using Mensajeria.Transversal;

namespace Mensajeria.Views
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

    public partial class ChatWindow : Window
    {
        private readonly IFachadaCliente _fachada;
        private readonly EstadoAvisoInline _estadoAvisoInline = new();
        private readonly ObservableCollection<UsuarioVM> _usuarios = new();
        private readonly ObservableCollection<MensajeVM> _mensajes = new();
        private List<UsuarioVM> _directorio = new();
        private IReadOnlyDictionary<string, DateTimeOffset> _ultimosMensajes =
            new Dictionary<string, DateTimeOffset>(StringComparer.OrdinalIgnoreCase);
        private string? _usuarioDestino;
        private int _versionCargaHistorial;
        private bool _cargandoHistorial;
        private bool _errorCargaHistorial;
        private string? _imagenPendiente;
        private string? _archivoPendiente;
        private bool _cerrando;
        private int _offsetActual;
        // UIX-06/UIX-07: búsqueda en el historial con debounce.
        private readonly List<string> _idsBusqueda = new();
        private int _indiceBusqueda = -1;
        private string _patronBusqueda = "";
        private string? _saltoPendiente;
        private int _intentosSalto;
        // UIX-16: anclaje y aviso de nuevos sin perder lectura.
        private bool _seguirAbajoAntesDeCargar = true;
        private int _nuevosPendientes;
        private readonly System.Windows.Threading.DispatcherTimer _timerBuscar = new();
        private readonly System.Windows.Threading.DispatcherTimer _timerFiltro = new();
        private readonly Dictionary<string, int> _paginaRemota = new();
        private readonly Dictionary<string, int> _totalPaginasRemotas = new();
        private readonly SemaphoreSlim _limiteImagenes = new(3, 3);
        /// <summary>UIX-09: cargas de imagen en vuelo por id, cancelables.</summary>
        private readonly Dictionary<string, CancellationTokenSource> _cargasImagen = new();

        public ChatWindow(IFachadaCliente fachada)
        {
            _fachada = fachada;
            InitializeComponent();
            txtSesion.Text = $"Sesión activa · {_fachada.UsuarioActual}";
            lstUsuarios.ItemsSource = _usuarios;
            lstMensajes.ItemsSource = _mensajes;
            _fachada.OnMensajeRecibido += Fachada_OnMensajeRecibido;
            _fachada.OnCierre += Fachada_OnCierre;
            _fachada.OnPresencia += Fachada_OnPresencia;
            // PAT-03/UIX-00a: aquí faltaba la suscripción. El banner ámbar existía
            // en el XAML pero ninguna vista lo cambiaba, así que la pérdida de
            // conexión era invisible en .NET.
            _fachada.OnEstadoConexion += Fachada_OnEstadoConexion;
            PintarEstadoConexion(_fachada.EstadoConexion);
            // UIX-06/UIX-07: debounce compartido para las dos búsquedas (300 ms).
            _timerBuscar.Interval = TimeSpan.FromMilliseconds(300);
            _timerBuscar.Tick += (_, _) => { _timerBuscar.Stop(); EjecutarBusqueda(); };
            _timerFiltro.Interval = TimeSpan.FromMilliseconds(300);
            _timerFiltro.Tick += (_, _) => { _timerFiltro.Stop(); AplicarFiltro(_usuarioDestino); };
            // UIX-06: Ctrl+F abre la búsqueda; Esc la cierra si está abierta.
            PreviewKeyDown += (s, e) =>
            {
                bool ctrlF = e.Key == System.Windows.Input.Key.F
                    && System.Windows.Input.Keyboard.Modifiers
                        .HasFlag(System.Windows.Input.ModifierKeys.Control);
                if (ctrlF)
                {
                    MostrarBuscar();
                    e.Handled = true;
                }
                else if (e.Key == System.Windows.Input.Key.Escape
                    && barraBuscar.Visibility == Visibility.Visible)
                {
                    CerrarBuscar();
                    e.Handled = true;
                }
            };
            cmbTema.SelectedIndex = Temas.Tema.Leer() == Temas.Tema.Claro ? 1 : 0;
            // UIX-11: restaura el ancho del sidebar (250–360).
            int anchoSide = Transversal.ClienteConfig.Entero("sidebar.ancho", 300, 250, 360);
            colSidebar.Width = new GridLength(anchoSide);
            // UIX-13: sin conversación, el panel no queda negro sin explicación.
            ActualizarVacioChat();
            CargarUsuarios();
            ActualizarEstadoEnvio();
        }

        /// <summary>UIX-11: persiste el ancho del sidebar al soltar el divisor.</summary>
        private void Splitter_DragCompleted(object sender,
            System.Windows.Controls.Primitives.DragCompletedEventArgs e)
        {
            Transversal.ClienteConfig.Guardar("sidebar.ancho",
                ((int)colSidebar.ActualWidth).ToString());
        }

        /// <summary>PAT-03: pinta el estado que publica la fachada.</summary>
        private void Fachada_OnEstadoConexion(EstadoConexion estado)
        {
            // Llega desde el hilo de red: el salto al hilo de UI es obligatorio.
            Dispatcher.Invoke(() => PintarEstadoConexion(estado));
        }

        private void PintarEstadoConexion(EstadoConexion estado)
        {
            bool degrading = EstadoConexionTexto.MuestraBanner(estado);
            bannerOffline.Visibility = degrading ? Visibility.Visible : Visibility.Collapsed;
            if (degrading)
            {
                bannerOfflineText.Text = EstadoConexionTexto.Texto(estado);
                txtEstado.Text = EstadoConexionTexto.Texto(estado);
            }
            else if (txtEstado.Text == EstadoConexionTexto.Texto(EstadoConexion.SinRed)
                || txtEstado.Text == EstadoConexionTexto.Texto(EstadoConexion.SesionCerrada))
            {
                txtEstado.Text = _usuarios.FirstOrDefault(u => u.Codigo == _usuarioDestino)?.Presencia ?? "";
            }
        }

        private void MostrarAvisoInline(string mensaje, TipoAvisoInline tipo)
        {
            _estadoAvisoInline.Mostrar(mensaje, tipo);
            txtTituloAvisoInline.Text = _estadoAvisoInline.Tipo switch
            {
                TipoAvisoInline.Error => "Error",
                TipoAvisoInline.Advertencia => "Atención",
                TipoAvisoInline.Exito => "Listo",
                _ => "Información"
            };
            txtAvisoInline.Text = _estadoAvisoInline.Mensaje;

            (Color fondo, Color borde, Color texto) = _estadoAvisoInline.Tipo switch
            {
                TipoAvisoInline.Error => (Color.FromRgb(0xFD, 0xEC, 0xEA), Color.FromRgb(0xF1, 0xA9, 0xA5), Color.FromRgb(0xB9, 0x1C, 0x1C)),
                TipoAvisoInline.Advertencia => (Color.FromRgb(0xFF, 0xFB, 0xEB), Color.FromRgb(0xF2, 0xCE, 0x84), Color.FromRgb(0xB4, 0x53, 0x09)),
                TipoAvisoInline.Exito => (Color.FromRgb(0xEC, 0xFD, 0xF3), Color.FromRgb(0xA7, 0xF3, 0xD0), Color.FromRgb(0x04, 0x78, 0x57)),
                _ => (Color.FromRgb(0xEF, 0xF6, 0xFF), Color.FromRgb(0xBF, 0xDB, 0xFE), Color.FromRgb(0x1D, 0x4E, 0xD8))
            };
            bordeAvisoInline.Background = new SolidColorBrush(fondo);
            bordeAvisoInline.BorderBrush = new SolidColorBrush(borde);
            txtTituloAvisoInline.Foreground = new SolidColorBrush(texto);
            txtAvisoInline.Foreground = new SolidColorBrush(texto);
            bordeAvisoInline.Visibility = _estadoAvisoInline.Visible ? Visibility.Visible : Visibility.Collapsed;
        }

        private void LimpiarAvisoInline()
        {
            _estadoAvisoInline.Limpiar();
            bordeAvisoInline.Visibility = Visibility.Collapsed;
        }

        private void BtnCerrarAvisoInline_Click(object sender, RoutedEventArgs e) => LimpiarAvisoInline();

        protected override void OnClosing(CancelEventArgs e)
        {
            if (!_cerrando)
            {
                if (MessageBox.Show("¿Deseas cerrar esta sesión y volver al inicio?", "Confirmar salida",
                        MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes)
                {
                    e.Cancel = true;
                    return;
                }
                VolverAlInicio();
            }
            base.OnClosing(e);
        }

        private void VolverAlInicio(string aviso = "")
        {
            _cerrando = true;
            CancelarTodasLasCargas();
            _fachada.OnMensajeRecibido -= Fachada_OnMensajeRecibido;
            _fachada.OnCierre -= Fachada_OnCierre;
            _fachada.OnPresencia -= Fachada_OnPresencia;
            _fachada.Desconectar();
            new LoginWindow(_fachada, aviso).Show();
        }

        private void Fachada_OnCierre(Dictionary<string, JsonElement> aviso)
        {
            string motivo = MensajesUsuario.SesionCerrada;
            Dispatcher.BeginInvoke(() =>
            {
                if (_cerrando) return;
                VolverAlInicio(motivo);
                Close();
            });
        }

        private async void CargarUsuarios()
        {
            List<Dictionary<string, object>> lista;
            try { lista = await _fachada.ListarUsuariosAsync(); }
            catch { lista = await Task.Run(() => _fachada.DirectorioLocal()); }
            await ActualizarFechasRecientesAsync();
            string? seleccionado = (lstUsuarios.SelectedItem as UsuarioVM)?.Codigo;
            // Encabezado de sesión con nombre completo + código cuando está disponible.
            var propia = lista.FirstOrDefault(u => u.TryGetValue("codigo", out var c) && c is string s && s == _fachada.UsuarioActual);
            if (propia != null)
            {
                string nom = (Valor(propia, "nombres") + " " + Valor(propia, "apellidos")).Trim();
                txtSesion.Text = string.IsNullOrWhiteSpace(nom)
                    ? $"Sesión activa · {_fachada.UsuarioActual}"
                    : $"Sesión activa · {nom} [{_fachada.UsuarioActual}]";
            }
            _directorio = lista
                .Where(u => u.TryGetValue("codigo", out var c) && c is string s && s != _fachada.UsuarioActual)
                .Select(u => new UsuarioVM
                {
                    Codigo = Valor(u, "codigo"), Nombres = Valor(u, "nombres"),
                    Apellidos = Valor(u, "apellidos"), Conectado = EsConectado(u)
                }).ToList();
            // UIX-02: insignia de no leídos (una sola query para todos).
            var noLeidos = _fachada.ContarNoLeidos();
            foreach (var u in _directorio) u.NoLeidos = noLeidos.GetValueOrDefault(u.Codigo);
            AplicarFiltro(seleccionado);
        }

        private async Task ActualizarFechasRecientesAsync()
        {
            try
            {
                var fechas = await Task.Run(() => _fachada.UltimosMensajesPorContacto());
                var ordenReciente = new Dictionary<string, DateTimeOffset>(StringComparer.OrdinalIgnoreCase);
                foreach (var par in fechas)
                {
                    if (DateTimeOffset.TryParse(par.Value, CultureInfo.InvariantCulture,
                            DateTimeStyles.AssumeLocal, out var fecha))
                        ordenReciente[par.Key] = fecha.ToUniversalTime();
                }
                _ultimosMensajes = ordenReciente;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("No se pudieron ordenar las conversaciones por fecha: {0}", ex);
                _ultimosMensajes = new Dictionary<string, DateTimeOffset>(StringComparer.OrdinalIgnoreCase);
            }
        }

        private void MarcarConversacionReciente(string codigo)
        {
            if (string.IsNullOrWhiteSpace(codigo)) return;
            var fechas = new Dictionary<string, DateTimeOffset>(
                _ultimosMensajes, StringComparer.OrdinalIgnoreCase)
            {
                [codigo] = DateTimeOffset.UtcNow
            };
            _ultimosMensajes = fechas;
            AplicarFiltro(_usuarioDestino);
        }

        private void AplicarFiltro(string? seleccionado = null)
        {
            if (txtBuscar == null || txtEstado == null || lstUsuarios == null) return;
            string filtro = (txtBuscar.Text ?? "").Trim();
            string? modo = (cmbFiltro.SelectedItem as System.Windows.Controls.ComboBoxItem)
                ?.Content?.ToString();
            bool soloConectados = modo == "Conectados";
            // UIX-02: filtro "Con no leídos" (paridad con Java y Python).
            bool soloNoLeidos = modo == "Con no leídos";
            var vista = _directorio.Where(u =>
                (!soloConectados || u.Conectado)
                && (!soloNoLeidos || u.NoLeidos > 0)
                && (filtro.Length == 0 || u.Etiqueta.Contains(filtro, StringComparison.CurrentCultureIgnoreCase)
                || u.Codigo.Contains(filtro, StringComparison.CurrentCultureIgnoreCase)))
                .OrderByDescending(u => _ultimosMensajes.GetValueOrDefault(
                    u.Codigo, DateTimeOffset.MinValue))
                .ThenByDescending(u => u.Conectado)
                .ThenBy(u => u.Apellidos, StringComparer.CurrentCultureIgnoreCase).ToList();
            _usuarios.Clear();
            foreach (var usuario in vista) _usuarios.Add(usuario);
            if (seleccionado != null)
                lstUsuarios.SelectedItem = _usuarios.FirstOrDefault(u => u.Codigo == seleccionado);
            txtEstado.Text = $"{_directorio.Count(u => u.Conectado)} contactos en línea";
        }

        private void TxtBuscar_TextChanged(object sender, TextChangedEventArgs e)
        {
            // UIX-07: sin debounce cada tecla reconstruía la lista.
            _timerFiltro.Stop();
            _timerFiltro.Start();
        }

        private void CmbFiltro_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
        {
            // El evento se dispara durante InitializeComponent (IsSelected en XAML)
            // cuando txtEstado/lstUsuarios aun no existen: ignorar ese disparo inicial.
            if (txtEstado == null || lstUsuarios == null || txtBuscar == null) return;
            AplicarFiltro(_usuarioDestino);
        }

        private void Fachada_OnPresencia(Dictionary<string, JsonElement> aviso) =>
            Dispatcher.BeginInvoke(CargarUsuarios);

        private void Fachada_OnMensajeRecibido(Dictionary<string, JsonElement> msg)
        {
            if (!msg.TryGetValue("tipo", out var t) || t.ValueKind != JsonValueKind.String) return;
            string tipo = t.GetString()!;
            if (tipo == TipoMensaje.MENSAJE_ENTREGADO || tipo == TipoMensaje.MENSAJE_LEIDO)
            {
                Dispatcher.BeginInvoke(() =>
                {
                    if (_usuarioDestino != null) CargarHistorial(_usuarioDestino);
                });
                return;
            }
            if (tipo != TipoMensaje.MENSAJE_TEXTO && tipo != TipoMensaje.MENSAJE_IMAGEN
                && tipo != TipoMensaje.BROADCAST && tipo != TipoMensaje.SYNC_LOGIN) return;
            string remitente = GetString(msg, "remitente") ?? "desconocido";
            string destinatario = GetString(msg, "destinatario") ?? "";
            string contacto = tipo == TipoMensaje.BROADCAST
                ? remitente
                : string.Equals(remitente, _fachada.UsuarioActual, StringComparison.OrdinalIgnoreCase)
                    ? destinatario : remitente;
            Dispatcher.BeginInvoke(() =>
            {
                MarcarConversacionReciente(contacto);
                if (_usuarioDestino != contacto) return;
                // UIX-16: solo baja solo si ya se estaba abajo; si no, avisa.
                bool avisar = !EstaAbajo();
                CargarHistorial(contacto);
                if (avisar) AvisarNuevo();
                if (!string.Equals(remitente, _fachada.UsuarioActual, StringComparison.OrdinalIgnoreCase)
                    && _usuarioDestino == remitente)
                    _ = Task.Run(() => _fachada.MarcarLeidosAsync(remitente));
            });
        }

        private void BtnActualizar_Click(object sender, RoutedEventArgs e) => CargarUsuarios();

        private void CmbTema_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (cmbTema.SelectedIndex < 0) return;
            string modo = cmbTema.SelectedIndex == 1 ? Temas.Tema.Claro : Temas.Tema.Oscuro;
            Temas.Tema.Guardar(modo);
            Temas.Tema.Aplicar(modo);
            // Las burbujas guardan su pincel: se reconstruyen con el modo nuevo.
            if (_usuarioDestino != null) CargarHistorial(_usuarioDestino);
        }

        private void LstUsuarios_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (lstUsuarios.SelectedItem is not UsuarioVM usuario
                || string.Equals(_usuarioDestino, usuario.Codigo, StringComparison.OrdinalIgnoreCase)) return;
            _usuarioDestino = usuario.Codigo;
            // UIX-02: la insignia se apaga al abrir (el marcado real viaja por
            // MarcarLeidosAsync en segundo plano).
            usuario.NoLeidos = 0;
            // UIX-16: conversación nueva, aviso nuevo apagado.
            _nuevosPendientes = 0;
            btnNuevos.Visibility = Visibility.Collapsed;
            // UIX-06: los resultados son por conversación; al cambiar, fuera.
            if (barraBuscar.Visibility == Visibility.Visible) CerrarBuscar();
            // UIX-09: las descargas del chat anterior ya no pintan aquí.
            CancelarTodasLasCargas();
            _offsetActual = 0;
            _mensajes.Clear();
            _cargandoHistorial = true;
            _errorCargaHistorial = false;
            ActualizarVacioChat();
            LimpiarCita();
            txtTitulo.Text = usuario.Etiqueta;
            txtEstado.Text = usuario.Presencia;
            _paginaRemota[usuario.Codigo] = 0;
            _totalPaginasRemotas.Remove(usuario.Codigo);
            CargarHistorialRemoto(usuario.Codigo, 0);
            CargarHistorial(usuario.Codigo);
            _ = Task.Run(() => _fachada.MarcarLeidosAsync(usuario.Codigo));
        }

        private async void CargarHistorial(string codigo, int offset = 0, bool prepend = false)
        {
            int version = Interlocked.Increment(ref _versionCargaHistorial);
            List<Dictionary<string, object>> historial;
            int totalLocal;
            // UIX-14: la rueda gira mientras se trae historial.
            barraProgreso.Visibility = Visibility.Visible;
            // UIX-16: se captura ANTES de la carga, porque al terminar la lista cambió.
            if (!prepend) _seguirAbajoAntesDeCargar = EstaAbajo();
            try
            {
                (historial, totalLocal) = await Task.Run(() =>
                    (_fachada.CargarHistorial(codigo, offset), _fachada.ContarConversacion(codigo)));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("No se pudo cargar el historial de {0}: {1}", codigo, ex);
                if (_usuarioDestino == codigo && version == _versionCargaHistorial)
                {
                    barraProgreso.Visibility = Visibility.Collapsed;
                    _cargandoHistorial = false;
                    _errorCargaHistorial = true;
                    ActualizarVacioChat();
                    MostrarAvisoInline("No se pudo cargar el historial de esta conversación. Intenta actualizar los contactos.",
                        TipoAvisoInline.Error);
                }
                return;
            }
            if (_usuarioDestino != codigo || version != _versionCargaHistorial) return;
            barraProgreso.Visibility = Visibility.Collapsed;
            _cargandoHistorial = false;
            _errorCargaHistorial = false;
            if (!prepend) _offsetActual = offset;
            if (!prepend) _mensajes.Clear();
            // Fachada ordena por instante real de más antiguo a más nuevo.
            // La colección conserva ese orden para que lo reciente quede abajo.
            var cronologicas = historial;
            string? fechaPrev = null;
            string? autorPrev = null;
            MensajeVM? vecino = null;
            if (prepend)
            {
                vecino = _mensajes.FirstOrDefault(m => !m.EsCabeceraDia);
            }
            else
            {
                var ultimo = _mensajes.LastOrDefault(m => !m.EsCabeceraDia);
                if (ultimo != null) { fechaPrev = ultimo.FechaCruda; autorPrev = ultimo.AutorCrudo; }
            }
            var lote = new List<MensajeVM>();
            foreach (var row in cronologicas)
            {
                string tipo = Valor(row, "tipo");
                string remitente = Valor(row, "origen");
                bool esImagen = tipo == TipoMensaje.MENSAJE_IMAGEN;
                bool esArchivo = tipo == TipoMensaje.MENSAJE_ARCHIVO;
                bool propio = remitente == _fachada.UsuarioActual;
                string identidad = propio ? "Tú" : Identidad(remitente);
                string estadoBruto = Valor(row, "estado");
                string fechaCruda = Valor(row, "fechaEnvio");
                if (Transversal.FilasChat.CambiaElDia(fechaPrev, fechaCruda))
                {
                    lote.Add(new MensajeVM
                    {
                        EsCabeceraDia = true,
                        TextoCabecera = Transversal.FormatoFecha.EtiquetaDia(fechaCruda)
                    });
                }
                bool agrupado = Transversal.FilasChat.Agrupar(
                    fechaPrev, autorPrev, fechaCruda, remitente);
                lote.Add(new MensajeVM
                {
                    Id = Valor(row, "id"),
                    Encabezado = agrupado ? "" : identidad,
                    Autor = identidad,
                    AutorCrudo = remitente,
                    Fecha = FormatearFecha(fechaCruda),
                    FechaCruda = fechaCruda,
                    Contenido = esImagen ? "" : Valor(row, "contenido"),
                    Estado = propio ? EstadoVisible(estadoBruto) : "",
                    EsImagen = esImagen,
                    EsArchivo = esArchivo,
                    EsPropio = propio,
                    EsLeido = estadoBruto == "LEIDO",
                    ArchivoId = Valor(row, "archivoId"),
                    NombreArchivo = Valor(row, "nombreArchivo"),
                    RutaArchivo = Valor(row, "rutaArchivo")
                });
                fechaPrev = fechaCruda;
                autorPrev = remitente;
            }
            if (prepend)
            {
                for (int i = 0; i < lote.Count; i++) _mensajes.Insert(i, lote[i]);
                // Unión con el vecino de abajo: separador si cambia el día y
                // encabezado recalculado (ya no va pegado al anterior).
                if (vecino != null && lote.Count > 0)
                {
                    var ultimoLote = lote[^1];
                    if (!ultimoLote.EsCabeceraDia
                        && Transversal.FilasChat.CambiaElDia(ultimoLote.FechaCruda, vecino.FechaCruda))
                    {
                        _mensajes.Insert(lote.Count, new MensajeVM
                        {
                            EsCabeceraDia = true,
                            TextoCabecera = Transversal.FormatoFecha.EtiquetaDia(vecino.FechaCruda)
                        });
                    }
                    if (!ultimoLote.EsCabeceraDia)
                    {
                        vecino.Encabezado = Transversal.FilasChat.Agrupar(
                            ultimoLote.FechaCruda, ultimoLote.AutorCrudo,
                            vecino.FechaCruda, vecino.AutorCrudo) ? "" : vecino.Autor;
                    }
                }
            }
            else
            {
                foreach (var vm in lote) _mensajes.Add(vm);
            }
            if (prepend) lstMensajes.ScrollIntoView(_mensajes.FirstOrDefault());
            else if (_seguirAbajoAntesDeCargar)
            {
                lstMensajes.ScrollIntoView(_mensajes.LastOrDefault());
                _nuevosPendientes = 0;
                btnNuevos.Visibility = Visibility.Collapsed;
            }
            // UIX-13: si no hay mensajes, se dice en vez de dejar negro.
            ActualizarVacioChat();
            // UIX-06: si hay un salto pendiente (búsqueda), se reintenta ahora
            // que hay más mensajes cargados.
            if (_saltoPendiente != null)
            {
                string id = _saltoPendiente;
                int intentos = _intentosSalto;
                _saltoPendiente = null;
                SaltarA(id, intentos);
            }
            int paginasRemotas = _totalPaginasRemotas.GetValueOrDefault(codigo);
            btnAnteriores.Visibility = offset + 50 < totalLocal
                || _paginaRemota.GetValueOrDefault(codigo) + 1 < paginasRemotas
                ? Visibility.Visible : Visibility.Collapsed;
        }

        private async void CargarHistorialRemoto(string codigo, int pagina, bool olderPage = false)
        {
            try
            {
                int total = await _fachada.TraerHistorialAsync(codigo, pagina);
                _paginaRemota[codigo] = pagina;
                _totalPaginasRemotas[codigo] = total;
                if (pagina == 0)
                {
                    await ActualizarFechasRecientesAsync();
                    if (_usuarioDestino != null) AplicarFiltro(_usuarioDestino);
                }
                if (_usuarioDestino == codigo)
                    CargarHistorial(codigo, olderPage ? _offsetActual : 0, olderPage);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("No se pudo sincronizar el historial de {0}: {1}", codigo, ex);
                if (_usuarioDestino == codigo && pagina == 0)
                {
                    txtEstado.Text = "No se pudo sincronizar; mostrando el historial guardado en este equipo.";
                    MostrarAvisoInline(MensajesUsuario.Accion(ex, "sincronizar el historial"),
                        TipoAvisoInline.Error);
                }
            }
        }

        private void BtnAnteriores_Click(object sender, RoutedEventArgs e)
        {
            if (_usuarioDestino == null) return;
            string codigo = _usuarioDestino;
            int totalLocal = _fachada.ContarConversacion(codigo);
            if (_offsetActual + 50 < totalLocal)
            {
                _offsetActual += 50;
                CargarHistorial(codigo, _offsetActual, true);
                return;
            }
            int siguiente = _paginaRemota.GetValueOrDefault(codigo) + 1;
            if (siguiente < _totalPaginasRemotas.GetValueOrDefault(codigo))
            {
                _offsetActual = totalLocal;
                CargarHistorialRemoto(codigo, siguiente, true);
            }
        }

        /// <summary>UIX-16: True si ya se está al fondo (margen 60 px).</summary>
        private bool EstaAbajo()
        {
            var scroll = EncontrarScroll(lstMensajes);
            if (scroll == null) return true;
            return scroll.VerticalOffset >= scroll.ScrollableHeight - 60;
        }

        private static System.Windows.Controls.ScrollViewer? EncontrarScroll(DependencyObject raiz)
        {
            if (raiz is System.Windows.Controls.ScrollViewer scroll) return scroll;
            for (int i = 0; i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(raiz); i++)
            {
                var hallado = EncontrarScroll(System.Windows.Media.VisualTreeHelper.GetChild(raiz, i));
                if (hallado != null) return hallado;
            }
            return null;
        }

        private void BtnNuevos_Click(object sender, RoutedEventArgs e)
        {
            _nuevosPendientes = 0;
            btnNuevos.Visibility = Visibility.Collapsed;
            if (_usuarioDestino != null) CargarHistorial(_usuarioDestino);
        }

        private void AvisarNuevo()
        {
            _nuevosPendientes++;
            btnNuevos.Content = $"↓ {_nuevosPendientes} nuevos";
            btnNuevos.Visibility = Visibility.Visible;
        }

        /// <summary>UIX-13: si no hay mensajes, se dice en vez de dejar negro.</summary>
        private void ActualizarVacioChat()
        {
            bool hay = _mensajes.Any(m => !m.EsCabeceraDia);
            txtVacioChat.Visibility = hay ? Visibility.Collapsed : Visibility.Visible;
            if (!hay)
            {
                txtVacioChat.Text = _usuarioDestino == null
                    ? "Elige un contacto para empezar a conversar."
                    : _cargandoHistorial ? "Cargando conversación…"
                    : _errorCargaHistorial ? "No se pudo cargar el historial. Revisa la conexión e inténtalo de nuevo."
                    : "Sin mensajes aún. Escribe el primero.";
            }
        }

        // ------------------------------------------------- UIX-06: buscar en el historial

        private void MostrarBuscar()
        {
            if (_usuarioDestino == null)
            {
                txtEstado.Text = "Elige una conversación para buscar en su historial.";
                return;
            }
            barraBuscar.Visibility = Visibility.Visible;
            txtBuscarHistorial.Focus();
            txtBuscarHistorial.SelectAll();
        }

        private void CerrarBuscar()
        {
            barraBuscar.Visibility = Visibility.Collapsed;
            txtBuscarHistorial.Clear();
            _patronBusqueda = "";
            _idsBusqueda.Clear();
            _indiceBusqueda = -1;
            _saltoPendiente = null;
            txtBuscarContador.Text = "";
            foreach (var vm in _mensajes) vm.PatronResaltado = "";
        }

        private void BtnBuscarCerrar_Click(object sender, RoutedEventArgs e) => CerrarBuscar();

        private void TxtBuscarHistorial_TextChanged(object sender, TextChangedEventArgs e)
        {
            // UIX-07: la query a SQLite espera 300 ms tras la última tecla.
            _timerBuscar.Stop();
            _timerBuscar.Start();
        }

        private void TxtBuscarHistorial_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
        {
            if (e.Key == System.Windows.Input.Key.Enter)
            {
                if (e.KeyboardDevice.Modifiers.HasFlag(System.Windows.Input.ModifierKeys.Shift))
                    BuscarAnterior();
                else
                    BuscarSiguiente();
                e.Handled = true;
            }
            else if (e.Key == System.Windows.Input.Key.Escape)
            {
                CerrarBuscar();
                e.Handled = true;
            }
        }

        private void BtnBuscarSiguiente_Click(object sender, RoutedEventArgs e) => BuscarSiguiente();

        private void BtnBuscarAnterior_Click(object sender, RoutedEventArgs e) => BuscarAnterior();

        private void EjecutarBusqueda()
        {
            string? codigo = _usuarioDestino;
            string patron = (txtBuscarHistorial.Text ?? "").Trim();
            if (codigo == null || patron.Length == 0)
            {
                _patronBusqueda = "";
                _idsBusqueda.Clear();
                _indiceBusqueda = -1;
                ActualizarContadorBusqueda();
                foreach (var vm in _mensajes) vm.PatronResaltado = "";
                return;
            }
            _patronBusqueda = patron;
            _idsBusqueda.Clear();
            _idsBusqueda.AddRange(_fachada.BuscarEnHistorial(codigo, patron));
            _indiceBusqueda = _idsBusqueda.Count > 0 ? 0 : -1;
            ActualizarContadorBusqueda();
            foreach (var vm in _mensajes) vm.PatronResaltado = patron;
            if (_idsBusqueda.Count > 0) SaltarA(_idsBusqueda[0], 5);
        }

        private void ActualizarContadorBusqueda()
        {
            txtBuscarContador.Text = _idsBusqueda.Count == 0
                ? (_patronBusqueda.Length == 0 ? "" : "Sin resultados")
                : $"{_indiceBusqueda + 1}/{_idsBusqueda.Count}";
        }

        private void BuscarSiguiente()
        {
            if (_idsBusqueda.Count == 0) return;
            _indiceBusqueda = (_indiceBusqueda + 1) % _idsBusqueda.Count;
            ActualizarContadorBusqueda();
            SaltarA(_idsBusqueda[_indiceBusqueda], 5);
        }

        private void BuscarAnterior()
        {
            if (_idsBusqueda.Count == 0) return;
            _indiceBusqueda = (_indiceBusqueda - 1 + _idsBusqueda.Count) % _idsBusqueda.Count;
            ActualizarContadorBusqueda();
            SaltarA(_idsBusqueda[_indiceBusqueda], 5);
        }

        /// <summary>
        /// Baja al mensaje: si está cargado, scroll + selección; si no, trae
        /// una página más y reintenta (acotado).
        /// </summary>
        private void SaltarA(string id, int intentos)
        {
            if (_usuarioDestino == null) return;
            var vm = _mensajes.FirstOrDefault(m => !m.EsCabeceraDia && m.Id == id);
            if (vm != null)
            {
                lstMensajes.ScrollIntoView(vm);
                lstMensajes.SelectedItem = vm;
                return;
            }
            if (intentos <= 0 || btnAnteriores.Visibility != Visibility.Visible)
            {
                txtBuscarContador.Text = "Fuera del historial cargado";
                return;
            }
            _saltoPendiente = id;
            _intentosSalto = intentos - 1;
            BtnAnteriores_Click(this, new RoutedEventArgs());
        }

        private async void MessageImage_Loaded(object sender, RoutedEventArgs e)
        {
            if (sender is Image { DataContext: MensajeVM vm }) await CargarImagenAsync(vm);
        }

        private async Task CargarImagenAsync(MensajeVM vm)
        {
            if (vm.Imagen != null || vm.Cargando || vm.Solicitada) return;
            // UIX-09: una sola carga por mensaje; la anterior se cancela.
            if (_cargasImagen.TryGetValue(vm.Id, out var previa))
            {
                try { previa.Cancel(); } catch { }
                _cargasImagen.Remove(vm.Id);
            }
            var cts = new CancellationTokenSource();
            _cargasImagen[vm.Id] = cts;
            CancellationToken token = cts.Token;
            vm.Cargando = true;
            vm.EstadoImagen = "Cargando imagen…";
            try
            {
                await _limiteImagenes.WaitAsync(token).ConfigureAwait(true);
                token.ThrowIfCancellationRequested();
                    var bitmap = await Task.Run(async () =>
                    {
                        token.ThrowIfCancellationRequested();
                        string ruta = vm.RutaArchivo;
                    if (string.IsNullOrWhiteSpace(ruta) || !File.Exists(ruta))
                    {
                        string extension = Path.GetExtension(vm.NombreArchivo);
                        if (string.IsNullOrWhiteSpace(extension)) extension = ".img";
                        string carpeta = Path.Combine(Environment.GetFolderPath(
                            Environment.SpecialFolder.LocalApplicationData), "Mensajeria", "imagenes");
                        Directory.CreateDirectory(carpeta);
                        ruta = Path.Combine(carpeta, vm.Id + extension);
                        if (!File.Exists(ruta))
                        {
                            if (!string.IsNullOrWhiteSpace(vm.ArchivoId))
                            {
                                await _fachada.DescargarArchivoAsync(vm.Id, vm.ArchivoId, ruta);
                            }
                            else
                            {
                                string? local = _fachada.ContenidoImagenLocal(vm.Id);
                                if (string.IsNullOrWhiteSpace(local) || local == "[Imagen]")
                                    throw new InvalidOperationException("La imagen no tiene archivo disponible.");
                                byte[] imagenLocal = Convert.FromBase64String(local);
                                if (imagenLocal.Length == 0)
                                    throw new InvalidOperationException("La imagen local está vacía.");
                                await File.WriteAllBytesAsync(ruta, imagenLocal);
                            }
                        }
                    }
                    var image = new BitmapImage();
                    image.BeginInit();
                    image.CacheOption = BitmapCacheOption.OnLoad;
                    image.UriSource = new Uri(ruta, UriKind.Absolute);
                    image.EndInit();
                    image.Freeze();
                    return (Image: image, Path: ruta);
                });
                vm.Imagen = bitmap.Image;
                vm.RutaArchivo = bitmap.Path;
                vm.EstadoImagen = vm.NombreArchivo;
            }
            catch (OperationCanceledException)
            {
                // UIX-09: cancelación del usuario o cambio de vista: silencio,
                // sin ofrecer reintento de algo que ya se dejó atrás.
                vm.EstadoImagen = "Carga cancelada";
            }
            catch (Exception ex)
            {
                vm.EstadoImagen = MensajesUsuario.Archivo(ex);
                vm.Solicitada = true;
            }
            finally
            {
                _limiteImagenes.Release();
                vm.Cargando = false;
                _cargasImagen.Remove(vm.Id);
            }
        }

        private async void BtnReintentarImagen_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button { DataContext: MensajeVM vm })
            {
                vm.Solicitada = false;
                await CargarImagenAsync(vm);
            }
        }

        private void BtnCancelarImagen_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button { DataContext: MensajeVM vm }) CancelarCargaImagen(vm.Id);
        }

        /// <summary>UIX-09: cancela la carga en vuelo de ese mensaje.</summary>
        private void CancelarCargaImagen(string id)
        {
            if (_cargasImagen.TryGetValue(id, out var cts))
            {
                try { cts.Cancel(); } catch { }
            }
        }

        /// <summary>UIX-09: cancela todas las cargas (cambio de chat o salida).</summary>
        private void CancelarTodasLasCargas()
        {
            foreach (var cts in _cargasImagen.Values)
            {
                try { cts.Cancel(); } catch { }
            }
            _cargasImagen.Clear();
        }

        private string _citaAutor = "";
        private string _citaExtracto = "";

        private void BtnResponder_Click(object sender, RoutedEventArgs e)
        {
            if (sender is not Button { DataContext: MensajeVM vm }) return;
            ResponderA(vm);
        }

        private void LstMensajes_DoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e)
        {
            // Telegram Web: doble-click en cualquier mensaje = responder.
            if (lstMensajes.SelectedItem is MensajeVM vm) ResponderA(vm);
        }

        private void MessageImage_Click(object sender, System.Windows.Input.MouseButtonEventArgs e)
        {
            // Un click en imagen abre grande; el doble-click lo gestiona LstMensajes_DoubleClick (responder).
            if (e.ClickCount >= 2) return;
            if (sender is System.Windows.Controls.Image { DataContext: MensajeVM vm, Source: not null })
                AbrirImagenGrande(vm);
        }

        private static void AbrirImagenGrande(MensajeVM vm)
        {
            // UIX-08: visor con zoom y pan en vez de ventana fija.
            try
            {
                new ImagenViewerWindow(vm.Imagen,
                    string.IsNullOrWhiteSpace(vm.NombreArchivo) ? "Imagen" : vm.NombreArchivo).Show();
            }
            catch { }
        }

        private void ResponderA(MensajeVM vm)
        {
            string autor = string.IsNullOrWhiteSpace(vm.Autor)
                ? vm.Encabezado.Split('·')[0].Trim() : vm.Autor;
            string extracto = (vm.EsImagen || vm.EsArchivo) && !string.IsNullOrEmpty(vm.NombreArchivo)
                ? vm.NombreArchivo : vm.Contenido;
            if (extracto.Length > 120) extracto = extracto[..120] + "…";
            _citaAutor = autor;
            _citaExtracto = extracto;
            txtRespuesta.Text = $"Respuesta a {autor}: \"{extracto}\"";
            bordeRespuesta.Visibility = Visibility.Visible;
            txtMensaje.Focus();
            ActualizarEstadoEnvio();
        }

        private void BtnQuitarRespuesta_Click(object sender, RoutedEventArgs e) => LimpiarCita();

        private void LimpiarCita()
        {
            _citaAutor = "";
            _citaExtracto = "";
            txtRespuesta.Text = "";
            bordeRespuesta.Visibility = Visibility.Collapsed;
        }

        private async void BtnEnviar_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrEmpty(_usuarioDestino)) return;
            string texto = (txtMensaje.Text ?? "").Trim();
            bool hayImagen = !string.IsNullOrEmpty(_imagenPendiente);
            bool hayArchivo = !string.IsNullOrEmpty(_archivoPendiente);
            if (texto.Length == 0 && !hayImagen && !hayArchivo) return;
            LimpiarAvisoInline();
            btnEnviar.IsEnabled = false;
            try
            {
                bool enRed = true;
                string destino = _usuarioDestino;
                string cita = !string.IsNullOrEmpty(_citaAutor)
                    ? $"Respuesta a {_citaAutor}: \"{_citaExtracto}\"\n\n" : "";
                if (texto.Length > 0)
                {
                    enRed = await Task.Run(() => _fachada.EnviarTextoAsync(destino, cita + texto));
                    txtMensaje.Clear();
                }
                if (hayImagen)
                {
                    string ruta = _imagenPendiente!;
                    enRed = await Task.Run(() => _fachada.EnviarImagenAsync(destino, ruta)) && enRed;
                    LimpiarAdjunto();
                }
                if (hayArchivo)
                {
                    string ruta = _archivoPendiente!;
                    var progreso = new Progress<double>(p =>
                        txtArchivoAdjunto.Text = $"Enviando {Path.GetFileName(ruta)}… {p:P0}");
                    enRed = await Task.Run(() => _fachada.EnviarArchivoAsync(destino, ruta, progreso)) && enRed;
                    LimpiarAdjuntoArchivo();
                }
                txtRespuesta.Text = "";
                bordeRespuesta.Visibility = Visibility.Collapsed;
                _citaAutor = "";
                _citaExtracto = "";
                if (!enRed) MostrarAvisoInline("Sin red: guardado para reintentar.", TipoAvisoInline.Informacion);
                CargarHistorial(_usuarioDestino);
                MarcarConversacionReciente(_usuarioDestino);
            }
            catch (NullReferenceException) { MostrarAvisoInline("Sesión no válida, vuelve a iniciar sesión.", TipoAvisoInline.Error); }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("Falló el envío de un mensaje: {0}", ex);
                MostrarAvisoInline(MensajesUsuario.Accion(ex, "enviar el mensaje"), TipoAvisoInline.Error);
            }
            finally { ActualizarEstadoEnvio(); }
        }

        private void TxtMensaje_TextChanged(object sender, TextChangedEventArgs e) => ActualizarEstadoEnvio();

        private void TxtMensaje_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
        {
            if (e.Key == System.Windows.Input.Key.Escape)
            {
                LimpiarCita();
                LimpiarAdjunto();
                LimpiarAdjuntoArchivo();
                e.Handled = true;
                return;
            }
            if (e.Key == System.Windows.Input.Key.Enter
                && !System.Windows.Input.Keyboard.IsKeyDown(System.Windows.Input.Key.LeftShift)
                && !System.Windows.Input.Keyboard.IsKeyDown(System.Windows.Input.Key.RightShift))
            {
                e.Handled = true;
                BtnEnviar_Click(sender, new RoutedEventArgs());
            }
        }

        private void ActualizarEstadoEnvio() =>
            btnEnviar.IsEnabled = _usuarioDestino != null
                && (!string.IsNullOrWhiteSpace(txtMensaje.Text) || !string.IsNullOrEmpty(_imagenPendiente)
                    || !string.IsNullOrEmpty(_archivoPendiente));

        private void BtnAdjuntar_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrEmpty(_usuarioDestino)) return;
            // Botón ＋ único tipo Java: abre siempre Todos *.* y decide por extensión.
            var dlg = new OpenFileDialog { Filter = "Todos los archivos|*.*", Title = "Elegir archivo (imagen, PDF, documento...)" };
            if (dlg.ShowDialog() != true) return;
            if (EsImagenSoportada(dlg.FileName)) FijarImagenPendiente(dlg.FileName);
            else FijarArchivoPendiente(dlg.FileName);
        }

        private void ZonaEnvio_DragOver(object sender, DragEventArgs e)
        {
            e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop) ? DragDropEffects.Copy : DragDropEffects.None;
            e.Handled = true;
        }

        private void ZonaEnvio_Drop(object sender, DragEventArgs e)
        {
            if (string.IsNullOrEmpty(_usuarioDestino)) return;
            if (!e.Data.GetDataPresent(DataFormats.FileDrop)) return;
            var archivos = (string[]?)e.Data.GetData(DataFormats.FileDrop);
            string? candidato = archivos?.FirstOrDefault(File.Exists);
            if (candidato == null) return;
            if (EsImagenSoportada(candidato)) FijarImagenPendiente(candidato);
            else FijarArchivoPendiente(candidato);
        }

        private static bool EsImagenSoportada(string ruta)
        {
            string ext = Path.GetExtension(ruta).ToLowerInvariant();
            return ext is ".jpg" or ".jpeg" or ".png" or ".gif" or ".bmp";
        }

        private void FijarImagenPendiente(string ruta)
        {
            _imagenPendiente = ruta;
            txtImagenAdjunta.Text = $"Adjunta: {Path.GetFileName(ruta)} (arrastra otra para reemplazar)";
            panelPreview.Visibility = Visibility.Visible;
            ActualizarEstadoEnvio();
            // Decodificación fuera del dispatcher: el bitmap se congela antes de pintarse.
            _ = Task.Run(() =>
            {
                try
                {
                    var bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.UriSource = new Uri(ruta, UriKind.Absolute);
                    bmp.EndInit();
                    bmp.Freeze();
                    return (BitmapImage?)bmp;
                }
                catch { return null; }
            }).ContinueWith(t =>
            {
                if (t.Result != null) imgPreview.Source = t.Result;
                else txtImagenAdjunta.Text = $"Adjunta: {Path.GetFileName(ruta)} (sin vista previa)";
            }, TaskScheduler.FromCurrentSynchronizationContext());
        }

        private void BtnQuitarImagen_Click(object sender, RoutedEventArgs e) => LimpiarAdjunto();

        private void LimpiarAdjunto()
        {
            _imagenPendiente = null;
            txtImagenAdjunta.Text = "";
            imgPreview.Source = null;
            panelPreview.Visibility = Visibility.Collapsed;
            ActualizarEstadoEnvio();
        }

        private void FijarArchivoPendiente(string ruta)
        {
            try
            {
                long tamano = new FileInfo(ruta).Length;
                if (tamano > FachadaCliente.MaxBytesArchivo)
                {
                    MostrarAvisoInline($"El archivo supera el máximo de 50 MB ({tamano} bytes).",
                        TipoAvisoInline.Advertencia);
                    return;
                }
                _archivoPendiente = ruta;
                txtArchivoAdjunto.Text = $"Archivo: {Path.GetFileName(ruta)} ({tamano} bytes)";
                panelArchivo.Visibility = Visibility.Visible;
                ActualizarEstadoEnvio();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("No se pudo leer el archivo adjunto: {0}", ex);
                MostrarAvisoInline(MensajesUsuario.Archivo(ex), TipoAvisoInline.Error);
            }
        }

        private void BtnQuitarArchivo_Click(object sender, RoutedEventArgs e) => LimpiarAdjuntoArchivo();

        private void LimpiarAdjuntoArchivo()
        {
            _archivoPendiente = null;
            txtArchivoAdjunto.Text = "";
            panelArchivo.Visibility = Visibility.Collapsed;
            ActualizarEstadoEnvio();
        }

        private async void BtnDescargarArchivo_Click(object sender, RoutedEventArgs e)
        {
            if (sender is not Button { DataContext: MensajeVM vm } button) return;
            var dlg = new SaveFileDialog
            {
                FileName = string.IsNullOrWhiteSpace(vm.NombreArchivo) ? "archivo" : vm.NombreArchivo
            };
            if (dlg.ShowDialog() != true) return;
            button.IsEnabled = false;
            try
            {
                string ruta = dlg.FileName;
                if (!string.IsNullOrWhiteSpace(vm.ArchivoId))
                {
                    await Task.Run(() => _fachada.DescargarArchivoAsync(vm.Id, vm.ArchivoId, ruta));
                }
                else
                {
                    // Sin identificador remoto: respaldo con el contenido local
                    // (imágenes cuyo base64 vive en el historial local).
                    System.Diagnostics.Debug.WriteLine(
                        $"Descarga sin archivoId: Id={vm.Id} EsImagen={vm.EsImagen} EsArchivo={vm.EsArchivo} Nombre={vm.NombreArchivo}");
                    string? local = _fachada.ContenidoImagenLocal(vm.Id);
                    if (string.IsNullOrWhiteSpace(local) || local == "[Imagen]")
                        throw new InvalidOperationException("El servidor no entregó el identificador de este archivo.");
                    byte[] bytes = Convert.FromBase64String(local);
                    if (bytes.Length == 0)
                        throw new InvalidOperationException("El contenido local está vacío.");
                    await File.WriteAllBytesAsync(ruta, bytes);
                }
                vm.RutaArchivo = ruta;
                MostrarAvisoInline($"Archivo guardado en {ruta}.", TipoAvisoInline.Exito);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("No se pudo descargar un archivo: {0}", ex);
                MostrarAvisoInline(MensajesUsuario.Accion(ex, "descargar el archivo"), TipoAvisoInline.Error);
            }
            finally
            {
                button.IsEnabled = true;
            }
        }

        private void BtnSalir_Click(object sender, RoutedEventArgs e)
        {
            if (MessageBox.Show("¿Deseas salir y cerrar esta sesión?", "Confirmar salida",
                    MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
            VolverAlInicio();
            Close();
        }

        private async void BtnSalirTodos_Click(object sender, RoutedEventArgs e)
        {
            if (MessageBox.Show("¿Cerrar todas las sesiones de esta cuenta?", "Confirmar salida",
                    MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
            try
            {
                await _fachada.ExpulsarOtrasSesionesAsync();
                VolverAlInicio();
                Close();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Trace.TraceError("No se pudieron cerrar las otras sesiones: {0}", ex);
                MostrarAvisoInline(MensajesUsuario.Accion(ex, "cerrar las otras sesiones"), TipoAvisoInline.Error);
            }
        }

        private string Identidad(string codigo)
        {
            var usuario = _directorio.FirstOrDefault(u => u.Codigo == codigo);
            return usuario == null ? codigo : usuario.Etiqueta;
        }

        private static string EstadoVisible(string estado) => estado switch
        {
            // Visto literal con palabra, igual que el cliente Python (UI-06).
            "PENDIENTE" => "⏳ Pendiente", "ENVIADO" => "✓ Enviado", "ENTREGADO" => "✓✓ Entregado", "LEIDO" => "✓✓ Leído", _ => ""
        };

        private static bool EsConectado(Dictionary<string, object> usuario)
        {
            if (!usuario.TryGetValue("conectado", out var c)) return false;
            return c switch
            {
                bool b => b,
                int i => i != 0,
                long l => l != 0,
                _ => false
            };
        }

        private static string Valor(Dictionary<string, object> fila, string clave) =>
            fila.TryGetValue(clave, out var valor) && valor != null ? valor.ToString() ?? "" : "";

        private static DateTimeOffset? ParseFechaConversacion(string fecha) =>
            DateTimeOffset.TryParse(fecha, CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeLocal, out var instante)
                ? instante.ToUniversalTime() : null;

        /// <summary>Hora corta relativa (UIX-03): delega al contrato compartido.</summary>
        private static string FormatearFecha(string crudo) =>
            Transversal.FormatoFecha.HoraCorta(crudo);

        private static string? GetString(Dictionary<string, JsonElement> fila, string clave) =>
            fila.TryGetValue(clave, out var valor) && valor.ValueKind == JsonValueKind.String
                ? valor.GetString() : null;
    }
}
