package control;

import entity.TipoCaracteristica;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class TipoCaracteristicaDAO extends DefaultDAO<TipoCaracteristica> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public TipoCaracteristicaDAO() {
        super(TipoCaracteristica.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
}