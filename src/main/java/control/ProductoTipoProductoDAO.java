/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.ProductoTipoProducto;
import entity.Producto;
import entity.TipoProducto;
import jakarta.persistence.LockModeType;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import validation.ProductoTipoProductoValidator;

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

    @Override
    public void crear(ProductoTipoProducto reg) {

        // Reutiliza el validador para referencias incompletas sin consultar la BD.
        if (reg == null || reg.getProducto() == null || reg.getProducto().getIdProducto() == null
                || reg.getIdTipoProducto() == null || reg.getIdTipoProducto().getIdTipoProducto() == null) {
            ProductoTipoProductoValidator.validar(reg);
        }
        if (reg.getIdProductoTipoProducto() == null) {
            throw new IllegalArgumentException("La asociación debe tener un identificador");
        }
        UUID idProducto = reg.getProducto().getIdProducto();
        UUID idTipo = reg.getIdTipoProducto().getIdTipoProducto();
        // Orden constante: producto WRITE, tipo READ. Serializa altas del mismo producto.
        // En READ COMMITTED el conteo posterior ve la insercion de quien libero el bloqueo.
        Producto producto = em.find(Producto.class, idProducto, LockModeType.PESSIMISTIC_WRITE);
        if (producto == null) throw new IllegalArgumentException("El producto seleccionado no existe");
        TipoProducto tipo = em.find(TipoProducto.class, idTipo, LockModeType.PESSIMISTIC_READ);
        if (tipo == null) throw new IllegalArgumentException("El tipo de producto seleccionado no existe");
        // Tambien obtiene el estado actual si el padre ya estaba en el contexto JPA.
        em.refresh(tipo, LockModeType.PESSIMISTIC_READ);
        reg.setProducto(producto);
        reg.setIdTipoProducto(tipo);
        ProductoTipoProductoValidator.validar(reg);
        if (em.find(ProductoTipoProducto.class, reg.getIdProductoTipoProducto()) != null) {
            throw new IllegalArgumentException("La asociación ya existe");
        }
        if (existeRelacion(idProducto, idTipo)) {
            throw new IllegalArgumentException("Este tipo de producto ya está asignado al producto");
        }
        super.crear(reg);
        em.flush();
    }

    /** Elimina solo la asociacion administrada, sin mezclar objetos recibidos. */
    @Override
    public void eliminar(ProductoTipoProducto reg) {
        if (reg == null || reg.getIdProductoTipoProducto() == null) {
            throw new IllegalArgumentException("Debe seleccionar una asociación válida");
        }
        ProductoTipoProducto actual = em.find(ProductoTipoProducto.class,
                reg.getIdProductoTipoProducto(), LockModeType.PESSIMISTIC_WRITE);
        if (actual == null) throw new IllegalArgumentException("La asociación ya no existe");
        em.remove(actual);
        em.flush();
    }

    public List<ProductoTipoProducto> findByIdTipoProducto(UUID idTipoProducto) {
        return em.createQuery("SELECT p FROM ProductoTipoProducto p "
                + "WHERE p.idTipoProducto.idTipoProducto = :idTipoProducto", ProductoTipoProducto.class)
                .setParameter("idTipoProducto", idTipoProducto).getResultList();
    }

    @Override
    public void modificar(ProductoTipoProducto reg) {
        throw new UnsupportedOperationException(
                "La relación producto-tipo no admite modificación; "
                + "debe eliminarse y crearse nuevamente");
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
