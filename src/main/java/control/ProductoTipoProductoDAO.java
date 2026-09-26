/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.ProductoTipoProducto;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author erick
 */
@Stateless
public class ProductoTipoProductoDAO extends DefaultDAO<ProductoTipoProducto> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public ProductoTipoProductoDAO() {
        super(ProductoTipoProducto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public List<ProductoTipoProducto> findByIdProducto(UUID idProducto) {

        return em.createQuery(
                "SELECT p "
                + "FROM ProductoTipoProducto p "
                + "WHERE p.producto.idProducto = :idProducto",
                ProductoTipoProducto.class
        )
                .setParameter("idProducto", idProducto)
                .getResultList();
    }

    public long countByIdProducto(UUID idProducto) {

        return em.createQuery(
                "SELECT COUNT(p) "
                + "FROM ProductoTipoProducto p "
                + "WHERE p.producto.idProducto = :idProducto",
                Long.class
        )
                .setParameter("idProducto", idProducto)
                .getSingleResult();
    }

    public boolean existeRelacion(
            UUID idProducto,
            UUID idTipoProducto) {

        Long cantidad = em.createQuery(
                "SELECT COUNT(p) "
                + "FROM ProductoTipoProducto p "
                + "WHERE p.producto.idProducto = :idProducto "
                + "AND p.idTipoProducto.idTipoProducto = :idTipoProducto",
                Long.class
        )
                .setParameter("idProducto", idProducto)
                .setParameter("idTipoProducto", idTipoProducto)
                .getSingleResult();

        return cantidad > 0;
    }
}
