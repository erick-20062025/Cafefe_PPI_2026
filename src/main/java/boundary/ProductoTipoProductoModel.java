package boundary;

import control.DAOInterface;
import control.ProductoTipoProductoDAO;
import control.TipoProductoDAO;
import entity.Producto;
import entity.ProductoTipoProducto;
import entity.TipoProducto;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Named("productoTipoProductoModel")
@ViewScoped
public class ProductoTipoProductoModel
        extends AbstractRelacionModel<
                ProductoTipoProducto,
                Producto,
                TipoProducto> {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProductoTipoProductoDAO productoTipoProductoDAO;

    @Inject
    private TipoProductoDAO tipoProductoDAO;


    /* =========================================================
       INICIALIZACIÓN
       ========================================================= */

    @PostConstruct
    public void init() {
        cargarOpciones();
    }


    /* =========================================================
       DAO PRINCIPAL DE LA RELACIÓN
       ========================================================= */

    @Override
    protected DAOInterface<ProductoTipoProducto> getDAO() {
        return productoTipoProductoDAO;
    }


    /* =========================================================
       IMPLEMENTACIÓN DEL PATRÓN DE RELACIONES
       ========================================================= */

    /**
     * Obtiene todos los tipos de producto
     * que pueden ser seleccionados.
     */
    @Override
    protected List<TipoProducto> consultarOpcionesDisponibles() {

        return tipoProductoDAO.findAll();
    }


    /**
     * Obtiene únicamente los tipos asociados
     * al producto actual.
     */
    @Override
    protected List<ProductoTipoProducto> consultarRelaciones(
            Producto producto) {

        return productoTipoProductoDAO.findByIdProducto(
                producto.getIdProducto()
        );
    }


    /**
     * Comprueba que el Producto pueda utilizarse
     * para administrar relaciones.
     */
    @Override
    protected boolean principalValido(
            Producto producto) {

        return producto != null
                && producto.getIdProducto() != null;
    }


    /**
     * Convierte el UUID recibido desde JSF
     * y obtiene el TipoProducto correspondiente.
     */
    @Override
    protected TipoProducto buscarOpcion(
            String idOpcion) {

        if (idOpcion == null
                || idOpcion.isBlank()) {

            return null;
        }

        try {

            UUID idTipoProducto
                    = UUID.fromString(idOpcion);

            return tipoProductoDAO.findById(
                    idTipoProducto
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }


    /**
     * Comprueba que el mismo TipoProducto
     * no esté asignado dos veces.
     */
    @Override
    protected boolean existeRelacion(
            Producto producto,
            TipoProducto tipoProducto) {

        return productoTipoProductoDAO.existeRelacion(
                producto.getIdProducto(),
                tipoProducto.getIdTipoProducto()
        );
    }


    /**
     * Construye la nueva relación.
     */
    @Override
    protected ProductoTipoProducto construirRelacion(
            Producto producto,
            TipoProducto tipoProducto) {

        ProductoTipoProducto relacion
                = new ProductoTipoProducto();

        relacion.setIdProductoTipoProducto(
                UUID.randomUUID()
        );

        relacion.setProducto(producto);

        relacion.setIdTipoProducto(
                tipoProducto
        );

        relacion.setFechaCreacion(
                new Date()
        );

        return relacion;
    }


    /* =========================================================
       MÉTODOS DE COMPATIBILIDAD CON PRODUCTO.XHTML
       ========================================================= */

    /*
     * Estos métodos conservan los nombres que ya utiliza
     * Producto.xhtml.
     *
     * Internamente delegan toda la lógica común
     * a AbstractRelacionModel.
     */

    public void cargarTiposProducto() {
        cargarOpciones();
    }


    public void cargarProducto(
            Producto producto) {

        cargarPrincipal(producto);
    }


    public void agregarTipo() {
        agregarRelacion();
    }


    public void quitarTipo(
            ProductoTipoProducto relacion) {

        quitarRelacion(relacion);
    }


    /* =========================================================
       PROPIEDADES UTILIZADAS POR LA VISTA
       ========================================================= */

    public Producto getProducto() {
        return getPrincipal();
    }


    public void setProducto(
            Producto producto) {

        setPrincipal(producto);
    }


    public List<TipoProducto> getTipoProductoList() {
        return getOpcionesDisponibles();
    }


    public void setTipoProductoList(
            List<TipoProducto> lista) {

        setOpcionesDisponibles(lista);
    }


    public String getIdTipoProductoSeleccionado() {
        return getIdOpcionSeleccionada();
    }


    public void setIdTipoProductoSeleccionado(
            String idTipoProductoSeleccionado) {

        setIdOpcionSeleccionada(
                idTipoProductoSeleccionado
        );
    }
}