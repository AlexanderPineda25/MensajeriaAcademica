package universidad.mensajeria.cliente.presentacion;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import universidad.mensajeria.cliente.composicion.ClienteRuntime;
import universidad.mensajeria.cliente.composicion.FabricaCliente;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

/** Shell JavaFX y composition root de la aplicación del cliente. */
public final class ClienteApp extends Application implements InterfazNavegacionCliente {

    private ClienteRuntime runtime;
    private FabricaControladores fabricaControladores;
    private Stage escenario;
    private String avisoPendiente;
    private boolean temaBaseInicializado;

    @Override
    public void init() throws Exception {
        runtime = new FabricaCliente().crear();
        fabricaControladores = new FabricaControladores(
                runtime.fachada(), runtime.fachada(), runtime.fachada().fondo(),
                runtime.config(), this);
        imprimirConfiguracion();
    }

    @Override
    public void start(Stage escenarioPrincipal) throws Exception {
        escenario = escenarioPrincipal;
        escenario.setTitle("Mensajería Académica");
        mostrarLogin();
        escenario.show();
    }

    @Override
    public void stop() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Override
    public void mostrarLogin() throws ExcepcionCliente, IOException {
        FXMLLoader cargador = crearCargador("/vistas/login.fxml");
        Parent raiz = cargador.load();
        escenario.setTitle("Mensajería Académica | Iniciar sesión");
        Scene escena = new Scene(raiz, 540, 740);
        aplicarTema(escena, true);
        escenario.setScene(escena);
        escenario.setMinWidth(480);
        escenario.setMinHeight(660);
        escenario.setOnCloseRequest(evento -> {
            if (!confirmar("¿Deseas cerrar el programa?")) {
                evento.consume();
            }
        });
    }

    @Override
    public void avisarProximoLogin(String motivo) {
        avisoPendiente = motivo;
    }

    @Override
    public String consumirAviso() {
        String aviso = avisoPendiente;
        avisoPendiente = null;
        return aviso;
    }

    @Override
    public void mostrarChat() throws ExcepcionCliente, IOException {
        FXMLLoader cargador = crearCargador("/vistas/chat.fxml");
        Parent raiz = cargador.load();
        escenario.setTitle("Mensajería Académica | Chat");
        Scene escena = new Scene(raiz, 1100, 700);
        aplicarTema(escena, false);
        escenario.setScene(escena);
        ChatController chat = cargador.getController();
        escenario.setOnCloseRequest(evento -> {
            evento.consume();
            if (confirmar("¿Deseas cerrar esta sesión y volver al inicio?")) {
                chat.salirAlInicio();
            }
        });
    }

    @Override
    public boolean confirmar(String pregunta) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, pregunta,
                ButtonType.YES, ButtonType.NO);
        alerta.initOwner(escenario);
        alerta.setTitle("Confirmar salida");
        alerta.setHeaderText(null);
        ((Button) alerta.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
        return alerta.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    @Override
    public void aplicarTema(Scene escena, boolean login) {
        String modo = Tema.cargar();
        // Conserva fijo el user-agent de JavaFX para evitar recálculos CSS globales.
        if (!temaBaseInicializado) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            temaBaseInicializado = true;
        }
        String tema = Tema.CLARO.equals(modo)
                ? new PrimerLight().getUserAgentStylesheet()
                : new PrimerDark().getUserAgentStylesheet();
        String css = login ? Tema.cssLogin(modo) : Tema.cssChat(modo);
        URL url = ClienteApp.class.getResource(css);
        if (url != null) {
            escena.getStylesheets().setAll(tema, url.toExternalForm());
        }
    }

    private FXMLLoader crearCargador(String recurso) {
        FXMLLoader cargador = new FXMLLoader(ClienteApp.class.getResource(recurso));
        cargador.setControllerFactory(fabricaControladores::crear);
        return cargador;
    }

    private void imprimirConfiguracion() {
        var config = runtime.config();
        System.out.println("=== Cliente Mensajería (Java) ===");
        System.out.println("build      : Fase 15 (identidad, respuestas e imágenes diferidas)");
        System.out.println("servidor   : " + config.host() + ":" + config.puerto()
                + (config.tlsHabilitado() ? " (TLS :" + config.tlsPuerto() + ")" : ""));
        System.out.println("historial  : " + config.dbUrl() + " ("
                + runtime.sentenciasInicializacion() + " DDL)");
        System.out.println("idioma     : " + config.idioma());
    }
}
