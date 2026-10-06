using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using Mensajeria.Fachada;
using Mensajeria.Transversal;

namespace Mensajeria.Views
{
    public partial class LoginWindow : Window
    {
        private readonly IFachadaCliente _fachada;
        private bool _cierreProgramado;

        protected override void OnClosing(CancelEventArgs e)
        {
            if (!_cierreProgramado
                && MessageBox.Show("¿Deseas cerrar el programa?", "Confirmar salida",
                    MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes)
            {
                e.Cancel = true;
                return;
            }
            base.OnClosing(e);
        }

        public LoginWindow(IFachadaCliente fachada, string aviso = "")
        {
            _fachada = fachada;
            InitializeComponent();
            PreviewKeyDown += (_, e) =>
            {
                if (e.Key == Key.Enter && btnEntrar.IsEnabled
                    && (txtCodigo.IsKeyboardFocusWithin || txtContrasena.IsKeyboardFocusWithin))
                {
                    e.Handled = true;
                    btnEntrar.RaiseEvent(new RoutedEventArgs(
                        System.Windows.Controls.Primitives.ButtonBase.ClickEvent));
                }
            };
            // UIX-24: foco inicial en el campo de codigo (Java y Python ya
            // lo tienen). En Loaded, cuando el visual esta listo.
            this.Loaded += (s, e) => txtCodigo.Focus();
            CargarServidor();
            cmbTema.SelectedIndex = Temas.Tema.Leer() == Temas.Tema.Claro ? 1 : 0;
            if (!string.IsNullOrWhiteSpace(aviso))
            {
                txtAviso.Text = "⏳ " + aviso;
                bordeAviso.Visibility = Visibility.Visible;
            }
        }

        private void CargarServidor()
        {
            // Mismo lector que la fachada (MUERTE-5): antes este metodo solo
            // leia host/puerto del directorio de trabajo, asi que el login
            // podia mostrar un servidor distinto del que luego se usaba.
            txtHost.Text = ClienteConfig.Valor("host", ClienteConfig.HostPorDefecto);
            txtPuerto.Text = ClienteConfig.Entero("puerto",
                    ClienteConfig.PuertoPorDefecto, 1, 65535).ToString();
        }

        private void CmbTema_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
        {
            if (cmbTema.SelectedIndex < 0) return;
            string modo = cmbTema.SelectedIndex == 1 ? Temas.Tema.Claro : Temas.Tema.Oscuro;
            Temas.Tema.Guardar(modo);
            Temas.Tema.Aplicar(modo);
        }

        private async void BtnEntrar_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrWhiteSpace(txtCodigo.Text) || string.IsNullOrEmpty(txtContrasena.Password))
            {
                txtError.Text = "✖ Ingresa el código y la contraseña.";
                bordeError.Visibility = Visibility.Visible;
                return;
            }
            bordeError.Visibility = Visibility.Collapsed;
            bordeAviso.Visibility = Visibility.Collapsed;
            btnEntrar.IsEnabled = false;
            try
            {
                // UIX-00f: "Conectando…" va a su propia etiqueta en gris neutro.
                // Escribirlo en txtError lo pintaba dentro de la caja roja.
                txtCargando.Text = EstadoConexionTexto.Texto(EstadoConexion.Conectando);
                txtCargando.Visibility = Visibility.Visible;
                await _fachada.ConectarAsync();

                bool ok = await _fachada.LoginAsync(txtCodigo.Text.Trim(), txtContrasena.Password);
                if (ok)
                {
                    var chat = new ChatWindow(_fachada);
                    chat.Show();
                    _cierreProgramado = true;
                    this.Close();
                }
                else
                {
                    txtError.Text = "✖ " + MensajesUsuario.CredencialesInvalidas;
                    bordeError.Visibility = Visibility.Visible;
                    _fachada.Desconectar();
                }
            }
            catch (System.Exception ex)
            {
                Trace.TraceError("Fallo durante el inicio de sesión: {0}", ex);
                txtError.Text = "✖ " + MensajesUsuario.Conexion(ex);
                bordeError.Visibility = Visibility.Visible;
            }
            finally
            {
                txtCargando.Visibility = Visibility.Collapsed;
                btnEntrar.IsEnabled = true;
            }
        }

        // Fase 10.2 (P2): sin «Crear cuenta». La alta de usuarios es solo por
        // archivo plano (usuarios_iniciales.csv); RegistroWindow queda inalcanzable.
    }
}
