package boundary.rest;

import boundary.rest.dto.FacturaSolicitud;
import boundary.rest.seguridad.AccesoPersonal;
import control.EmpleadoRolDAO;
import control.OrdenDAO;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Rol;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CobrosResourceTest {
    private final UUID empleado = UUID.randomUUID();
    private final UUID orden = UUID.randomUUID();
    private final UUID caja = UUID.randomUUID();
    private final CobrosResource cobros = new CobrosResource();

    @BeforeEach
    void preparar() {
        RestTestRuntime.instalar();
        cobros.request = mock(HttpServletRequest.class);
        cobros.acceso = mock(AccesoPersonal.class);
        cobros.ordenDAO = mock(OrdenDAO.class);
        cobros.empleadoRolDAO = mock(EmpleadoRolDAO.class);
        cobros.facturas = mock(FacturaResource.class);
        cobros.pagos = mock(PagoResource.class);
        when(cobros.request.getHeader("Origin")).thenReturn(SesionResource.ORIGEN);
    }

    @AfterEach
    void limpiar() {
        RestTestRuntime.limpiar();
    }

    private void sesion(String... roles) {
        HttpSession sesion = mock(HttpSession.class);
        when(cobros.request.getSession(false)).thenReturn(sesion);
        when(sesion.getAttribute(AccesoPersonal.ATRIBUTO)).thenReturn(empleado);
        when(cobros.acceso.recuperar(cobros.request))
                .thenReturn(new AccesoPersonal.Identidad("Prueba", List.of(roles)));
    }

    @Test
    void cobrosRequiereSesionYRolCajero() {
        assertEquals(401, cobros.pendientes().getStatus());
        sesion("cocinero");
        assertEquals(403, cobros.pendientes().getStatus());
        verifyNoInteractions(cobros.ordenDAO);
        sesion("cajero");
        when(cobros.ordenDAO.findPendientesFacturar()).thenReturn(List.of());
        Response respuesta = cobros.pendientes();
        assertEquals(200, respuesta.getStatus());
        assertEquals("no-store", respuesta.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    void facturaUsaAsignacionDelCajeroYPreciosDeLaOrden() {
        sesion("cajero", "cocinero");
        EmpleadoRol cajero = asignacion("Cajero", true);
        when(cobros.empleadoRolDAO.findByIdEmpleado(empleado)).thenReturn(List.of(
                asignacion("cocinero", true), asignacion("cajero", false), cajero));
        OrdenProducto producto = new OrdenProducto(UUID.randomUUID());
        producto.setPrecio(new BigDecimal("6.50"));
        when(cobros.ordenDAO.findById(orden)).thenReturn(new Orden(orden));
        when(cobros.ordenDAO.findProductosFacturables(orden)).thenReturn(List.of(producto));
        Response creada = Response.status(201).build();
        when(cobros.facturas.crear(anyString())).thenReturn(creada);

        assertEquals(201, cobros.crearFactura(orden.toString(), "{\"idCaja\":\"" + caja + "\"}").getStatus());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(cobros.facturas).crear(json.capture());
        var solicitud = FacturaSolicitud.leer(json.getValue(), true);
        assertEquals(caja, solicitud.idCaja());
        assertEquals(cajero.getIdEmpleadoRol(), solicitud.idEmpleadoRol());
        assertEquals(producto.getIdOrdenProducto(), solicitud.productos().getFirst().idOrdenProducto());
        assertEquals(new BigDecimal("6.50"), solicitud.productos().getFirst().precio());
    }

    @Test
    void facturaRechazaAsignacionEnviadaDesdeElCliente() {
        sesion("cajero");
        assertEquals(400, cobros.crearFactura(orden.toString(), "{\"idEmpleadoRol\":\"otro\"}").getStatus());
        verifyNoInteractions(cobros.facturas);
    }

    @Test
    void elAbonoRequiereCajeroYOrigenPermitido() {
        String factura = UUID.randomUUID().toString();
        sesion("cocinero");
        assertEquals(403, cobros.registrarAbono(factura, "{}").getStatus());
        sesion("cajero");
        when(cobros.request.getHeader("Origin")).thenReturn(null);
        assertEquals(403, cobros.registrarAbono(factura, "{}").getStatus());
        verifyNoInteractions(cobros.pagos);
        when(cobros.request.getHeader("Origin")).thenReturn(SesionResource.ORIGEN);
        Response creada = Response.status(201).build();
        when(cobros.pagos.registrarAbono(factura, "{}")).thenReturn(creada);
        assertEquals(201, cobros.registrarAbono(factura, "{}").getStatus());
    }

    private static EmpleadoRol asignacion(String nombre, boolean activa) {
        Rol rol = new Rol(UUID.randomUUID());
        rol.setNombre(nombre);
        rol.setActivo(true);
        EmpleadoRol asignacion = new EmpleadoRol(UUID.randomUUID());
        asignacion.setIdRol(rol);
        asignacion.setActivo(activa);
        return asignacion;
    }
}
