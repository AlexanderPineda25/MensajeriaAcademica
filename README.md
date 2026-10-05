# Mensajería académica

Aplicación de escritorio cliente/servidor sobre TCP y JSON. El servidor Java procesa mensajes y
almacena datos en MySQL; los clientes JavaFX, PySide6 y WPF mantienen historial/cache local H2 o
SQLite. Los clientes no se conectan a la base del servidor.

## Documentación

| Documento | Qué contiene |
|---|---|
| [REQ.md](REQ.md) | Brief original del proyecto. No editar. |
| [**INSTALACION.md**](INSTALACION.md) | **Preparar un computador desde cero:** qué instalar, cómo actualizar Java, y cómo verificarlo. |
| [REQUERIMIENTOS.md](REQUERIMIENTOS.md) | Contrato funcional y no funcional (`RF-*`, `RNF-*`, `PD-*`) y control de cambios. |
| [PLAN.md](PLAN.md) | Arquitectura y decisiones de diseño. |
| [PLAN_PATRONES_UI.md](PLAN_PATRONES_UI.md) | **Plan de patrones de diseño y mejora de UI de los 3 clientes**, por fases. |
| [ANALISIS_TECNICO.md](ANALISIS_TECNICO.md) | **Auditoría 2026-10-05** con la evidencia (`archivo:línea`) de cada hallazgo. |
| [ESTADO_ROADMAP.md](ESTADO_ROADMAP.md) | **Tablero: qué falta.** Este archivo manda hacia adelante. |
| [PLAN_MEJORAS.md](PLAN_MEJORAS.md) | Diario de lo ya ejecutado. |
| [GUIA_ACEPTACION.md](GUIA_ACEPTACION.md) | Pruebas automatizadas y matriz manual entre procesos. |
| [DESPLIEGUE.md](DESPLIEGUE.md) | Instalar y operar cada proceso. |
| [DIAGRAMAS/README.md](DIAGRAMAS/README.md) | Catálogo de diagramas y plan de consolidación. |

## Estado actual (2026-10-05)

✅ El servidor y los 3 clientes funcionan, las suites están en verde, la distribución y el
rendimiento están medidos y documentados.

🔴 **Antes de producción hay que cerrar un hallazgo de seguridad:** `LISTAR_USUARIOS` responde el
directorio completo de la comunidad académica **sin código ni contraseña**. La cadena verificada
está en [ANALISIS_TECNICO.md §1](ANALISIS_TECNICO.md#hallazgo-seguridad)
y la tarea es [`SEC-05`](ESTADO_ROADMAP.md#seguridad-bloqueante).

🟡 5 smokes manuales siguen pendientes de verificación humana, entre ellos la matriz de 6
direcciones entre los 3 clientes.

La auditoría del 2026-10-05 inventarió además 38 items de código muerto, 6 bugs de interfaz que son
funciones rotas y 16 diagramas que faltaban (ya escritos como fuente, **sin render**: falta
`mmdc`). Todo planificado, **nada aplicado**.

## Llevar a otro computador

**Empieza por [INSTALACION.md](INSTALACION.md)**: qué instalar y cómo verificarlo. Después, lee
[DESPLIEGUE.md](DESPLIEGUE.md). Cada proceso tiene su propia guía dentro de su carpeta:

| Componente | Guía |
|---|---|
| Servidor Java + MySQL | [server/servidor-app/README.md](universidad-mensajeria/server/servidor-app/README.md) |
| Cliente JavaFX | [client/README.md](universidad-mensajeria/client/README.md) |
| Cliente Python | [client-python/README.md](universidad-mensajeria/client-python/README.md) |
| Cliente .NET/WPF | [client-dotnet/README.md](universidad-mensajeria/client-dotnet/README.md) |

En la red, los clientes conectan a `IP_SERVIDOR:5000/TCP`. Solo el servidor necesita alcanzar
MySQL. `localhost` no sirve como dirección de servidor en una aplicación cliente ubicada en otro
PC.

## Desarrollo y validación

El directorio `universidad-mensajeria/` es el monorepo de construcción, no la carpeta que se instala
en cada equipo. Requiere JDK 21 o superior (probado en 25), Maven 3.9+, Docker Compose para MySQL de
prueba, Python según `client-python/requirements.txt` y .NET SDK para WPF.

```powershell
cd universidad-mensajeria
docker compose up -d mysql
mvn -pl server/servidor-app,client/client-app -am test
```

La [guía de aceptación](GUIA_ACEPTACION.md) contiene las pruebas Python/.NET y la matriz manual de
las seis direcciones.

Para regenerar los diagramas hace falta `npm i -g @mermaid-js/mermaid-cli` y luego
`DIAGRAMAS\render.ps1`. Sin eso, un `.mmd` nuevo se queda sin PNG y por la regla de
[DIAGRAMAS/README.md §1](DIAGRAMAS/README.md#regenerar) **no cuenta como diagramo terminado**.

## Referencias de contrato

- [PROTOCOLO.md](universidad-mensajeria/common-protocol/PROTOCOLO.md): framing y mensajes TCP/JSON.
- [HISTORIAL_LOCAL.md](universidad-mensajeria/common-protocol/HISTORIAL_LOCAL.md): esquema y
  comportamiento común de persistencia local.
