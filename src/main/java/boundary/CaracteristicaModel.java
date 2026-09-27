package boundary;

import control.CaracteristicaDAO;
import control.DAOInterface;
import control.TipoCaracteristicaDAO;
import entity.Caracteristica;
import entity.TipoCaracteristica;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.UUID;

@Named("caracteristicaModel")
@ViewScoped
public class CaracteristicaModel extends AbstractModel<Caracteristica> {

    @Inject
    private CaracteristicaDAO caracteristicaDAO;

    @Inject
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    private List<TipoCaracteristica> tiposCaracteristica;

    @PostConstruct
    public void init() {
        cargarDatos();
        tiposCaracteristica = tipoCaracteristicaDAO.findAll();
    }

    @Override
    protected DAOInterface<Caracteristica> getDAO() {
        return caracteristicaDAO;
    }

    @Override
    protected Caracteristica crearNuevoRegistro() {

        Caracteristica caracteristica = new Caracteristica();

        caracteristica.setIdCaracteristica(UUID.randomUUID());
        caracteristica.setActivo(true);

        return caracteristica;
    }

    public List<TipoCaracteristica> getTiposCaracteristica() {
        return tiposCaracteristica;
    }
}