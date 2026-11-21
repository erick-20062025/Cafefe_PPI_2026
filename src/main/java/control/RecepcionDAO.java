package control;

import entity.DescripcionOrden;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.math.BigDecimal;

/** Lectura agregada: una consulta, sin consultas adicionales por orden. */
@Stateless
public class RecepcionDAO {
    @PersistenceContext(unitName="my_persistence_unit") private EntityManager em;
    // Agregar facturas y abonos ANTES del join evita multiplicarlos por cada producto.
    private static final String SQL = """
        WITH totales AS (
          SELECT id_factura, SUM(precio) total,
                 BOOL_AND(precio IS NOT NULL AND precio >= 0) valido
          FROM factura_orden_producto GROUP BY id_factura
        ), abonos AS (
          SELECT p.id_factura, SUM(pd.monto) total
          FROM pago p JOIN pago_detalle pd ON pd.id_pago=p.id_pago
          WHERE COALESCE(UPPER(TRIM(p.estado)), '') <> 'ANULADO'
          GROUP BY p.id_factura
        )
        SELECT o.id_orden, o.fecha_creacion, op.observaciones, fp.id_factura,
               t.total, COALESCE(a.total,0), COALESCE(t.valido,false)
        FROM orden o JOIN orden_producto op ON op.id_orden=o.id_orden
        LEFT JOIN factura_orden_producto fp ON fp.id_orden_producto=op.id_orden_producto
        LEFT JOIN totales t ON t.id_factura=fp.id_factura
        LEFT JOIN abonos a ON a.id_factura=fp.id_factura
        ORDER BY o.fecha_creacion DESC, o.id_orden, op.id_orden_producto
        """;

    public List<Resumen> pendientes() {
        List<Object[]> filas = em.createNativeQuery(SQL).getResultList();
        return resumir(filas).stream()
                .filter(r -> !"PAGADA".equals(r.getEstadoPago())).toList();
    }
    static List<Resumen> resumir(List<Object[]> filas) {
        Map<UUID, Resumen> ordenes = new LinkedHashMap<>();
        for (Object[] f : filas) {
            UUID id = UUID.fromString(f[0].toString());
            Resumen r = ordenes.computeIfAbsent(id, k -> new Resumen(k, (Date) f[1]));
            if (r.cliente == null) r.cliente = DescripcionOrden.cliente((String) f[2]);
            BigDecimal total = (BigDecimal) f[4], abonado = (BigDecimal) f[5];
            // Un abono de una factura compartida no se asigna arbitrariamente a una orden.
            // Solo la cobertura COMPLETA de esa factura cubre sus filas asociadas.
            boolean cubierta = f[3] != null && Boolean.TRUE.equals(f[6]) && total != null
                    && total.signum() >= 0 && abonado.compareTo(total) >= 0;
            r.todasCubiertas &= cubierta;
            r.hayAbonos |= f[3] != null && abonado.signum() > 0;
        }
        return new ArrayList<>(ordenes.values());
    }
    public static final class Resumen {
        private final UUID idOrden;
        private final Date fecha;
        private String cliente;
        private boolean todasCubiertas = true, hayAbonos;
        public Resumen(UUID id, Date fecha) { this.idOrden=id; this.fecha=fecha; }
        public UUID getIdOrden() { return idOrden; }
        public String getFechaCreacion() { return fecha == null ? null : fecha.toInstant().toString(); }
        public String getCliente() { return cliente; }
        public String getEstadoPago() { return todasCubiertas ? "PAGADA" : hayAbonos ? "ABONADA_PARCIALMENTE" : "PENDIENTE_DE_PAGO"; }
    }
}
