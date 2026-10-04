package control;

import entity.DescuentoProducto;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
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

    /*
     * =========================================================
     * EMPLEADOS QUE PUEDEN TOMAR ÓRDENES
     * =========================================================
     */

    /**
     * Obtiene únicamente empleados que pueden tomar órdenes:
     *
     * - Empleado activo.
     * - Relación EmpleadoRol activa.
     * - Rol activo.
     * - Rol llamado "camarero".
     */
    public List<EmpleadoRol> findCamarerosActivos() {

        return em.createQuery(
                "SELECT er "
                + "FROM EmpleadoRol er "
                + "JOIN FETCH er.idEmpleado e "
                + "JOIN FETCH er.idRol r "
                + "WHERE LOWER(TRIM(r.nombre)) = :nombreRol "
                + "ORDER BY e.nombre, e.apellido",
                EmpleadoRol.class
        )
                .setParameter(
                        "nombreRol",
                        "camarero"
                )
                .getResultList();
    }

    /**
     * Método temporal de compatibilidad con el OrdenModel
     * que ya existía.
     *
     * Aunque conserve el nombre anterior, ahora devuelve
     * únicamente empleados con rol camarero.
     */
    public List<EmpleadoRol> findEmpleadosRolActivos() {

        return findCamarerosActivos();
    }

    /**
     * Busca una relación EmpleadoRol específica solamente
     * si todavía cumple las condiciones para tomar órdenes.
     */
    public EmpleadoRol findCamareroActivoById(
            UUID idEmpleadoRol) {

        List<EmpleadoRol> resultado
                = em.createQuery(
                        "SELECT er "
                        + "FROM EmpleadoRol er "
                        + "JOIN FETCH er.idEmpleado e "
                        + "JOIN FETCH er.idRol r "
                        + "WHERE er.idEmpleadoRol = :idEmpleadoRol "
                        + "AND er.activo = true "
                        + "AND e.activo = true "
                        + "AND r.activo = true "
                        + "AND LOWER(TRIM(r.nombre)) = :nombreRol",
                        EmpleadoRol.class
                )
                        .setParameter(
                                "idEmpleadoRol",
                                idEmpleadoRol
                        )
                        .setParameter(
                                "nombreRol",
                                "camarero"
                        )
                        .getResultList();

        if (resultado.isEmpty()) {
            return null;
        }

        return resultado.get(0);
    }

    /**
     * Se conserva temporalmente para compatibilidad
     * con el OrdenModel actual.
     */
    public EmpleadoRol findEmpleadoRolById(
            UUID idEmpleadoRol) {

        return em.find(
                EmpleadoRol.class,
                idEmpleadoRol
        );
    }

    /*
     * =========================================================
     * PRODUCTOS DE LA ORDEN
     * =========================================================
     */

    /**
     * Agrega un producto a una orden ya guardada.
     */
    public void agregarProducto(
            OrdenProducto ordenProducto) {

        em.persist(ordenProducto);
    }

    /**
     * Actualiza un producto de la orden.
     *
     * Se utilizará, entre otras cosas,
     * cuando se aplique un descuento.
     */
    public OrdenProducto actualizarProducto(
            OrdenProducto ordenProducto) {

        return em.merge(ordenProducto);
    }

    /**
     * Elimina únicamente el producto de la orden.
     *
     * No elimina el Producto del catálogo.
     */
    public void eliminarProducto(
            OrdenProducto ordenProducto) {

        if (ordenProducto == null) {
            return;
        }

        OrdenProducto administrado
                = em.contains(ordenProducto)
                ? ordenProducto
                : em.merge(ordenProducto);

        em.remove(administrado);
    }

    /**
     * Obtiene los productos pertenecientes
     * a una orden específica.
     */
    public List<OrdenProducto> findProductosByOrden(
            UUID idOrden) {

        return em.createQuery(
                "SELECT op "
                + "FROM OrdenProducto op "
                + "JOIN FETCH op.idProducto "
                + "WHERE op.idOrden.idOrden = :idOrden",
                OrdenProducto.class
        )
                .setParameter(
                        "idOrden",
                        idOrden
                )
                .getResultList();
    }

    /**
     * Cuenta los productos asociados a una orden.
     *
     * Se utilizará para impedir eliminar una orden
     * mientras todavía tenga productos.
     */
    public long countProductosByOrden(
            UUID idOrden) {

        return em.createQuery(
                "SELECT COUNT(op) "
                + "FROM OrdenProducto op "
                + "WHERE op.idOrden.idOrden = :idOrden",
                Long.class
        )
                .setParameter(
                        "idOrden",
                        idOrden
                )
                .getSingleResult();
    }

    /*
     * =========================================================
     * DESCUENTOS APLICABLES
     * =========================================================
     */

    /**
     * Busca descuentos que:
     *
     * 1. Están asociados al producto.
     * 2. La relación DescuentoProducto es válida
     *    para la fecha de la orden.
     * 3. El Descuento principal también es válido
     *    para la fecha de la orden.
     *
     * fechaHasta == null representa un rango abierto.
     */
    public List<DescuentoProducto> findDescuentosAplicables(
            UUID idProducto,
            Date fechaOrden) {

        return em.createQuery(
                "SELECT dp "
                + "FROM DescuentoProducto dp "
                + "JOIN FETCH dp.idProducto p "
                + "JOIN FETCH dp.idDescuento d "
                + "WHERE p.idProducto = :idProducto "
                + "AND dp.fechaDesde <= :fechaOrden "
                + "AND (dp.fechaHasta IS NULL "
                + "     OR dp.fechaHasta >= :fechaOrden) "
                + "AND d.fechaDesde <= :fechaOrden "
                + "AND (d.fechaHasta IS NULL "
                + "     OR d.fechaHasta >= :fechaOrden) "
                + "ORDER BY d.nombre",
                DescuentoProducto.class
        )
                .setParameter(
                        "idProducto",
                        idProducto
                )
                .setParameter(
                        "fechaOrden",
                        fechaOrden
                )
                .getResultList();
    }

    /*
     * =========================================================
     * MÉTODO ANTERIOR
     * =========================================================
     *
     * Lo conservamos por ahora para no romper
     * el OrdenModel existente mientras hacemos
     * la siguiente modificación.
     */
    public void crearOrdenCompleta(
            Orden orden,
            List<OrdenProducto> productos) {

        em.persist(orden);

        for (OrdenProducto ordenProducto : productos) {

            ordenProducto.setIdOrden(orden);

            em.persist(ordenProducto);
        }
    }
}