package control;

import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Stateless
public class OrdenDAO extends DefaultDAO<Orden> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public OrdenDAO() {
        super(Orden.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public List<EmpleadoRol> findEmpleadosRolActivos() {
        return em.createQuery(
                "SELECT er FROM EmpleadoRol er "
                + "WHERE er.activo = true",
                EmpleadoRol.class)
                .getResultList();
    }

    public EmpleadoRol findEmpleadoRolById(UUID idEmpleadoRol) {
        return em.find(EmpleadoRol.class, idEmpleadoRol);
    }

    public void crearOrdenCompleta(
            Orden orden,
            List<OrdenProducto> productos) {

        em.persist(orden);

        for (OrdenProducto ordenProducto : productos) {

            ordenProducto.setIdOrden(orden);

            em.persist(ordenProducto);
        }
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
}
