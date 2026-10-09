"""Combina historial reciente, presencia y contactos sin conversación."""
from puertos.datos import RecienteChat, UsuarioLocal


class ConstructorRecientes:
    """Construcción pura: recibe records y no abre red ni almacenamiento."""

    @staticmethod
    def armar(base: list[RecienteChat], directorio: list[UsuarioLocal],
              yo: str) -> list[RecienteChat]:
        conectados = {usuario.codigo for usuario in directorio if usuario.conectado}
        salida = [
            RecienteChat(
                reciente.otro,
                reciente.ultimo_contenido,
                reciente.ultimo_tipo,
                reciente.fecha_ultimo,
                reciente.no_leidos,
                reciente.otro in conectados,
            )
            for reciente in base
        ]
        # Orden tipo WhatsApp (paridad con .NET): fecha del último mensaje DESC
        # manda; la presencia solo desempata a fecha igual. Así entrar/salir de
        # línea no mueve una conversación con mensajes bajo el dedo del usuario.
        salida.sort(key=lambda reciente: (reciente.fecha_ultimo or "",
                                          bool(reciente.conectado)),
                    reverse=True)
        existentes = {reciente.otro for reciente in salida}
        # Sin conversación van al final: conectados primero, luego alfabéticos.
        sin_conversacion = sorted(
            (usuario for usuario in directorio
             if usuario.codigo != yo and usuario.codigo not in existentes),
            key=lambda usuario: (not bool(usuario.conectado),
                                 (usuario.apellidos or "").casefold(),
                                 usuario.codigo))
        for usuario in sin_conversacion:
            salida.append(RecienteChat(
                usuario.codigo, "", "", "", 0, usuario.codigo in conectados))
        return salida
