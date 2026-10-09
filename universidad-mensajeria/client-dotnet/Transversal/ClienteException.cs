using System;
using System.IO;

namespace Mensajeria.Transversal
{
    /// <summary>
    /// Raíz de los errores de aplicación del cliente .NET. Espejo de
    /// <c>ExcepcionCliente</c> de <c>client-interfaces</c>.
    ///
    /// <para><b>POR QUÉ una raíz y no <c>Exception</c>:</b> sustituye al
    /// <c>throws Exception</c> genérico de los puertos, que en C# no obliga a nada.</para>
    ///
    /// <para><b>Las tres familias del contrato</b> (contrato/puertos.json). Un
    /// <c>catch</c> por tipo no es intercambiable entre ellas y NO se puede
    /// "colapsar" en una sola jerarquía:</para>
    /// <list type="bullet">
    /// <item><b>Cliente</b> (<see cref="ClienteException"/>, y sus dos submáquinas
    /// historial y persistencia): fallo previsto del dominio, checked en Java.</item>
    /// <item><b>Validación</b> (<see cref="ValidacionException"/> ⊂
    /// <c>ArgumentException</c>): entrada inválida. Deliberadamente <b>NO</b> hereda
    /// de <see cref="ClienteException"/>, para no arrastrar a los
    /// <c>catch (ClienteException)</c> a un error de programación.</item>
    /// <item><b>Sesión</b> (<see cref="SesionException"/> ⊂
    /// <c>InvalidOperationException</c>): estado inválido o rechazo del servidor.
    /// No se reintenta.</item>
    /// </list>
    /// Y <see cref="OperacionNoSoportadaException"/> ⊂ <c>IOException</c>, que es lo
    /// que permite que un puerto declare una operación opcional sin romper el LSP de
    /// los llamantes (ya tratan fallos de red).
    /// </summary>
    public class ClienteException : Exception
    {
        public ClienteException(string mensaje) : base(mensaje) { }

        public ClienteException(string mensaje, Exception causa) : base(mensaje, causa) { }
    }

    /// <summary>Fallo del historial local (consultas, no leídos, cola offline).</summary>
    public sealed class HistorialException : ClienteException
    {
        public HistorialException(string mensaje) : base(mensaje) { }

        public HistorialException(string mensaje, Exception causa) : base(mensaje, causa) { }
    }

    /// <summary>Fallo de la base local (apertura, esquema, migración por cuenta).</summary>
    public sealed class PersistenciaException : ClienteException
    {
        public PersistenciaException(string mensaje) : base(mensaje) { }

        public PersistenciaException(string mensaje, Exception causa) : base(mensaje, causa) { }
    }

    /// <summary>
    /// Entrada inválida del usuario (código, contenido, destinatario, imagen).
    /// Deriva de <see cref="ArgumentException"/> para que un <c>catch</c> por la
    /// excepción estándar siga funcionando igual que en Java.
    /// </summary>
    public sealed class ValidacionException : ArgumentException
    {
        public ValidacionException(string mensaje) : base(mensaje) { }

        public ValidacionException(string mensaje, Exception causa) : base(mensaje, causa) { }
    }

    /// <summary>
    /// Estado de sesión inválido (operar sin sesión, directorio no disponible,
    /// rechazo definitivo del servidor). Deriva de <see cref="InvalidOperationException"/>
    /// y <b>no</b> de <see cref="ClienteException"/>, igual que en Java.
    /// </summary>
    public sealed class SesionException : InvalidOperationException
    {
        public SesionException(string mensaje) : base(mensaje) { }

        public SesionException(string mensaje, Exception causa) : base(mensaje, causa) { }
    }

    /// <summary>
    /// Operación opcional del puerto de red no soportada por este adaptador.
    /// Deriva de <see cref="IOException"/> a propósito (contrato LSP).
    /// </summary>
    public sealed class OperacionNoSoportadaException : IOException
    {
        public OperacionNoSoportadaException(string operacion)
            : base(operacion + " no soportado por este adaptador de red") { }
    }
}