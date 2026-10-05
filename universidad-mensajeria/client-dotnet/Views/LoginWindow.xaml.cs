using System.ComponentModel;
using System.IO;
using System.Windows;
using Mensajeria.Fachada;

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
            try
            {
                string host = "localhost";
                string puerto = "5000";
                string ruta = "client.properties";
                if (File.Exists(ruta))
                {
                    foreach (var linea in File.ReadAllLines(ruta))
                    {
                        var t = linea.Trim();
                        if (t.StartsWith("host=")) host = t.Substring(5).Trim();
                        if (t.StartsWith("puerto=")) puerto = t.Substring(7).Trim();
                    }
                }
                txtHost.Text = host;
                txtPuerto.Text = puerto;
            }
            catch
            {
                txtHost.Text = "localhost";
                txtPuerto.Text = "5000";
            }
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
                txtError.Text = "Conectando…";
                bordeError.Visibility = Visibility.Visible;
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
                    txtError.Text = "✖ Usuario no registrado o datos incorrectos.";
                    bordeError.Visibility = Visibility.Visible;
                    _fachada.Desconectar();
                }
            }
            catch (System.Exception ex)
            {
                txtError.Text = "✖ No se pudo conectar (" + ex.GetType().Name + "): " + ex.Message;
                bordeError.Visibility = Visibility.Visible;
            }
            finally
            {
                btnEntrar.IsEnabled = true;
            }
        }

        // Fase 10.2 (P2): sin «Crear cuenta». La alta de usuarios es solo por
        // archivo plano (usuarios_iniciales.csv); RegistroWindow queda inalcanzable.
    }
}
