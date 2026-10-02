package boundary;

import control.DAOInterface;
import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Named("descuentoModel")
@ViewScoped
public class DescuentoModel extends AbstractModel<Descuento> {

    private String idTipoDescuentoSeleccionado;

    @Inject
    private DescuentoDAO descuentoDAO;

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    @Inject
    private DescuentoProductoModel descuentoProductoModel;

    private List<TipoDescuento> tiposDescuento;

    @PostConstruct
    public void init() {

        cargarDatos();

        tiposDescuento
                = tipoDescuentoDAO.findAll();
    }

    @Override
    protected DAOInterface<Descuento> getDAO() {
        return descuentoDAO;
    }

    @Override
    protected Descuento crearNuevoRegistro() {

        Descuento descuento
                = new Descuento();

        descuento.setIdDescuento(
                UUID.randomUUID()
        );

        return descuento;
    }

    /*
     * =========================================================
     * NUEVO DESCUENTO
     * =========================================================
     *
     * Todavía no se pueden administrar productos porque
     * primero debe guardarse el descuento principal.
     */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);

        idTipoDescuentoSeleccionado = null;

        limpiarProductos();
    }

    /*
     * =========================================================
     * SELECCIÓN
     * =========================================================
     *
     * Al seleccionar un descuento:
     *
     * - sincronizamos su TipoDescuento;
     * - cargamos sus productos relacionados.
     */
    @Override
    public void onRowSelect() {

        super.onRowSelect();

        if (this.registro != null
                && this.registro.getIdTipoDescuento() != null) {

            idTipoDescuentoSeleccionado
                    = this.registro
                            .getIdTipoDescuento()
                            .toString();

        } else {

            idTipoDescuentoSeleccionado = null;
        }

        descuentoProductoModel
                .cargarDescuento(this.registro);
    }

    /*
     * Si se quita la selección,
     * limpiamos también las relaciones.
     */
    @Override
    public void onRowUnselect() {

        super.onRowUnselect();

        idTipoDescuentoSeleccionado = null;

        limpiarProductos();
    }

    /*
     * =========================================================
     * GUARDAR
     * =========================================================
     *
     * Realizamos las validaciones propias de Descuento
     * antes de delegar el CRUD a AbstractModel.
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        if (registro == null) {
            return;
        }

        /*
         * El descuento siempre debe tener
         * un TipoDescuento.
         */
        if (registro.getIdTipoDescuento() == null) {

            mostrarAdvertencia(
                    "Debe seleccionar un tipo de descuento"
            );

            return;
        }

        /*
         * Comprobamos que el TipoDescuento todavía exista.
         */
        TipoDescuento tipoDescuento
                = tipoDescuentoDAO.findById(
                        registro.getIdTipoDescuento()
                );

        if (tipoDescuento == null) {

            mostrarAdvertencia(
                    "El tipo de descuento seleccionado no existe"
            );

            return;
        }

        /*
         * Un TipoDescuento inactivo no puede utilizarse
         * para crear un nuevo descuento.
         *
         * Si un descuento antiguo ya utilizaba ese tipo
         * antes de ser desactivado, podrá seguir conservándolo.
         */
        if (nuevo
                && !Boolean.TRUE.equals(
                        tipoDescuento.getActivo()
                )) {

            mostrarAdvertencia(
                    "No se puede asignar un tipo de descuento inactivo"
            );

            return;
        }

        /*
         * Fecha inicial obligatoria.
         */
        if (registro.getFechaDesde() == null) {

            mostrarAdvertencia(
                    "La fecha desde es obligatoria"
            );

            return;
        }

        /*
         * Para un descuento NUEVO no permitimos
         * una fecha inicial anterior al día actual.
         *
         * No aplicamos esta regla a descuentos antiguos
         * para permitir consultar/modificar registros históricos.
         */
        if (nuevo
                && normalizarFecha(
                        registro.getFechaDesde()
                ).before(getFechaMinima())) {

            mostrarAdvertencia(
                    "La fecha desde no puede ser anterior a hoy"
            );

            return;
        }

        /*
         * fechaHasta puede ser null.
         *
         * null = descuento con rango abierto.
         *
         * Si existe fechaHasta, nunca puede ser
         * anterior a fechaDesde.
         */
        if (registro.getFechaHasta() != null
                && normalizarFecha(
                        registro.getFechaHasta()
                ).before(
                        normalizarFecha(
                                registro.getFechaDesde()
                        )
                )) {

            mostrarAdvertencia(
                    "La fecha hasta no puede ser anterior a la fecha desde"
            );

            return;
        }

        /*
         * Las validaciones fueron superadas.
         * AbstractModel decide si crea o modifica.
         */
        super.btnGuardarHandler(ae);
    }

    /*
     * =========================================================
     * CANCELAR
     * =========================================================
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);

        idTipoDescuentoSeleccionado = null;

        limpiarProductos();
    }

    /*
     * Limpia el descuento principal utilizado
     * por DescuentoProductoModel.
     */
    private void limpiarProductos() {

        descuentoProductoModel
                .cargarDescuento(null);
    }

    /*
     * =========================================================
     * PRODUCTOS
     * =========================================================
     *
     * El segundo tab solamente se habilita cuando
     * el descuento ya fue guardado.
     */
    public boolean isPuedeGestionarProductos() {

        return this.registro != null
                && this.registro.getIdDescuento() != null
                && !this.nuevo;
    }

    /*
     * =========================================================
     * TIPOS DE DESCUENTO
     * =========================================================
     */
    public List<TipoDescuento> getTiposDescuento() {
        return tiposDescuento;
    }

    public String getIdTipoDescuentoSeleccionado() {

        if (idTipoDescuentoSeleccionado == null
                && registro != null
                && registro.getIdTipoDescuento() != null) {

            idTipoDescuentoSeleccionado
                    = registro
                            .getIdTipoDescuento()
                            .toString();
        }

        return idTipoDescuentoSeleccionado;
    }

    public void setIdTipoDescuentoSeleccionado(
            String idTipoDescuentoSeleccionado) {

        /*
         * Durante edición el TipoDescuento
         * no debe cambiarse.
         */
        if (registro != null && !nuevo) {
            return;
        }

        this.idTipoDescuentoSeleccionado
                = idTipoDescuentoSeleccionado;

        if (registro == null) {
            return;
        }

        if (idTipoDescuentoSeleccionado == null
                || idTipoDescuentoSeleccionado.isBlank()) {

            registro.setIdTipoDescuento(null);

            return;
        }

        try {

            registro.setIdTipoDescuento(
                    UUID.fromString(
                            idTipoDescuentoSeleccionado
                    )
            );

        } catch (IllegalArgumentException e) {

            registro.setIdTipoDescuento(null);
        }
    }

    /*
     * Devuelve el nombre del TipoDescuento
     * para mostrarlo en la tabla principal.
     */
    public String obtenerNombreTipoDescuento(
            UUID idTipoDescuento) {

        if (idTipoDescuento == null
                || tiposDescuento == null) {

            return "Sin tipo";
        }

        return tiposDescuento.stream()
                .filter(tipo
                        -> idTipoDescuento.equals(
                                tipo.getIdTipoDescuento()
                        ))
                .map(TipoDescuento::getNombre)
                .findFirst()
                .orElse("Tipo no disponible");
    }

    /*
     * Etiqueta utilizada en el combo.
     *
     * Los registros inactivos siguen siendo visibles,
     * pero la vista los deshabilita.
     */
    public String obtenerEtiquetaTipoDescuento(
            TipoDescuento tipo) {

        if (tipo == null) {
            return "";
        }

        if (Boolean.TRUE.equals(tipo.getActivo())) {
            return tipo.getNombre();
        }

        return tipo.getNombre() + " (INACTIVO)";
    }

    /*
     * =========================================================
     * FECHAS
     * =========================================================
     */

    /*
     * Fecha mínima permitida para crear
     * un nuevo descuento: hoy.
     */
    public Date getFechaMinima() {

        Calendar calendario
                = Calendar.getInstance();

        calendario.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendario.set(
                Calendar.MINUTE,
                0
        );

        calendario.set(
                Calendar.SECOND,
                0
        );

        calendario.set(
                Calendar.MILLISECOND,
                0
        );

        return calendario.getTime();
    }

    /*
     * Elimina la parte de hora para comparar
     * únicamente días.
     */
    private Date normalizarFecha(Date fecha) {

        Calendar calendario
                = Calendar.getInstance();

        calendario.setTime(fecha);

        calendario.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendario.set(
                Calendar.MINUTE,
                0
        );

        calendario.set(
                Calendar.SECOND,
                0
        );

        calendario.set(
                Calendar.MILLISECOND,
                0
        );

        return calendario.getTime();
    }
}