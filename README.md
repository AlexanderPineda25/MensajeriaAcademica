# Mensajería académica

Sistema de escritorio cliente/servidor para mensajería académica. Un servidor Java recibe conexiones TCP y procesa mensajes JSON; tres clientes independientes usan JavaFX, PySide6 y WPF. Solo el servidor accede a MySQL; los clientes mantienen su historial local en H2 o SQLite.

> Estado documental revisado el **2026-10-06**. Para el estado operativo y las verificaciones fechadas, consulta [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md). Este README es la entrada rápida, no reemplaza los registros históricos de auditoría ni los requerimientos.

## Componentes

| Componente | Proyecto | Guía |
|---|---|---|
| Servidor Java 21+ / Spring Boot | `universidad-mensajeria/server/servidor-app` | [Operación del servidor](universidad-mensajeria/server/servidor-app/README.md) |
| Cliente JavaFX | `universidad-mensajeria/client/client-app` | [Cliente Java](universidad-mensajeria/client/README.md) |
| Cliente Python / PySide6 | `universidad-mensajeria/client-python` | [Cliente Python](universidad-mensajeria/client-python/README.md) |
| Cliente .NET / WPF | `universidad-mensajeria/client-dotnet` | [Cliente WPF](universidad-mensajeria/client-dotnet/README.md) |

El contrato de red es TCP con una cabecera big-endian de 4 bytes y un cuerpo JSON UTF-8. Los clientes remotos deben conectarse a la IP o DNS del servidor, no a `localhost`. Las distribuciones se generan por separado desde el monorepo.

## Preparación y desarrollo

- Para preparar Windows sin permisos de administrador, sigue [`INSTALACION.md`](INSTALACION.md) y [`AUTOMATIZACION_ENTORNO.md`](AUTOMATIZACION_ENTORNO.md).
- Para compilar Java, inicia MySQL de desarrollo y ejecuta los comandos de [universidad-mensajeria/README.md](universidad-mensajeria/README.md).
- Para pruebas de aceptación entre los tres clientes, usa [`GUIA_ACEPTACION.md`](GUIA_ACEPTACION.md).
- Para desplegar y respaldar servicios, consulta [`DESPLIEGUE.md`](DESPLIEGUE.md).

## Documentación del proyecto

| Documento | Propósito |
|---|---|
| [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md) | Estado vigente, pendientes conocidos y evidencia con fecha. |
| [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) | Comportamiento acordado del sistema. |
| [`PLAN.md`](PLAN.md) | Arquitectura y decisiones técnicas. |
| [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) | Auditoría histórica con ubicación de hallazgos; consultar el roadmap para saber si siguen abiertos. |
| [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) | Plan e historial de mejoras de las interfaces. |
| [`PLAN_MEJORAS.md`](PLAN_MEJORAS.md) | Bitácora histórica de cambios y verificaciones. |
| [`DIAGRAMAS/README.md`](DIAGRAMAS/README.md) | Índice de fuentes/renderes de diagramas y conservación de los archivos legados. |
| [`REVISION_DOCUMENTAL.md`](REVISION_DOCUMENTAL.md) | Revisión de vigencia y propuestas de fusión o retiro (sin borrar archivos). |

## Estado resumido

- `SEC-05`/`SEC-06` — autorización de comandos: cerrados y cubiertos por pruebas; el detalle del hallazgo se conserva como historial en la auditoría.
- Vista administrativa JavaFX: incluye pestaña reutilizable de ficha de usuario, métricas de mensajes, sesiones/IP disponibles, detalle de texto/imagen, y pestañas Estado, Conectados, Usuarios, Mensajes, Informes, Logs, Pool y Difundir. La consola JLine conserva los comandos e incorpora jerarquía visual.
- UIX-15 — avisos/errores recuperables en el cliente WPF: banner inline; continúan tres confirmaciones modales de salida.
- La automatización de preparación de entorno instala/verifica herramientas del usuario sin permisos de administrador, documentada en [`AUTOMATIZACION_ENTORNO.md`](AUTOMATIZACION_ENTORNO.md).
- Los reportes de pruebas no son uniformes: al 2026-10-06 el último resultado anotado de WPF es 71/71; los reportes Surefire existentes registran dos fallos de umbrales de rendimiento en `CargaMixtaTest`. Las fechas, límites y otras suites se detallan en el roadmap; no se afirma que esta revisión haya ejecutado las suites.

## Advertencia JavaFX `sun.misc.Unsafe`

La distribución actual del servidor usa JavaFX Graphics **21.0.6**. En JDK recientes el renderizador `com.sun.marlin.OffHeapArray` puede emitir una advertencia al llamar `Unsafe.allocateMemory`; el stack trace señala el JAR de JavaFX, no una llamada de la lógica de mensajería. La guía del servidor explica el alcance y las alternativas de runtime. El aviso no significa por sí solo que el servidor haya fallado.

Consulta [`REVISION_DOCUMENTAL.md`](REVISION_DOCUMENTAL.md) para ver qué documentos conviene mantener separados y qué archivos se pueden fusionar o retirar después de revisar su contenido. No se eliminó documentación en esta actualización.
