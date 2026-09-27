package converter;

import control.TipoCaracteristicaDAO;
import entity.TipoCaracteristica;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import jakarta.inject.Inject;
import java.util.UUID;

@FacesConverter(value = "tipoCaracteristicaConverter", managed = true)
public class TipoCaracteristicaConverter implements Converter<TipoCaracteristica> {

    @Inject
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    @Override
    public TipoCaracteristica getAsObject(FacesContext context,
                                          UIComponent component,
                                          String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        UUID id = UUID.fromString(value);

        return tipoCaracteristicaDAO.findById(id);
    }

    @Override
    public String getAsString(FacesContext context,
                              UIComponent component,
                              TipoCaracteristica value) {

        if (value == null || value.getIdTipoCaracteristica() == null) {
            return "";
        }

        return value.getIdTipoCaracteristica().toString();
    }
}