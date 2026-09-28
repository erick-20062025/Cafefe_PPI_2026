package control;

import entity.EmpleadoRol;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Stateless
public class EmpleadoRolDAO extends DefaultDAO<EmpleadoRol> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public EmpleadoRolDAO() {
        super(EmpleadoRol.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public List<EmpleadoRol> findByIdEmpleado(UUID idEmpleado) {

        return em.createQuery(
                "SELECT e "
                + "FROM EmpleadoRol e "
                + "WHERE e.idEmpleado = :idEmpleado",
                EmpleadoRol.class
        )
                .setParameter("idEmpleado", idEmpleado)
                .getResultList();
    }

    public long countByIdEmpleado(UUID idEmpleado) {

        return em.createQuery(
                "SELECT COUNT(e) "
                + "FROM EmpleadoRol e "
                + "WHERE e.idEmpleado = :idEmpleado",
                Long.class
        )
                .setParameter("idEmpleado", idEmpleado)
                .getSingleResult();
    }

    public boolean existeRelacion(
            UUID idEmpleado,
            UUID idRol) {

        Long cantidad = em.createQuery(
                "SELECT COUNT(e) "
                + "FROM EmpleadoRol e "
                + "WHERE e.idEmpleado = :idEmpleado "
                + "AND e.idRol.idRol = :idRol",
                Long.class
        )
                .setParameter("idEmpleado", idEmpleado)
                .setParameter("idRol", idRol)
                .getSingleResult();

        return cantidad > 0;
    }
}