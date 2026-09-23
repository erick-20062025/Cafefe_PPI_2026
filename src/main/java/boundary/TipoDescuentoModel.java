package boundary;

import control.DAOInterface;
import control.TipoDescuentoDAO;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;

@Named("tipoDescuentoModel")
@ViewScoped
public class TipoDescuentoModel extends AbstractModel<TipoDescuento> {

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<TipoDescuento> getDAO() {
        return tipoDescuentoDAO;
    }

    @Override
    protected TipoDescuento crearNuevoRegistro() {

        TipoDescuento tipoDescuento = new TipoDescuento();

        tipoDescuento.setIdTipoDescuento(UUID.randomUUID());
        tipoDescuento.setActivo(true);

        return tipoDescuento;
    }
}