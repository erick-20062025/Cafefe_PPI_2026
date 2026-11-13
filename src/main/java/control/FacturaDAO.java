package control;

import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import entity.FacturaOrdenProducto;
import entity.OrdenProducto;
import java.math.BigDecimal;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.LockModeType;
import java.util.HashSet;
import java.util.Set;

@Stateless

public class FacturaDAO extends DefaultDAO<Factura> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public FacturaDAO() {
        super(Factura.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public List<Caja> findCajasActivas() {

        return em.createQuery(
                "SELECT c FROM Caja c "
                + "WHERE c.activo = true",
                Caja.class)
                .getResultList();
    }

    public Caja findCajaById(UUID idCaja) {
        return em.find(Caja.class, idCaja);
    }

    public boolean puedeModificarFactura(UUID idFactura) {

        if (idFactura == null) {
            return false;
        }

        Factura factura = em.find(Factura.class, idFactura);

        if (factura == null
                || !"PENDIENTE".equalsIgnoreCase(factura.getEstado())) {
            return false;
        }

        BigDecimal abonado = em.createNamedQuery(
                "PagoDetalle.totalAbonadoFactura",
                BigDecimal.class)
                .setParameter("idFactura", idFactura)
                .getSingleResult();

        return abonado == null
                || abonado.compareTo(BigDecimal.ZERO) == 0;
    }

    public void crearFacturaCompleta(
            Factura factura,
            List<FacturaOrdenProducto> productos) {

        em.persist(factura);

        for (FacturaOrdenProducto detalle : productos) {

            detalle.setIdFactura(factura);

            em.persist(detalle);
        }

    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void modificarFacturaCompleta(
            Factura cambios,
            List<FacturaOrdenProducto> productos) {

        if (cambios == null || cambios.getIdFactura() == null) {
            throw new IllegalArgumentException("Debe seleccionar una factura");
        }

        UUID idFactura = cambios.getIdFactura();

        // Mismo bloqueo de fila utilizado al registrar abonos.
        Factura factura = em.find(
                Factura.class,
                idFactura,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (factura == null) {
            throw new IllegalArgumentException("La factura no existe");
        }

        if (!"PENDIENTE".equalsIgnoreCase(factura.getEstado())) {
            throw new IllegalStateException(
                    "Solo se pueden modificar facturas pendientes");
        }

        BigDecimal abonado = em.createNamedQuery(
                "PagoDetalle.totalAbonadoFactura",
                BigDecimal.class)
                .setParameter("idFactura", idFactura)
                .getSingleResult();

        if (abonado != null && abonado.signum() != 0) {
            throw new IllegalStateException(
                    "No se puede modificar una factura con abonos registrados");
        }

        if (cambios.getIdCaja() == null
                || cambios.getIdEmpleadoRol() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar caja y empleado");
        }

        if (productos == null || productos.isEmpty()) {
            throw new IllegalArgumentException(
                    "La factura debe contener al menos un producto");
        }

        Set<UUID> idsProductos = new HashSet<>();
        UUID idOrden = null;

        // Validar todos los productos antes de modificar la base de datos.
        for (FacturaOrdenProducto detalle : productos) {

            if (detalle == null
                    || detalle.getIdOrdenProducto() == null
                    || detalle.getIdOrdenProducto().getIdOrdenProducto() == null) {
                throw new IllegalArgumentException(
                        "La factura contiene un producto inválido");
            }

            UUID idProducto = detalle.getIdOrdenProducto()
                    .getIdOrdenProducto();

            if (!idsProductos.add(idProducto)) {
                throw new IllegalArgumentException(
                        "No se permiten productos duplicados");
            }

            OrdenProducto producto = em.find(
                    OrdenProducto.class, idProducto);

            if (producto == null || producto.getIdOrden() == null) {
                throw new IllegalArgumentException(
                        "El producto de la orden no existe");
            }

            UUID ordenProducto = producto.getIdOrden().getIdOrden();

            if (idOrden == null) {
                idOrden = ordenProducto;
            } else if (!idOrden.equals(ordenProducto)) {
                throw new IllegalArgumentException(
                        "No se pueden mezclar productos de diferentes órdenes");
            }

            if (detalle.getPrecio() == null
                    || detalle.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Hay productos con precio inválido");
            }
        }

        // Actualizar la cabecera, sin permitir cambiar su estado.
        factura.setIdCaja(cambios.getIdCaja());
        factura.setIdEmpleadoRol(cambios.getIdEmpleadoRol());
        factura.setObservaciones(cambios.getObservaciones());

        List<FacturaOrdenProducto> anteriores = em.createQuery(
                "SELECT fop FROM FacturaOrdenProducto fop "
                + "WHERE fop.idFactura.idFactura = :idFactura",
                FacturaOrdenProducto.class)
                .setParameter("idFactura", idFactura)
                .getResultList();

        // Conservar los detalles existentes que siguen en la factura.
        Set<UUID> idsDetallesNuevos = new HashSet<>();

        for (FacturaOrdenProducto detalle : productos) {
            if (detalle.getIdFacturaOrdenProducto() != null) {
                idsDetallesNuevos.add(
                        detalle.getIdFacturaOrdenProducto());
            }
        }

        for (FacturaOrdenProducto anterior : anteriores) {
            if (!idsDetallesNuevos.contains(
                    anterior.getIdFacturaOrdenProducto())) {
                em.remove(anterior);
            }
        }

        for (FacturaOrdenProducto detalle : productos) {

            UUID idDetalle = detalle.getIdFacturaOrdenProducto();

            if (idDetalle == null) {
                idDetalle = UUID.randomUUID();
            }

            FacturaOrdenProducto existente = em.find(
                    FacturaOrdenProducto.class, idDetalle);

            OrdenProducto producto = em.find(
                    OrdenProducto.class,
                    detalle.getIdOrdenProducto().getIdOrdenProducto());

            if (existente == null) {

                FacturaOrdenProducto nuevoDetalle
                        = new FacturaOrdenProducto(idDetalle);

                nuevoDetalle.setIdFactura(factura);
                nuevoDetalle.setIdOrdenProducto(producto);
                nuevoDetalle.setPrecio(detalle.getPrecio());
                nuevoDetalle.setObservaciones(
                        detalle.getObservaciones());

                em.persist(nuevoDetalle);

            } else {

                if (existente.getIdFactura() == null
                        || !idFactura.equals(
                                existente.getIdFactura().getIdFactura())) {
                    throw new IllegalArgumentException(
                            "El detalle pertenece a otra factura");
                }

                existente.setIdOrdenProducto(producto);
                existente.setPrecio(detalle.getPrecio());
                existente.setObservaciones(
                        detalle.getObservaciones());
            }
        }
    }

    public void eliminarFacturaCompleta(Factura factura) {

        em.createQuery(
                "DELETE FROM FacturaOrdenProducto fop "
                + "WHERE fop.idFactura.idFactura = :idFactura"
        )
                .setParameter(
                        "idFactura",
                        factura.getIdFactura()
                )
                .executeUpdate();

        Factura facturaManaged
                = em.find(
                        Factura.class,
                        factura.getIdFactura()
                );

        if (facturaManaged != null) {
            em.remove(facturaManaged);
        }
    }

    public List<EmpleadoRol> findEmpleadosRolActivos() {

        return em.createQuery(
                "SELECT er FROM EmpleadoRol er "
                + "WHERE er.activo = true",
                EmpleadoRol.class)
                .getResultList();
    }

    public EmpleadoRol findEmpleadoRolById(UUID idEmpleadoRol) {
        return em.find(EmpleadoRol.class, idEmpleadoRol);
    }
}
