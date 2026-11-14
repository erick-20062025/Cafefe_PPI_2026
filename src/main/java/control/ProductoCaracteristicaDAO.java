/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.ProductoCaracteristica;
import entity.Producto;
import entity.Caracteristica;
import jakarta.persistence.LockModeType;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import validation.ProductoCaracteristicaValidator;

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

    @Override
    public void crear(ProductoCaracteristica reg) {
        // Mantener el validador existente para referencias incompletas.
        if (reg == null || reg.getIdProducto() == null || reg.getIdProducto().getIdProducto() == null
                || reg.getIdCaracteristica() == null || reg.getIdCaracteristica().getIdCaracteristica() == null) {
            ProductoCaracteristicaValidator.validar(reg);
        }
        UUID idProducto = reg.getIdProducto().getIdProducto();
        UUID idCaracteristica = reg.getIdCaracteristica().getIdCaracteristica();
        Producto producto = em.find(Producto.class, idProducto, LockModeType.PESSIMISTIC_WRITE);
        if (producto == null) throw new IllegalArgumentException("El producto no existe");
        Caracteristica caracteristica = em.find(Caracteristica.class, idCaracteristica, LockModeType.PESSIMISTIC_READ);
        if (caracteristica == null) throw new IllegalArgumentException("La característica no existe");
        reg.setIdProducto(producto);
        reg.setIdCaracteristica(caracteristica);
        ProductoCaracteristicaValidator.validar(reg);

        if (existeRelacion(idProducto, idCaracteristica)) {
            throw new IllegalArgumentException(
                    "La característica ya está asociada al producto");
        }

        super.crear(reg);
        em.flush();
    }

    @Override
    public void eliminar(ProductoCaracteristica reg) {
        ProductoCaracteristica actual = em.find(ProductoCaracteristica.class,
                reg.getIdProductoCaracteristica(), LockModeType.PESSIMISTIC_WRITE);
        if (actual == null) throw new IllegalArgumentException("La asociación no existe");
        em.remove(actual);
        em.flush();
    }

    @Override
    public void modificar(ProductoCaracteristica reg) {
        ProductoCaracteristicaValidator.validar(reg);
        super.modificar(reg);
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
