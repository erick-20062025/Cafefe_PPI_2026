package control;

import entity.Empleado;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Stateless
public class EmpleadoDAO extends DefaultDAO<Empleado> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public EmpleadoDAO() {
        super(Empleado.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }
}