package universidad.mensajeria.server.vistaescritorio;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TableRow;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import universidad.mensajeria.common.interno.InformeDTO;
import universidad.mensajeria.common.interno.InformeFiltroDTO;
import universidad.mensajeria.common.interno.DetalleUsuarioDTO;
import universidad.mensajeria.common.interno.IdSesion;
import universidad.mensajeria.common.interno.MensajeResumenDTO;
import universidad.mensajeria.common.interno.PaginaDTO;
import universidad.mensajeria.common.interno.UsuarioResumen;
import universidad.mensajeria.server.transversal.fachada.Fachada;
import universidad.mensajeria.utilerias.imagen.Imagenes;
// Nota: Imagenes es helper puro de previsualizacion local (aplicarFiltroVista).
// El pipeline real (5 derivadas en uploads/) solo lo ejecuta Mensajes via
// TuberiaFactory; esta vista nunca persiste ni reordena filtros.

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Vista Escritorio (JavaFX, diagrama A2.1): habla SOLO con Fachada para datos
 * (Estado, usuarios, eventos, informes, mensajes detalle, bytes de archivos).
 * La previsualizacion con filtros es solo render local (Imagenes puras),
 * sin logica de negocio: no persiste ni reordena la tuberia del servidor.
 */
public class VistaEscritorio extends Application {

    private static volatile Fachada fachada;

    private TextArea eventos;
    private Label estado;
    private TableView<UsuarioResumen> tabla;
    private ListView<String> conectados;
    private ComboBox<String> objetivoDesconexion;
    private Label estadoDesconexion;
    private Button botonDesconexion;
    // Sesión B: visibles "Nombre Apellidos [código]" + mapa inverso + flag anti-rebote.
    private Map<String, String> codigoPorVisible = Map.of();
    private boolean sincronizandoConectados;

    // FASE 9: pestaña Informes
    private Stage escenario;
    private TabPane tabs;
    private Tab tabInformes;
    private ComboBox<String> comboTipo;
    private DatePicker desde;
    private DatePicker hasta;
    private TextField textoCodigo;
    private Label estadoInforme;
    private TableView<List<String>> tablaInformes;
    private InformeDTO ultimoInforme;

    // Difusion administrativa (solo a todos, sin selector de destinatario)
    private TextField campoDifundir;
    private Label estadoDifundir;

    // Detalle de mensajes: dos instancias del mismo componente
    // (pestaña Mensajes y pestaña Informes cuando el tipo es mensajes).
    private Tab tabMensajes;
    private ComboBox<String> filtroCodigoMensaje;
    private ComboBox<String> comboTipoMensaje;
    private Label estadoMensajes;
    private TableView<MensajeResumenDTO> tablaMensajes;
    private DetalleMensaje detalleMensajes;
    private DetalleMensaje detalleInformes;
    private List<MensajeResumenDTO> mensajesInforme = List.of();
    private Map<String, String> ipsActivasPorCodigo = Map.of();

    // Ficha de usuario: una pestaña reusable, con historiales acotados a 50 filas.
    private Tab tabDetalleUsuario;
    private Label estadoAccionUsuario;
    private Label tituloFichaUsuario;
    private Label resumenFichaUsuario;
    private Label resumenCabeceraFicha;
    private Label estadoFichaUsuario;
    private ListView<String> sesionesFichaUsuario;
    private TableView<MensajeResumenDTO> tablaHistorialUsuario;
    private TableView<MensajeResumenDTO> tablaArchivosUsuario;
    private DetalleMensaje detalleArchivosUsuario;
    private Label paginaHistorialUsuario;
    private Label paginaArchivosUsuario;
    private Button anteriorHistorialUsuario;
    private Button siguienteHistorialUsuario;
    private Button anteriorArchivosUsuario;
    private Button siguienteArchivosUsuario;
    // Sesión B: ficha→Mensajes con paridad (filtro local + detalle + divisor).
    private ComboBox<String> comboTipoHistorialUsuario;
    private Label estadoHistorialUsuario;
    private DetalleMensaje detalleHistorialUsuario;
    private List<MensajeResumenDTO> mensajesPaginaUsuario = List.of();
    private String codigoFichaUsuario;
    private int paginaMensajesUsuario;
    private int paginasMensajesUsuario;
    private int paginaArchivosUsuarioActual;
    private int paginasArchivosUsuario;
    private static final int TAMANO_PAGINA_USUARIO = 50;

    // Pool (RF-S49): barras + etiquetas numericas, auto-refresco con Estado
    private javafx.scene.control.ProgressBar barraRed;
    private javafx.scene.control.ProgressBar barraFiltros;
    private Label estadoPool;
    private Label resumenPoolRed;
    private Label resumenPoolCola;
    private Label kpiEstado;
    private Label kpiConexiones;
    private Label kpiSistema;
    private Label indicadorCabecera;
    private Label ultimaActualizacion;
    private Timeline refrescoAutomatico;

    // PLAN_MEJORAS §3: codigo -> "Nombres Apellidos [codigo]" para el detalle.
    private Map<String, String> nombresDirectorio = Map.of();

    /** Lo invoca InicioServidor antes de lanzar (DI manual). */
    public static void lanzar(Fachada fachadaLista) {
        fachada = fachadaLista;
        try {
            Application.launch(VistaEscritorio.class);
        } catch (IllegalStateException yaLanzado) {
            // toolkit ya arriba (tests): nada que hacer
        }
    }

    @Override
    public void start(Stage escenario) {
        Fachada f = fachada;
        this.escenario = escenario;
        escenario.setTitle("Mensajería Académica · Administración del servidor");

        tabInformes = pestanaInformes(f);
        tabMensajes = pestanaMensajes();
        Tab tabUsuarios = pestanaUsuarios(f);
        tabDetalleUsuario = crearPestanaDetalleUsuario();
        tabDetalleUsuario.setDisable(true);
        tabs = new TabPane(pestanaEstado(f), pestanaConectados(f), tabUsuarios,
                tabDetalleUsuario, tabMensajes, tabInformes, pestanaEventos(f),
                pestanaPool(), pestanaDifundir());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("admin-tabs");

        BorderPane raiz = new BorderPane(tabs);
        Label titulo = new Label("Centro de operaciones");
        titulo.getStyleClass().add("server-title");
        Label subtitulo = new Label("Mensajería académica  ·  Administración del servidor");
        subtitulo.getStyleClass().add("server-subtitle");
        VBox identidad = new VBox(3, titulo, subtitulo);
        Region separador = new Region();
        HBox.setHgrow(separador, Priority.ALWAYS);
        indicadorCabecera = new Label("COMPROBANDO");
        indicadorCabecera.getStyleClass().addAll("status-pill", "status-checking");
        HBox cabecera = new HBox(18, identidad, separador, indicadorCabecera);
        cabecera.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        cabecera.getStyleClass().add("server-header");
        Button iniciar = boton("Iniciar servidor", () -> {
            if (f != null) ejecutarSeguro(f::iniciarServidor);
        });
        iniciar.getStyleClass().add("action-primary");
        Button detener = boton("Detener servidor", () -> {
            if (f != null) ejecutarSeguro(f::detenerServidor);
        });
        detener.getStyleClass().add("action-danger");
        Button actualizar = boton("Actualizar datos", this::refrescarSeguro);
        actualizar.getStyleClass().add("action-secondary");
        HBox barra = new HBox(8, iniciar, detener, actualizar);
        barra.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        barra.getStyleClass().add("server-toolbar");
        VBox cabeceraCompleta = new VBox(cabecera, barra);
        raiz.setTop(cabeceraCompleta);

        ultimaActualizacion = new Label("Esperando la primera actualización…");
        ultimaActualizacion.getStyleClass().add("footer-note");
        HBox pie = new HBox(ultimaActualizacion);
        pie.getStyleClass().add("server-footer");
        raiz.setBottom(pie);

        if (f != null) {
            f.suscribirEventos(linea -> Platform.runLater(() -> {
                if (eventos != null) {
                    eventos.appendText(linea + "\n");
                }
            }));
        } else {
            indicadorCabecera.setText("SIN FACHADA");
            indicadorCabecera.getStyleClass().remove("status-checking");
            indicadorCabecera.getStyleClass().add("status-offline");
            ultimaActualizacion.setText("No se pudo conectar con la fachada del servidor.");
        }

        Scene escena = new Scene(raiz, 1280, 820);
        try {
            var css = getClass().getResource("/estilos/escritorio.css");
            if (css != null) {
                escena.getStylesheets().add(css.toExternalForm());
            }
        } catch (Exception ignorada) {
            // sin CSS la vista sigue funcional
        }
        escenario.setScene(escena);
        escenario.setMinWidth(1080);
        escenario.setMinHeight(700);
        escenario.setOnCloseRequest(evento -> {
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Deseas cerrar la vista administrativa del servidor? "
                            + "El servicio y la consola JLine seguirán activos.",
                    ButtonType.YES, ButtonType.NO);
            confirmacion.setTitle("Cerrar vista del servidor");
            confirmacion.setHeaderText("Salir de la vista administrativa");
            confirmacion.initOwner(escenario);
            if (confirmacion.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                evento.consume();
            }
        });
        escenario.show();
        refrescar();

        // Fase 10.4 (P1): auto-refresh cada 5 s de Estado/Usuarios/Conectados
        // (Eventos ya llega en vivo via suscribirEventos).
        refrescoAutomatico = new Timeline(new KeyFrame(Duration.seconds(5),
                evento -> refrescarSeguro()));
        refrescoAutomatico.setCycleCount(Timeline.INDEFINITE);
        refrescoAutomatico.play();
        escenario.setOnHidden(evento -> refrescoAutomatico.stop());
    }

    private Tab pestanaEstado(Fachada f) {
        estado = new Label();
        estado.setWrapText(true);
        kpiEstado = new Label();
        kpiEstado.getStyleClass().add("kpi-value");
        kpiConexiones = new Label();
        kpiConexiones.getStyleClass().add("kpi-value");
        kpiSistema = new Label();
        kpiSistema.getStyleClass().add("kpi-value");
        VBox tarjetaEstado = tarjetaKpi("SERVIDOR", kpiEstado);
        VBox tarjetaConexiones = tarjetaKpi("CONEXIONES", kpiConexiones);
        VBox tarjetaMensajes = tarjetaKpi("MENSAJES", kpiSistema);
        VBox kpis = new VBox(10, tarjetaEstado, tarjetaConexiones, tarjetaMensajes);
        kpis.getStyleClass().add("kpi-list");
        kpis.setFillWidth(true);
        tarjetaEstado.setMaxWidth(Double.MAX_VALUE);
        tarjetaConexiones.setMaxWidth(Double.MAX_VALUE);
        tarjetaMensajes.setMaxWidth(Double.MAX_VALUE);
        estado.getStyleClass().add("status-summary");
        Label ayuda = new Label("Datos operativos del servidor. El panel se actualiza automáticamente cada 5 segundos.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("help-text");
        VBox caja = new VBox(18, kpis, estado, ayuda);
        caja.getStyleClass().add("page-content");
        ScrollPane desplazamiento = new ScrollPane(caja);
        desplazamiento.setFitToWidth(true);
        desplazamiento.getStyleClass().add("page-scroll");
        Tab tab = new Tab("Estado", desplazamiento);
        return tab;
    }

    private VBox tarjetaKpi(String titulo, Label valor) {
        valor.setWrapText(true);
        Label encabezado = new Label(titulo);
        encabezado.getStyleClass().add("kpi-heading");
        VBox tarjeta = new VBox(10, encabezado, valor);
        tarjeta.getStyleClass().add("kpi-card");
        return tarjeta;
    }

    /** Pool red + filtros (RF-S49): numerico + barra, paridad con consola. */
    private Tab pestanaPool() {
        barraRed = new javafx.scene.control.ProgressBar(0);
        barraRed.getStyleClass().add("pool-bar");
        barraRed.setMaxWidth(Double.MAX_VALUE);
        barraFiltros = new javafx.scene.control.ProgressBar(0);
        barraFiltros.getStyleClass().add("pool-bar");
        barraFiltros.setMaxWidth(Double.MAX_VALUE);
        resumenPoolRed = new Label("Sin datos");
        resumenPoolRed.getStyleClass().add("pool-stat");
        resumenPoolCola = new Label("Sin datos");
        resumenPoolCola.getStyleClass().add("pool-stat");
        estadoPool = new Label("Las métricas se actualizan automáticamente cada 5 segundos.");
        estadoPool.setWrapText(true);
        estadoPool.setMinHeight(22);
        estadoPool.getStyleClass().add("pool-stat");
        estadoPool.setMinHeight(22);
        estadoPool.getStyleClass().add("pool-stat");
        Label tituloRed = new Label("POOL DE CONEXIONES");
        tituloRed.getStyleClass().add("pool-heading");
        Label tituloCola = new Label("COLA DE MENSAJES");
        tituloCola.getStyleClass().add("pool-heading");
        VBox tarjetaRed = new VBox(10, tituloRed,
                new Label("Trabajadores ocupados / total"), resumenPoolRed, barraRed);
        VBox tarjetaCola = new VBox(10, tituloCola,
                new Label("Presión de cola (referencia visual: 20 mensajes)"),
                resumenPoolCola, barraFiltros);
        tarjetaRed.getStyleClass().add("pool-card");
        tarjetaCola.getStyleClass().add("pool-card");
        Label tituloResumen = new Label("RESUMEN OPERATIVO");
        tituloResumen.getStyleClass().add("pool-heading");
        VBox resumen = new VBox(10, tituloResumen, estadoPool);
        resumen.getStyleClass().add("pool-summary");
        VBox contenido = new VBox(14, tarjetaRed, tarjetaCola, resumen);
        contenido.getStyleClass().add("pool-content");
        ScrollPane desplazamiento = new ScrollPane(contenido);
        desplazamiento.setFitToWidth(true);
        desplazamiento.getStyleClass().add("page-scroll");
        return new Tab("Pool", desplazamiento);
    }

    private Tab pestanaUsuarios(Fachada f) {
        tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setMaxWidth(1100);
        tabla.getColumns().addAll(
                columna("Código", 120, UsuarioResumen::codigo),
                columna("Nombres", 150, UsuarioResumen::nombres),
                columna("Apellidos", 160, UsuarioResumen::apellidos),
                columna("Programa", 220, UsuarioResumen::programa),
                columna("Conectado", 105, u -> u.conectado() ? "Sí" : "No"));
        tabla.setRowFactory(vista -> {
            TableRow<UsuarioResumen> fila = new TableRow<>();
            fila.setOnMouseClicked(evento -> {
                if (evento.getClickCount() == 2 && !fila.isEmpty()) {
                    abrirFichaUsuario(fila.getItem());
                }
            });
            return fila;
        });
        Button abrir = boton("Abrir ficha", () -> {
            UsuarioResumen seleccionado = tabla.getSelectionModel().getSelectedItem();
            if (seleccionado != null) abrirFichaUsuario(seleccionado);
        });
        Button desconectar = boton("Desconectar usuario", this::cerrarSesionesUsuario);
        desconectar.getStyleClass().add("action-danger");
        Label ayuda = new Label("Doble clic sobre un usuario para abrir su ficha completa.");
        ayuda.getStyleClass().add("help-text");
        estadoAccionUsuario = new Label(" ");
        HBox acciones = new HBox(10, abrir, desconectar, ayuda);
        acciones.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox caja = new VBox(10, tabla, acciones, estadoAccionUsuario);
        caja.setAlignment(javafx.geometry.Pos.CENTER);
        caja.getStyleClass().add("page-content");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        ScrollPane desplazamiento = new ScrollPane(caja);
        desplazamiento.setFitToWidth(true);
        desplazamiento.getStyleClass().add("page-scroll");
        return new Tab("Usuarios", desplazamiento);
    }

    private void abrirFichaUsuario(UsuarioResumen usuario) {
        if (usuario == null || tabs == null) return;
        if (tabDetalleUsuario == null) {
            tabDetalleUsuario = crearPestanaDetalleUsuario();
        }
        tabDetalleUsuario.setDisable(false);
        tabDetalleUsuario.setText("Ficha usuario");
        codigoFichaUsuario = usuario.codigo();
        paginaMensajesUsuario = 0;
        paginaArchivosUsuarioActual = 0;
        actualizarFichaUsuario();
        tabs.getSelectionModel().select(tabDetalleUsuario);
    }

    private Tab crearPestanaDetalleUsuario() {
        tituloFichaUsuario = new Label("Ficha del usuario");
        tituloFichaUsuario.getStyleClass().add("detail-title");
        resumenFichaUsuario = new Label(" ");
        resumenFichaUsuario.setWrapText(true);
        resumenFichaUsuario.getStyleClass().add("detail-summary");
        resumenCabeceraFicha = new Label(" ");
        resumenCabeceraFicha.setWrapText(true);
        resumenCabeceraFicha.getStyleClass().add("detail-summary");
        estadoFichaUsuario = new Label(" ");
        sesionesFichaUsuario = new ListView<>();

        VBox resumen = new VBox(10, new Label("Perfil y actividad"), resumenFichaUsuario);
        resumen.getStyleClass().add("detail-section");
        VBox conexiones = new VBox(10, new Label("Sesiones activas e IP"), sesionesFichaUsuario);
        VBox.setVgrow(sesionesFichaUsuario, Priority.ALWAYS);
        conexiones.getStyleClass().add("detail-section");

        tablaHistorialUsuario = tablaHistorial(false);
        paginaHistorialUsuario = new Label(" ");
        estadoHistorialUsuario = new Label(" ");
        comboTipoHistorialUsuario = new ComboBox<>();
        comboTipoHistorialUsuario.getItems().setAll("Todos", "TEXTO", "IMAGEN", "ARCHIVO", "BROADCAST");
        comboTipoHistorialUsuario.setValue("Todos");
        comboTipoHistorialUsuario.valueProperty().addListener((obs, ant, actual) ->
                mostrarPaginaUsuarioFiltrada());
        detalleHistorialUsuario = new DetalleMensaje(escenario, () -> fachada,
                this::nombreVisible, this::ipDestinatarioActual);
        tablaHistorialUsuario.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> detalleHistorialUsuario.mostrar(actual));
        anteriorHistorialUsuario = boton("Anterior", () -> {
            if (paginaMensajesUsuario > 0) {
                paginaMensajesUsuario--;
                cargarPaginaUsuario(false);
            }
        });
        siguienteHistorialUsuario = boton("Siguiente", () -> {
            if (paginaMensajesUsuario + 1 < paginasMensajesUsuario) {
                paginaMensajesUsuario++;
                cargarPaginaUsuario(false);
            }
        });
        HBox filtrosFicha = new HBox(8, new Label("Tipo:"), comboTipoHistorialUsuario,
                estadoHistorialUsuario);
        filtrosFicha.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox listaFicha = new VBox(8, new Label("Mensajes enviados y recibidos"),
                filtrosFicha,
                tablaHistorialUsuario,
                new HBox(8, anteriorHistorialUsuario, paginaHistorialUsuario,
                        siguienteHistorialUsuario));
        listaFicha.setMinHeight(160);
        VBox.setVgrow(tablaHistorialUsuario, Priority.ALWAYS);
        SplitPane detalleFicha = new SplitPane(listaFicha, detalleHistorialUsuario.vista());
        detalleFicha.setOrientation(javafx.geometry.Orientation.VERTICAL);
        detalleFicha.setDividerPositions(0.58);
        detalleFicha.setResizableWithParent(listaFicha, true);
        VBox.setVgrow(detalleFicha, Priority.ALWAYS);
        VBox historial = new VBox(8, detalleFicha);
        VBox.setVgrow(detalleFicha, Priority.ALWAYS);

        tablaArchivosUsuario = tablaHistorial(true);
        detalleArchivosUsuario = new DetalleMensaje(escenario, () -> fachada,
                this::nombreVisible, this::ipDestinatarioActual);
        tablaArchivosUsuario.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> detalleArchivosUsuario.mostrar(actual));
        paginaArchivosUsuario = new Label(" ");
        anteriorArchivosUsuario = boton("Anterior", () -> {
            if (paginaArchivosUsuarioActual > 0) {
                paginaArchivosUsuarioActual--;
                cargarPaginaUsuario(true);
            }
        });
        siguienteArchivosUsuario = boton("Siguiente", () -> {
            if (paginaArchivosUsuarioActual + 1 < paginasArchivosUsuario) {
                paginaArchivosUsuarioActual++;
                cargarPaginaUsuario(true);
            }
        });
        VBox listaArchivos = new VBox(8, new Label("Archivos e imágenes compartidos"),
                tablaArchivosUsuario,
                new HBox(8, anteriorArchivosUsuario, paginaArchivosUsuario,
                        siguienteArchivosUsuario));
        VBox.setVgrow(tablaArchivosUsuario, Priority.ALWAYS);
        SplitPane detalleArchivos = new SplitPane(listaArchivos, detalleArchivosUsuario.vista());
        detalleArchivos.setOrientation(javafx.geometry.Orientation.VERTICAL);
        detalleArchivos.setDividerPositions(0.58);
        VBox archivos = new VBox(8, detalleArchivos);
        VBox.setVgrow(detalleArchivos, Priority.ALWAYS);

        TabPane secciones = new TabPane(
                new Tab("Resumen", resumen),
                new Tab("Conexiones", conexiones),
                new Tab("Mensajes", historial),
                new Tab("Archivos", archivos));
        secciones.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox raiz = new VBox(12, tituloFichaUsuario, resumenCabeceraFicha, estadoFichaUsuario,
                new Separator(), secciones);
        VBox.setVgrow(secciones, Priority.ALWAYS);
        raiz.getStyleClass().add("detail-page");
        Tab tab = new Tab("Detalle usuario", raiz);
        tab.setClosable(false);
        return tab;
    }

    private TableView<MensajeResumenDTO> tablaHistorial(boolean soloArchivos) {
        TableView<MensajeResumenDTO> tablaDetalle = new TableView<>();
        if (soloArchivos) {
            tablaDetalle.getColumns().addAll(
                    colMensaje("Fecha", 170, m -> txt(m.fechaEnvio())),
                    colMensaje("Nombre", 230, m -> txt(m.nombreArchivo())),
                    colMensaje("Tipo", 100, m -> txt(m.tipo())),
                    colMensaje("Formato", 150, m -> txt(m.mime())),
                    colMensaje("Tamaño", 120, m -> m.tamanoArchivo() == null
                            ? "-" : m.tamanoArchivo() + " bytes"),
                    colMensaje("Remitente", 150, m -> nombreVisible(m.remitente())),
                    colMensaje("Destinatario", 150, m -> nombreVisible(m.destinatario())));
        } else {
            tablaDetalle.getColumns().addAll(
                    colMensaje("Fecha", 170, m -> txt(m.fechaEnvio())),
                    colMensaje("Remitente", 170, m -> nombreVisible(m.remitente())),
                    colMensaje("Destinatario", 170, m -> nombreVisible(m.destinatario())),
                    colMensaje("Tipo", 100, m -> txt(m.tipo())),
                    colMensaje("Contenido / archivo", 260,
                            m -> txt(m.nombreArchivo() == null ? m.contenido() : m.nombreArchivo())));
        }
        return tablaDetalle;
    }

    private void actualizarFichaUsuario() {
        actualizarFichaUsuario(true);
    }

    private void actualizarFichaUsuario(boolean recargarHistorial) {
        Fachada f = fachada;
        if (f == null || codigoFichaUsuario == null || tabDetalleUsuario == null) return;
        try {
            DetalleUsuarioDTO ficha = f.detalleUsuario(codigoFichaUsuario);
            tituloFichaUsuario.setText("Ficha · " + txt(ficha.nombres()) + " "
                    + txt(ficha.apellidos()) + " [" + ficha.codigo() + "]");
            resumenFichaUsuario.setText(String.join("\n", List.of(
                    "Estado: " + (ficha.conectado() ? "En línea" : "Fuera de línea"),
                    "Programa: " + txt(ficha.programa()),
                    "Registro: " + txt(ficha.fechaRegistro()),
                    "Última conexión: " + txt(ficha.ultimaConexion()),
                    "Conexiones registradas: " + ficha.conexionesRegistradas(),
                    "Sesiones activas: " + ficha.sesionesActivas(),
                    "Mensajes enviados o recibidos: " + ficha.totalMensajes(),
                    "Archivos compartidos: " + ficha.totalArchivos(),
                    "Contacto principal: " + (ficha.contactoPrincipal() == null
                            ? "Sin conversaciones" : nombreVisible(ficha.contactoPrincipal())
                            + " · " + ficha.mensajesCompartidos() + " mensajes"))));
            resumenCabeceraFicha.setText("Estado: "
                    + (ficha.conectado() ? "En línea" : "Fuera de línea")
                    + " · Programa: " + txt(ficha.programa()));
            List<String> sesiones = f.sesionesActivas().stream()
                    .filter(s -> codigoFichaUsuario.equalsIgnoreCase(s.codigo()))
                    .map(s -> "Sesión " + s.id() + " · IP " + txt(s.direccionIp()))
                    .toList();
            sesionesFichaUsuario.getItems().setAll(sesiones.isEmpty()
                    ? List.of("No hay sesiones activas para este usuario.") : sesiones);
            if (recargarHistorial) {
                estadoFichaUsuario.setText("Historial completo paginado de 50 registros por página.");
                cargarPaginaUsuario(false);
                cargarPaginaUsuario(true);
            }
        } catch (RuntimeException e) {
            estadoFichaUsuario.setText("No se pudo cargar la ficha: " + e.getMessage());
        }
    }

    private void cargarPaginaUsuario(boolean soloArchivos) {
        if (codigoFichaUsuario == null || fachada == null) return;
        int paginaSolicitada = soloArchivos ? paginaArchivosUsuarioActual : paginaMensajesUsuario;
        try {
            PaginaDTO<MensajeResumenDTO> pagina = fachada.paginaMensajesUsuario(
                    codigoFichaUsuario, soloArchivos, paginaSolicitada, TAMANO_PAGINA_USUARIO);
            TableView<MensajeResumenDTO> tablaPagina = soloArchivos
                    ? tablaArchivosUsuario : tablaHistorialUsuario;
            if (!soloArchivos) {
                mensajesPaginaUsuario = List.copyOf(pagina.contenido());
                mostrarPaginaUsuarioFiltrada();
            } else {
                MensajeResumenDTO sel = tablaPagina.getSelectionModel().getSelectedItem();
                long idSel = sel == null ? -1L : sel.id();
                tablaPagina.getItems().setAll(pagina.contenido());
                restaurarSeleccionPorId(tablaPagina, pagina.contenido(), idSel);
            }
            int paginas = pagina.totalPaginas();
            String estadoPagina = pagina.contenido().isEmpty()
                    ? "Sin registros" : "Página " + (pagina.pagina() + 1) + " de " + paginas
                            + " · " + pagina.contenido().size() + " registros";
            if (soloArchivos) {
                paginasArchivosUsuario = paginas;
                paginaArchivosUsuario.setText(estadoPagina);
                anteriorArchivosUsuario.setDisable(paginaArchivosUsuarioActual <= 0);
                siguienteArchivosUsuario.setDisable(paginaArchivosUsuarioActual + 1 >= paginas);
            } else {
                paginasMensajesUsuario = paginas;
                paginaHistorialUsuario.setText(estadoPagina);
                anteriorHistorialUsuario.setDisable(paginaMensajesUsuario <= 0);
                siguienteHistorialUsuario.setDisable(paginaMensajesUsuario + 1 >= paginas);
            }
        } catch (RuntimeException e) {
            estadoFichaUsuario.setText("No se pudo cargar el historial: " + e.getMessage());
        }
    }

    /** Sesión B: filtro local por tipo + conserva selección por id como Mensajes. */
    private void mostrarPaginaUsuarioFiltrada() {
        if (tablaHistorialUsuario == null) {
            return;
        }
        String tipo = comboTipoHistorialUsuario == null ? "Todos"
                : comboTipoHistorialUsuario.getValue();
        MensajeResumenDTO sel = tablaHistorialUsuario.getSelectionModel().getSelectedItem();
        long idSel = sel == null ? -1L : sel.id();
        List<MensajeResumenDTO> filtrados = filtrarPorTipo(mensajesPaginaUsuario, tipo);
        tablaHistorialUsuario.getItems().setAll(filtrados);
        if (estadoHistorialUsuario != null) {
            estadoHistorialUsuario.setText(filtrados.size() + " mensaje(s)"
                    + ("Todos".equals(tipo) ? "" : " de tipo " + tipo));
        }
        restaurarSeleccionPorId(tablaHistorialUsuario, filtrados, idSel);
    }

    private static void restaurarSeleccionPorId(TableView<MensajeResumenDTO> tablaPagina,
            List<MensajeResumenDTO> visibles, long idSel) {
        if (tablaPagina == null || idSel < 0) {
            return;
        }
        int idx = indicePorId(visibles, idSel);
        if (idx >= 0) {
            tablaPagina.getSelectionModel().select(idx);
        }
    }

    private void cerrarSesionesUsuario() {
        Fachada f = fachada;
        UsuarioResumen usuario = tabla == null ? null : tabla.getSelectionModel().getSelectedItem();
        if (f == null || usuario == null) return;
        if (!confirmarDesconexion(nombreVisible(usuario.codigo()))) {
            return;
        }
        try {
            int cerradas = f.cerrarConexionAdmin(usuario.codigo(), "cierre administrativo");
            refrescarSeguro();
            if (codigoFichaUsuario != null && codigoFichaUsuario.equals(usuario.codigo())) {
                actualizarFichaUsuario();
                estadoFichaUsuario.setText("Desconectadas " + cerradas + " sesiones de "
                        + usuario.codigo() + ".");
            }
            estadoAccionUsuario.setText("Desconectadas " + cerradas + " sesiones de "
                    + usuario.codigo() + ".");
        } catch (RuntimeException e) {
            estadoAccionUsuario.setText("No se pudo desconectar: " + e.getMessage());
        }
    }

    private void verMensajesUsuario() {
        UsuarioResumen usuario = tabla == null ? null : tabla.getSelectionModel().getSelectedItem();
        if (usuario == null || tabs == null || tabMensajes == null) return;
        tabs.getSelectionModel().select(tabMensajes);
        if (filtroCodigoMensaje != null) {
            filtroCodigoMensaje.setValue(usuario.codigo());
            filtroCodigoMensaje.getEditor().setText(usuario.codigo());
        }
        ejecutarSeguro(this::actualizarMensajes);
    }

    private static TableColumn<UsuarioResumen, String> columna(String titulo, int ancho,
                                                               java.util.function.Function<UsuarioResumen, String> valor) {
        TableColumn<UsuarioResumen, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(datos -> new ReadOnlyStringWrapper(valor.apply(datos.getValue())));
        columna.setPrefWidth(ancho);
        return columna;
    }

    private Tab pestanaConectados(Fachada f) {
        conectados = new ListView<>();
        objetivoDesconexion = new ComboBox<>();
        objetivoDesconexion.setPromptText("No hay usuarios activos");
        objetivoDesconexion.setMaxWidth(360);
        estadoDesconexion = new Label("No hay usuarios activos");
        botonDesconexion = boton("Desconectar usuario", this::desconectarUsuarioActivo);
        botonDesconexion.getStyleClass().add("action-danger");
        botonDesconexion.setDisable(true);
        // Sesión B: sync lista↔combo una sola vez (flag anti-rebote).
        conectados.getSelectionModel().selectedItemProperty().addListener((obs, ant, actual) -> {
            if (sincronizandoConectados || actual == null || objetivoDesconexion == null) {
                return;
            }
            sincronizandoConectados = true;
            try {
                objetivoDesconexion.setValue(actual);
            } finally {
                sincronizandoConectados = false;
            }
        });
        objetivoDesconexion.valueProperty().addListener((obs, ant, actual) -> {
            if (sincronizandoConectados || actual == null || conectados == null) {
                return;
            }
            sincronizandoConectados = true;
            try {
                conectados.getSelectionModel().select(actual);
            } finally {
                sincronizandoConectados = false;
            }
        });
        HBox barra = new HBox(10, new Label("Usuario activo:"), objetivoDesconexion,
                botonDesconexion);
        barra.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        barra.getStyleClass().add("disconnect-toolbar");
        VBox caja = new VBox(10, conectados, barra, estadoDesconexion);
        VBox.setVgrow(conectados, Priority.ALWAYS);
        return new Tab("Conectados", caja);
    }

    private void desconectarUsuarioActivo() {
        Fachada f = fachada;
        String visible = objetivoDesconexion == null ? null : objetivoDesconexion.getValue();
        if (f == null || visible == null || visible.isBlank()) return;
        String codigo = codigoPorVisible.getOrDefault(visible, visible);
        if (!confirmarDesconexion(visible)) {
            return;
        }
        try {
            int cerradas = f.cerrarConexionAdmin(codigo, "desconexión administrativa");
            estadoDesconexion.setText("Desconectadas " + cerradas + " sesiones de " + visible + ".");
            refrescarSeguro();
        } catch (RuntimeException e) {
            estadoDesconexion.setText("No se pudo desconectar " + visible + ": " + e.getMessage());
        }
    }

    /** Sesión B: confirmación Sí/No; extraída para no duplicar el diálogo. */
    private boolean confirmarDesconexion(String visible) {
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                mensajeConfirmacionDesconexion(visible),
                ButtonType.YES, ButtonType.NO);
        conf.setTitle("Desconectar usuario");
        conf.setHeaderText(null);
        if (escenario != null) {
            conf.initOwner(escenario);
        }
        return conf.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private Tab pestanaEventos(Fachada f) {
        eventos = new TextArea();
        eventos.setEditable(false);
        eventos.setPromptText("Eventos en vivo (Observer): conexiones, mensajes, cierres, logs.");
        eventos.setPrefRowCount(30);
        eventos.setWrapText(true);
        VBox caja = new VBox(eventos);
        VBox.setVgrow(eventos, Priority.ALWAYS);
        caja.getStyleClass().add("logs-content");
        return new Tab("Logs", caja);
    }

    /** Difusion administrativa: solo a TODOS, sin selector de destinatario. */
    private Tab pestanaDifundir() {
        campoDifundir = new TextField();
        campoDifundir.setPromptText("Mensaje para toda la comunidad (broadcast, sin destinatario unico)");
        estadoDifundir = new Label("La difusion llega a todos los usuarios registrados.");
        estadoDifundir.setWrapText(true);
        Button difundir = boton("Enviar difusión", () -> ejecutarSeguro(() -> {
            Fachada f = fachada;
            if (f == null) {
                return;
            }
            String contenido = campoDifundir.getText() == null ? "" : campoDifundir.getText().trim();
            if (contentoVacio(contenido)) {
                estadoDifundir.setText("Escribe un mensaje no vacio para difundir.");
                return;
            }
            try {
                f.difundirAdministrativo(contenido);
                estadoDifundir.setText("Difusion encolada para todos.");
                campoDifundir.clear();
            } catch (RuntimeException e) {
                estadoDifundir.setText("No se pudo difundir: " + e.getMessage());
            }
        }));
        HBox fila = new HBox(8, campoDifundir, difundir);
        fila.setStyle("-fx-padding: 8;");
        HBox.setHgrow(campoDifundir, Priority.ALWAYS);
        Label ayuda = new Label("El servidor no puede elegir un unico destinatario: siempre es broadcast.");
        ayuda.setWrapText(true);
        VBox caja = new VBox(8, fila, estadoDifundir, ayuda);
        caja.setStyle("-fx-padding: 12;");
        return new Tab("Difundir", caja);
    }

    private static boolean contentoVacio(String contenido) {
        return contenido == null || contenido.isBlank();
    }

    // ------------------------------------------------- Detalle de mensajes

    private Tab pestanaMensajes() {
        filtroCodigoMensaje = new ComboBox<>();
        filtroCodigoMensaje.setEditable(true);
        filtroCodigoMensaje.setPromptText("Código o SERVIDOR (vacío = todos)");
        filtroCodigoMensaje.setPrefWidth(280);
        filtroCodigoMensaje.getEditor().setOnAction(evento -> ejecutarSeguro(this::actualizarMensajes));
        comboTipoMensaje = new ComboBox<>();
        comboTipoMensaje.getItems().setAll("Todos", "TEXTO", "IMAGEN", "ARCHIVO", "BROADCAST");
        comboTipoMensaje.setValue("Todos");
        Button actualizar = boton("Actualizar", () -> ejecutarSeguro(this::actualizarMensajes));
        HBox filtros = new HBox(8, new Label("Código:"), filtroCodigoMensaje,
                new Label("Tipo:"), comboTipoMensaje, actualizar);
        filtros.setStyle("-fx-padding: 8;");

        estadoMensajes = new Label("Pulsa Actualizar para ver el detalle con contenido.");
        tablaMensajes = new TableView<>();
        tablaMensajes.getColumns().addAll(
                colMensaje("Id", 60, m -> String.valueOf(m.id())),
                colMensaje("Fecha", 130, m -> txt(m.fechaEnvio())),
                colMensaje("Remitente", 150, m -> m.remitente()),
                colMensaje("Destinatario", 150, m -> m.destinatario()),
                colMensaje("IP remitente", 140, m -> txt(m.ipRemitente())),
                colMensaje("IP destinatario", 160, m -> ipDestinatarioActual(m.destinatario())),
                colMensaje("Tipo", 90, m -> txt(m.tipo())),
                colMensaje("Hash", 120, m -> corto(m.hashSha256())));
        tablaMensajes.setMinHeight(130);
        tablaMensajes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaMensajes.getSelectionModel().selectedItemProperty().addListener(
                (obs, ant, actual) -> mostrarDetalle(actual));

        detalleMensajes = new DetalleMensaje(escenario, () -> fachada,
                this::nombreVisible, this::ipDestinatarioActual);
        VBox lista = new VBox(4, filtros, estadoMensajes, tablaMensajes);
        lista.setMinHeight(160);
        VBox.setVgrow(tablaMensajes, Priority.ALWAYS);
        SplitPane contenido = new SplitPane(lista, detalleMensajes.vista());
        contenido.setOrientation(javafx.geometry.Orientation.VERTICAL);
        contenido.setDividerPositions(0.42);
        contenido.setStyle("-fx-padding: 4 8 8 8;");
        VBox caja = new VBox(contenido);
        VBox.setVgrow(contenido, Priority.ALWAYS);
        return new Tab("Mensajes", caja);
    }

    private static TableColumn<MensajeResumenDTO, String> colMensaje(String titulo, int ancho,
            java.util.function.Function<MensajeResumenDTO, String> valor) {
        TableColumn<MensajeResumenDTO, String> col = new TableColumn<>(titulo);
        col.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor.apply(d.getValue())));
        col.setPrefWidth(ancho);
        return col;
    }

    private void actualizarMensajes() {
        Fachada f = fachada;
        if (f == null) {
            return;
        }
        String codigo = filtroCodigoMensaje.getEditor().getText() == null
                ? "" : filtroCodigoMensaje.getEditor().getText().trim();
        if (codigo.isEmpty() && filtroCodigoMensaje.getValue() != null) {
            codigo = filtroCodigoMensaje.getValue().trim();
        }
        String tipo = comboTipoMensaje.getValue();
        InformeFiltroDTO filtro = new InformeFiltroDTO(null, null, codigo);
        List<MensajeResumenDTO> todos;
        try {
            todos = f.mensajesDetalle(filtro);
        } catch (RuntimeException e) {
            estadoMensajes.setText("No se pudo cargar: " + e.getMessage());
            return;
        }
        List<MensajeResumenDTO> filtrados = todos.stream()
                .filter(m -> "Todos".equals(tipo) || (m.tipo() != null && m.tipo().equalsIgnoreCase(tipo)))
                .toList();
        // Conservar la seleccion: el auto-refresh no borra el detalle en curso.
        MensajeResumenDTO sel = tablaMensajes.getSelectionModel().getSelectedItem();
        long idSel = sel == null ? -1L : sel.id();
        tablaMensajes.getItems().setAll(filtrados);
        estadoMensajes.setText(filtrados.size() + " mensaje(s)"
                + (codigo.isEmpty() ? "" : " con " + codigo)
                + (todos.size() != filtrados.size() ? " (de " + todos.size() + " totales)" : ""));
        if (idSel >= 0) {
            for (MensajeResumenDTO m : filtrados) {
                if (m.id() == idSel) {
                    tablaMensajes.getSelectionModel().select(m);
                    break;
                }
            }
        }
    }

    private String ipDestinatarioActual(String codigo) {
        return ipVivaDe(ipsActivasPorCodigo, codigo);
    }

    /** RF-S53: IP de sesiones activas del destinatario, o "No disponible". */
    static String ipVivaDe(Map<String, String> ipsActivas, String codigo) {
        if (codigo == null || codigo.isBlank()) return "No disponible";
        if (ipsActivas == null) return "No disponible";
        return ipsActivas.getOrDefault(
                codigo.trim().toUpperCase(java.util.Locale.ROOT), "No disponible");
    }

    /** "Nombres Apellidos [codigo]" o el codigo si no esta en el directorio. */
    private String nombreVisible(String codigo) {
        return resolverVisible(codigo, nombresDirectorio);
    }

    // Sesión B: lógica sin toolkit (testeable) para Conectados / ficha / Informes.
    static String resolverVisible(String codigo, Map<String, String> directorio) {
        if (codigo == null || codigo.isBlank()) {
            return "-";
        }
        if ("SERVIDOR".equalsIgnoreCase(codigo)) {
            return "SERVIDOR (sistema)";
        }
        if (directorio == null) {
            return codigo;
        }
        return directorio.getOrDefault(codigo, codigo);
    }

    static List<String> visiblesDe(List<String> activos, Map<String, String> directorio) {
        if (activos == null) {
            return List.of();
        }
        List<String> visibles = new ArrayList<>(activos.size());
        for (String codigo : activos) {
            visibles.add(resolverVisible(codigo, directorio));
        }
        return visibles;
    }

    static Map<String, String> indiceCodigoPorVisible(List<String> activos,
            Map<String, String> directorio) {
        Map<String, String> inverso = new HashMap<>();
        if (activos == null) {
            return inverso;
        }
        for (String codigo : activos) {
            inverso.put(resolverVisible(codigo, directorio), codigo);
        }
        return inverso;
    }

    static String mensajeConfirmacionDesconexion(String visible) {
        return "¿Desconectar a " + visible
                + "? Se cerrarán sus sesiones activas.";
    }

    static List<MensajeResumenDTO> filtrarPorTipo(List<MensajeResumenDTO> pagina, String tipo) {
        if (pagina == null) {
            return List.of();
        }
        if (tipo == null || "Todos".equals(tipo)) {
            return List.copyOf(pagina);
        }
        List<MensajeResumenDTO> filtrados = new ArrayList<>();
        for (MensajeResumenDTO m : pagina) {
            if (m != null && m.tipo() != null && m.tipo().equalsIgnoreCase(tipo)) {
                filtrados.add(m);
            }
        }
        return filtrados;
    }

    static int indicePorId(List<MensajeResumenDTO> lista, long id) {
        if (lista == null) {
            return -1;
        }
        for (int i = 0; i < lista.size(); i++) {
            MensajeResumenDTO m = lista.get(i);
            if (m != null && m.id() == id) {
                return i;
            }
        }
        return -1;
    }

    private void mostrarDetalle(MensajeResumenDTO m) {
        if (detalleMensajes != null) {
            detalleMensajes.mostrar(m);
        }
    }

    /** Núcleo puro de la previsualización (testeable sin JavaFX). */
    static java.awt.image.BufferedImage aplicarFiltroVista(
            java.awt.image.BufferedImage base, String opcion) {
        if (base == null) {
            throw new IllegalArgumentException("imagen base vacía");
        }
        String o = opcion == null ? "" : opcion;
        return switch (o) {
            case "Grises" -> Imagenes.aGrises(base);
            case "Sepia" -> Imagenes.aSepia(base);
            case "Giro 180" -> Imagenes.girar(base, 180);
            case "Brillo x1.25" -> Imagenes.ajustarBrillo(base, 1.25);
            case "Reducida 50%" -> Imagenes.reducir(base, 0.5);
            default -> Imagenes.nuevaCopia(base);
        };
    }

    /** true si ImageIO puede abrirlo (requisito para previsualizar filtros). */
    static boolean decodificableParaFiltros(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        try {
            return javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean esImagen(byte[] bytes, String mime, String nombre) {
        if (mime != null && mime.toLowerCase().startsWith("image/")) {
            return true;
        }
        String n = nombre == null ? "" : nombre.toLowerCase();
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp")) {
            return true;
        }
        return bytes != null && bytes.length >= 4
                && ((bytes[0] & 0xFF) == 0x89 || (bytes[0] & 0xFF) == 0xFF
                        || (bytes[0] & 0xFF) == 0x47 || (bytes[0] & 0xFF) == 0x42);
    }

    static String resumirEtapas(String json) {
        if (json == null || json.isBlank()) {
            return "-";
        }
        String recorte = json.length() > 400 ? json.substring(0, 400) + "..." : json;
        return recorte.replaceAll("\\s+", " ");
    }

    static String txt(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    static String baseSinExtension(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "archivo";
        }
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    static String corto(String hash) {
        if (hash == null || hash.isBlank()) {
            return "-";
        }
        return hash.length() <= 12 ? hash : hash.substring(0, 12) + "...";
    }

    // ------------------------------------------------- FASE 9: pestaña Informes

    private Tab pestanaInformes(Fachada f) {
        comboTipo = new ComboBox<>();
        comboTipo.getItems().setAll("usuarios", "conexiones", "mensajes", "auditoria");
        comboTipo.setValue("usuarios");
        desde = new DatePicker();
        desde.setPromptText("desde (aaaa-mm-dd)");
        hasta = new DatePicker();
        hasta.setPromptText("hasta (aaaa-mm-dd)");
        textoCodigo = new TextField();
        textoCodigo.setPromptText("codigo usuario (o SERVIDOR)");
        textoCodigo.setPrefWidth(170);
        estadoInforme = new Label(" ");

        Button actualizar = boton("Actualizar", () -> ejecutarSeguro(this::actualizarInforme));
        Button exportar = boton("Exportar CSV", () -> ejecutarSeguro(this::exportarCsv));
        Label ayuda = new Label("Pulsa Actualizar; el refresco automático no mueve tu posición.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("help-text");
        HBox filtros = new HBox(8, new Label("Tipo:"), comboTipo,
                new Label("Desde:"), desde, new Label("Hasta:"), hasta,
                new Label("Codigo:"), textoCodigo, actualizar, exportar);
        filtros.setStyle("-fx-padding: 8;");

        tablaInformes = new TableView<>();
        tablaInformes.getSelectionModel().selectedItemProperty().addListener(
                (obs, ant, actual) -> mostrarDetalleInforme(actual));
        detalleInformes = new DetalleMensaje(escenario, () -> fachada,
                this::nombreVisible, this::ipDestinatarioActual);
        detalleInformes.vista().setVisible(false);
        detalleInformes.vista().setManaged(false);
        comboTipo.valueProperty().addListener((obs, anterior, tipo) -> {
            boolean mostrar = "mensajes".equals(tipo);
            detalleInformes.vista().setVisible(mostrar);
            detalleInformes.vista().setManaged(mostrar);
            if (!mostrar) detalleInformes.mostrar(null);
        });
        VBox caja = new VBox(8, filtros, ayuda, estadoInforme, tablaInformes, detalleInformes.vista());
        VBox.setVgrow(tablaInformes, javafx.scene.layout.Priority.ALWAYS);
        return new Tab("Informes", caja);
    }

    /** Maestro-detalle en Informes: solo el tipo mensajes tiene detalle. */
    private void mostrarDetalleInforme(List<String> fila) {
        if (detalleInformes == null) {
            return;
        }
        if (!"mensajes".equals(comboTipo == null ? "" : comboTipo.getValue())
                || fila == null || fila.isEmpty()) {
            detalleInformes.mostrar(null);
            return;
        }
        long id;
        try {
            id = Long.parseLong(fila.get(0).trim());
        } catch (NumberFormatException e) {
            detalleInformes.mostrar(null);
            return;
        }
        for (MensajeResumenDTO m : mensajesInforme) {
            if (m.id() == id) {
                detalleInformes.mostrar(m);
                return;
            }
        }
        detalleInformes.mostrar(null);
    }

    /** Genera el informe con los filtros de la pestaña (manual, NO auto-refresh). */
    private void actualizarInforme() {
        Fachada f = fachada;
        if (f == null) {
            return;
        }
        // Sesión B: conservar fila/selección/detalle aunque se pulse Actualizar.
        List<String> selAnterior = tablaInformes == null ? null
                : tablaInformes.getSelectionModel().getSelectedItem();
        String idAnterior = selAnterior == null || selAnterior.isEmpty() ? null
                : selAnterior.get(0);
        int idxAnterior = tablaInformes == null ? -1
                : tablaInformes.getSelectionModel().getSelectedIndex();
        InformeFiltroDTO filtro = new InformeFiltroDTO(
                desde.getValue() == null ? null : desde.getValue().toString(),
                hasta.getValue() == null ? null : hasta.getValue().toString(),
                textoCodigo.getText() == null ? "" : textoCodigo.getText().trim());
        InformeDTO informe = switch (comboTipo.getValue()) {
            case "conexiones" -> f.informeConexiones(filtro);
            case "mensajes" -> f.informeMensajes(filtro);
            case "auditoria" -> f.informeAuditoria(filtro);
            default -> f.informeUsuarios(filtro);
        };
        ultimoInforme = informe;
        if ("mensajes".equals(comboTipo.getValue())) {
            try {
                mensajesInforme = f.mensajesDetalle(filtro);
            } catch (RuntimeException e) {
                mensajesInforme = List.of();
            }
        } else {
            mensajesInforme = List.of();
        }
        pintar(informe);
        estadoInforme.setText(informe.titulo() + " · " + informe.filas().size() + " fila(s)");
        restaurarSeleccionInforme(idAnterior, idxAnterior);
    }

    /** Sesión B: restaura la fila anterior por id (columna 0) o por índice. */
    private void restaurarSeleccionInforme(String idAnterior, int idxAnterior) {
        if (tablaInformes == null) {
            return;
        }
        List<List<String>> filas = tablaInformes.getItems();
        if (filas.isEmpty()) {
            return;
        }
        int destino = -1;
        if (idAnterior != null) {
            for (int i = 0; i < filas.size(); i++) {
                List<String> fila = filas.get(i);
                if (fila != null && !fila.isEmpty() && idAnterior.equals(fila.get(0))) {
                    destino = i;
                    break;
                }
            }
        }
        if (destino < 0 && idxAnterior >= 0 && idxAnterior < filas.size()) {
            destino = idxAnterior;
        }
        if (destino >= 0) {
            tablaInformes.getSelectionModel().select(destino);
            tablaInformes.scrollTo(destino);
            mostrarDetalleInforme(filas.get(destino));
        }
    }

    private void pintar(InformeDTO informe) {
        // Sesión B: no reconstruir columnas si no cambiaron (evita parpadeo).
        List<String> columnas = informe.columnas();
        List<String> actuales = new ArrayList<>();
        for (TableColumn<List<String>, ?> col : tablaInformes.getColumns()) {
            actuales.add(col.getText());
        }
        if (!actuales.equals(columnas)) {
            tablaInformes.getColumns().clear();
            for (int i = 0; i < columnas.size(); i++) {
                int indice = i;
                TableColumn<List<String>, String> columna = new TableColumn<>(columnas.get(i));
                columna.setCellValueFactory(datos -> {
                    List<String> fila = datos.getValue();
                    String valor = indice < fila.size() && fila.get(indice) != null
                            ? fila.get(indice) : "";
                    return new ReadOnlyStringWrapper(valor);
                });
                columna.setPrefWidth(indice == 0 ? 150 : 170);
                tablaInformes.getColumns().add(columna);
            }
        }
        tablaInformes.getItems().setAll(informe.filas());
    }

    private void exportarCsv() {
        if (ultimoInforme == null) {
            actualizarInforme();
        }
        if (ultimoInforme == null) {
            return;
        }
        FileChooser elegidor = new FileChooser();
        elegidor.setTitle("Exportar informe");
        elegidor.setInitialFileName(ultimoInforme.nombreArchivoCsv());
        elegidor.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File archivo = elegidor.showSaveDialog(escenario);
        if (archivo == null) {
            return;
        }
        List<String> lineas = new ArrayList<>();
        lineas.add(unirCsv(ultimoInforme.columnas()));
        for (List<String> fila : ultimoInforme.filas()) {
            lineas.add(unirCsv(fila));
        }
        try {
            Files.writeString(archivo.toPath(), String.join("\n", lineas) + "\n",
                    StandardCharsets.UTF_8);
            estadoInforme.setText("Exportado: " + archivo.getName());
        } catch (Exception e) {
            estadoInforme.setText("No se pudo exportar: " + e.getMessage());
        }
    }

    private static String unirCsv(List<String> campos) {
        StringBuilder linea = new StringBuilder();
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                linea.append(',');
            }
            String valor = campos.get(i) == null ? "" : campos.get(i);
            if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
                linea.append('"').append(valor.replace("\"", "\"\"")).append('"');
            } else {
                linea.append(valor);
            }
        }
        return linea.toString();
    }

    private void refrescar() {
        Fachada f = fachada;
        if (f == null) {
            return;
        }
        if (estado == null || tabla == null || conectados == null) {
            return;
        }
        var e = f.estadoServidor();
        estado.setText(String.join("\n", List.of(
                "Estado: " + (e.activo() ? "Activo" : "Detenido"),
                "Puerto: " + e.puerto(),
                "Pool de red: " + e.trabajadoresOcupados() + "/"
                        + e.trabajadoresTotal() + " ocupados",
                "Trabajadores disponibles: " + e.trabajadoresDisponibles())));
        if (indicadorCabecera != null) {
            indicadorCabecera.setText(e.activo() ? "●  EN LÍNEA" : "○  DETENIDO");
            indicadorCabecera.getStyleClass().removeAll("status-online", "status-offline", "status-checking");
            indicadorCabecera.getStyleClass().add(e.activo() ? "status-online" : "status-offline");
        }
        if (ultimaActualizacion != null) {
            ultimaActualizacion.setText("Actualizado a las "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + "  ·  refresco automático cada 5 s");
        }
        if (kpiEstado != null) {
            kpiEstado.setText(e.activo() ? "En línea · puerto " + e.puerto() : "Detenido");
            kpiConexiones.setText(e.usuariosConectados() + " usuarios · "
                    + e.sesionesActivas() + "/" + e.maxConexiones() + " sesiones");
            kpiSistema.setText(e.mensajesProcesados() + " procesados · "
                    + e.mensajesEnCola() + " en cola");
        }
        if (barraRed != null && estadoPool != null) {
            double total = Math.max(1, e.trabajadoresTotal());
            barraRed.setProgress(e.trabajadoresOcupados() / total);
            barraFiltros.setProgress(Math.min(1.0, e.mensajesEnCola() / 20.0));
            resumenPoolRed.setText(e.trabajadoresOcupados() + " ocupados · "
                    + e.trabajadoresDisponibles() + " libres · " + e.trabajadoresTotal()
                    + " totales");
            resumenPoolCola.setText(e.mensajesEnCola() + " en cola · "
                    + e.mensajesProcesados() + " procesados");
            estadoPool.setText("Conexiones: " + e.usuariosConectados() + " usuarios · "
                    + e.sesionesActivas() + "/" + e.maxConexiones() + " sesiones activas.");
        }
        List<UsuarioResumen> usuarios = f.usuariosRegistrados();
        tabla.getItems().setAll(usuarios);
        Map<String, String> nombres = new HashMap<>();
        for (UsuarioResumen u : usuarios) {
            String nombre = ((u.nombres() == null ? "" : u.nombres()) + " "
                    + (u.apellidos() == null ? "" : u.apellidos())).trim();
            nombres.put(u.codigo(), nombre.isEmpty() ? u.codigo() : nombre + " [" + u.codigo() + "]");
        }
        nombresDirectorio = Map.copyOf(nombres);
        if (filtroCodigoMensaje != null) {
            String escrito = filtroCodigoMensaje.getEditor().getText();
            if ((escrito == null || escrito.isBlank()) && filtroCodigoMensaje.getValue() != null) {
                escrito = filtroCodigoMensaje.getValue();
            }
            List<String> codigos = new ArrayList<>(usuarios.stream()
                    .map(UsuarioResumen::codigo).toList());
            if (usuarios.stream().noneMatch(u -> "SERVIDOR".equalsIgnoreCase(u.codigo()))) {
                codigos.add("SERVIDOR");
            }
            filtroCodigoMensaje.getItems().setAll(codigos);
            filtroCodigoMensaje.getEditor().setText(escrito == null ? "" : escrito);
        }
        List<String> activos = f.usuariosConectados();
        // Sesión B: lista y combo muestran "Nombre Apellidos [código]".
        List<String> visibles = visiblesDe(activos, nombresDirectorio);
        codigoPorVisible = Map.copyOf(indiceCodigoPorVisible(activos, nombresDirectorio));
        String anteriorVisible = objetivoDesconexion == null ? null
                : objetivoDesconexion.getValue();
        String codigoAnterior = anteriorVisible == null ? null
                : codigoPorVisible.get(anteriorVisible);
        if (codigoAnterior == null && anteriorVisible != null
                && activos.contains(anteriorVisible)) {
            codigoAnterior = anteriorVisible;
        }
        conectados.getItems().setAll(visibles);
        String visibleAnterior = codigoAnterior == null ? null
                : resolverVisible(codigoAnterior, nombresDirectorio);
        if (visibleAnterior != null && visibles.contains(visibleAnterior)) {
            conectados.getSelectionModel().select(visibleAnterior);
        }
        if (objetivoDesconexion != null) {
            objetivoDesconexion.getItems().setAll(visibles);
            if (visibles.isEmpty()) {
                objetivoDesconexion.setValue(null);
                objetivoDesconexion.setPromptText("No hay usuarios activos");
                botonDesconexion.setDisable(true);
                estadoDesconexion.setText("No hay usuarios activos");
            } else {
                objetivoDesconexion.setPromptText("Selecciona un usuario activo");
                String restaurar = visibleAnterior != null && visibles.contains(visibleAnterior)
                        ? visibleAnterior : visibles.get(0);
                objetivoDesconexion.setValue(restaurar);
                conectados.getSelectionModel().select(restaurar);
                botonDesconexion.setDisable(false);
                estadoDesconexion.setText(activos.size() + " usuario(s) activo(s).");
            }
        }
        Map<String, List<String>> ips = new HashMap<>();
        List<IdSesion> sesiones = f.sesionesActivas();
        for (IdSesion sesion : sesiones) {
            if (sesion.codigo() == null || sesion.direccionIp() == null
                    || sesion.direccionIp().isBlank()) continue;
            String codigo = sesion.codigo().trim().toUpperCase(java.util.Locale.ROOT);
            List<String> direcciones = ips.computeIfAbsent(codigo, ignorado -> new ArrayList<>());
            if (!direcciones.contains(sesion.direccionIp())) direcciones.add(sesion.direccionIp());
        }
        Map<String, String> ipResumen = new HashMap<>();
        ips.forEach((codigo, direcciones) -> ipResumen.put(codigo,
                String.join(", ", direcciones)));
        ipsActivasPorCodigo = Map.copyOf(ipResumen);
        // Las columnas usan valores calculados, no propiedades observables: hay
        // que repintar las celdas y el detalle tras cada lectura de sesiones.
        if (tablaMensajes != null) tablaMensajes.refresh();
        if (detalleMensajes != null) detalleMensajes.actualizarIpDestinatario();
        if (detalleInformes != null) detalleInformes.actualizarIpDestinatario();
        if (detalleArchivosUsuario != null) detalleArchivosUsuario.actualizarIpDestinatario();
        if (detalleHistorialUsuario != null) detalleHistorialUsuario.actualizarIpDestinatario();
        if (tabDetalleUsuario != null && tabs.getSelectionModel().getSelectedItem() == tabDetalleUsuario) {
            actualizarFichaUsuario(false);
        }
    }

    private void refrescarSeguro() {
        try {
            refrescar();
            // Sesión B: Informes es manual (botón Actualizar) para no mover
            // scroll/selección/detalle con el auto-refresh de 5 s.
        } catch (RuntimeException e) {
            if (indicadorCabecera != null) {
                indicadorCabecera.setText("ESTADO NO DISPONIBLE");
                indicadorCabecera.getStyleClass().removeAll("status-online", "status-offline", "status-checking");
                indicadorCabecera.getStyleClass().add("status-offline");
            }
            if (ultimaActualizacion != null) {
                ultimaActualizacion.setText("No se pudo actualizar. Revisa Eventos/Logs y vuelve a intentarlo.");
            }
        }
    }

    static Button boton(String texto, Runnable accion) {
        Button boton = new Button(texto);
        boton.setOnAction(evento -> accion.run());
        return boton;
    }

    static void ejecutarSeguro(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            // la vista no tumba: el error ya quedo en logs
        }
    }
}
