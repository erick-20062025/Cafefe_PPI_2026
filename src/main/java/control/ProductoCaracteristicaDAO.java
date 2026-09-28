/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.ProductoCaracteristica;
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
public class ProductoCaracteristicaDAO extends DefaultDAO<ProductoCaracteristica> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public ProductoCaracteristicaDAO() {
        super(ProductoCaracteristica.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /*
     * Obtiene todas las características
     * asociadas a un Producto.
     */
    public List<ProductoCaracteristica> findByIdProducto(
            UUID idProducto) {

        return em.createQuery(
                "SELECT pc "
                + "FROM ProductoCaracteristica pc "
                + "WHERE pc.idProducto.idProducto = :idProducto",
                ProductoCaracteristica.class
        )
        .setParameter("idProducto", idProducto)
        .getResultList();
    }

    /*
     * Comprueba si una Caracteristica ya está
     * asociada al Producto.
     */
    public boolean existeRelacion(
            UUID idProducto,
            UUID idCaracteristica) {

        Long cantidad = em.createQuery(
                "SELECT COUNT(pc) "
                + "FROM ProductoCaracteristica pc "
                + "WHERE pc.idProducto.idProducto = :idProducto "
                + "AND pc.idCaracteristica.idCaracteristica = :idCaracteristica",
                Long.class
        )
        .setParameter("idProducto", idProducto)
        .setParameter("idCaracteristica", idCaracteristica)
        .getSingleResult();

        return cantidad > 0;
    }
}
