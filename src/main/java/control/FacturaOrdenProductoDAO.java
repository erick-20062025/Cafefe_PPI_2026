package control;

import entity.FacturaOrdenProducto;
import entity.OrdenProducto;
import jakarta.ejb.Stateless;
import jakarta.ejb.EJB;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Stateless
public class FacturaOrdenProductoDAO
        extends DefaultDAO<FacturaOrdenProducto> {

    @EJB
    private FacturaDAO facturaDAO;

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public FacturaOrdenProductoDAO() {
        super(FacturaOrdenProducto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public List<FacturaOrdenProducto> findByIdFactura(UUID idFactura) {

        return em.createQuery(
                "SELECT fop "
                + "FROM FacturaOrdenProducto fop "
                + "JOIN FETCH fop.idOrdenProducto op "
                + "JOIN FETCH op.idProducto "
                + "WHERE fop.idFactura.idFactura = :idFactura",
                FacturaOrdenProducto.class)
                .setParameter("idFactura", idFactura)
                .getResultList();
    }

    public List<OrdenProducto> findProductosByOrden(UUID idOrden) {

        return em.createQuery(
                "SELECT op "
                + "FROM OrdenProducto op "
                + "JOIN FETCH op.idProducto "
                + "WHERE op.idOrden.idOrden = :idOrden",
                OrdenProducto.class)
                .setParameter("idOrden", idOrden)
                .getResultList();
    }

    public void agregarProductoAFactura(
            FacturaOrdenProducto detalle) {

        facturaDAO.agregarDetalle(detalle);
    }

    public boolean existeRelacion(
            UUID idFactura,
            UUID idOrdenProducto) {

        Long cantidad = em.createQuery(
                "SELECT COUNT(fop) "
                + "FROM FacturaOrdenProducto fop "
                + "WHERE fop.idFactura.idFactura = :idFactura "
                + "AND fop.idOrdenProducto.idOrdenProducto = :idOrdenProducto",
                Long.class)
                .setParameter("idFactura", idFactura)
                .setParameter("idOrdenProducto", idOrdenProducto)
                .getSingleResult();

        return cantidad > 0;
    }
}
