-- V5: reintroduce la IP histórica del destinatario para mantener la vista administrativa
-- consistente con el historial y con la huella persistida del mensaje.
ALTER TABLE mensajes ADD COLUMN ip_destinatario VARCHAR(45) NULL;
