#!/usr/bin/env python
"""Verificacion de contrato para los tres clientes. Un solo comando, codigo 0 o 1.

    .\\universidad-mensajeria\\client-python\\.venv\\Scripts\\python.exe contrato\\verificar.py

Ejecuta, en orden:

1. `generar_tokens.py --check` — la paleta esta generada y los contrastes llegan a 4.5:1
2. `verificar_estado_mensaje.py` — la precedencia PENDIENTE→LEIDO es la misma en los 3
3. `verificar_puertos.py` — el codigo Java cumple el contrato de puertos
4. suite de `contrato/` — el contrato se autocomprueba
5. `mvn -pl client/client-app -am test` — suite JavaFX (incluye las 6 reglas de ArchUnit)
6. `python -m unittest discover -s tests` — suite Python
7. `dotnet build client-dotnet` — el XAML generado es valido
8. `dotnet test client-dotnet.Tests` — suite .NET

Por que un solo script: en la fase de paridad se acordo que el diseno se genera.
Si el generador queda desfasado, el fallo tiene que verse aqui y no tres commits
despues, cuando alguien edite un CSS a mano.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MONOREPOS = RAIZ / "universidad-mensajeria"
VENV_PYTHON = MONOREPOS / "client-python/.venv/Scripts/python.exe"


def _python() -> Path:
    return VENV_PYTHON if VENV_PYTHON.exists() else Path(sys.executable)


def _maven() -> list[str]:
    mvn = Path("C:/apache-maven-3.9.11/bin/mvn.cmd")
    return [str(mvn)] if mvn.exists() else ["mvn"]


PASOS: list[tuple[str, list[str], Path]] = [
    ("Tokens de diseño al día y contrastes >= 4.5:1",
     [str(_python()), str(RAIZ / "contrato/generar_tokens.py"), "--check"], RAIZ),
    ("Máquina de estados del mensaje igual en los 3 clientes",
     [str(_python()), str(RAIZ / "contrato/verificar_estado_mensaje.py")], RAIZ),
    ("Contrato de puertos: el código Java lo cumple",
     [str(_python()), str(RAIZ / "contrato/verificar_puertos.py")], RAIZ),
    ("Contrato de estados y de puertos desde la suite",
     [str(_python()), "-m", "unittest",
      "test_estado_mensaje", "test_puertos"],
     RAIZ / "contrato"),
    ("Suite JavaFX (incluye ArchUnit)",
     _maven() + ["-q", "-pl", "client/client-app", "-am", "test"], MONOREPOS),
    ("Suite Python",
     [str(_python()), "-m", "unittest", "discover", "-s", "tests"],
     MONOREPOS / "client-python"),
    ("Build WPF (valida el XAML generado)",
     ["dotnet", "build", "client-dotnet/Mensajeria.csproj", "-c", "--nologo", "-v", "q"],
     MONOREPOS),
    ("Suite .NET",
     ["dotnet", "test", "client-dotnet.Tests/Mensajeria.Tests.csproj", "--nologo", "-v", "q"],
     MONOREPOS),
]


def main() -> int:
    print("=" * 72)
    print("Verificacion de contrato — los tres clientes")
    print("=" * 72)

    fallos: list[str] = []
    for nombre, comando, cwd in PASOS:
        print(f"\n>>> {nombre}")
        try:
            codigo = subprocess.run(comando, cwd=str(cwd)).returncode
        except FileNotFoundError as error:
            print(f"    no encontrado: {comando[0]} ({error})")
            fallos.append(f"{nombre}: comando no disponible")
            continue
        if codigo == 0:
            print("    OK")
        else:
            print(f"    FALLO (codigo {codigo})")
            fallos.append(f"{nombre} (codigo {codigo})")

    if fallos and "-v" in sys.argv:
        print("\nDetalle del paso que fallo (ejecutalo a mano):")
        for nombre, comando, cwd in PASOS:
            print(f"  cd {cwd}")
            print("  " + " ".join(comando))

    print("\n" + "=" * 72)
    if fallos:
        print(f"{len(fallos)} paso(s) con fallo:")
        for fallo in fallos:
            print(f"  - {fallo}")
        return 1
    print("Todo en verde.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())