# Contrato compartido entre los tres clientes

Fuente unica de lo que debe ser **igual** en `client/` (JavaFX), `client-python/`
(PySide6) y `client-dotnet/` (WPF), aunque cada uno lo escriba en su lenguaje.

## Por que existe

El mismo color vivia en **7 sitios**: `chat.css`, `chat-claro.css`, `login.css`,
`login-claro.css`, `TemaOscuro.xaml`, `TemaClaro.xaml` y un diccionario en
`tema.py`. Esa duplicacion ya habia producido dos derivas reales:

- `chat-claro.css` **no tenia** `.message-out .message-text`, `.message-out
  .image-caption` ni `.message-out .message-time`, que si existen en `chat.css`.
- La paleta **clara no coincidia en ningun cliente** (burbuja propia `#E4F3EC` /
  `#E1F5FE` / `#D9FDD3`, acento `#2456A6` / `#147D73` / defecto de WPF-UI).
  Es `VIS-01`, abierto en `ESTADO_ROADMAP.md` §7.

## Que hay aqui

| Fichero | Que es |
|---|---|
| `tokens-diseno.json` | **La paleta.** Se edita aqui y nowhere mas |
| `generar_tokens.py` | La genera para los 3 clientes y verifica contraste |
| `plantillas/chat.css.in` | Plantilla del chat, comun a los dos temas |
| `plantillas/login.css.in` | Plantilla del login, comun a los dos temas |
| `plantillas/tema.xaml.in` | Plantilla de los ResourceDictionary de WPF |
| `plantillas/tema_tokens.py.in` | Mapa token canonico -> nombre que ya usaba Python |
| `estado-mensaje.json` | **La maquina de estados** del mensaje (parte grave de `VIS-02`) |
| `verificar_estado_mensaje.py` | La contrasta con lo escrito en los 3 clientes |
| `test_estado_mensaje.py` | 7 tests que fallan si divergen |
| `puertos.json` | **Los 23 puertos**, con su equivalente en Python y C# |
| `verificar_puertos.py` | Comprueba que el codigo Java cumple el contrato |
| `test_puertos.py` | 12 tests que fallan si divergen |
| `verificar.py` | Los 8 pasos, exit 0 o 1 |

## La maquina de estados del mensaje

`PENDIENTE -> ENVIADO -> ENTREGADO -> LEIDO`, mas `ERROR` terminal. La precedencia
estaba escrita a mano **5 veces** en 3 lenguajes con dos mecanismos distintos:

```
Java   : CASE de enteros en el WHERE de MARCAR_ESTADO, y DELETE+INSERT al guardar
Python : ON CONFLICT DO UPDATE (x2) + marcar_estado
.NET   : ON CONFLICT DO UPDATE (x2) + MarcarEstado
```

Anadir un estado en una sola de esas 5 copias hacia que ese cliente discrepara del
resto **sin que ninguna prueba fallara**: solo se notaba cuando un mensaje cambiaba
de estado de forma distinta segun desde que cliente se mirara. Por eso
`verificar_estado_mensaje.py` extrae las reglas reales de cada cliente y las
compara con el JSON en vez de fiarse de lo que el JSON dice.

Al montarlo encontro un bug real: **`ERROR` no se pintaba en Python ni en WPF.** El
estado si se escribia en la base de los tres, y JavaFX si lo mostraba como
`! No enviado`, pero en los otros dos caia en el caso por defecto del `switch` y la
burbuja se quedaba sin indicador. El mismo rechazo de servidor se veia en un
cliente y no en los otros dos.

## Uso

```powershell
# Cambiar un color: se edita tokens-diseno.json y se regenera.
.\universidad-mensajeria\client-python\.venv\Scripts\python.exe contrato\generar_tokens.py

# Solo verificar (para CI): falla si algo esta desfasado o si un contraste baja de 4.5:1.
.\universidad-mensajeria\client-python\.venv\Scripts\python.exe contrato\generar_tokens.py --check

# La maquina de estados: no se genera, se contrasta con lo escrito en cada cliente.
.\universidad-mensajeria\client-python\.venv\Scripts\python.exe contrato\verificar_estado_mensaje.py

# Los puertos: el JSON es la especificacion, esto la contrasta con el codigo Java.
.\universidad-mensajeria\client-python\.venv\Scripts\python.exe contrato\verificar_puertos.py

# Todo: tokens + maquina de estados + puertos + las 3 suites + el build de WPF.
.\universidad-mensajeria\client-python\.venv\Scripts\python.exe contrato\verificar.py
```

Genera:

```
universidad-mensajeria/client/client-app/src/main/resources/estilos/chat.css
universidad-mensajeria/client/client-app/src/main/resources/estilos/chat-claro.css
universidad-mensajeria/client/client-app/src/main/resources/estilos/login.css
universidad-mensajeria/client/client-app/src/main/resources/estilos/login-claro.css
universidad-mensajeria/client-dotnet/Themes/TemaOscuro.xaml
universidad-mensajeria/client-dotnet/Themes/TemaClaro.xaml
universidad-mensajeria/client-python/ui/tema_tokens.py
```

## Reglas

1. **Una plantilla por pantalla, no por tema.** De ahi que `chat-claro.css` ya no
   pueda quedarse corto: sale de la misma plantilla que `chat.css`.
2. **El generador falla, no avisa.** Si un contraste baja de 4.5:1, si una
   plantilla usa un token inexistente o si un `.xaml` lleva un color que WPF no
   entiende, sale con codigo 1.
3. **El cliente Python no lleva la paleta.** `ui/tema.py` deriva de
   `tema_tokens.py` y traduce a los nombres que `chat_window.py` ya usaba, para
   que ese fichero no cambie ni una linea. Hay un test que falla si aparece un
   hex literal en `tema.py`.
4. **Los `rgba()` se aplanan para WPF.** JavaFX necesita la cita translucida
   sobre la foto del chat; WPF no compone `rgba()`, asi que el generador la
   calcula sobre el fondo del tema.
5. **El claro viene de JavaFX**, con las correcciones de contraste que Python ya
   habia hecho y Java no (UIX-17). `contrastes` en el JSON dice cuales se miden y
   con que minimo.
6. **La maquina de estados se verifica, no se genera.** Habria que hacer codegen
   del SQL en 3 lenguajes y 2 motores; verificar lo que hay escrito fija el mismo
   contrato sin generar codigo que luego hay que depurar. Los ticks de la burbuja
   si son literales y se comprueban tal cual.

## Los puertos del cliente

`puertos.json` es la especificacion normativa de la superficie del cliente, y
`verificar_puertos.py` comprueba que el codigo Java la cumple. Sin el verificador
el JSON seria documentacion que nadie relee: el dia que se anada un metodo al
puerto y no al contrato, nadie se entera.

**No son 12 interfaces: son 23 puertos.** El recuento inicial solo miraba
`client-interfaces`; hay 9 mas por `client-app` y `presentacion`:

```
Capa 1 · Adaptadores   InterfazConexionRed, ReceptorMensajes,
                       InterfazMapeadorDatosServidor, InterfazAlmacenLocal
Capa 2 · Datos         InterfazHistorialLocal, InterfazCacheUsuarios,
                       InterfazAutenticacion
Capa 3 · Casos de uso  InterfazFachadaCliente, InterfazChatCliente,
                       InterfazInicioSesion, InterfazArchivosConversacion,
                       InterfazEventosCliente, InterfazCoordinadorMensajeria,
                       InterfazCoordinadorHistorial, InterfazGestorReconexion,
                       InterfazSincronizadorDirectorio, InterfazReintentadorPendientes,
                       InterfazLectorHistorialRemoto
Capa 3 · Presentacion  InterfazChatViewModel, InterfazNavegacionCliente,
                       InterfazGestorAdjuntos, InterfazFabricaAdjuntos, FlujoConversacion
```

177 metodos, 8 records, 2 enums, 6 excepciones. Cada puerto lleva su equivalente
en Python y C#, marcado como `a crear` o `a tipar` segun toque la Fase 1.4 o la 2.

Lo que el verificador comprueba: firmas exactas (en orden, como multiconjunto, para
no perder sobrecargas), que no haya metodos sin declarar, los 28 valores de
`TipoMensajeCliente`, los textos de `EstadoConexion`, las 3 familias de
excepcion, que las operaciones opcionales sigan siendo `default`, que
`client-interfaces/pom.xml` no tenga dependencias y que la tabla de backoff no
haya cambiado.

> Cuando se monto, las tres primeras versiones del verificador dieron falsos
> negativos: contaban el `guardar(u)` de dentro de un `default` como metodo,
> trataban dos sobrecargas distintas como la misma al ordenar los parametros, y
> confundian `java.util.List<X>` con `List<X>`. La mutacion (borrar un metodo del
> JSON y comprobar que falla) es la que lo dejo trustworthy.

## Relacion con el plan

`PLAN_PARIDAD_CLIENTES.md`, fases **0.2** (tokens, cierra `VIS-01`), **0.3**
(maquina de estados, parte grave de `VIS-02`) y **0.4** (puertos). Con esto la
Fase 0 esta completa y la Fase 2 puede empezar: `puertos.json` es la lista de
trabajo con la equivalencia exacta en Python y C#.

Pendiente de centralizar aqui: los literales de ventana y dialogo que cataloga
`DIAGRAMAS/textos-ui.md` (~68 de los 73; los 5 del tick ya estan).
$tokens-diseno.json y el codigo no se versionan artefactos: anade contrato/__pycache__/ y
cliente-python/uvicorn cache a .gitignore si no esta ya.
