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
import java.util.HashMap;
import java.util.Map;
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

    public static class DatosInvalidos extends IllegalArgumentException {
        public DatosInvalidos(String mensaje) { super(mensaje); }
    }

    public static class NoEncontrada extends IllegalArgumentException {
        public NoEncontrada() { super("La factura no existe"); }
    }

    public static class Conflicto extends IllegalStateException {
        public Conflicto(String mensaje) { super(mensaje); }
    }

    public void crearFacturaCompleta(Factura factura, List<FacturaOrdenProducto> productos) {
        if (factura == null || factura.getIdFactura() == null) {
            throw new DatosInvalidos("Identificador de factura obligatorio");
        }
        if (factura.getCliente() != null && factura.getCliente().length() > 155) {
            throw new DatosInvalidos("El cliente supera 155 caracteres");
        }
        validarCabecera(factura);
        validarDetalles(factura.getIdFactura(), productos);
        em.persist(factura);
        for (FacturaOrdenProducto detalle : productos) {
            persistirDetalle(factura, detalle);
        }
        em.flush();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void modificarFacturaCompleta(Factura cambios, List<FacturaOrdenProducto> productos) {
        Factura factura = bloquearFactura(cambios);
        exigirEditable(factura);
        validarCabecera(cambios);
        validarDetalles(factura.getIdFactura(), productos);
        actualizarDetalles(factura, productos);
        factura.setIdCaja(cambios.getIdCaja());
        factura.setIdEmpleadoRol(cambios.getIdEmpleadoRol());
        factura.setObservaciones(cambios.getObservaciones());
        em.flush();
    }

    // JSF guarda inmediatamente al agregar un detalle a una factura existente.
    public void agregarDetalle(FacturaOrdenProducto detalle) {
        if (detalle == null) {
            throw new DatosInvalidos("Detalle de factura obligatorio");
        }
        Factura factura = bloquearFactura(detalle.getIdFactura());
        exigirEditable(factura);
        List<FacturaOrdenProducto> productos = new ArrayList<>(detalles(factura.getIdFactura()));
        productos.add(detalle);
        validarDetalles(factura.getIdFactura(), productos);
        persistirDetalle(factura, detalle);
        em.flush();
    }

    private Factura bloquearFactura(Factura referencia) {
        if (referencia == null || referencia.getIdFactura() == null) {
            throw new DatosInvalidos("Debe seleccionar una factura");
        }
        Factura factura = em.find(Factura.class, referencia.getIdFactura(),
                LockModeType.PESSIMISTIC_WRITE);
        if (factura == null) {
            throw new NoEncontrada();
        }
        return factura;
    }

    private void exigirPendiente(Factura factura) {
        if (!"PENDIENTE".equalsIgnoreCase(factura.getEstado())) {
            throw new Conflicto("La factura debe estar pendiente");
        }
    }

    private void exigirEditable(Factura factura) {
        exigirPendiente(factura);
        BigDecimal abonado = em.createNamedQuery("PagoDetalle.totalAbonadoFactura", BigDecimal.class)
                .setParameter("idFactura", factura.getIdFactura()).getSingleResult();
        if (abonado != null && abonado.signum() != 0) {
            throw new Conflicto("No se puede modificar una factura con abonos registrados");
        }
    }

    private void validarCabecera(Factura factura) {
        UUID idCaja = factura.getIdCaja() == null ? null : factura.getIdCaja().getIdCaja();
        UUID idEmpleado = factura.getIdEmpleadoRol() == null
                ? null : factura.getIdEmpleadoRol().getIdEmpleadoRol();
        Caja caja = idCaja == null ? null : findCajaById(idCaja);
        EmpleadoRol empleado = idEmpleado == null ? null : findEmpleadoRolById(idEmpleado);
        if (caja == null || !Boolean.TRUE.equals(caja.getActivo())) {
            throw new DatosInvalidos("Debe seleccionar una caja existente y activa");
        }
        if (empleado == null || !Boolean.TRUE.equals(empleado.getActivo())) {
            throw new DatosInvalidos("Debe seleccionar una asignación de empleado existente y activa");
        }
        factura.setIdCaja(caja);
        factura.setIdEmpleadoRol(empleado);
    }

    private void validarDetalles(UUID idFactura, List<FacturaOrdenProducto> productos) {
        if (productos == null || productos.isEmpty()) {
            throw new DatosInvalidos("La factura debe contener al menos un producto");
        }
        Map<UUID, FacturaOrdenProducto> porProducto = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FacturaOrdenProducto detalle : productos) {
            if (detalle == null || detalle.getIdOrdenProducto() == null
                    || detalle.getIdOrdenProducto().getIdOrdenProducto() == null) {
                throw new DatosInvalidos("La factura contiene un producto inválido");
            }
            if (porProducto.putIfAbsent(detalle.getIdOrdenProducto().getIdOrdenProducto(), detalle) != null) {
                throw new DatosInvalidos("No se permiten productos duplicados");
            }
            BigDecimal precio = detalle.getPrecio();
            if (precio == null || precio.signum() < 0 || precio.scale() > 2) {
                throw new DatosInvalidos("El precio debe ser no negativo y tener hasta dos decimales");
            }
            total = total.add(precio);
        }
        if (total.signum() <= 0) {
            throw new DatosInvalidos("El total de la factura debe ser positivo");
        }

        // Bloquear en orden estable y consultar ocupación después de tomar cada bloqueo.
        UUID idOrden = null;
        for (UUID id : porProducto.keySet().stream().sorted().toList()) {
            OrdenProducto producto = em.find(OrdenProducto.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (producto == null || producto.getIdOrden() == null
                    || producto.getIdOrden().getIdOrden() == null) {
                throw new DatosInvalidos("El producto de la orden no existe");
            }
            UUID orden = producto.getIdOrden().getIdOrden();
            if (idOrden != null && !idOrden.equals(orden)) {
                throw new DatosInvalidos("No se pueden mezclar productos de diferentes órdenes");
            }
            idOrden = orden;
            Long ocupados = em.createQuery(
                    "SELECT COUNT(f) FROM FacturaOrdenProducto f "
                    + "WHERE f.idOrdenProducto.idOrdenProducto = :id "
                    + "AND f.idFactura.idFactura <> :idFactura", Long.class)
                    .setParameter("id", id).setParameter("idFactura", idFactura).getSingleResult();
            if (ocupados > 0) {
                throw new Conflicto("El producto de la orden ya pertenece a otra factura");
            }
            porProducto.get(id).setIdOrdenProducto(producto);
        }
    }

    private List<FacturaOrdenProducto> detalles(UUID idFactura) {
        return em.createQuery("SELECT fop FROM FacturaOrdenProducto fop "
                + "WHERE fop.idFactura.idFactura = :idFactura", FacturaOrdenProducto.class)
                .setParameter("idFactura", idFactura).getResultList();
    }

    private void actualizarDetalles(Factura factura, List<FacturaOrdenProducto> productos) {
        List<FacturaOrdenProducto> anteriores = detalles(factura.getIdFactura());
        Map<UUID, FacturaOrdenProducto> porId = new HashMap<>();
        Map<UUID, FacturaOrdenProducto> porProducto = new HashMap<>();
        for (FacturaOrdenProducto detalle : anteriores) {
            porId.put(detalle.getIdFacturaOrdenProducto(), detalle);
            if (detalle.getIdOrdenProducto() != null) {
                porProducto.putIfAbsent(detalle.getIdOrdenProducto().getIdOrdenProducto(), detalle);
            }
        }
        Set<UUID> conservar = new HashSet<>();
        List<FacturaOrdenProducto> nuevos = new ArrayList<>();
        Map<UUID, FacturaOrdenProducto> cambios = new HashMap<>();
        for (FacturaOrdenProducto detalle : productos) {
            UUID idDetalle = detalle.getIdFacturaOrdenProducto();
            FacturaOrdenProducto anterior = idDetalle == null
                    ? porProducto.get(detalle.getIdOrdenProducto().getIdOrdenProducto()) : porId.get(idDetalle);
            if (anterior == null) {
                if (idDetalle != null && em.find(FacturaOrdenProducto.class, idDetalle) != null) {
                    throw new DatosInvalidos("El detalle pertenece a otra factura");
                }
                nuevos.add(detalle);
            } else {
                UUID id = anterior.getIdFacturaOrdenProducto();
                if (!conservar.add(id)) {
                    throw new DatosInvalidos("No se permiten detalles duplicados");
                }
                cambios.put(id, detalle);
            }
        }
        for (FacturaOrdenProducto anterior : anteriores) {
            FacturaOrdenProducto cambio = cambios.get(anterior.getIdFacturaOrdenProducto());
            if (cambio == null) {
                em.remove(anterior);
            } else {
                anterior.setIdOrdenProducto(cambio.getIdOrdenProducto());
                anterior.setPrecio(cambio.getPrecio());
                anterior.setObservaciones(cambio.getObservaciones());
            }
        }
        for (FacturaOrdenProducto detalle : nuevos) {
            persistirDetalle(factura, detalle);
        }
    }

    private void persistirDetalle(Factura factura, FacturaOrdenProducto detalle) {
        if (detalle.getIdFacturaOrdenProducto() == null) {
            detalle.setIdFacturaOrdenProducto(UUID.randomUUID());
        }
        detalle.setIdFactura(factura);
        em.persist(detalle);
    }

    public void eliminarFacturaCompleta(Factura referencia) {
        Factura factura = bloquearFactura(referencia);
        exigirPendiente(factura);
        UUID id = factura.getIdFactura();
        if (!em.createNamedQuery("Pago.findByFactura", entity.Pago.class)
                .setParameter("idFactura", id).setMaxResults(1).getResultList().isEmpty()) {
            throw new Conflicto("No se puede eliminar una factura con pagos registrados");
        }
        for (FacturaOrdenProducto detalle : detalles(id)) {
            em.remove(detalle);
        }
        em.remove(factura);
        em.flush();
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
