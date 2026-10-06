-- V3: mensajes.ip_destinatario nunca se escribio (siempre NULL) y la entidad
-- JPA no lo mapea con getter ni setter (MUERT-12). La IP la ve el destinatario
-- en el tramo de entrega, no en la tabla. Se retira la columna.
ALTER TABLE mensajes DROP COLUMN ip_destinatario;