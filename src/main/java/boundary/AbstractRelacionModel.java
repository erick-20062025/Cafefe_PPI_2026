package boundary;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Modelo base para gestionar relaciones entre entidades.
 *
 * Ejemplos:
 *
 * Producto -> ProductoTipoProducto -> TipoProducto
 * Empleado -> EmpleadoRol -> Rol
 * Producto -> ProductoCaracteristica -> Caracteristica
 *
 * R = entidad que representa la relación
 * P = entidad principal
 * O = entidad que se desea asociar
 *
 * @param <R> entidad relación
 * @param <P> entidad principal
 * @param <O> entidad opción/asociada
 */
public abstract class AbstractRelacionModel<R, P, O>
        extends AbstractModel<R>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /*
     * Entidad principal sobre la cual
     * se administran las relaciones.
     */
    protected P principal;

    /*
     * Opciones disponibles para asociar.
     *
     * Ejemplos:
     * TipoProducto
     * Rol
     * Caracteristica
     */
    protected List<O> opcionesDisponibles
            = Collections.emptyList();

    /*
     * Identificador seleccionado desde la vista.
     *
     * Se maneja como String para evitar
     * converters JSF cuando utilizamos UUID.
     */
    protected String idOpcionSeleccionada;


    /* =========================================================
       MÉTODOS QUE CADA RELACIÓN DEBE IMPLEMENTAR
       ========================================================= */

    /**
     * Obtiene las opciones disponibles.
     */
    protected abstract List<O> consultarOpcionesDisponibles();


    /**
     * Obtiene las relaciones correspondientes
     * a la entidad principal.
     */
    protected abstract List<R> consultarRelaciones(P principal);


    /**
     * Indica si la entidad principal es válida
     * para administrar relaciones.
     */
    protected abstract boolean principalValido(P principal);


    /**
     * Busca la opción seleccionada utilizando
     * el identificador recibido desde la vista.
     */
    protected abstract O buscarOpcion(String idOpcion);


    /**
     * Comprueba si la relación ya existe.
     */
    protected abstract boolean existeRelacion(
            P principal,
            O opcion
    );


    /**
     * Construye la nueva entidad de relación.
     */
    protected abstract R construirRelacion(
            P principal,
            O opcion
    );


    /* =========================================================
       CARGA DE DATOS
       ========================================================= */

    /**
     * Carga todas las opciones disponibles.
     */
    public void cargarOpciones() {

        List<O> resultado
                = consultarOpcionesDisponibles();

        if (resultado != null) {

            this.opcionesDisponibles = resultado;

        } else {

            this.opcionesDisponibles
                    = Collections.emptyList();
        }
    }


    /**
     * Cambia la entidad principal y carga
     * únicamente sus relaciones.
     */
    public void cargarPrincipal(P principal) {

        this.principal = principal;

        /*
         * Limpiamos la opción seleccionada
         * cada vez que cambia el principal.
         */
        this.idOpcionSeleccionada = null;

        if (principalValido(principal)) {

            List<R> resultado
                    = consultarRelaciones(principal);

            if (resultado != null) {

                this.modelo = resultado;

            } else {

                this.modelo = Collections.emptyList();
            }

        } else {

            this.modelo = Collections.emptyList();
        }
    }


    /* =========================================================
       CREACIÓN DE LA RELACIÓN
       ========================================================= */

    /**
     * Implementación requerida por AbstractModel.
     *
     * Construye una relación utilizando
     * la entidad principal y la opción seleccionada.
     */
    @Override
    protected R crearNuevoRegistro() {

        if (!principalValido(principal)) {
            return null;
        }

        if (idOpcionSeleccionada == null
                || idOpcionSeleccionada.isBlank()) {

            return null;
        }

        O opcion = buscarOpcion(
                idOpcionSeleccionada
        );

        if (opcion == null) {
            return null;
        }

        return construirRelacion(
                principal,
                opcion
        );
    }


    /* =========================================================
       AGREGAR RELACIÓN
       ========================================================= */

    public void agregarRelacion() {

        if (!principalValido(principal)) {

            mostrarAdvertencia(
                    "Debe seleccionar un registro principal"
            );

            return;
        }

        if (idOpcionSeleccionada == null
                || idOpcionSeleccionada.isBlank()) {

            mostrarAdvertencia(
                    "Debe seleccionar una opción"
            );

            return;
        }

        try {

            O opcion = buscarOpcion(
                    idOpcionSeleccionada
            );

            if (opcion == null) {

                mostrarError(
                        "La opción seleccionada no existe"
                );

                return;
            }

            /*
             * Evita relaciones duplicadas.
             */
            if (existeRelacion(
                    principal,
                    opcion)) {

                mostrarAdvertencia(
                        "La relación ya existe"
                );

                return;
            }

            R relacion = construirRelacion(
                    principal,
                    opcion
            );

            if (relacion == null) {

                mostrarError(
                        "No se pudo crear la relación"
                );

                return;
            }

            getDAO().crear(relacion);

            /*
             * Recargamos las relaciones.
             *
             * cargarPrincipal también limpia
             * la selección actual.
             */
            cargarPrincipal(principal);

            mostrarExito(
                    "Registro asociado correctamente"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo crear la relación"
            );
        }
    }


    /* =========================================================
       QUITAR RELACIÓN
       ========================================================= */

    /**
     * Elimina únicamente la asociación.
     *
     * No elimina ninguna de las entidades
     * relacionadas.
     */
    public void quitarRelacion(R relacion) {

        if (relacion == null) {
            return;
        }

        try {

            getDAO().eliminar(relacion);

            cargarPrincipal(principal);

            mostrarExito(
                    "Relación eliminada correctamente"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo eliminar la relación"
            );
        }
    }


    /* =========================================================
       GETTERS Y SETTERS
       ========================================================= */

    public P getPrincipal() {
        return principal;
    }

    public void setPrincipal(P principal) {
        this.principal = principal;
    }

    public List<O> getOpcionesDisponibles() {
        return opcionesDisponibles;
    }

    public void setOpcionesDisponibles(
            List<O> opcionesDisponibles) {

        this.opcionesDisponibles
                = opcionesDisponibles;
    }

    public String getIdOpcionSeleccionada() {
        return idOpcionSeleccionada;
    }

    public void setIdOpcionSeleccionada(
            String idOpcionSeleccionada) {

        this.idOpcionSeleccionada
                = idOpcionSeleccionada;
    }
}