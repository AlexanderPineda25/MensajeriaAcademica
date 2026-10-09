package universidad.mensajeria.cliente.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArquitecturaClienteJavaTest {

    private static final JavaClasses CLIENTE = new ClassFileImporter()
            .importPackages("universidad.mensajeria.cliente");

    @Test
    void clientInterfacesNoDependeDeProtocolosNiUi() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("InterfazAutenticacion")
                .or().haveSimpleName("InterfazCacheUsuarios")
                .or().haveSimpleName("InterfazHistorialLocal")
                .or().haveSimpleName("InterfazMapeadorDatosServidor")
                .or().haveSimpleName("InterfazInicioSesion")
                .or().haveSimpleName("InterfazChatCliente")
                .or().haveSimpleName("InterfazArchivosConversacion")
                .or().haveSimpleName("InterfazFachadaCliente")
                .or().haveSimpleName("InterfazConexionRed")
                .or().haveSimpleName("ReceptorMensajes")
                .or().haveSimpleName("ResultadoOperacion")
                .or().haveSimpleName("EstadoConexion")
                .or().haveSimpleName("UsuarioLocal")
                .or().haveSimpleName("MensajeLocal")
                .or().haveSimpleName("RecienteChat")
                .or().haveSimpleName("PaginaHistorialRemota")
                .or().haveSimpleName("PendienteEnvio")
                .or().haveSimpleName("EventoCliente")
                .or().haveSimpleName("RespuestaRed")
                .or().haveSimpleName("TipoMensajeCliente")
                .or().haveSimpleName("EstadoMensaje")
                .or().haveSimpleName("ExcepcionCliente")
                .or().haveSimpleName("ExcepcionHistorial")
                .or().haveSimpleName("ExcepcionPersistencia")
                .or().haveSimpleName("ExcepcionValidacion")
                .or().haveSimpleName("ExcepcionSesion")
                .or().haveSimpleName("ExcepcionOperacionNoSoportada")
                .or().haveSimpleName("InterfazCoordinadorMensajeria")
                .or().haveSimpleName("InterfazCoordinadorHistorial")
                .or().haveSimpleName("InterfazGestorReconexion")
                .should().dependOnClassesThat()
                .resideInAnyPackage("universidad.mensajeria.common..",
                        "javafx..",
                        "com.google.gson..",
                        "java.sql..");
        rule.check(CLIENTE);
    }

    @Test
    void soloAdaptadoresDeProtocoloDependenDeCommonProtocol() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("FachadaCliente")
                .or().haveSimpleName("InterfazConexionRed")
                .or().haveSimpleName("ReceptorMensajes")
                .or().haveSimpleName("InterfazAutenticacion")
                .or().haveSimpleName("Autenticacion")
                .or().haveSimpleName("CoordinadorMensajeriaCliente")
                .or().haveSimpleName("CoordinadorHistorialCliente")
                .or().haveSimpleName("InterfazFachadaCliente")
                .or().haveSimpleName("InterfazInicioSesion")
                .or().haveSimpleName("InterfazChatCliente")
                .or().haveSimpleName("InterfazArchivosConversacion")
                .or().haveSimpleName("InterfazEventosCliente")
                .or().haveSimpleName("InterfazCoordinadorMensajeria")
                .or().haveSimpleName("InterfazCoordinadorHistorial")
                .or().haveSimpleName("InterfazGestorReconexion")
                .or().haveSimpleName("RespuestaRed")
                .or().haveSimpleName("TipoMensajeCliente")
                .or().haveSimpleName("InterfazReintentadorPendientes")
                .or().haveSimpleName("InterfazSincronizadorDirectorio")
                .or().haveSimpleName("InterfazLectorHistorialRemoto")
                .or().haveSimpleName("ReceptorEntranteCliente")
                .or().haveSimpleName("ReintentadorPendientesCliente")
                .or().haveSimpleName("SincronizadorDirectorioCliente")
                .or().haveSimpleName("ConstructorRecientes")
                .or().haveSimpleName("LectorHistorialRemotoCliente")
                .or().haveSimpleName("PublicadorEventosCliente")
                .should().dependOnClassesThat()
                .resideInAnyPackage("universidad.mensajeria.common.tipos..",
                        "universidad.mensajeria.common.codec..");
        rule.check(CLIENTE);
    }

    @Test
    void presentacionNoDependeDePersistenciaNiAdaptadoresDeRed() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..cliente.presentacion..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..persistencia..", "..componentes.conexion..",
                        "..componentes.protocolo..");
        rule.check(CLIENTE);
    }

    @Test
    void controladoresNoDependenDeAdaptadoresConcretos() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("ChatController")
                .or().haveSimpleName("LoginController")
                .or().haveSimpleName("GestorAdjuntosChat")
                .or().haveSimpleName("BusquedaHistorialChat")
                .or().haveSimpleName("PaginadorHistorialChat")
                .or().haveSimpleName("GestorSalidaSesionChat")
                .or().haveSimpleName("CargadorAdjuntoEntradaChat")
                .or().haveSimpleName("RefrescadorContactosChat")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..cliente.persistencia..",
                        "..cliente.componentes.conexion..",
                        "..cliente.componentes.protocolo..");
        rule.check(CLIENTE);
    }

    @Test
    void controladoresNoDependenDeLaFachadaConcreta() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("ChatController")
                .or().haveSimpleName("LoginController")
                .or().haveSimpleName("GestorAdjuntosChat")
                .or().haveSimpleName("BusquedaHistorialChat")
                .or().haveSimpleName("PaginadorHistorialChat")
                .or().haveSimpleName("GestorSalidaSesionChat")
                .or().haveSimpleName("CargadorAdjuntoEntradaChat")
                .or().haveSimpleName("RefrescadorContactosChat")
                .should().dependOnClassesThat()
                .haveSimpleName("FachadaCliente");
        rule.check(CLIENTE);
    }

    @Test
    void viewModelNoDependeDeRedPersistenciaNiProtocolo() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("ChatViewModel")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..cliente.persistencia..",
                        "..cliente.componentes..",
                        "universidad.mensajeria.common..");
        rule.check(CLIENTE);
    }
}
