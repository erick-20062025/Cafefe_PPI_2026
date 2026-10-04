/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;

import control.CaracteristicaDAO;
import control.DAOInterface;
import control.ProductoCaracteristicaDAO;
import entity.Caracteristica;
import entity.Producto;
import entity.ProductoCaracteristica;
import entity.TipoCaracteristica;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;

/**
 *
 * @author erick
 */
@Named("productoCaracteristicaModel")
@ViewScoped
public class ProductoCaracteristicaModel
        extends AbstractRelacionModel<
                ProductoCaracteristica, Producto, Caracteristica> {

    private static final long serialVersionUID = 1L;

    @Inject
    private ProductoCaracteristicaDAO productoCaracteristicaDAO;

    @Inject
    private CaracteristicaDAO caracteristicaDAO;

    /*
     * Dato adicional propio de esta relación.
     *
     * Ejemplo:
     * Característica = Tamaño
     * Valor = 600 ml
     */
    private String valorCaracteristica;


    /* =========================================================
       INICIALIZACIÓN
       ========================================================= */
    @PostConstruct
    public void init() {
        cargarOpciones();
    }


    /* =========================================================
       DAO DE LA RELACIÓN
       ========================================================= */
    @Override
    protected DAOInterface<ProductoCaracteristica> getDAO() {
        return productoCaracteristicaDAO;
    }


    /* =========================================================
       IMPLEMENTACIÓN DEL PATRÓN DE RELACIONES
       ========================================================= */
    /**
     * Carga todas las Caracteristicas disponibles.
     */
    @Override
    protected List<Caracteristica> consultarOpcionesDisponibles() {

        return caracteristicaDAO.findAll();
    }

    /**
     * Carga las características pertenecientes al Producto seleccionado.
     */
    @Override
    protected List<ProductoCaracteristica> consultarRelaciones(
            Producto producto) {

        return productoCaracteristicaDAO.findByIdProducto(
                producto.getIdProducto()
        );
    }

    /**
     * Comprueba que exista un Producto válido.
     */
    @Override
    protected boolean principalValido(
            Producto producto) {

        return producto != null
                && producto.getIdProducto() != null;
    }

    /**
     * Convierte el UUID textual recibido desde JSF y busca la Caracteristica
     * correspondiente.
     */
    @Override
    protected Caracteristica buscarOpcion(
            String idOpcion) {

        if (idOpcion == null
                || idOpcion.isBlank()) {

            return null;
        }

        try {

            UUID idCaracteristica
                    = UUID.fromString(idOpcion);

            return caracteristicaDAO.findById(
                    idCaracteristica
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    /**
     * Evita que una misma Caracteristica sea agregada dos veces al mismo
     * Producto.
     */
    @Override
    protected boolean existeRelacion(
            Producto producto,
            Caracteristica caracteristica) {

        return productoCaracteristicaDAO.existeRelacion(
                producto.getIdProducto(),
                caracteristica.getIdCaracteristica()
        );
    }

    /**
     * Construye ProductoCaracteristica.
     */
    @Override
    protected ProductoCaracteristica construirRelacion(
            Producto producto,
            Caracteristica caracteristica) {

        ProductoCaracteristica relacion
                = new ProductoCaracteristica();

        relacion.setIdProductoCaracteristica(
                UUID.randomUUID()
        );

        relacion.setIdProducto(producto);

        relacion.setIdCaracteristica(
                caracteristica
        );

        relacion.setValor(
                valorCaracteristica
        );

        return relacion;
    }


    /* =========================================================
       CARGA DEL PRODUCTO
       ========================================================= */
    /**
     * Sobrescribimos este método porque esta relación tiene un dato adicional:
     * valorCaracteristica.
     *
     * Cuando cambia el Producto o se recarga después de una asociación exitosa,
     * limpiamos también ese valor.
     */
    @Override
    public void cargarPrincipal(
            Producto producto) {

        super.cargarPrincipal(producto);

        this.valorCaracteristica = null;
    }


    /* =========================================================
       MÉTODOS CON NOMBRES ESPECÍFICOS PARA LA VISTA
       ========================================================= */
    public void cargarProducto(
            Producto producto) {

        cargarPrincipal(producto);
    }

    public void cargarCaracteristicas() {

        cargarOpciones();
    }

    public void agregarCaracteristica() {

        if (getIdCaracteristicaSeleccionada() == null
                || getIdCaracteristicaSeleccionada().isBlank()) {

            mostrarAdvertencia(
                    "Debe seleccionar una característica"
            );

            return;
        }

        if (valorCaracteristica == null
                || valorCaracteristica.isBlank()) {

            mostrarAdvertencia(
                    "Debe ingresar un valor para la característica"
            );

            return;
        }

        Caracteristica caracteristica
                = buscarOpcion(
                        getIdCaracteristicaSeleccionada()
                );

        if (caracteristica == null) {

            mostrarError(
                    "La característica seleccionada no existe"
            );

            return;
        }
        if (!Boolean.TRUE.equals(caracteristica.getActivo())) {

            mostrarAdvertencia(
                    "No se puede asignar una característica inactiva"
            );

            return;
        }

        TipoCaracteristica tipo
                = caracteristica.getIdTipoCaracteristica();

        if (tipo != null) {

            String expresion
                    = tipo.getExpresionRegular();

            /*
         * Si no existe expresión regular,
         * cualquier valor es permitido.
             */
            if (expresion != null
                    && !expresion.isBlank()) {

                try {

                    if (!valorCaracteristica.matches(expresion)) {

                        mostrarAdvertencia(
                                "El valor ingresado no cumple con el formato requerido"
                        );

                        return;
                    }

                } catch (PatternSyntaxException e) {

                    mostrarError(
                            "La expresión regular configurada no es válida"
                    );

                    return;
                }
            }
        }

        agregarRelacion();
    }

    public void quitarCaracteristica(
            ProductoCaracteristica relacion) {

        quitarRelacion(relacion);
    }


    /* =========================================================
       PROPIEDADES PARA PRODUCTO.XHTML
       ========================================================= */
    public List<Caracteristica> getCaracteristicaList() {

        return getOpcionesDisponibles();
    }

    public String getIdCaracteristicaSeleccionada() {

        return getIdOpcionSeleccionada();
    }

    public void setIdCaracteristicaSeleccionada(
            String idCaracteristicaSeleccionada) {

        setIdOpcionSeleccionada(
                idCaracteristicaSeleccionada
        );
    }

    public String getValorCaracteristica() {

        return valorCaracteristica;
    }

    public void setValorCaracteristica(
            String valorCaracteristica) {

        this.valorCaracteristica
                = valorCaracteristica;
    }
}
