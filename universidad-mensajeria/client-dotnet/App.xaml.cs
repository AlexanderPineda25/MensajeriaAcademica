using System.Windows;
using Mensajeria.Composicion;
using Mensajeria.Fachada;

namespace Mensajeria
{
    public partial class App : Application
    {
        private IFachadaCliente? _fachada;

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            Temas.Tema.Aplicar(Temas.Tema.Leer());
            var fachada = FabricaCliente.CrearFachada();
            _fachada = fachada;
            new Views.LoginWindow(fachada).Show();
        }

        protected override void OnExit(ExitEventArgs e)
        {
            _fachada?.Desconectar();
            base.OnExit(e);
        }
    }
}
