package control;

import entity.Descuento;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Stateless
public class DescuentoDAO extends DefaultDAO<Descuento> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public DescuentoDAO() {
        super(Descuento.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Busca los descuentos asociados a un tipo de descuento.
     *
     * @param idTipoDescuento identificador del tipo de descuento
     * @return lista de descuentos asociados
     */
    public List<Descuento> findByTipoDescuento(UUID idTipoDescuento) {

        return em.createQuery(
                "SELECT d FROM Descuento d "
                + "WHERE d.idTipoDescuento = :idTipoDescuento",
                Descuento.class
        )
                .setParameter("idTipoDescuento", idTipoDescuento)
                .getResultList();
    }
}