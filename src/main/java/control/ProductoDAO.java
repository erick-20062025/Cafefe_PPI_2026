/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.Producto;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import validation.ProductoValidator;

/**
 *
 * @author erick
 */
@Stateless
public class ProductoDAO extends DefaultDAO<Producto> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public ProductoDAO() {
        super(Producto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    public void crear(Producto reg) {
        ProductoValidator.validar(reg);
        super.crear(reg);
    }

    @Override
    public void modificar(Producto reg) {
        ProductoValidator.validar(reg);
        super.modificar(reg);
    }


    /** Conteo independiente de colecciones JPA potencialmente desactualizadas. */
    public long countRelaciones(UUID id) {
        if (id == null) throw new IllegalArgumentException("El identificador del producto es obligatorio");
        long total = 0;
        for (String relacion : RELACIONES) {
            total += em.createQuery("SELECT COUNT(r) FROM " + relacion + " = :id", Long.class)
                    .setParameter("id", id).getSingleResult();
        }
        return total;
    }

    private static final String[] RELACIONES = {
        "OrdenProducto r WHERE r.idProducto.idProducto",
        "DescuentoProducto r WHERE r.idProducto.idProducto",
        "ProductoCaracteristica r WHERE r.idProducto.idProducto",
        "ProductoTipoProducto r WHERE r.producto.idProducto"
    };

    /**
     * Transaccion REQUIRED del EJB: bloqueo, comprobacion y borrado atomico.
     * El DELETE JPQL no ejecuta CascadeType.REMOVE ni mezcla entidades externas.
     * Las FK de la base mantienen la integridad ante escritores concurrentes.
     */
    @Override
    public void eliminar(Producto reg) {
        if (reg == null || reg.getIdProducto() == null) {
            throw new IllegalArgumentException("El identificador del producto es obligatorio");
        }
        UUID id = reg.getIdProducto();
        Producto existente = em.find(Producto.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (existente == null) throw new IllegalArgumentException("El producto ya no existe");
        if (countRelaciones(id) > 0) throw new IllegalArgumentException(
                "No se puede eliminar un producto que tiene relaciones asociadas");
        em.flush();
        StringBuilder jpql = new StringBuilder("DELETE FROM Producto p WHERE p.idProducto = :id");
        for (String relacion : RELACIONES) {
            jpql.append(" AND NOT EXISTS (SELECT r FROM ").append(relacion).append(" = :id)");
        }
        int eliminados = em.createQuery(jpql.toString()).setParameter("id", id).executeUpdate();
        if (eliminados != 1) throw new IllegalArgumentException(
                "No se puede eliminar un producto que tiene relaciones asociadas");
        em.detach(existente);
    }

}
