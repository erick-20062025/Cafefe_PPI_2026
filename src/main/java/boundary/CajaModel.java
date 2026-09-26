/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;
import control.CajaDAO;
import control.DAOInterface;
import entity.Caja;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;

/**
 *
 * @author diegoli
 */
@Named("cajaModel")
@ViewScoped
public class CajaModel extends AbstractModel<Caja> {

    @Inject
    private CajaDAO cajaDAO;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<Caja> getDAO() {
        return cajaDAO;
    }

    @Override
    protected Caja crearNuevoRegistro() {

        Caja caja = new Caja();

        caja.setIdCaja(UUID.randomUUID());
        caja.setActivo(true);

        return caja;
    }
}
