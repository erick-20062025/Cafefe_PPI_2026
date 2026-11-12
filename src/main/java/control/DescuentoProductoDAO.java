package control;

import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import validation.DescuentoProductoValidator;

/**
 * Persistencia de asociaciones entre productos y descuentos. La asociación es
 * única por combinación producto-descuento. Las validaciones de este DAO se
 * aplican aunque la llamada no provenga de JSF.
 */
@Stateless
public class DescuentoProductoDAO extends DefaultDAO<DescuentoProducto> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public DescuentoProductoDAO() {
        super(DescuentoProducto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Lista los productos asociados a un descuento (compatibilidad).
     */
    public List<DescuentoProducto> findByIdDescuento(UUID idDescuento) {
        exigirId(idDescuento, "descuento");
        return em.createNamedQuery("DescuentoProducto.findByIdDescuento", DescuentoProducto.class)
                .setParameter("idDescuento", idDescuento)
                .getResultList();
    }

    /**
     * Lista los descuentos de un producto guardado.
     */
    public List<DescuentoProducto> findByIdProducto(UUID idProducto) {
        exigirId(idProducto, "producto");
        return em.createNamedQuery("DescuentoProducto.findByIdProducto", DescuentoProducto.class)
                .setParameter("idProducto", idProducto)
                .getResultList();
    }

    /**
     * Verifica si ya existe esta combinación, incluyendo las asociaciones
     * antiguas.
     */
    public boolean existeRelacion(UUID idProducto, UUID idDescuento) {
        exigirId(idProducto, "producto");
        exigirId(idDescuento, "descuento");
        Long cantidad = em.createNamedQuery(
                "DescuentoProducto.countByProductoAndDescuento", Long.class)
                .setParameter("idProducto", idProducto)
                .setParameter("idDescuento", idDescuento)
                .getSingleResult();
        return cantidad != null && cantidad > 0;
    }

    /**
     * Crea una asociación válida sin repetir producto y descuento. La
     * transacción la administra el contenedor EJB.
     */
    @Override
    public void crear(DescuentoProducto relacion) {
        DescuentoProductoValidator.validar(relacion);
        UUID idProducto = relacion.getIdProducto().getIdProducto();
        UUID idDescuento = relacion.getIdDescuento().getIdDescuento();

        // Consultar primero el producto evita persistir referencias inexistentes.
        Producto producto = em.find(Producto.class, idProducto);
        if (producto == null) {
            throw new IllegalArgumentException("El producto seleccionado no existe");
        }
        Descuento descuento = em.find(Descuento.class, idDescuento);
        if (descuento == null) {
            throw new IllegalArgumentException("El descuento seleccionado no existe");
        }
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new IllegalArgumentException("No se pueden asociar descuentos a un producto inactivo");
        }

        validarVigencia(relacion, descuento);

        if (descuento.getIdTipoDescuento() == null) {
            throw new IllegalArgumentException("El descuento no tiene un tipo válido");
        }
        TipoDescuento tipo = em.find(TipoDescuento.class, descuento.getIdTipoDescuento());
        Integer maximo = tipo != null ? tipo.getDescuentoMaximo() : null;
        if (maximo == null) {
            throw new IllegalArgumentException("No se pudo determinar el límite del tipo de descuento");
        }
        if (relacion.getValor() > maximo) {
            throw new IllegalArgumentException(
                    "El descuento aplicado no puede superar " + maximo + " %");
        }

        if (em.find(DescuentoProducto.class, relacion.getIdDescuentoProducto()) != null) {
            throw new IllegalArgumentException("La asociación ya existe");
        }
        if (existeRelacion(idProducto, idDescuento)) {
            throw new IllegalArgumentException("Este descuento ya está asociado al producto");
        }

        relacion.setIdProducto(producto);
        relacion.setIdDescuento(descuento);
        em.persist(relacion);
        em.flush();
    }

    /**
     * Elimina exclusivamente la asociación, no el producto ni el descuento.
     */
    @Override
    public void eliminar(DescuentoProducto relacion) {
        if (relacion == null || relacion.getIdDescuentoProducto() == null) {
            throw new IllegalArgumentException("Debe seleccionar una asociación válida");
        }
        DescuentoProducto actual = em.find(DescuentoProducto.class,
                relacion.getIdDescuentoProducto());
        if (actual == null) {
            throw new IllegalArgumentException("La asociación ya no existe");
        }
        em.remove(actual);
        em.flush();
    }

    private static void exigirId(UUID id, String concepto) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador de " + concepto + " es obligatorio");
        }
    }

    private static void validarVigencia(DescuentoProducto r, Descuento descuento) {
        Date desde = r.getFechaDesde();
        Date hasta = r.getFechaHasta();
        Date inicio = descuento.getFechaDesde();
        Date fin = descuento.getFechaHasta();
        if (inicio == null) {
            throw new IllegalArgumentException("El descuento no tiene fecha inicial");
        }
        if (dia(desde).before(dia(inicio))) {
            throw new IllegalArgumentException("La fecha desde no puede ser anterior a la fecha inicial del descuento");
        }
        if (hasta != null && dia(hasta).before(dia(desde))) {
            throw new IllegalArgumentException("La fecha hasta no puede ser anterior a la fecha desde");
        }
        if (fin != null) {
            if (hasta == null) {
                throw new IllegalArgumentException("Debe indicar la fecha hasta para este descuento");
            }
            if (dia(desde).after(dia(fin)) || dia(hasta).after(dia(fin))) {
                throw new IllegalArgumentException("Las fechas están fuera de la vigencia del descuento");
            }
        }
    }

    private static Date dia(Date fecha) {
        Calendar calendario = Calendar.getInstance();
        calendario.setTime(fecha);
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTime();
    }
}
