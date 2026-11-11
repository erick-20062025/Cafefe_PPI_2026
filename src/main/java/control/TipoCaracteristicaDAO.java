package control;

import entity.TipoCaracteristica;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import validation.TipoCaracteristicaValidator;

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

    @Override
    public void crear(TipoCaracteristica reg) {
        TipoCaracteristicaValidator.validar(reg);
        super.crear(reg);
    }

    @Override
    public void modificar(TipoCaracteristica reg) {
        TipoCaracteristicaValidator.validar(reg);
        super.modificar(reg);
    }

}
