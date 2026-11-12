package control;

import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.Pago;
import entity.PagoDetalle;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Operaciones de cobro. La cabecera Pago y sus PagoDetalle se escriben juntos
 * en una transacción EJB, bloqueando la Factura para evitar cobros simultáneos.
 * Las consultas JPQL residen en @NamedQuery de Pago y PagoDetalle.
 */
@Stateless
public class PagoDAO extends DefaultDAO<Pago> {

    private static final Set<String> METODOS = Set.of(
            "EFECTIVO", "TARJETA", "TRANSFERENCIA", "QR");

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public PagoDAO() { super(Pago.class); }

    @Override
    protected EntityManager getEntityManager() { return em; }

    /** Facturas pendientes, creadas previamente en el módulo Factura. */
    public List<Factura> listarFacturasPendientes() {
        return em.createNamedQuery("Pago.facturasPendientes", Factura.class)
                .getResultList();
    }

    /** Detalles con el precio propio de la factura (no el precio actual de Orden). */
    public List<FacturaOrdenProducto> obtenerDetallesFactura(UUID idFactura) {
        validarId(idFactura);
        return em.createNamedQuery("Pago.detallesFactura", FacturaOrdenProducto.class)
                .setParameter("idFactura", idFactura)
                .getResultList();
    }

    /** Permite consultar facturas pendientes o pagadas sin modificarlas. */
    public ResumenPago consultarResumen(UUID idFactura) {
        validarId(idFactura);
        Factura factura = em.find(Factura.class, idFactura);
        if (factura == null) {
            throw new IllegalArgumentException("La factura seleccionada no existe");
        }
        BigDecimal total = consultarTotalValidado(idFactura);
        BigDecimal abonado = consultarAbonado(idFactura);
        if (abonado.compareTo(total) > 0) {
            throw new IllegalStateException("La factura tiene abonos superiores a su total");
        }
        return new ResumenPago(total, abonado, total.subtract(abonado),
                "PAGADA".equalsIgnoreCase(factura.getEstado()));
    }

    /**
     * Registra un abono y completa automáticamente la factura al cancelar
     * exactamente todo el saldo. No acepta sobrepagos ni calcula vuelto.
     *
     * @throws IllegalArgumentException cuando falla una regla de negocio.
     * @throws IllegalStateException cuando los registros ya están inconsistentes.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ResumenPago registrarAbono(UUID idFactura, BigDecimal monto,
            String tipoPago, String referenciaExterna, String observaciones) {

        validarId(idFactura);
        BigDecimal importe = validarMonto(monto);
        String metodo = normalizarMetodo(tipoPago);
        String referencia = normalizarTexto(referenciaExterna);
        String nota = normalizarTexto(observaciones);

        if (!"EFECTIVO".equals(metodo) && referencia == null) {
            throw new IllegalArgumentException(
                    "Debe indicar una referencia para " + metodo.toLowerCase(Locale.ROOT));
        }

        // Bloqueo exclusivo sobre la misma factura: serializa cobros concurrentes
        // realizados mediante este DAO. El proveedor y PostgreSQL gestionan JTA.
        Factura factura = em.find(Factura.class, idFactura,
                LockModeType.PESSIMISTIC_WRITE);
        if (factura == null) {
            throw new IllegalArgumentException("La factura seleccionada no existe");
        }
        if (!"PENDIENTE".equalsIgnoreCase(factura.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden registrar abonos a facturas pendientes");
        }

        BigDecimal total = consultarTotalValidado(idFactura);
        BigDecimal abonado = consultarAbonado(idFactura);
        if (abonado.compareTo(total) >= 0) {
            throw new IllegalArgumentException("La factura ya está completamente cubierta");
        }

        BigDecimal saldo = total.subtract(abonado);
        if (importe.compareTo(saldo) > 0) {
            throw new IllegalArgumentException(
                    "El abono supera el saldo pendiente de $" + saldo.toPlainString());
        }

        if (!"EFECTIVO".equals(metodo)) {
            Long repetidas = em.createNamedQuery(
                    "PagoDetalle.countReferenciaFactura", Long.class)
                    .setParameter("idFactura", idFactura)
                    .setParameter("tipoPago", metodo)
                    .setParameter("referenciaExterna", referencia)
                    .getSingleResult();
            if (repetidas != null && repetidas > 0) {
                throw new IllegalArgumentException(
                        "La referencia de este pago ya fue registrada en la factura");
            }
        }

        List<Pago> pagos = em.createNamedQuery("Pago.findByFactura", Pago.class)
                .setParameter("idFactura", idFactura)
                .getResultList();
        if (pagos.size() > 1) {
            throw new IllegalStateException(
                    "La factura tiene varias cabeceras de pago; requiere revisión");
        }
        Pago pago;
        if (pagos.isEmpty()) {
            pago = new Pago(UUID.randomUUID());
            pago.setIdFactura(factura);
            pago.setEstado("PARCIAL");
            em.persist(pago);
        } else {
            pago = pagos.get(0);
            if ("ANULADO".equalsIgnoreCase(pago.getEstado())
                    || "COMPLETADO".equalsIgnoreCase(pago.getEstado())) {
                throw new IllegalStateException(
                        "El registro de pago existente no admite nuevos abonos");
            }
        }

        PagoDetalle detalle = new PagoDetalle(UUID.randomUUID());
        detalle.setIdPago(pago);
        detalle.setMonto(importe);
        detalle.setTipoPago(metodo);
        detalle.setReferenciaExterna(referencia);
        detalle.setObservaciones(nota);
        em.persist(detalle);

        BigDecimal totalAbonado = abonado.add(importe);
        boolean completado = totalAbonado.compareTo(total) == 0;
        pago.setEstado(completado ? "COMPLETADO" : "PARCIAL");
        if (completado) {
            factura.setEstado("PAGADA");
        }

        // Fuerza a detectar fallas de integridad antes de responder éxito.
        em.flush();
        return new ResumenPago(total, totalAbonado,
                total.subtract(totalAbonado), completado);
    }

    private BigDecimal consultarTotalValidado(UUID idFactura) {
        Long invalidos = em.createNamedQuery("Pago.detallesInvalidosFactura", Long.class)
                .setParameter("idFactura", idFactura).getSingleResult();
        if (invalidos != null && invalidos > 0) {
            throw new IllegalStateException("La factura contiene precios inválidos");
        }
        BigDecimal total = em.createNamedQuery("Pago.totalFactura", BigDecimal.class)
                .setParameter("idFactura", idFactura).getSingleResult();
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException(
                    "La factura no tiene productos con un total positivo");
        }
        return total.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal consultarAbonado(UUID idFactura) {
        BigDecimal valor = em.createNamedQuery(
                "PagoDetalle.totalAbonadoFactura", BigDecimal.class)
                .setParameter("idFactura", idFactura).getSingleResult();
        if (valor == null) return new BigDecimal("0.00");
        if (valor.signum() < 0) {
            throw new IllegalStateException("Los abonos de la factura son inválidos");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void validarId(UUID idFactura) {
        if (idFactura == null) {
            throw new IllegalArgumentException("Debe seleccionar una factura");
        }
    }

    private static BigDecimal validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El abono debe ser mayor que cero");
        }
        try {
            return monto.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "El monto admite como máximo dos decimales", e);
        }
    }

    private static String normalizarMetodo(String tipoPago) {
        if (tipoPago == null || tipoPago.isBlank()) {
            throw new IllegalArgumentException("Debe seleccionar un método de pago");
        }
        String metodo = tipoPago.trim().toUpperCase(Locale.ROOT);
        if (!METODOS.contains(metodo)) {
            throw new IllegalArgumentException("El método de pago no es válido");
        }
        return metodo;
    }

    private static String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return texto.trim();
    }

    /** Resumen monetario inmutable para el modelo de caja. */
    public static final class ResumenPago implements Serializable {
        private static final long serialVersionUID = 1L;
        private final BigDecimal total;
        private final BigDecimal abonado;
        private final BigDecimal saldo;
        private final boolean completado;

        public ResumenPago(BigDecimal total, BigDecimal abonado,
                BigDecimal saldo, boolean completado) {
            this.total = total;
            this.abonado = abonado;
            this.saldo = saldo;
            this.completado = completado;
        }

        public BigDecimal getTotal() { return total; }
        public BigDecimal getAbonado() { return abonado; }
        public BigDecimal getSaldo() { return saldo; }
        public boolean isCompletado() { return completado; }
    }
}
