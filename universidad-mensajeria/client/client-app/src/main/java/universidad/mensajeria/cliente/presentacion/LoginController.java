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
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

/** Login mockup §2: solo autenticacion, host/puerto visibles, spinner y avisos. */
public final class LoginController {

    @FXML
    private TextField campoCodigo;
    @FXML
    private PasswordField campoContrasena;
    @FXML
    private TextField campoHost;
    @FXML
    private TextField campoPuerto;
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
        try {
            ClienteConfig config = ClienteConfig.cargar();
            campoHost.setText(config.host());
            campoPuerto.setText(String.valueOf(config.puerto()));
        } catch (Exception e) {
            campoHost.setText("localhost");
            campoPuerto.setText("5000");
        }
        comboTema.setItems(FXCollections.observableArrayList("Oscuro", "Claro"));
        comboTema.getSelectionModel().select(
                Tema.CLARO.equals(Tema.cargar()) ? "Claro" : "Oscuro");
        comboTema.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            Tema.guardar("Claro".equals(b) ? Tema.CLARO : Tema.OSCURO);
            if (campoCodigo.getScene() != null) {
                ClienteApp.aplicarTema(campoCodigo.getScene(), true);
            }
        });
        String aviso = ClienteApp.consumirAviso();
        if (aviso != null && !aviso.isBlank()) {
            mostrarAviso(aviso);
        }
        Runnable refrescar = this::actualizarBoton;
        campoCodigo.textProperty().addListener((o, a, b) -> refrescar.run());
        campoContrasena.textProperty().addListener((o, a, b) -> refrescar.run());
        actualizarBoton();
        Platform.runLater(() -> campoCodigo.requestFocus());
    }

    private void actualizarBoton() {
        boolean listo = campoCodigo.getText() != null && !campoCodigo.getText().isBlank()
                && campoContrasena.getText() != null && !campoContrasena.getText().isEmpty();
        botonEntrar.setDisable(!listo || spinner.isVisible());
    }

    @FXML
    private void alEntrar() {
        String codigo = campoCodigo.getText() == null ? "" : campoCodigo.getText().trim();
        String clave = campoContrasena.getText() == null ? "" : campoContrasena.getText();
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
                return ClienteApp.fachada().login(codigo, clave);
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
                    ClienteApp.fachada().desconectar();
                } catch (Exception ignorada) {
                    // ya fuera
                }
                mostrarError("Usuario no registrado o datos incorrectos.");
                return;
            }
            try {
                ClienteApp.mostrarChat();
            } catch (Exception ex) {
                mostrarError(fallo(ex));
            }
        });
        tarea.setOnFailed(e -> {
            Throwable causa = tarea.getException();
            Platform.runLater(() -> {
                spinner.setVisible(false);
                spinner.setManaged(false);
                botonEntrar.setDisable(false);
                String detalle = causa != null && causa.getMessage() != null
                        ? causa.getMessage() : "no se pudo entrar";
                mostrarError("No se pudo conectar (" + causaNombre(causa) + "): " + detalle);
            });
        });
        new Thread(tarea, "login-fondo").start();
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

    private static String fallo(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "no se pudo entrar";
    }

    private static String causaNombre(Throwable causa) {
        return causa == null ? "Error" : causa.getClass().getSimpleName();
    }
}
