/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author erick
 */
public class RolTest {

    @Test
    void debeAsignarYObtenerNombre() {
        Rol rol = new Rol();

        rol.setNombre("Administrador");

        assertEquals("Administrador", rol.getNombre());
    }
}
