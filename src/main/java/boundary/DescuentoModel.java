package boundary;

import control.DAOInterface;
import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
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

    private List<TipoDescuento> tiposDescuento;

    @PostConstruct
    public void init() {
        cargarDatos();
        tiposDescuento = tipoDescuentoDAO.findAll();
    }

    @Override
    protected DAOInterface<Descuento> getDAO() {
        return descuentoDAO;
    }

    @Override
    protected Descuento crearNuevoRegistro() {

        Descuento descuento = new Descuento();

        descuento.setIdDescuento(UUID.randomUUID());

        return descuento;
    }

    public List<TipoDescuento> getTiposDescuento() {
        return tiposDescuento;
    }

    public String getIdTipoDescuentoSeleccionado() {
        if (idTipoDescuentoSeleccionado == null
                && registro != null
                && registro.getIdTipoDescuento() != null) {

            idTipoDescuentoSeleccionado
                    = registro.getIdTipoDescuento().toString();
        }

        return idTipoDescuentoSeleccionado;
    }

    public void setIdTipoDescuentoSeleccionado(String idTipoDescuentoSeleccionado) {
        this.idTipoDescuentoSeleccionado = idTipoDescuentoSeleccionado;

        if (registro != null) {
            if (idTipoDescuentoSeleccionado == null
                    || idTipoDescuentoSeleccionado.isBlank()) {

                registro.setIdTipoDescuento(null);

            } else {
                registro.setIdTipoDescuento(
                        UUID.fromString(idTipoDescuentoSeleccionado)
                );
            }
        }
    }

    public void actualizarFechaHasta() {
        if (registro != null && registro.getFechaDesde() != null) {
            registro.setFechaHasta(registro.getFechaDesde());
        }
    }
}
