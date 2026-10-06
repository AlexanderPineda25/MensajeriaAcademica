-- V5: reintroduce la IP histórica del destinatario para mantener la vista administrativa
-- consistente con el historial y con la huella persistida del mensaje.
ALTER TABLE mensajes ADD (ip_destinatario VARCHAR2(45));
