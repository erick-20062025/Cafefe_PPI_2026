package boundary.rest.seguridad;

import boundary.rest.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermisosPrivadosTest {
    @BeforeEach void runtime() { RestTestRuntime.instalar(); }
    @AfterEach void limpiar() { RestTestRuntime.limpiar(); }

    static Stream<Arguments> matriz() {
        return Stream.of(FacturaResource.class, PagoResource.class, EmpleadoResource.class,
                EmpleadoRolResource.class, RolResource.class).flatMap(tipo ->
            Stream.of("anonimo", "cajero", "barista", "cocinero", "camarero", "administrador").map(rol -> {
                boolean cobro = tipo == FacturaResource.class || tipo == PagoResource.class;
                int esperado = rol.equals("anonimo") ? 401 : rol.equals(cobro ? "cajero" : "administrador") ? 200 : 403;
                return Arguments.of(tipo, rol, esperado);
            }));
    }

    @ParameterizedTest @MethodSource("matriz")
    void validaRolesEnLecturasYEscrituras(Class<?> tipo, String rol, int esperado) {
        for (String metodo : List.of("GET", "POST", "PUT", "DELETE")) {
            var filtro = new RecepcionFilter();
            filtro.acceso = mock(AccesoPersonal.class); filtro.request = mock(HttpServletRequest.class);
            filtro.recurso = mock(ResourceInfo.class); doReturn(tipo).when(filtro.recurso).getResourceClass();
            when(filtro.request.getHeader("Origin")).thenReturn(SesionResource.ORIGEN);
            if (!rol.equals("anonimo")) when(filtro.acceso.recuperar(filtro.request))
                    .thenReturn(new AccesoPersonal.Identidad("Prueba", List.of(rol)));
            var contexto = mock(ContainerRequestContext.class); when(contexto.getMethod()).thenReturn(metodo);
            filtro.filter(contexto);
            if (esperado == 200) verify(contexto, never()).abortWith(any());
            else verify(contexto).abortWith(argThat(r -> r.getStatus() == esperado));
            var respuesta = mock(ContainerResponseContext.class);
            var headers = new jakarta.ws.rs.core.MultivaluedHashMap<String,Object>();
            when(respuesta.getHeaders()).thenReturn(headers);
            filtro.filter(contexto, respuesta);
            assertEquals("no-store", headers.getFirst("Cache-Control"));
        }
    }

    @Test void rechazaOrigenSinConsultarDatosYPermitePreflight() {
        var f = new RecepcionFilter(); f.acceso=mock(AccesoPersonal.class);
        f.request=mock(HttpServletRequest.class); f.recurso=mock(ResourceInfo.class);
        doReturn(PagoResource.class).when(f.recurso).getResourceClass();
        var c=mock(ContainerRequestContext.class); when(c.getMethod()).thenReturn("POST");
        when(f.request.getHeader("Origin")).thenReturn("http://ajeno.example");
        f.filter(c); verify(c).abortWith(argThat(r -> r.getStatus()==403)); verifyNoInteractions(f.acceso);
        reset(c); when(c.getMethod()).thenReturn("OPTIONS"); f.filter(c);
        verify(c,never()).abortWith(any()); verifyNoInteractions(f.acceso);
    }

    @ParameterizedTest @CsvSource({
        "/paginas/Empleado.xhtml,cajero,403", "/faces/paginas/Rol.xhtml,barista,403",
        "/paginas/Empleado.jsf,anonimo,401", "/paginas/Factura.xhtml,cajero,200",
        "/paginas/Pago.faces,cocinero,403", "/paginas/Pago.xhtml,administrador,403",
        "/paginas/Empleado.xhtml,administrador,200", "/paginas/Orden.xhtml,camarero,200"
    })
    void vistasHeredadasNoEvadenPermisos(String ruta, String rol, int esperado) throws Exception {
        var f=new VistasInternasFilter(); f.acceso=mock(AccesoPersonal.class);
        var request=mock(HttpServletRequest.class); var response=mock(HttpServletResponse.class);
        var chain=mock(FilterChain.class); when(request.getServletPath()).thenReturn(ruta);
        when(request.getMethod()).thenReturn("GET");
        if (!rol.equals("anonimo")) when(f.acceso.recuperar(request)).thenReturn(new AccesoPersonal.Identidad("Prueba",List.of(rol)));
        f.doFilter(request,response,chain);
        if(esperado==200) verify(chain).doFilter(request,response);
        else { verify(response).sendError(esperado); verifyNoInteractions(chain); }
    }

    @Test void recursosJsfSiguenPublicos() throws Exception {
        var f=new VistasInternasFilter(); f.acceso=mock(AccesoPersonal.class);
        var request=mock(HttpServletRequest.class); var response=mock(HttpServletResponse.class);
        var chain=mock(FilterChain.class);
        when(request.getServletPath()).thenReturn("/jakarta.faces.resource/components.js.xhtml");
        f.doFilter(request,response,chain); verify(chain).doFilter(request,response); verifyNoInteractions(f.acceso);
    }
    @ParameterizedTest @CsvSource({
        "crearPublica,POST,anonimo,200", "cotizacion,POST,anonimo,200",
        "listar,GET,anonimo,401", "listar,GET,barista,403", "listar,GET,camarero,200",
        "productosFacturables,GET,cajero,200", "productosFacturables,GET,camarero,403",
        "crear,POST,anonimo,401", "crear,POST,camarero,200", "crear,POST,cajero,403",
        "agregarProductos,POST,barista,403", "quitarProducto,DELETE,anonimo,401",
        "aplicarPromocion,POST,cajero,403", "eliminar,DELETE,anonimo,401"
    })
    void rutasInternasOrden(String operacion,String metodo,String rol,int esperado) {
        var f=new RecepcionFilter(); f.acceso=mock(AccesoPersonal.class);
        f.request=mock(HttpServletRequest.class); f.recurso=mock(ResourceInfo.class);
        doReturn(OrdenResource.class).when(f.recurso).getResourceClass();
        when(f.recurso.getResourceMethod()).thenReturn(java.util.Arrays.stream(OrdenResource.class.getMethods())
                .filter(m->m.getName().equals(operacion)).findFirst().orElseThrow());
        when(f.request.getHeader("Origin")).thenReturn(SesionResource.ORIGEN);
        if(!rol.equals("anonimo")) when(f.acceso.recuperar(f.request)).thenReturn(new AccesoPersonal.Identidad("Prueba",List.of(rol)));
        var c=mock(ContainerRequestContext.class);when(c.getMethod()).thenReturn(metodo);f.filter(c);
        if(esperado==200) verify(c,never()).abortWith(any());
        else verify(c).abortWith(argThat(r->r.getStatus()==esperado));
    }

    @Test void catalogoPublicoSoloLectura() {
        for(Class<?> tipo:List.of(ProductoResource.class,TipoProductoResource.class,CajaResource.class,
                CaracteristicaResource.class,ProductoTipoProductoResource.class,ProductoCaracteristicaResource.class,
                DescuentoResource.class,DescuentoProductoResource.class,TipoCaracteristicaResource.class,TipoDescuentoResource.class)) {
            var f=new RecepcionFilter();f.acceso=mock(AccesoPersonal.class);f.request=mock(HttpServletRequest.class);
            f.recurso=mock(ResourceInfo.class);doReturn(tipo).when(f.recurso).getResourceClass();
            when(f.request.getHeader("Origin")).thenReturn(SesionResource.ORIGEN);
            var c=mock(ContainerRequestContext.class);when(c.getMethod()).thenReturn("GET");f.filter(c);
            verify(c,never()).abortWith(any());verifyNoInteractions(f.acceso);
            when(c.getMethod()).thenReturn("POST");f.filter(c);
            verify(c).abortWith(argThat(r->r.getStatus()==401));
        }
    }
}
