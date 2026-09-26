/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;

import control.DAOInterface;
import control.RolDAO;
import entity.Rol;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
/**
 *
 * @author diegoli
 */
@Named("rolModel")
@ViewScoped
public class RolModel extends AbstractModel<Rol> {

    @Inject
    private RolDAO rolDAO;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<Rol> getDAO() {
          return rolDAO;
    }

    @Override
    protected Rol crearNuevoRegistro() {

        Rol rol = new Rol();

        rol.setIdRol(UUID.randomUUID());
        rol.setActivo(true);

        return rol;
    }
}