package boundary;

import control.DAOInterface;
import control.TipoProductoDAO;
import entity.TipoProducto;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;

@Named("tipoProductoModel")
@ViewScoped
public class TipoProductoModel extends AbstractModel<TipoProducto> {

    @Inject
    private TipoProductoDAO tipoProductoDAO;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<TipoProducto> getDAO() {
        return tipoProductoDAO;
    }

    @Override
    protected TipoProducto crearNuevoRegistro() {

        TipoProducto tipoProducto = new TipoProducto();

        tipoProducto.setIdTipoProducto(UUID.randomUUID());
        tipoProducto.setActivo(true);

        return tipoProducto;
    }
}
