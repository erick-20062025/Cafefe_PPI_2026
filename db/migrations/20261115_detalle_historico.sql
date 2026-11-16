-- Migración aditiva e idempotente. No recalcula ni elimina registros anteriores.
BEGIN;
ALTER TABLE orden_producto ADD COLUMN IF NOT EXISTS precio_base numeric(18,2);
ALTER TABLE orden_producto ADD COLUMN IF NOT EXISTS descuento_aplicado numeric(18,2);
ALTER TABLE orden_producto ADD COLUMN IF NOT EXISTS promocion_aplicada varchar(36);
ALTER TABLE orden_producto ADD COLUMN IF NOT EXISTS caracteristicas_historicas text;
ALTER TABLE orden_producto ADD COLUMN IF NOT EXISTS producto_historico text;
ALTER TABLE orden ADD COLUMN IF NOT EXISTS estado_preparacion varchar(20) DEFAULT 'PENDIENTE';
ALTER TABLE orden ADD COLUMN IF NOT EXISTS cliente_web varchar(155);
ALTER TABLE orden ADD COLUMN IF NOT EXISTS atendido_por varchar(520);
ALTER TABLE pago_detalle ADD COLUMN IF NOT EXISTS efectivo_recibido numeric(18,2);
ALTER TABLE pago_detalle ADD COLUMN IF NOT EXISTS cambio_entregado numeric(18,2);
ALTER TABLE orden_producto ALTER COLUMN promocion_aplicada TYPE varchar(36) USING promocion_aplicada::text;
COMMIT;
