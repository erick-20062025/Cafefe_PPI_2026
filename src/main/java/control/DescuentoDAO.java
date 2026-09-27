package control;

import entity.Descuento;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
}