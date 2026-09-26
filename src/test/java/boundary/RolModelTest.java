/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;

import boundary.RolModel;
import control.RolDAO;
import entity.Rol;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 *
 * @author erick
 */
@ExtendWith(MockitoExtension.class)
public class RolModelTest {

    @Mock
    private RolDAO rolDAO;

    @InjectMocks
    private RolModel rolModel;

    @Test
    void debeCargarRolesDesdeDAO() {

        Rol administrador = new Rol();
        administrador.setNombre("Administrador");

        Rol cajero = new Rol();
        cajero.setNombre("Cajero");

        List<Rol> roles = List.of(administrador, cajero);

        when(rolDAO.findAll()).thenReturn(roles);

        rolModel.cargarDatos();

        assertEquals(2, rolModel.getModelo().size());
        assertEquals("Administrador", rolModel.getModelo().get(0).getNombre());
        assertEquals("Cajero", rolModel.getModelo().get(1).getNombre());

        verify(rolDAO).findAll();
    }
}
