# Guía Rápida de Despliegue en 4 Computadores (LAN)

Esta guía explica paso a paso cómo separar y ejecutar el sistema de mensajería académica en **4 computadores independientes** dentro de una misma red local (LAN o WiFi).

---

## 1. Principio Fundamental de la Arquitectura

```text
PC 2 (Cliente JavaFX)  ───┐
PC 3 (Cliente Python)  ───┼── TCP/JSON (Puerto 5000) ──▶ PC 1 (Servidor Central) ──▶ MySQL
PC 4 (Cliente .NET WPF) ──┘
```

1. **¿Qué pasa con la carpeta `common-protocol`?**  
   **No se copia a ningún computador.** `common-protocol` es código fuente que se compila en tu máquina de desarrollo. Los binarios de Java ya lo llevan empaquetado adentro, y Python y C# tienen su propia implementación nativa del protocolo en su propio lenguaje.
2. **¿Se copian las carpetas de código fuente (`server`, `client`, etc.)?**  
   **No.** Se generan las **4 carpetas de distribución empaquetadas** y se lleva una carpeta a cada PC.
3. **¿Los clientes se conectan a la base de datos MySQL?**  
   **No.** Solo el servidor toca MySQL. Los clientes únicamente se comunican por TCP/JSON con el servidor.

---

## 2. Generar las Distribuciones (En tu PC de desarrollo)

Abre PowerShell o CMD en la máquina donde tienes todo el proyecto:

```powershell
Set-Location "c:\Users\AlexP\Downloads\ClienteServidor-main\universidad-mensajeria"
cmd /c empaquetar-todo.cmd
```

Esto generará las 4 carpetas listas para copiar dentro de `universidad-mensajeria\dist\`:
- `dist\MensajeriaServidor\`
- `dist\MensajeriaClienteJava\`
- `dist\MensajeriaPython\`
- `dist\MensajeriaWPF\`

---

## 3. Asignación de Carpetas por Computador

Copia cada carpeta a una memoria USB o compártela por red local:

| Computador | Carpeta que debes copiar | Requisitos previos en ese PC |
|---|---|---|
| **PC 1 (Servidor)** | `dist\MensajeriaServidor\` | **JRE 21+** y **MySQL 8.4** (o Docker) |
| **PC 2 (Cliente Java)** | `dist\MensajeriaClienteJava\` | **JRE 21+** |
| **PC 3 (Cliente Python)** | `dist\MensajeriaPython\` | **Python 3.13** |
| **PC 4 (Cliente WPF)** | `dist\MensajeriaWPF\` | **Ninguno** (es auto-contenido / self-contained) |

---

## 4. Configuración de Red (Paso a Paso)

### En el PC 1 (Servidor Central)
1. Conoce la IP local del Servidor. Abre PowerShell y ejecuta:
   ```powershell
   ipconfig
   ```
   Anota la **Dirección IPv4** (por ejemplo: `192.168.1.50`).
2. Abre el puerto `5000` en el Firewall de Windows para que los otros PCs puedan conectarse:
   ```powershell
   New-NetFirewallRule -DisplayName "Mensajeria Servidor" -Direction Inbound -LocalPort 5000 -Protocol TCP -Action Allow
   ```
3. Inicia MySQL (ejemplo con Docker desde la raíz del proyecto):
   ```powershell
   docker compose up -d mysql
   ```
4. Inicia el servidor ejecutando dentro de `MensajeriaServidor\`:
   ```powershell
   start-server.cmd
   ```
   *(Verás en consola que el servidor arranca en el puerto 5000 y aplica las migraciones de base de datos automáticamente).*

---

### En el PC 2 (Cliente JavaFX)
1. Abre la carpeta `MensajeriaClienteJava\`.
2. Edita el archivo `client.properties` con el Bloc de Notas:
   ```properties
   host=192.168.1.50   <-- Coloca la IP real del PC 1
   puerto=5000
   ```
3. Ejecuta:
   ```powershell
   launch-client.cmd
   ```
4. Inicia sesión con:
   - **Código:** `160004726`
   - **Contraseña:** `12345`  (Alex Pineda)

---

### En el PC 3 (Cliente Python)
1. Abre la carpeta `MensajeriaPython\`.
2. Edita `client.properties` con la IP del Servidor:
   ```properties
   host=192.168.1.50   <-- Coloca la IP real del PC 1
   puerto=5000
   ```
3. Abre una terminal en esa carpeta y prepara el entorno virtual (solo la primera vez):
   ```powershell
   py -3.13 -m venv .venv
   .\.venv\Scripts\pip install -r requirements.txt
   ```
4. Ejecuta el cliente:
   ```powershell
   .\.venv\Scripts\python main.py
   ```
5. Inicia sesión con:
   - **Código:** `160004716`
   - **Contraseña:** `12345`  (Joan Garcia)

---

### En el PC 4 (Cliente .NET WPF)
1. Abre la carpeta `MensajeriaWPF\`.
2. Edita `client.properties` con la IP del Servidor:
   ```properties
   host=192.168.1.50   <-- Coloca la IP real del PC 1
   puerto=5000
   ```
3. Haz doble clic directamente en `Mensajeria.exe` (no requiere instalar nada).
4. Inicia sesión con:
   - **Código:** `160004822`
   - **Contraseña:** `12345`  (Eduard Murcia)

---

## 5. Prueba de Funcionamiento Cruzado

Una vez conectados los 3 clientes al PC 1:
1. Desde el **Cliente JavaFX (PC 2)**, selecciona en la lista a **Joan Garcia** (PC 3) y envíale un mensaje de texto.
2. Comprueba que el mensaje llega instantáneamente al **Cliente Python (PC 3)** y se actualiza el estado a `ENTREGADO`.
3. Desde el **Cliente Python (PC 3)**, envía una imagen a **Eduard Murcia** (PC 4).
4. Comprueba en el **Cliente WPF (PC 4)** que la imagen se descarga y visualiza correctamente.
5. En el **PC 1 Servidor**, observa en la carpeta `uploads\` cómo se generó el archivo original más las 5 variantes procesadas con los filtros (grises, sepia, rotación, etc.).
