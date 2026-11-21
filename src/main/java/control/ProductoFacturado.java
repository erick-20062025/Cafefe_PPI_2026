package control;

import java.math.BigDecimal;
import java.util.UUID;

/** Proyección de lectura: importe facturado, sin cargar la orden completa. */
public record ProductoFacturado(UUID idOrdenProducto, UUID idProducto,
        String nombreProducto, BigDecimal precio) { }
