# Revisión de documentación y archivos candidatos a consolidar

Revisión realizada el **2026-10-06**. Resume qué documentación describe el estado vigente, qué
documentos deben conservar funciones distintas y qué archivos se podrían fusionar o retirar en
una limpieza futura. **Esta revisión no elimina ni mueve archivos.** Las recomendaciones sobre
diagramas son preliminares hasta comparar su contenido y renders.

## Fuente de verdad por tema

| Tema | Documento que manda | Cómo usar los relacionados |
|---|---|---|
| Estado actual y trabajo pendiente | [`ESTADO_ROADMAP.md`](ESTADO_ROADMAP.md) | Consultar aquí el estado de cada ID; auditorías antiguas no representan por sí solas el estado actual. |
| Requisitos de producto | [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) | `REQ.md` se conserva como documento fuente histórico. |
| Arquitectura y decisiones | [`PLAN.md`](PLAN.md) | Mantenerlo separado de los requisitos: explica diseño y decisiones. |
| Evidencia de auditoría | [`ANALISIS_TECNICO.md`](ANALISIS_TECNICO.md) | Es una instantánea con referencias al código. Los hallazgos cerrados se preservan como historial. |
| Implementación e hitos pasados | [`PLAN_MEJORAS.md`](PLAN_MEJORAS.md) | Bitácora cronológica; no usarla como tablero de pendientes. |
| Diseño y tareas de interfaz | [`PLAN_PATRONES_UI.md`](PLAN_PATRONES_UI.md) | Conserva decisiones, lotes y criterios técnicos de refactor. El estado vigente se refleja en el roadmap. |
| Aceptación manual | [`GUIA_ACEPTACION.md`](GUIA_ACEPTACION.md) | Pasos reproducibles, capturas y resultados de smoke; el roadmap conserva sus IDs/estado. |
| Preparación del equipo | [`INSTALACION.md`](INSTALACION.md) y [`AUTOMATIZACION_ENTORNO.md`](AUTOMATIZACION_ENTORNO.md) | La primera orienta; la segunda documenta el script, sus verificaciones y límites sin permisos de administrador. |
| Operación y publicación | [`DESPLIEGUE.md`](DESPLIEGUE.md) | Servidor, clientes, copias de seguridad y operación. No fusionar con la preparación de una máquina de desarrollo. |
| Diagramas y textos literales | [`DIAGRAMAS/README.md`](DIAGRAMAS/README.md) y [`DIAGRAMAS/textos-ui.md`](DIAGRAMAS/textos-ui.md) | Índice de fuentes/renderes y catálogo de textos visibles, respectivamente. |

## Qué conservar separado

- `README.md` y `AGENTS.md`: entrada general para una persona usuaria/desarrolladora frente a
  orientación rápida para agentes y colaboradores.
- `REQUERIMIENTOS.md`, `PLAN.md` y `ESTADO_ROADMAP.md`: contrato de comportamiento, explicación de
  arquitectura y tablero de estado son propósitos diferentes.
- `ANALISIS_TECNICO.md` y `PLAN_MEJORAS.md`: evidencia de auditoría frente a cronología de cambios.
  El roadmap ya sirve de puente entre ambos.
- `INSTALACION.md`, `AUTOMATIZACION_ENTORNO.md` y `DESPLIEGUE.md`: instalación inicial, preparación
  automatizada de herramientas de usuario y operación/despliegue.
- Los README de cada componente y `universidad-mensajeria/README.md`: instrucciones locales al
  componente frente a build del monorepo.
- `DIAGRAMAS/textos-ui.md`: catálogo de literales exactos; no sustituirlo por mockups ni por
  criterios narrativos de requisitos.

## Fusiones documentales posibles, si luego se desea reducir navegación

No hace falta fusionar estos archivos para que la documentación sea coherente. Si se decide
reducirla más adelante:

1. Mantener `ESTADO_ROADMAP.md` como tablero breve y dejar en `GUIA_ACEPTACION.md` todos los pasos
   y resultados de pruebas manuales; el roadmap solo enlazaría el procedimiento por ID.
2. Mantener `PLAN_PATRONES_UI.md` como decisiones técnicas y mover a `PLAN_MEJORAS.md` cualquier
   repetición puramente cronológica. Antes de hacerlo, preservar fechas y evidencia de verificación.
3. Mantener `INSTALACION.md` como guía principal y enlazar desde allí a los detalles de
   `AUTOMATIZACION_ENTORNO.md`; no copiar listas de dependencias ni comandos completos en ambos.

`REQ.md` es candidato a quedar identificado explícitamente como fuente original/archivo, pero no
conviene mezclarlo con `REQUERIMIENTOS.md` si se necesita conservar trazabilidad de cómo evolucionó
el alcance.

## Diagramas que conviene revisar primero

`DIAGRAMAS/` es el catálogo canónico actual. El inventario físico observado es de 45 fuentes `.mmd`
con PNG/SVG, y 15 fuentes `.excalidraw` con PNG. Las carpetas legadas mantienen estos archivos; no
son todos duplicados byte por byte. Para los diagramas, comparar contenido, relaciones, etiquetas,
fecha de la UI y render antes de decidir. Mantener juntas la fuente editable y su render elegido.

| Archivo legado actual | Equivalente o destino de fusión | Recomendación para una revisión futura |
|---|---|---|
| `DIAGRAMAS_ENTREGA/05-clases-servidor.mmd` y `.png` | `DIAGRAMAS/clases/clases-servidor-app.mmd`, `clases-inicio-vistas.mmd`, `clases-fachada-casos.mmd`, `clases-nucleo.mmd`, `clases-persistencia.mmd` | Comparar clases y relaciones exclusivas; retirar el legado si todos sus detalles están cubiertos. |
| `DIAGRAMAS_ENTREGA/06-clases-cliente.mmd` y `.png` | `DIAGRAMAS/clases/clases-client-interfaces.mmd`, `clases-client-app.mmd`, `clases-client-python.mmd`, `clases-client-dotnet.mmd` | Fusionar cualquier comparación entre toolkits que sea única; después retirar el anterior si no aporta relaciones adicionales. |
| `DIAGRAMAS_ENTREGA/07-er-servidor.mmd` y `.png` | `DIAGRAMAS/modelo-relacional/er-modelo-servidor.mmd` | Comparar entidades/atributos; el canónico documenta el modelo lógico y los índices actuales. Candidato a retiro tras cotejo. |
| `DIAGRAMAS_ENTREGA/08-er-local.mmd` y `.png` | `DIAGRAMAS/modelo-relacional/er-modelo-local.mmd` | Comparar columnas y restricciones; candidato a retiro tras cotejo. |
| `DIAGRAMAS_FINALES/clases/01-componentes-servidor.mmd` y `.png` | `DIAGRAMAS/clases/arq-vista-general.mmd`, `clases-servidor-app.mmd`, `clases-inicio-vistas.mmd` | Fusionar cualquier vista o relación exclusiva; luego retirar solo si el nuevo conjunto conserva el contenido necesario. |
| `DIAGRAMAS_FINALES/clases/02-clases-puertos-adaptadores.mmd` y `.png` | Los diagramas de componentes `clases-int-*`, `clases-componentes-*` y `clases-comp-*` de `DIAGRAMAS/clases/` | Candidato a retiro después de cotejar puertos, implementaciones y relaciones. |
| `DIAGRAMAS_FINALES/clases/04-clases-cliente.mmd` y `.png` | `DIAGRAMAS/clases/clases-client-app.mmd`, `clases-client-python.mmd`, `clases-client-dotnet.mmd`, `arq-flujo-datos.mmd` | Conservar hasta decidir dónde documentar las relaciones cruzadas de los tres toolkits; luego fusionar o mantener como vista de conjunto. |
| `DIAGRAMAS_FINALES/clases/09-clases-base.mmd` y `.png` | Los diagramas canónicos por módulo de `DIAGRAMAS/clases/` | Revisar las clases base y contratos; candidato a retiro si no contiene relaciones únicas. |
| `DIAGRAMAS_FINALES/modelo-relacional/er-mysql.mmd` y `.png` | `DIAGRAMAS/modelo-relacional/er-modelo-servidor.mmd` | Candidato a retiro después de cotejar nombres y campos; conservar como histórico si hace falta mostrar una versión específica del dialecto. |
| `DIAGRAMAS_FINALES/modelo-relacional/er-sqlite.mmd` y `.png` | `DIAGRAMAS/modelo-relacional/er-modelo-local.mmd` | Candidato a retiro después de cotejar columnas y restricciones. |
| `DIAGRAMAS_FINALES/mockups/mockup-login.*`, `mockup-chat.*`, `mockup-estados.*` | Archivos homónimos en `DIAGRAMAS/mockups/` | Los nombres coinciden, pero las fuentes y PNG tienen hashes distintos. Comparar ambos visualmente y conservar la versión vigente o el valor histórico que se necesite. |
| `DIAGRAMAS_FINALES/mockups/mockup-servidor-consola.*` y `mockup-servidor-escritorio.*` | Archivos homónimos en `DIAGRAMAS/mockups/` | Conservar hasta actualizar/cotejar con la vista administrativa actual; si se quieren mantener diseños previos, marcarlos como históricos en el índice. |

En las filas de mockups, `.*` representa la fuente `.excalidraw` y el PNG asociado. Las diferencias
de hash confirman que los pares con igual nombre no son copias idénticas; no indican por sí solas
cuál es la versión válida.

## Otros archivos candidatos a limpieza controlada

- Hay copias de `client-dotnet/README.md` dentro de `client-dotnet/bin/Debug`, `bin/Release` y
  `bin/Release/.../win-x64`. Las tres copias observadas tienen el mismo SHA-256 entre sí, pero son
  artefactos de compilación/publicación, no fuentes de documentación. Mantener como canónico el
  README del proyecto y revisar `.gitignore`/salida de `publish` antes de limpiar `bin/`; no editar
  esas copias a mano.
- Las imágenes PNG y fuentes Mermaid/Excalidraw forman pares de distribución/edición. No retirar
  una fuente porque el PNG se vea bien ni un render si se usa en informes; primero identificar
  referencias y quién consume cada formato.
- No se recomienda fusionar los README de los tres clientes: sus toolkits, ejecución y persistencia
  local difieren.

## Criterio antes de cualquier retiro

1. Confirmar que no haya referencias desde scripts, enlaces, presentaciones ni entregables.
2. Comparar semántica de fuentes y render, no solo nombres o hashes.
3. Actualizar índices y enlaces para que apunten a un destino existente.
4. Conservar una copia/versionado recuperable y registrar el destino del material único.

El alcance de esta revisión fue documentar el estado y las opciones. **No se eliminó, movió ni
fusionó ningún documento o diagrama.**
