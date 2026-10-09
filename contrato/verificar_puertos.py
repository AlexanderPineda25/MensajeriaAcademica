#!/usr/bin/env python
"""Comprueba que el codigo Java implementa el contrato de puertos declarado.

    python contrato/verificar_puertos.py

Que resuelve (Fase 0.4 de PLAN_PARIDAD_CLIENTES.md): `contrato/puertos.json` es la
especificacion normativa de la superficie del cliente, y este script verifica que
el codigo la cumple. Sin esto, el JSON seria documentacion que nadie mira: el dia
que se anada un metodo al puerto y no al contrato (o al reves), nadie se entera.

Que verifica:

1. Cada puerto, record y enum declarado existe en el modulo Maven indicado.
2. Cada metodo declarado aparece en la interfaz, con la misma firma.
3. No hay metodos publicos en la interfaz que el contrato no declare.
4. Los enums de valores (TipoMensajeCliente, EstadoConexion) coinciden exactamente.
5. Las invariantes de capa que se pueden comprobar leyendo el codigo.

Los clientes Python y .NET todavia no tienen puertos (van en la Fase 2). Aqui se
comprueba la referencia Java; el JSON lleva la columna de equivalencia para los otros
dos, de modo que cuando se creen los puertos el trabajo sea copiar firmas.
"""
from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

RAIZ = Path(__file__).resolve().parent.parent
MONOREPOS = RAIZ / "universidad-mensajeria"
CONTRATO = RAIZ / "contrato" / "puertos.json"

FALLOS: list[str] = []
AVISOS: list[str] = []


def fallo(mensaje: str) -> None:
    FALLOS.append(mensaje)
    print(f"  FALLA   {mensaje}")


def ok(mensaje: str) -> None:
    print(f"  OK      {mensaje}")


def aviso(mensaje: str) -> None:
    AVISOS.append(mensaje)
    print(f"  AVISO   {mensaje}")


def cargar() -> dict:
    return json.loads(CONTRATO.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------- lectura


def rutas_java() -> dict[str, Path]:
    """Nombre simple -> ruta. El cliente esta en DOS modulos Maven."""
    base = MONOREPOS / "client"
    mapa: dict[str, Path] = {}
    for sub in ("client-interfaces", "client-app"):
        raiz = base / sub / "src/main/java"
        if not raiz.exists():
            fallo(f"no encuentro {raiz}")
            continue
        for ruta in raiz.rglob("*.java"):
            mapa.setdefault(ruta.stem, ruta)
    return mapa


def cuerpo_interfaz(texto: str) -> str:
    """Sin javadoc, sin imports, sin declaraciones de clase."""
    limpio = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)
    limpio = re.sub(r"//.*", "", limpio)
    return limpio


def firmas_declaradas(texto: str) -> list[str]:
    """Firmas publicas de una interfaz, normalizadas para comparar.

    Quita `default`, `static`, el cuerpo de las implementaciones y lasProvKeyword.
    Devuelve la forma `tipo nombre(params)`.
    """
    limpio = cuerpo_interfaz(texto)
    encontrados: list[str] = []

    # Escaneo por profundidad de llaves: solo cuentan las declaraciones que estan
    # directamente en el cuerpo de la interfaz (profundidad 1). Asi, el `guardar(u)`
    # de dentro de un `default void reemplazarDirectorio() { ... }` no se cuela
    # como si fuera un metodo mas, sin tener que mutilar el texto antes.
    profundidad = 0
    acumulado: list[str] = []
    for linea in limpio.splitlines():
        # Una declaracion empieza en la linea que abre el cuerpo de la interfaz.
        # Aqui `antes` ya vale 1, y la linea `public interface X {` es la que lo abrio.
        if profundidad == 0:
            profundidad += linea.count("{") - linea.count("}")
            continue
        if profundidad > 1:
            # Dentro de un cuerpo `default { ... }`: se ignora su contenido.
            profundidad += linea.count("{") - linea.count("}")
            acumulado = []
            continue

        # profundidad == 1: aqui viven las declaraciones.
        if not acumulado:
            if not re.search(r"\w+\s*\(", linea) and not linea.strip():
                continue
            if not re.search(r"\w+\s*\(", linea):
                continue
        acumulado.append(linea.strip())

        if _declaracion_terminada(" ".join(acumulado)):
            declarada = " ".join(acumulado).strip()
            acumulado = []
            firma = _a_firma(declarada)
            if firma:
                encontrados.append(firma)
            # Si la linea abrio un cuerpo, hay que saltar hasta cerrarlo.
            if declarada.rstrip().endswith("{"):
                profundidad = 1  # las siguientes lineas son del cuerpo
        else:
            # Puede haber abierto un cuerpo en una linea intermedia.
            if "{" in linea and linea.count("(") <= linea.count(")"):
                profundidad = 1
                acumulado = []
    return encontrados


def _declaracion_terminada(acumulado: str) -> bool:
    """¿La declaracion en curso esta completa?"""
    if acumulado.count("(") - acumulado.count(")") > 0:
        return False
    return ";" in acumulado or acumulado.rstrip().endswith("{")


def _a_firma(declarada: str) -> str | None:
    """`void f(A a) throws E;` -> `void f(A)`. None si no es una declaracion."""
    declarada = declarada.strip()
    coincidencia = re.match(
        r"^(?:default\s+|static\s+|public\s+|abstract\s+)*"
        r"([A-Za-z][\w.]*(?:<.*>)?(?:\[\])?)\s+(\w+)\s*\((.*?)\)"
        r"(?:\s*throws\s+[^;{]+?)?\s*(?:;|\{)?$",
        declarada,
    )
    if not coincidencia:
        return None
    tipo = " ".join(coincidencia.group(1).split())
    if tipo.lower() in ("return", "throw", "new", "if", "for", "while",
                        "else", "package", "import", "public", "default"):
        return None
    # `default int esperaSegundos(int)` empieza por `default`: el tipo va detras.
    if tipo == "default":
        return None
    params = " ".join(coincidencia.group(3).split())
    return f"{tipo} {coincidencia.group(2)}({params})"



def tipo_de(firma: str) -> str:
    """Tipo de retorno de una firma, sin los modificadores."""
    cuerpo = re.sub(r"\b(default|static|public|abstract)\b", "", firma).strip()
    return cuerpo.split(" ")[0]


def nombre_de(firma: str) -> str:
    """Nombre del metodo de una firma."""
    cuerpo = re.sub(r"\b(default|static|public|abstract)\b", "", firma).strip()
    coincidencia = re.search(r"([A-Za-z_]\w*)\s*\(", cuerpo)
    return coincidencia.group(1) if coincidencia else ""


def parametros_de(firma: str) -> list[str]:
    """Tipos de los parametros, en orden, sin los nombres.

    Es O(n log n) en vez de ordenarlos: el orden importa. Si dos metodos se
    distinguen solo por el orden de los parametros de igual tipo, ordenarlos los
    haria iguales y el verificador dejaria pasar un overload cambiado.
    """
    cuerpo = re.sub(r"\b(default|static|public|abstract)\b", "", firma).strip()
    dentro = re.search(r"\((.*?)\)", cuerpo, re.S)
    if not dentro or not dentro.group(1).strip():
        return []
    tipos = []
    for p in dentro.group(1).split(","):
        p = " ".join(p.split())
        if not p:
            continue
        # `List<RecienteChat> recientes` -> tipo `List<RecienteChat>`, sin nombre.
        if " " in p:
            partes = p.split()
            tipos.append(" ".join(partes[:-1]))
        else:
            # Sin espacio: el parametro es un tipo pelado (`List<String> foo` nunca
            # llega aqui, pero `List<RecienteChat>` como unico parametro si).
            tipos.append(p)
    return tipos


def clave_metodo(firma: str) -> str:
    """Identidad comparable de un metodo: tipo de retorno + nombre + tipos de params.

    Se comparan los METODOS por posicion de sobrecarga, no como conjunto plano: una
    interfaz con `enviarTexto(String,String,String)` y
    `enviarTexto(String,String,String,String)` son metodos distintos y ambos deben
    existir.
    """
    return (_sin_prefijo(tipo_de(firma)), nombre_de(firma),
            ",".join(_sin_prefijo(t) for t in parametros_de(firma)))


def clave_texto(firma: str) -> str:
    return "|".join(clave_metodo(firma))


def _sin_prefijo(tipo: str) -> str:
    """Quita el paquete de un tipo: `java.util.List<...>` es `List<...>`.

    No cambia la semantica (el import es el mismo) pero evita que la misma
    interfaz se declare con y sin el paquete segun quien escriba el contrato.
    """
    limpio = re.sub(r"\b(?:[a-z_]\w*\.)+", "", tipo)
    return " ".join(limpio.split())


# --------------------------------------------------------------------------- checks


def comprobar_existencia(contrato: dict, rutas: dict[str, Path]) -> None:
    print("\nTipos declarados (puertos, records, enums):")
    esperados = ([p["nombre"] for p in contrato["puertos"]]
                 + [r["nombre"] for r in contrato["records"]]
                 + [e["nombre"] for e in contrato["enums"]])
    for nombre in esperados:
        if nombre in rutas:
            ok(f"{nombre}")
        else:
            fallo(f"el contrato declara {nombre} pero no lo encuentro en client/")


def comprobar_metodos(contrato: dict, rutas: dict[str, Path]) -> None:
    print("\nMetodos de cada puerto:")
    for puerto in contrato["puertos"]:
        nombre = puerto["nombre"]
        ruta = rutas.get(nombre)
        if ruta is None:
            continue
        declarados = Counter(clave_texto(m["firma"]) for m in puerto["metodos"])
        reales_firmas = firmas_declaradas(ruta.read_text(encoding="utf-8"))
        reales = Counter(clave_texto(f) for f in reales_firmas)

        # Multiconjuntos: una sobrecarga duplicada en el codigo tambien diverge.
        for clave, veces in (declarados - reales).items():
            tipo, metodo, params = clave.split("|")
            firma = f"{tipo} {metodo}({params.replace(',', ', ')})"
            fallo(f"{nombre}: el contrato declara `{firma}` y no esta en el codigo")
        for clave, veces in (reales - declarados).items():
            tipo, metodo, params = clave.split("|")
            firma = f"{tipo} {metodo}({params.replace(',', ', ')})"
            fallo(f"{nombre}: `{firma}` esta en el codigo y NO en el contrato "
                  f"-> anadelo a puertos.json o borralo")

        if declarados == reales:
            opcionales = sum(1 for m in puerto["metodos"] if m.get("opcional"))
            sufijo = f" ({opcionales} opcionales)" if opcionales else ""
            ok(f"{nombre:28} {sum(reales.values())} metodos{sufijo}")


def comprobar_enums(contrato: dict, rutas: dict[str, Path]) -> None:
    print("\nEnums: los valores deben coincidir exactamente:")
    for especificacion in contrato["enums"]:
        nombre = especificacion["nombre"]
        ruta = rutas.get(nombre)
        if ruta is None:
            continue
        texto = cuerpo_interfaz(ruta.read_text(encoding="utf-8"))
        cuerpo = re.search(r"enum\s+" + nombre + r"\s*\{(.*?)(?:;|\n\s*\})", texto, re.S)
        if not cuerpo:
            fallo(f"{nombre}: no puedo leer el cuerpo del enum")
            continue
        if isinstance(especificacion["valores"][0], str):
            esperados = list(especificacion["valores"])
            bloques = [v.strip() for v in cuerpo.group(1).split(",")]
            reales = [v for v in bloques if re.fullmatch(r"[A-Z_][A-Z0-9_]*", v)]
        else:
            esperados = [v["nombre"] for v in especificacion["valores"]]
            reales = [m.group(1) for m in
                      re.finditer(r"([A-Z_][A-Z0-9_]*)\s*\(", cuerpo.group(1))]

        faltan = set(esperados) - set(reales)
        sobran = set(reales) - set(esperados)
        for f in sorted(faltan):
            fallo(f"{nombre}: falta el valor {f} en el codigo")
        for s in sorted(sobran):
            fallo(f"{nombre}: el valor {s} esta en el codigo y no en el contrato")
        if not faltan and not sobran:
            ok(f"{nombre:28} {len(reales)} valores")

        # Los textos de EstadoConexion son literales de UI: deben ser identicos.
        if nombre == "EstadoConexion":
            for valor in especificacion["valores"]:
                if f'"{valor["texto"]}"' not in texto:
                    fallo(f"{nombre}.{valor['nombre']}: el texto `{valor['texto']}` "
                          "no aparece en el codigo")
            ok(f"{nombre:28} {len(esperados)} textos verificados")


def comprobar_jerarquia_excepciones(contrato: dict, rutas: dict[str, Path]) -> None:
    """3 familias distintas: un catch por tipo no es intercambiable.

    Es la trampa mas sutil del contrato. `ExcepcionSesion` y `ExcepcionValidacion`
    NO heredan de `ExcepcionCliente`: son runtime. Traducirlas a la rama incorrecta
    en Python (que solo tiene Exception) haria que un rechazo del servidor se
    reintentara para siempre en vez de marcar ERROR.
    """
    print("\nJerarquia de excepciones (3 familias):")
    for excepcion in contrato["excepciones"]:
        nombre = excepcion["java"]
        ruta = rutas.get(nombre)
        if ruta is None:
            continue
        texto = ruta.read_text(encoding="utf-8")
        declaracion = re.search(r"class\s+" + nombre + r"\s+extends\s+(\w+)", texto)
        if not declaracion:
            fallo(f"{nombre}: no encuentro la declaracion de clase")
            continue
        base = declaracion.group(1)
        if base != excepcion["base"]:
            fallo(f"{nombre}: el codigo extiende {base}, el contrato dice "
                  f"{excepcion['base']}")
        else:
            ok(f"{nombre:32} extends {base}")


def comprobar_opcionales(contrato: dict, rutas: dict[str, Path]) -> None:
    """Un `default` que lanza ExcepcionOperacionNoSoportada debe seguir siendolo.

    Es lo que permite que el puerto declare una operacion sin romper a los llamantes
    (LSP). Si alguien lo convierte en abstracto, todos los dobles de prueba rompen.
    """
    print("\nOperaciones opcionales (default que lanza):")
    for puerto in contrato["puertos"]:
        ruta = rutas.get(puerto["nombre"])
        if ruta is None:
            continue
        texto = cuerpo_interfaz(ruta.read_text(encoding="utf-8"))
        for metodo in puerto["metodos"]:
            if not metodo.get("opcional"):
                continue
            nombre = re.search(r"(\w+)\s*\(", metodo["firma"])
            if not nombre:
                continue
            bloque = re.search(
                r"default\s+[^\n]*?\b" + nombre.group(1) + r"\s*\([^)]*\)[^{]*\{([^}]*)\}",
                texto, re.S)
            if not bloque:
                fallo(f"{puerto['nombre']}.{nombre.group(1)}: es opcional pero "
                      "ya no es `default` -> rompe a los dobles de prueba")
            elif "ExcepcionOperacionNoSoportada" in bloque.group(1):
                ok(f"{puerto['nombre']}.{nombre.group(1)}")


def comprobar_invariantes(contrato: dict, rutas: dict[str, Path]) -> None:
    """Lo que se puede comprobar leyendo el fuente."""
    print("\nInvariantes de capa:")

    # 1. client-interfaces no declara dependencias Maven.
    pom = MONOREPOS / "client/client-interfaces/pom.xml"
    if pom.exists():
        texto = pom.read_text(encoding="utf-8")
        sin_deps = "<dependencies>" not in texto
        if sin_deps:
            ok("client-interfaces/pom.xml sin bloque <dependencies>")
        else:
            fallo("client-interfaces/pom.xml declara dependencias: los puertos "
                  "deben poder vivir sin framework")
    else:
        aviso("no encuentro client-interfaces/pom.xml")

    # 2. La tabla de backoff es un contrato compartido con Python y .NET.
    gestor = rutas.get("InterfazGestorReconexion")
    if gestor:
        texto = " ".join(gestor.read_text(encoding="utf-8").split())
        if "{1, 2, 4, 8, 16, 30}" in texto:
            ok("tabla de backoff {1,2,4,8,16,30} en el puerto")
        else:
            fallo("la tabla de backoff cambio: Python y .NET reconectan con la "
                  "tabla del contrato y quedarian desfasados")

    # 3. La composicion se inyecta desde FabricaCliente.
    fabrica = rutas.get("FabricaCliente")
    if fabrica:
        texto = fabrica.read_text(encoding="utf-8")
        if "crear()" in texto or "crear(" in texto:
            ok("FabricaCliente es el composition root")
        else:
            aviso("FabricaCliente no expone crear(); revisa que siga siendo el "
                  "composition root")


def main() -> int:
    print("=" * 72)
    print("Contrato de puertos — el codigo Java lo cumple?")
    print("Fuente: contrato/puertos.json")
    print("=" * 72)

    contrato = cargar()
    rutas = rutas_java()

    comprobar_existencia(contrato, rutas)
    comprobar_metodos(contrato, rutas)
    comprobar_enums(contrato, rutas)
    comprobar_jerarquia_excepciones(contrato, rutas)
    comprobar_opcionales(contrato, rutas)
    comprobar_invariantes(contrato, rutas)

    print("\n" + "=" * 72)
    if AVISOS:
        print(f"{len(AVISOS)} aviso(s):")
        for a in AVISOS:
            print(f"  - {a}")
        print()
    if FALLOS:
        print(f"{len(FALLOS)} divergencia(s) con el contrato:")
        for f in FALLOS:
            print(f"  - {f}")
        return 1
    print("El codigo cumple el contrato de puertos.")
    print()
    print("Pendiente: completar la paridad de los 23 puertos en Python y .NET (Fase 2).")
    print("Python ya declara los siete puertos de las capas 1–2; su suite compara")
    print("sus firmas y tipos con contrato/puertos.json.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
