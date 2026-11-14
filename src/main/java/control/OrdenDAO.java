package control;

import entity.Descuento;
import entity.DescuentoProducto;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Persistencia y reglas transaccionales de órdenes. Todas las consultas JPQL
 * están declaradas como @NamedQuery en las entidades.
 */
@Stateless
public class OrdenDAO extends DefaultDAO<Orden> {

    private static final String NOMBRE_ROL = "camarero";
    private static final TimeZone ZONA = TimeZone.getTimeZone("America/El_Salvador");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public OrdenDAO() {
        super(Orden.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Órdenes con al menos un producto que aún no pertenece a una factura
     * PAGADA. No modifica el estado de ninguna factura ni añade columnas al
     * esquema.
     */
    public List<Orden> findPendientesPago() {
        return em.createNamedQuery("Orden.findPendientesPago", Orden.class)
                .getResultList();
    }

    /**
     * Obtiene las relaciones activas para tomar órdenes.
     */
    public List<EmpleadoRol> findCamarerosActivos() {
        return em.createNamedQuery("Orden.findCamarerosActivos", EmpleadoRol.class)
                .setParameter("nombreRol", NOMBRE_ROL)
                .getResultList();
    }

    /**
     * Alias de compatibilidad con el controlador previo.
     */
    public List<EmpleadoRol> findEmpleadosRolActivos() {
        return findCamarerosActivos();
    }

    /**
     * Comprueba que la relación, el empleado y el rol siguen activos.
     */
    public EmpleadoRol findCamareroActivoById(UUID idEmpleadoRol) {
        if (idEmpleadoRol == null) {
            return null;
        }
        List<EmpleadoRol> resultado = em.createNamedQuery(
                "Orden.findCamareroActivoById", EmpleadoRol.class)
                .setParameter("idEmpleadoRol", idEmpleadoRol)
                .setParameter("nombreRol", NOMBRE_ROL)
                .getResultList();
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    /**
     * Consulta por identificador, conservada para compatibilidad.
     */
    public EmpleadoRol findEmpleadoRolById(UUID id) {
        return id == null ? null : em.find(EmpleadoRol.class, id);
    }

    /**
     * Valida encabezado y camarero antes de crear la orden. Acepta fechas
     * pasadas.
     */
    @Override
    public void crear(Orden orden) {
        if (orden == null || orden.getIdOrden() == null
                || orden.getFechaCreacion() == null || orden.getIdEmpleadoRol() == null
                || orden.getIdEmpleadoRol().getIdEmpleadoRol() == null) {
            throw new IllegalArgumentException("La orden requiere identificador, fecha y camarero");
        }
        EmpleadoRol camarero = findCamareroActivoById(orden.getIdEmpleadoRol().getIdEmpleadoRol());
        if (camarero == null) {
            throw new IllegalArgumentException("El camarero seleccionado no está activo");
        }
        if (em.find(Orden.class, orden.getIdOrden()) != null) {
            throw new IllegalArgumentException("Ya existe una orden con ese identificador");
        }
        orden.setIdEmpleadoRol(camarero);
        em.persist(orden);
        em.flush();
    }

    /**
     * La fecha y el camarero son datos inmutables de una orden ya creada. Se
     * evita que una llamada externa al DAO los modifique por accidente.
     */
    @Override
    public void modificar(Orden orden) {
        if (orden == null || orden.getIdOrden() == null || orden.getFechaCreacion() == null
                || orden.getIdEmpleadoRol() == null
                || orden.getIdEmpleadoRol().getIdEmpleadoRol() == null) {
            throw new IllegalArgumentException("La orden está incompleta");
        }
        Orden actual = em.find(Orden.class, orden.getIdOrden(), LockModeType.PESSIMISTIC_WRITE);
        if (actual == null) {
            throw new IllegalArgumentException("La orden no existe");
        }
        UUID camareroActual = actual.getIdEmpleadoRol() == null ? null
                : actual.getIdEmpleadoRol().getIdEmpleadoRol();
        if (!actual.getFechaCreacion().equals(orden.getFechaCreacion())
                || !Objects.equals(camareroActual, orden.getIdEmpleadoRol().getIdEmpleadoRol())) {
            throw new IllegalArgumentException(
                    "No se permite cambiar la fecha ni el camarero de una orden existente");
        }
        // No hay otros campos editables en la cabecera. Los detalles se manejan
        // con operaciones específicas que validan su integridad.
    }

    /**
     * No elimina cabeceras con detalles; mantiene la integridad.
     */
    @Override
    public void eliminar(Orden orden) {
        if (orden == null || orden.getIdOrden() == null) {
            throw new IllegalArgumentException("Debe indicar una orden guardada");
        }
        Orden administrada = em.find(Orden.class, orden.getIdOrden(), LockModeType.PESSIMISTIC_WRITE);
        if (administrada == null) {
            throw new IllegalArgumentException("La orden ya no existe");
        }
        if (countProductosByOrden(orden.getIdOrden()) > 0) {
            throw new IllegalArgumentException("No se puede eliminar una orden con productos");
        }
        em.remove(administrada);
        em.flush();
    }

    /**
     * Agrega una fila de producto validando referencias y precio.
     */
    public void agregarProducto(OrdenProducto detalle) {
        if (detalle == null || detalle.getIdOrdenProducto() == null
                || detalle.getIdOrden() == null || detalle.getIdOrden().getIdOrden() == null
                || detalle.getIdProducto() == null || detalle.getIdProducto().getIdProducto() == null) {
            throw new IllegalArgumentException("La fila requiere orden y producto válidos");
        }
        BigDecimal precio = validarPrecio(detalle.getPrecio());
        Orden orden = em.find(Orden.class, detalle.getIdOrden().getIdOrden(), LockModeType.PESSIMISTIC_WRITE);
        Producto producto = em.find(Producto.class, detalle.getIdProducto().getIdProducto());
        if (orden == null || producto == null || !Boolean.TRUE.equals(producto.getActivo())) {
            throw new IllegalArgumentException("La orden o el producto no existe o está inactivo");
        }
        BigDecimal precioCatalogo = validarPrecio(producto.getPrecioSugerido());
        if (precio.compareTo(precioCatalogo) != 0) {
            throw new IllegalArgumentException(
                    "El precio sugerido cambió; actualice el producto antes de agregarlo");
        }
        if (em.find(OrdenProducto.class, detalle.getIdOrdenProducto()) != null) {
            throw new IllegalArgumentException("La fila ya existe");
        }
        detalle.setIdOrden(orden);
        detalle.setIdProducto(producto);
        detalle.setPrecio(precio);
        em.persist(detalle);
        em.flush();
    }

    /** Agrega filas en una sola transacción, con el precio vigente del catálogo. */
    public List<OrdenProducto> agregarProductos(UUID idOrden, UUID idProducto,
            int cantidad, String observaciones) {
        if (cantidad < 1 || cantidad > 20) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y 20");
        }
        Orden orden = bloquearOrdenEditable(idOrden);
        Producto producto = em.find(Producto.class, idProducto);
        if (producto == null || !Boolean.TRUE.equals(producto.getActivo())) {
            throw new IllegalArgumentException("El producto no existe o está inactivo");
        }
        BigDecimal precio = validarPrecio(producto.getPrecioSugerido());
        List<OrdenProducto> filas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            OrdenProducto fila = new OrdenProducto(UUID.randomUUID());
            fila.setIdOrden(orden);
            fila.setIdProducto(producto);
            fila.setPrecio(precio);
            fila.setObservaciones(observaciones);
            em.persist(fila);
            filas.add(fila);
        }
        em.flush();
        return filas;
    }

    public void quitarProducto(UUID idOrden, UUID idFila) {
        Orden orden = bloquearOrdenEditable(idOrden);
        OrdenProducto referencia = new OrdenProducto(idFila);
        referencia.setIdOrden(orden);
        eliminarProducto(referencia);
    }

    public static class OrdenNoEncontrada extends IllegalArgumentException {
        public OrdenNoEncontrada() { super("La orden no existe"); }
    }

    public static class OrdenFacturada extends IllegalStateException {
        public OrdenFacturada() { super("No se puede editar una orden con productos facturados"); }
    }

    private Orden bloquearOrdenEditable(UUID id) {
        Orden orden = em.find(Orden.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (orden == null) throw new OrdenNoEncontrada();
        // Factura toma estos mismos bloqueos en orden de UUID antes de asociar filas.
        List<UUID> ids = findProductosByOrden(id).stream()
                .map(OrdenProducto::getIdOrdenProducto).sorted().toList();
        for (UUID fila : ids) {
            em.find(OrdenProducto.class, fila, LockModeType.PESSIMISTIC_WRITE);
        }
        if (tieneProductosFacturados(id)) throw new OrdenFacturada();
        return orden;
    }

    public List<OrdenProducto> findProductosFacturables(UUID idOrden) {
        return em.createQuery("SELECT op FROM OrdenProducto op LEFT JOIN FETCH op.idProducto "
                + "WHERE op.idOrden.idOrden = :id AND NOT EXISTS "
                + "(SELECT f FROM FacturaOrdenProducto f WHERE f.idOrdenProducto = op) "
                + "ORDER BY op.idOrdenProducto", OrdenProducto.class)
                .setParameter("id", idOrden).getResultList();
    }

    /**
     * Actualiza una fila existente solo si aún no está facturada.
     */
    public OrdenProducto actualizarProducto(OrdenProducto detalle) {
        if (detalle == null || detalle.getIdOrdenProducto() == null) {
            throw new IllegalArgumentException("Debe indicar una fila de orden");
        }
        BigDecimal precio = validarPrecio(detalle.getPrecio());
        OrdenProducto actual = em.find(OrdenProducto.class, detalle.getIdOrdenProducto(),
                LockModeType.PESSIMISTIC_WRITE);
        if (actual == null) {
            throw new IllegalArgumentException("La fila ya no existe");
        }
        if (estaFacturado(actual.getIdOrdenProducto())) {
            throw new IllegalArgumentException("No se puede modificar un producto facturado");
        }
        actual.setPrecio(precio);
        actual.setObservaciones(detalle.getObservaciones());
        em.flush();
        return actual;
    }

    /**
     * Elimina únicamente la fila, no el producto del catálogo.
     */
    public void eliminarProducto(OrdenProducto detalle) {
        if (detalle == null || detalle.getIdOrdenProducto() == null) {
            throw new IllegalArgumentException("Debe indicar una fila de orden");
        }
        OrdenProducto actual = em.find(OrdenProducto.class, detalle.getIdOrdenProducto(),
                LockModeType.PESSIMISTIC_WRITE);
        if (actual == null) {
            throw new IllegalArgumentException("La fila ya no existe");
        }
        if (detalle.getIdOrden() != null && detalle.getIdOrden().getIdOrden() != null
                && (actual.getIdOrden() == null || !detalle.getIdOrden().getIdOrden()
                .equals(actual.getIdOrden().getIdOrden()))) {
            throw new IllegalArgumentException("La fila no pertenece a la orden indicada");
        }
        if (estaFacturado(actual.getIdOrdenProducto())) {
            throw new IllegalArgumentException("No se puede eliminar un producto facturado");
        }
        em.remove(actual);
        em.flush();
    }

    /**
     * Devuelve las filas de la orden, con la referencia a producto cargada.
     */
    public List<OrdenProducto> findProductosByOrden(UUID idOrden) {
        if (idOrden == null) {
            throw new IllegalArgumentException("El identificador de orden es obligatorio");
        }
        return em.createNamedQuery("OrdenProducto.findByOrdenConProducto", OrdenProducto.class)
                .setParameter("idOrden", idOrden)
                .getResultList();
    }

    /**
     * Cuenta las filas asociadas.
     */
    public long countProductosByOrden(UUID idOrden) {
        if (idOrden == null) {
            throw new IllegalArgumentException("El identificador de orden es obligatorio");
        }
        return em.createNamedQuery("OrdenProducto.countByOrden", Long.class)
                .setParameter("idOrden", idOrden)
                .getSingleResult();
    }

    private boolean estaFacturado(UUID idDetalle) {
        return em.createNamedQuery("OrdenProducto.countFacturado", Long.class)
                .setParameter("idOrdenProducto", idDetalle)
                .getSingleResult() > 0;
    }

    public boolean tieneProductosFacturados(UUID idOrden) {
        if (idOrden == null) {
            throw new IllegalArgumentException("Debe indicar una orden");
        }

        return em.createQuery(
                "SELECT COUNT(fop) FROM FacturaOrdenProducto fop "
                + "WHERE fop.idOrdenProducto.idOrden.idOrden = :idOrden",
                Long.class)
                .setParameter("idOrden", idOrden)
                .getSingleResult() > 0;
    }

    /**
     * Consulta de compatibilidad: descuentos disponibles para un producto
     * individual.
     */
    public List<DescuentoProducto> findDescuentosAplicables(UUID idProducto, Date fecha) {
        if (idProducto == null || fecha == null) {
            throw new IllegalArgumentException("Producto y fecha son obligatorios");
        }
        Date[] rango = diaComercial(fecha);
        return em.createNamedQuery("DescuentoProducto.findAplicables", DescuentoProducto.class)
                .setParameter("idProducto", idProducto)
                .setParameter("inicioDia", rango[0])
                .setParameter("finDia", rango[1])
                .getResultList();
    }

    /**
     * Retorna una promoción una sola vez si existe una asociación vigente con
     * al menos una fila de producto de la orden.
     */
    public List<Descuento> findPromocionesAplicables(UUID idOrden, Date fecha) {
        List<DescuentoProducto> asociaciones = consultarAplicablesOrden(idOrden, fecha);
        Map<UUID, Descuento> unicas = new LinkedHashMap<>();
        for (DescuentoProducto dp : asociaciones) {
            if (dp != null && dp.getIdDescuento() != null
                    && dp.getIdDescuento().getIdDescuento() != null) {
                unicas.putIfAbsent(dp.getIdDescuento().getIdDescuento(), dp.getIdDescuento());
            }
        }
        List<Descuento> salida = new ArrayList<>(unicas.values());
        salida.sort(Comparator.comparing(
                d -> d.getNombre() == null ? "" : d.getNombre(),
                String.CASE_INSENSITIVE_ORDER));
        return salida;
    }

    private List<DescuentoProducto> consultarAplicablesOrden(UUID idOrden, Date fecha) {
        if (idOrden == null || fecha == null) {
            throw new IllegalArgumentException("Orden y fecha son obligatorias");
        }
        Date[] rango = diaComercial(fecha);
        return em.createNamedQuery("DescuentoProducto.findAplicablesOrden", DescuentoProducto.class)
                .setParameter("idOrden", idOrden)
                .setParameter("inicioDia", rango[0])
                .setParameter("finDia", rango[1])
                .getResultList();
    }

    /**
     * Aplica UNA promoción a TODAS las filas elegibles de la orden en una sola
     * transacción EJB. Una segunda aplicación descuenta sobre el precio
     * vigente. La operación es todo o nada si encuentra una fila facturada o
     * inválida.
     */
    public ResultadoPromocion aplicarPromocion(UUID idOrden, UUID idPromocion) {
        if (idOrden == null || idPromocion == null) {
            throw new IllegalArgumentException("Seleccione una orden y una promoción");
        }
        Orden orden = em.find(Orden.class, idOrden, LockModeType.PESSIMISTIC_WRITE);
        if (orden == null || orden.getFechaCreacion() == null) {
            throw new IllegalArgumentException("La orden no existe o no tiene fecha válida");
        }
        List<OrdenProducto> detalles = findProductosByOrden(idOrden);
        List<DescuentoProducto> asociaciones = consultarAplicablesOrden(idOrden, orden.getFechaCreacion());
        Map<UUID, DescuentoProducto> porProducto = new LinkedHashMap<>();
        for (DescuentoProducto dp : asociaciones) {
            if (dp == null || dp.getIdDescuento() == null || dp.getIdProducto() == null
                    || dp.getIdProducto().getIdProducto() == null) {
                continue;
            }
            if (!idPromocion.equals(dp.getIdDescuento().getIdDescuento())) {
                continue;
            }
            UUID idProducto = dp.getIdProducto().getIdProducto();
            if (porProducto.putIfAbsent(idProducto, dp) != null) {
                throw new IllegalArgumentException(
                        "Hay asociaciones duplicadas para este producto y promoción");
            }
        }
        if (porProducto.isEmpty()) {
            throw new IllegalArgumentException("La promoción no tiene productos aplicables en esta orden");
        }

        // Fase 1: bloquear, validar y calcular, sin modificar todavía la entidad.
        List<Cambio> cambios = new ArrayList<>();
        BigDecimal totalDescontado = BigDecimal.ZERO.setScale(2);
        // Factura también bloquea filas por UUID. Mantener el mismo orden evita
        // un bloqueo circular cuando ambos flujos afectan varias filas.
        List<OrdenProducto> filasOrdenadas = new ArrayList<>(detalles);
        filasOrdenadas.sort(Comparator.nullsFirst(Comparator.comparing(
                OrdenProducto::getIdOrdenProducto, Comparator.nullsFirst(Comparator.naturalOrder()))));
        for (OrdenProducto fila : filasOrdenadas) {
            if (fila == null || fila.getIdOrdenProducto() == null || fila.getIdProducto() == null
                    || fila.getIdProducto().getIdProducto() == null) {
                throw new IllegalArgumentException("La orden contiene una fila inválida");
            }
            DescuentoProducto asociacion = porProducto.get(fila.getIdProducto().getIdProducto());
            if (asociacion == null) {
                continue;
            }
            em.lock(fila, LockModeType.PESSIMISTIC_WRITE);
            if (estaFacturado(fila.getIdOrdenProducto())) {
                throw new IllegalArgumentException(
                        "La promoción no puede aplicarse: existe un producto facturado");
            }
            BigDecimal actual = validarPrecio(fila.getPrecio());
            int porcentaje = asociacion.getValor();
            if (porcentaje <= 0 || porcentaje > 100) {
                throw new IllegalArgumentException("Porcentaje de promoción inválido");
            }
            BigDecimal descuento = actual.multiply(BigDecimal.valueOf(porcentaje))
                    .divide(CIEN, 2, RoundingMode.HALF_UP);
            BigDecimal nuevo = actual.subtract(descuento).setScale(2, RoundingMode.HALF_UP);
            String nombre = asociacion.getIdDescuento().getNombre();
            String nota = "Aplicado descuento " + nombre + " (" + porcentaje + "%) "
                    + "sobre $" + actual.toPlainString() + " - Monto: $"
                    + descuento.toPlainString() + " - Nuevo precio: $" + nuevo.toPlainString();
            cambios.add(new Cambio(fila, nuevo, nota));
            totalDescontado = totalDescontado.add(descuento);
        }
        if (cambios.isEmpty()) {
            throw new IllegalArgumentException("La promoción no tiene productos aplicables");
        }

        // Fase 2: aplicar todas las modificaciones; fallo de persistencia => rollback JTA.
        try {
            for (Cambio cambio : cambios) {
                OrdenProducto fila = cambio.detalle;
                fila.setPrecio(cambio.nuevoPrecio);
                String anterior = fila.getObservaciones();
                fila.setObservaciones(anterior == null || anterior.isBlank()
                        ? cambio.nota : anterior + "\n" + cambio.nota);
            }
            em.flush();
        } catch (RuntimeException ex) {
            // El contenedor revertirá la transacción. Restauramos también
            // los objetos Java por si todavía están referenciados en la vista.
            for (Cambio cambio : cambios) {
                cambio.detalle.setPrecio(cambio.precioAnterior);
                cambio.detalle.setObservaciones(cambio.observacionesAnteriores);
            }
            throw ex;
        }
        return new ResultadoPromocion(cambios.size(), totalDescontado);
    }

    /**
     * Mantiene compatibilidad con la creación conjunta de cabecera y filas.
     */
    public void crearOrdenCompleta(Orden orden, List<OrdenProducto> productos) {
        if (orden == null || productos == null) {
            throw new IllegalArgumentException("Orden y lista no pueden ser nulas");
        }
        // Validación previa: ninguna persistencia si hay datos incompletos.
        for (OrdenProducto detalle : productos) {
            if (detalle == null || detalle.getIdOrdenProducto() == null
                    || detalle.getIdProducto() == null || detalle.getIdProducto().getIdProducto() == null) {
                throw new IllegalArgumentException("Existe una fila sin producto válido");
            }
            validarPrecio(detalle.getPrecio());
        }
        crear(orden);
        for (OrdenProducto detalle : productos) {
            detalle.setIdOrden(orden);
            agregarProducto(detalle);
        }
    }

    private static BigDecimal validarPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() < 0) {
            throw new IllegalArgumentException("El precio debe ser mayor o igual a cero");
        }
        try {
            return precio.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("El precio solo admite dos decimales", ex);
        }
    }

    private static Date[] diaComercial(Date fecha) {
        Calendar c = Calendar.getInstance(ZONA);
        c.setTime(fecha);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date inicio = c.getTime();
        c.add(Calendar.DATE, 1);
        return new Date[]{inicio, c.getTime()};
    }

    private static final class Cambio {

        private final OrdenProducto detalle;
        private final BigDecimal nuevoPrecio;
        private final BigDecimal precioAnterior;
        private final String observacionesAnteriores;
        private final String nota;

        Cambio(OrdenProducto detalle, BigDecimal nuevoPrecio, String nota) {
            this.detalle = Objects.requireNonNull(detalle);
            this.precioAnterior = detalle.getPrecio();
            this.observacionesAnteriores = detalle.getObservaciones();
            this.nuevoPrecio = nuevoPrecio;
            this.nota = nota;
        }
    }

    /**
     * Resumen para mensajes de éxito en la interfaz.
     */
    public static final class ResultadoPromocion {

        private final int productosAfectados;
        private final BigDecimal descuentoTotal;

        public ResultadoPromocion(int productosAfectados, BigDecimal descuentoTotal) {
            this.productosAfectados = productosAfectados;
            this.descuentoTotal = descuentoTotal;
        }

        public int getProductosAfectados() {
            return productosAfectados;
        }

        public BigDecimal getDescuentoTotal() {
            return descuentoTotal;
        }
    }
}
