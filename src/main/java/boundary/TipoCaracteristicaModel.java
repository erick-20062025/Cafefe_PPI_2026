package boundary;

import control.DAOInterface;
import control.TipoCaracteristicaDAO;
import entity.TipoCaracteristica;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import jakarta.faces.event.ActionEvent;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Named("tipoCaracteristicaModel")
@ViewScoped
public class TipoCaracteristicaModel extends AbstractModel<TipoCaracteristica> {

    @Inject
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<TipoCaracteristica> getDAO() {
        return tipoCaracteristicaDAO;
    }

    @Override
    protected TipoCaracteristica crearNuevoRegistro() {

        TipoCaracteristica tipoCaracteristica = new TipoCaracteristica();

        tipoCaracteristica.setIdTipoCaracteristica(UUID.randomUUID());
        tipoCaracteristica.setActivo(true);

        return tipoCaracteristica;
    }
    @Override
public void btnGuardarHandler(ActionEvent ae) {

    if (registro == null) {
        return;
    }

    String expresion = registro.getExpresionRegular();

    if (expresion != null && !expresion.isBlank()) {

        try {
            Pattern.compile(expresion);

        } catch (PatternSyntaxException e) {

            mostrarAdvertencia(
                    "La expresión regular ingresada no es válida"
            );

            return;
        }
    }

    super.btnGuardarHandler(ae);
}
}