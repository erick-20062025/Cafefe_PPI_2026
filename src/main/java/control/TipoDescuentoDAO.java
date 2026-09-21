package control;

import entity.TipoDescuento;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class TipoDescuentoDAO extends DefaultDAO<TipoDescuento> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public TipoDescuentoDAO() {
        super(TipoDescuento.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
}