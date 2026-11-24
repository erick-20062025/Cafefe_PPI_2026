package boundary.rest;
import boundary.rest.seguridad.AccesoPersonal;
import jakarta.servlet.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SesionResourceTest {
    SesionResource recurso; HttpSession nueva; UUID id;
    @BeforeEach void iniciar() {
        RestTestRuntime.instalar(); recurso = new SesionResource();
        recurso.acceso = mock(AccesoPersonal.class); recurso.request = mock(HttpServletRequest.class);
        nueva = mock(HttpSession.class); id = UUID.randomUUID();
        when(recurso.request.getHeader("Origin")).thenReturn(SesionResource.ORIGEN);
        when(recurso.request.getSession(true)).thenReturn(nueva);
    }
    @AfterEach void terminar() { RestTestRuntime.limpiar(); }
    String cuerpo() { return "{\"nombre\":\"Prueba\",\"uuid\":\""+id+"\"}"; }
    @Test void iniciaRenuevaYNoExponeIdentificador() {
        var identidad = new AccesoPersonal.Identidad("Prueba", List.of("barista"));
        when(recurso.acceso.consultar(id)).thenReturn(identidad);
        HttpSession vieja = mock(HttpSession.class); when(recurso.request.getSession(false)).thenReturn(vieja);
        var respuesta = recurso.entrar(cuerpo()); assertEquals(200,respuesta.getStatus());
        assertSame(identidad,respuesta.getEntity());
        verify(vieja).invalidate(); verify(nueva).setMaxInactiveInterval(1800); verify(nueva).setAttribute(AccesoPersonal.ATRIBUTO,id);
    }
    @Test void rechazaCredencialesNombreYRoles() {
        assertEquals(401,recurso.entrar(cuerpo()).getStatus());
        when(recurso.acceso.consultar(id)).thenReturn(new AccesoPersonal.Identidad("Otro",List.of("cajero")));
        assertEquals(401,recurso.entrar(cuerpo()).getStatus());
        when(recurso.acceso.consultar(id)).thenReturn(new AccesoPersonal.Identidad("Prueba",List.of()));
        assertEquals(403,recurso.entrar(cuerpo()).getStatus()); verify(recurso.request,never()).getSession(true);
    }
    @Test void validaJsonSinConsultarDatos() {
        for(String json: new String[]{"", "{", "[]", "{}", "{\"nombre\":null,\"uuid\":12}","{\"nombre\":\"Prueba\",\"uuid\":\"invalido\"}"})
            assertEquals(400,recurso.entrar(json).getStatus());
        verifyNoInteractions(recurso.acceso);
    }
    @Test void rechazaOrigenEnMutaciones() {
        when(recurso.request.getHeader("Origin")).thenReturn("http://no-autorizado.example");
        assertEquals(403,recurso.entrar(cuerpo()).getStatus()); assertEquals(403,recurso.salir().getStatus());
        verifyNoInteractions(recurso.acceso);
    }
    @Test void origenDelProxyAceptaPuertosDiferentesYRechazaAjeno() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Host")).thenReturn("localhost:18080");
        when(req.getHeader("Origin")).thenReturn("http://localhost:18080");
        assertTrue(SesionResource.origenPermitido(req));
        when(req.getHeader("Origin")).thenReturn("http://sitio-ajeno.example");
        assertFalse(SesionResource.origenPermitido(req));
        when(req.getHeader("Origin")).thenReturn("null");
        assertFalse(SesionResource.origenPermitido(req));
        when(req.getHeader("Origin")).thenReturn("http://localhost:18080.evil.example");
        assertFalse(SesionResource.origenPermitido(req));
        when(req.getHeader("Origin")).thenReturn("http://localhost:18080/path");
        assertFalse(SesionResource.origenPermitido(req));
        when(req.getHeader("Host")).thenReturn("localhost");
        when(req.getHeader("Origin")).thenReturn("http://localhost");
        assertTrue(SesionResource.origenPermitido(req));
    }
    @Test void recuperaYCierra() {
        assertEquals(401,recurso.recuperar().getStatus());
        when(recurso.acceso.recuperar(recurso.request)).thenReturn(new AccesoPersonal.Identidad("Prueba",List.of("cocinero")));
        assertEquals(200,recurso.recuperar().getStatus());
        when(recurso.request.getSession(false)).thenReturn(nueva);
        assertEquals(204,recurso.salir().getStatus());verify(nueva).invalidate();
    }
}
