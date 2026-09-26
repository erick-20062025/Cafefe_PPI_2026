/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;

import control.TipoProductoDAO;
import entity.TipoProducto;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 *
 * @author erick
 */
@ExtendWith(MockitoExtension.class)
public class TipoProductoModelTest {

    @Mock
    private TipoProductoDAO tipoProductoDAO;

    @InjectMocks
    private TipoProductoModel tipoProductoModel;

    @Test
    void debeCargarTipoProductosDesdeDAO() {

        TipoProducto tipoProducto1 = new TipoProducto();
        tipoProducto1.setNombre("Bebidas");

        TipoProducto tipoProducto2 = new TipoProducto();
        tipoProducto2.setNombre("Postres");

        List<TipoProducto> tipoProductos = List.of(tipoProducto1, tipoProducto2);

        when(tipoProductoDAO.findAll()).thenReturn(tipoProductos);

        tipoProductoModel.cargarDatos();

        assertEquals(2, tipoProductoModel.getModelo().size());
        assertEquals("Bebidas",
                tipoProductoModel.getModelo().get(0).getNombre());
        assertEquals("Postres",
                tipoProductoModel.getModelo().get(1).getNombre());

        verify(tipoProductoDAO).findAll();
    }

    @Test
    void debeCrearTipoProductoNuevoConUuidYActivo() {

        TipoProducto tipoProducto = tipoProductoModel.crearNuevoRegistro();

        assertNotNull(tipoProducto);
        assertNotNull(tipoProducto.getIdTipoProducto());
        assertInstanceOf(UUID.class, tipoProducto.getIdTipoProducto());
        assertTrue(tipoProducto.getActivo());
    }
}
