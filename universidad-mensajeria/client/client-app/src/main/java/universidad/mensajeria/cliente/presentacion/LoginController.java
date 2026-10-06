package universidad.mensajeria.cliente.presentacion;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import universidad.mensajeria.cliente.fachada.InterfazInicioSesion;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

/** Login mockup §2: solo autenticacion, host/puerto visibles, spinner y avisos. */
public final class LoginController {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    private final InterfazInicioSesion inicioSesion;
    private final Executor tareasFondo;
    private final ClienteConfig config;
    private final InterfazNavegacionCliente navegacion;

    public LoginController(InterfazInicioSesion inicioSesion, Executor tareasFondo,
                           ClienteConfig config,
                           InterfazNavegacionCliente navegacion) {
        this.inicioSesion = inicioSesion;
        this.tareasFondo = tareasFondo;
        this.config = config;
        this.navegacion = navegacion;
    }

    @FXML
    private TextField campoCodigo;
    @FXML
    private PasswordField campoContrasena;
    @FXML
    private TextField campoContrasenaVisible;
    @FXML
    private Button botonMostrarContrasena;
    @FXML
    private Label campoHost;
    @FXML
    private Label campoPuerto;
    @FXML
    private ComboBox<String> comboTema;
    @FXML
    private Button botonEntrar;
    @FXML
    private ProgressIndicator spinner;
    @FXML
    private Label etiquetaError;
    @FXML
    private Label etiquetaAviso;

    @FXML
    private void initialize() {
        campoHost.setText(config.host());
        campoPuerto.setText(String.valueOf(config.puerto()));
        comboTema.setItems(FXCollections.observableArrayList("Oscuro", "Claro"));
        comboTema.getSelectionModel().select(
                Tema.CLARO.equals(Tema.cargar()) ? "Claro" : "Oscuro");
        comboTema.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            Tema.guardar("Claro".equals(b) ? Tema.CLARO : Tema.OSCURO);
            if (campoCodigo.getScene() != null) {
                navegacion.aplicarTema(campoCodigo.getScene(), true);
            }
        });
        String aviso = navegacion.consumirAviso();
        if (aviso != null && !aviso.isBlank()) {
            mostrarAviso(aviso);
        }
        Runnable refrescar = this::actualizarBoton;
        campoCodigo.textProperty().addListener((o, a, b) -> refrescar.run());
        campoContrasena.textProperty().addListener((o, a, b) -> refrescar.run());
        campoContrasenaVisible.textProperty().addListener((o, a, b) -> refrescar.run());
        actualizarBoton();
        Platform.runLater(() -> campoCodigo.requestFocus());
    }

    @FXML
    private void alternarContrasena() {
        boolean mostrar = !campoContrasenaVisible.isVisible();
        if (mostrar) {
            campoContrasenaVisible.setText(campoContrasena.getText());
            campoContrasenaVisible.positionCaret(campoContrasena.getCaretPosition());
            campoContrasena.setVisible(false);
            campoContrasena.setManaged(false);
            campoContrasenaVisible.setVisible(true);
            campoContrasenaVisible.setManaged(true);
            campoContrasenaVisible.requestFocus();
        } else {
            campoContrasena.setText(campoContrasenaVisible.getText());
            campoContrasena.positionCaret(campoContrasenaVisible.getCaretPosition());
            campoContrasenaVisible.setVisible(false);
            campoContrasenaVisible.setManaged(false);
            campoContrasena.setVisible(true);
            campoContrasena.setManaged(true);
            campoContrasena.requestFocus();
        }
        botonMostrarContrasena.setAccessibleText(mostrar
                ? "Ocultar contraseña" : "Mostrar contraseña");
        actualizarBoton();
    }

    private void actualizarBoton() {
        boolean listo = campoCodigo.getText() != null && !campoCodigo.getText().isBlank()
                && !contrasenaActual().isEmpty();
        botonEntrar.setDisable(!listo || spinner.isVisible());
    }

    private String contrasenaActual() {
        return campoContrasenaVisible.isVisible()
                ? campoContrasenaVisible.getText() : campoContrasena.getText();
    }

    @FXML
    private void alEntrar() {
        String codigo = campoCodigo.getText() == null ? "" : campoCodigo.getText().trim();
        String clave = contrasenaActual() == null ? "" : contrasenaActual();
        if (codigo.isEmpty() || clave.isEmpty()) {
            mostrarError("Ingresa el código y la contraseña.");
            return;
        }
        ocultarMensajes();
        botonEntrar.setDisable(true);
        spinner.setVisible(true);
        spinner.setManaged(true);

        Task<Boolean> tarea = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return inicioSesion.login(codigo, clave);
            }
        };
        tarea.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                spinner.setVisible(false);
                spinner.setManaged(false);
                botonEntrar.setDisable(false);
            });
            if (!Boolean.TRUE.equals(tarea.getValue())) {
                // Credencial mala sin excepción: burbuja roja exacta, sin abrir el chat.
                try {
                    inicioSesion.desconectar();
                } catch (Exception ignorada) {
                    // ya fuera
                }
                mostrarError("Usuario no registrado o datos incorrectos.");
                return;
            }
            try {
                navegacion.mostrarChat();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "No se pudo abrir la vista de chat", ex);
                mostrarError("No se pudo abrir el chat. Intenta iniciar sesión de nuevo.");
            }
        });
        tarea.setOnFailed(e -> {
            Throwable causa = tarea.getException();
            Platform.runLater(() -> {
                spinner.setVisible(false);
                spinner.setManaged(false);
                botonEntrar.setDisable(false);
                LOG.log(Level.WARNING, "Falló el inicio de sesión", causa);
                mostrarError(mensajeUsuario(causa));
            });
        });
        tareasFondo.execute(tarea);
    }

    private void mostrarError(String texto) {
        etiquetaError.setText("✖ " + texto);
        etiquetaError.setVisible(true);
        etiquetaError.setManaged(true);
    }

    private void mostrarAviso(String texto) {
        etiquetaAviso.setText("⏳ " + texto);
        etiquetaAviso.setVisible(true);
        etiquetaAviso.setManaged(true);
    }

    private void ocultarMensajes() {
        etiquetaError.setVisible(false);
        etiquetaError.setManaged(false);
        etiquetaAviso.setVisible(false);
        etiquetaAviso.setManaged(false);
    }

    // Fase 10.2 (P2): sin «Crear cuenta». La alta de usuarios es solo por
    // archivo plano (usuarios_iniciales.csv); RegistroView queda inalcanzable.

    private static String mensajeUsuario(Throwable causa) {
        for (Throwable actual = causa; actual != null; actual = actual.getCause()) {
            if (actual instanceof SocketTimeoutException) {
                return "El servidor tardó demasiado en responder. Intenta de nuevo.";
            }
            if (actual instanceof IOException) {
                return "No fue posible conectar con el servidor. Verifica que esté activo e inténtalo de nuevo.";
            }
        }
        return "No fue posible iniciar sesión. Verifica tus datos e inténtalo de nuevo.";
    }
}
