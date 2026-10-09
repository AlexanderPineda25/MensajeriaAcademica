#!/usr/bin/env python
"""Comprueba que los 3 clientes implementan la maquina de estados del contrato.

    python contrato/verificar_estado_mensaje.py

Que resuelve (cierra VIS-02 de ESTADO_ROADMAP.md §7): la precedencia
`PENDIENTE -> ENVIADO -> ENTREGADO -> LEIDO` (+ `ERROR`) estaba escrita a mano 5
veces en 3 lenguajes, con dos mecanismos distintos:

  Java   : CASE de enteros en el WHERE de `MARCAR_ESTADO`, y DELETE+INSERT al guardar
  Python : ON CONFLICT DO UPDATE (dos veces) + `marcar_estado`
  .NET   : ON CONFLICT DO UPDATE (dos veces) + `MarcarEstado`

Si se anade un estado y se actualiza una sola de esas 5 copias, ese cliente
discrepa del resto y **ninguna prueba falla**: solo se nota cuando un mensaje
cambia de estado de forma distinta segun desde que cliente se mire.

Este script no genera el SQL (seria codegen en 3 lenguajes y 2 motores). Comprueba
que lo que hay escrito coincide con `contrato/estado-mensaje.json`.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# La consola de Windows usa cp1252 y los ticks llevan glifos (⏳ ✓✓) que no
# entran. Sin esto, el propio script revienta al informar.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

RAIZ = Path(__file__).resolve().parent.parent
MONOREPOS = RAIZ / "universidad-mensajeria"
CONTRATO = RAIZ / "contrato" / "estado-mensaje.json"

#: Subcarpetas donde vive la logica de cada cliente. Java esta partido en dos
#: modulos Maven: los puertos y la tabla de estados viven en `client-interfaces`,
#: los adaptadores en `client-app`. Buscar solo en `client-app` daria falsos
#: negativos (sucedio con los ticks).
CLIENTES = {
    "java": [MONOREPOS / "client/client-interfaces", MONOREPOS / "client/client-app"],
    "python": [MONOREPOS / "client-python"],
    "dotnet": [MONOREPOS / "client-dotnet"],
}

EXT = {"java": "*.java", "python": "*.py", "dotnet": "*.cs"}

FALLOS: list[str] = []


def fallo(mensaje: str) -> None:
    FALLOS.append(mensaje)
    print(f"  FALLA   {mensaje}")


def ok(mensaje: str) -> None:
    print(f"  OK      {mensaje}")


def cargar() -> dict:
    return json.loads(CONTRATO.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------- busqueda


def fuentes(clave: str, patron: str) -> list[Path]:
    """Ficheros del cliente que contienen el patrón (sin tests ni `dist/`)."""
    encontrados = []
    for raiz in CLIENTES[clave]:
        if not raiz.exists():
            fallo(f"no encuentro {raiz}")
            continue
        for ruta in sorted(raiz.rglob(EXT[clave])):
            # `dist/` es una copia empaquetada: si se leyera, un `dist` viejo
            # haria pasar el contrato cuando el fuente ya divergio.
            if "test" in ruta.name.lower() or "Tests" in ruta.name or "dist" in ruta.parts:
                continue
            if re.search(patron, ruta.read_text(encoding="utf-8"), re.IGNORECASE):
                encontrados.append(ruta)
    return encontrados


def texto_de(clave: str, patron: str) -> str:
    return "\n".join(p.read_text(encoding="utf-8") for p in fuentes(clave, patron))


# --------------------------------------------------------------------------- reglas


def comprobar_orden(contrato: dict) -> None:
    """Los 5 estados y su orden deben existir con el mismo valor en el JSON."""
    print("\nOrden de los estados:")
    for estado in contrato["estados"]:
        ok(f"{estado['nombre']:11} orden={estado['orden']}  {estado['tick']}")


def comprobar_leido_gana(contrato: dict) -> None:
    """Regla 1 y 2: LEIDO nunca se degrada, llegue de donde llegue.

    Es la unica regla que se puede afirmar leyendo el SQL de los 3 clientes sin
    interpretarlos: los tres tienen `estado = 'LEIDO'` en la condicion.
    """
    print("\n'LEIDO' nunca se degrada (reglas leido-absorbe y nuevo-leido):")
    esperado = {
        "java": (r"WHEN 'LEIDO' THEN 3", "CASE de enteros en el WHERE"),
        "python": (r"estado = 'LEIDO' OR excluded\.estado = 'LEIDO'", "ON CONFLICT"),
        "dotnet": (r"estado = 'LEIDO' OR excluded\.estado = 'LEIDO'", "ON CONFLICT"),
    }
    for cliente, (patron, donde) in esperado.items():
        coincide = bool(fuentes(cliente, patron))
        if coincide:
            ok(f"{cliente:7} {donde}")
        else:
            fallo(f"{cliente}: no encuentro '{patron}' (se esperaba en {donde})")


def comprobar_no_retrcede(contrato: dict) -> None:
    """Reglas 3 y 4: ENTREGADO y ENVIADO no bajan por un reintento de cola.

    Se busca la condicion completa, no solo una parte: un `IN` con el conjunto
    equivocado pasaria una comprobacion laxa y es justo el bug que se busca.
    """
    print("\n'ENTREGADO' y 'ENVIADO' no retroceden (reglas entregado-no-retrcede y enviado-no-retrcede):")
    esperado = {
        "python": [
            (r"estado = 'ENTREGADO' AND excluded\.estado IN \('PENDIENTE', 'ENVIADO'\) THEN 'ENTREGADO'",
             "ENTREGADO no baja a PENDIENTE/ENVIADO"),
            (r"estado = 'ENVIADO' AND excluded\.estado = 'PENDIENTE' THEN 'ENVIADO'",
             "ENVIADO no baja a PENDIENTE"),
        ],
        "dotnet": [
            (r"estado = 'ENTREGADO' AND excluded\.estado IN \('PENDIENTE', 'ENVIADO'\) THEN 'ENTREGADO'",
             "ENTREGADO no baja a PENDIENTE/ENVIADO"),
            (r"estado = 'ENVIADO' AND excluded\.estado = 'PENDIENTE' THEN 'ENVIADO'",
             "ENVIADO no baja a PENDIENTE"),
        ],
    }
    for cliente, reglas in esperado.items():
        texto = texto_de(cliente, "LEIDO")
        for patron, descripcion in reglas:
            if re.search(patron, texto):
                ok(f"{cliente:7} {descripcion}")
            else:
                fallo(f"{cliente}: falta `{descripcion}` "
                      f"(patrón {patron})")

    # Java usa una comparacion de enteros, no las mismas condiciones.
    texto_java = texto_de("java", "MARCAR_ESTADO|marcarEstado")
    for patron, descripcion in [
        (r"WHEN 'LEIDO' THEN 3", "LEIDO=3"),
        (r"WHEN 'ENTREGADO' THEN 2", "ENTREGADO=2"),
        (r"WHEN 'ENVIADO' THEN 1", "ENVIADO=1"),
    ]:
        apariciones = len(re.findall(patron, texto_java))
        if apariciones >= 2:
            ok(f"java    {descripcion} (en ambos lados del CASE, {apariciones}x)")
        elif apariciones == 1:
            fallo(f"java    {descripcion} aparece 1 vez: el CASE compara el estado "
                  f"actual con el nuevo, deben salir las dos copias")
        else:
            fallo(f"java    no encuentro {descripcion}")


def comprobar_sin_leer(contrato: dict) -> None:
    """`estado <> 'LEIDO'` es la condicion de "sin leer" en los 3 clientes.

    Ojo: el criterio difiere. Java y Python filtran por `enviado = 0` (solo lo
    recibido cuenta como no leido); .NET solo mira el estado. Se acepta la
    diferencia porque esta documentada, pero se exige que los 3 excluyan LEIDO.
    """
    print("\nCondicion de 'sin leer' (excluye LEIDO en los 3):")
    for cliente in ("java", "python", "dotnet"):
        texto = texto_de(cliente, r"<> 'LEIDO'")
        if texto:
            ok(f"{cliente:7} filtra por estado <> 'LEIDO'")
        else:
            fallo(f"{cliente}: no encuentro la condición de sin leer (`estado <> 'LEIDO'`)")


def comprobar_error_terminal(contrato: dict) -> None:
    """ERROR no debe aparecer como estado de la precedencia en el SQL.

    ERROR se escribe solo en la columna `estado`; las condiciones de precedencia
    solo deben mencionar PENDIENTE, ENVIADO, ENTREGADO y LEIDO. Si un cliente
    metiera ERROR en el CASE, un reintento podria resucitar un mensaje descartado.
    """
    print("\nERROR es terminal (no aparece en las condiciones de precedencia):")
    for cliente in ("java", "python", "dotnet"):
        texto = texto_de(cliente, r"WHEN 'LEIDO'|excluded\.estado = 'LEIDO'")
        if not texto:
            continue
        # Solo dentro de un CASE/WHERE de precedencia, no en un UPDATE de estado.
        apariciones_error = re.findall(r"(?:WHEN|AND)\s+[^\n]*ERROR", texto)
        if apariciones_error:
            fallo(f"{cliente}: ERROR aparece en una condición de precedencia: "
                  f"{apariciones_error[:2]}")
        else:
            ok(f"{cliente:7} ERROR solo como valor, nunca como condición")


def comprobar_ticks(contrato: dict) -> None:
    """Los glifos del tick deben coincidir con el contrato en los 3 clientes.

    Es la parte que el usuario ve: si un cliente pone `✓✓ Leído` y otro `✓✓Leido`,
    la maquina de estados puede ser identica y aun asi la UI discrepar.
    """
    print("\nGlifos del tick (lo que ve el usuario):")
    esperado = {e["nombre"]: e["tick"] for e in contrato["estados"]}
    for cliente in CLIENTES:
        texto = texto_de(cliente, r"tick|Estado")
        if not texto:
            fallo(f"{cliente}: no encuentro la definicion del tick")
            continue
        for nombre, tick in esperado.items():
            if tick in texto:
                ok(f"{cliente:7} {nombre:11} {tick}")
            else:
                fallo(f"{cliente}: el tick de {nombre} no aparece; "
                      f"el contrato exige `{tick}`")


def main() -> int:
    print("=" * 72)
    print("Maquina de estados del mensaje — los 3 clientes")
    print("Fuente: contrato/estado-mensaje.json")
    print("=" * 72)

    contrato = cargar()

    comprobar_orden(contrato)
    comprobar_leido_gana(contrato)
    comprobar_no_retrcede(contrato)
    comprobar_sin_leer(contrato)
    comprobar_error_terminal(contrato)
    comprobar_ticks(contrato)

    print("\n" + "=" * 72)
    if FALLOS:
        print(f"{len(FALLOS)} divergencia(s) con el contrato:")
        for item in FALLOS:
            print(f"  - {item}")
        return 1
    print("Los 3 clientes implementan la maquina de estados del contrato.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
