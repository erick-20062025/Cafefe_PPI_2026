package control;

import entity.DescuentoProducto;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Stateless
public class DescuentoProductoDAO extends DefaultDAO<DescuentoProducto> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public DescuentoProductoDAO() {
        super(DescuentoProducto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Obtiene todos los productos asociados
     * a un descuento específico.
     */
    public List<DescuentoProducto> findByIdDescuento(UUID idDescuento) {

        return em.createQuery(
                "SELECT dp FROM DescuentoProducto dp "
                + "WHERE dp.idDescuento.idDescuento = :idDescuento",
                DescuentoProducto.class
        )
                .setParameter("idDescuento", idDescuento)
                .getResultList();
    }
}
