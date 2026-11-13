/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.TipoProducto;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import validation.TipoProductoValidator;

/**
 *
 * @author diegoli
 */
@Stateless
public class TipoProductoDAO extends DefaultDAO<TipoProducto> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public TipoProductoDAO() {
        super(TipoProducto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    public void crear(TipoProducto reg) {
        TipoProductoValidator.validar(reg);
        super.crear(reg);
    }

    @Override
    public void modificar(TipoProducto reg) {
        TipoProductoValidator.validar(reg);
        super.modificar(reg);
    }


    /** Consulta la base, sin depender de colecciones JPA desactualizadas. */
    public long countRelaciones(UUID id) {
        if (id == null) throw new IllegalArgumentException("El identificador del tipo de producto es obligatorio");
        return em.createQuery("SELECT COUNT(r) FROM ProductoTipoProducto r "
                + "WHERE r.idTipoProducto.idTipoProducto = :id", Long.class)
                .setParameter("id", id).getSingleResult();
    }

    /**
     * Transaccion REQUIRED del EJB, compartida por REST y JSF.
     * Bloquea el tipo y repite la condicion en el DELETE sin ejecutar cascadas.
     * Las FK RESTRICT mantienen la integridad ante inserciones concurrentes.
     */
    @Override
    public void eliminar(TipoProducto reg) {
        if (reg == null || reg.getIdTipoProducto() == null) {
            throw new IllegalArgumentException("El identificador del tipo de producto es obligatorio");
        }
        UUID id = reg.getIdTipoProducto();
        TipoProducto existente = em.find(TipoProducto.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (existente == null) throw new IllegalArgumentException("El tipo de producto ya no existe");
        if (countRelaciones(id) > 0) throw new IllegalArgumentException(
                "No se puede eliminar un tipo de producto que tiene productos asociados");
        em.flush();
        int eliminados = em.createQuery("DELETE FROM TipoProducto t WHERE t.idTipoProducto = :id"
                + " AND NOT EXISTS (SELECT r FROM ProductoTipoProducto r"
                + " WHERE r.idTipoProducto.idTipoProducto = :id)")
                .setParameter("id", id).executeUpdate();
        if (eliminados != 1) throw new IllegalArgumentException(
                "No se puede eliminar un tipo de producto que tiene productos asociados");
        em.detach(existente);
    }
}
