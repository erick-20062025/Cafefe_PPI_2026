
package control;

import entity.Caracteristica;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import validation.CaracteristicaValidator;

@Stateless
public class CaracteristicaDAO extends DefaultDAO<Caracteristica> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public CaracteristicaDAO() {
        super(Caracteristica.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    public void crear(Caracteristica reg) {
        CaracteristicaValidator.validar(reg);
        super.crear(reg);
    }

    @Override
    public void modificar(Caracteristica reg) {
        CaracteristicaValidator.validar(reg);
        super.modificar(reg);
    }
}
