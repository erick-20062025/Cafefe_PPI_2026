package boundary.rest.seguridad;
import boundary.rest.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
class RecepcionFilterTest {
    @Test void permisosSeAplicanAlMetodoResueltoInclusoSinCompararUrl() throws Exception {
        var filtro=new RecepcionFilter(); filtro.acceso=mock(AccesoPersonal.class);
        filtro.request=mock(HttpServletRequest.class); filtro.recurso=mock(ResourceInfo.class);
        var contexto=mock(ContainerRequestContext.class);
        when(contexto.getMethod()).thenReturn("GET");
        doReturn(OrdenResource.class).when(filtro.recurso).getResourceClass();
        when(filtro.recurso.getResourceMethod()).thenReturn(OrdenResource.class.getMethod("pendientes"));
        // No se construye respuesta: se comprueba con RuntimeDelegate de integración en HTTP.
        when(filtro.acceso.recuperar(filtro.request)).thenReturn(new AccesoPersonal.Identidad("Prueba",List.of("cocinero")));
        filtro.filter(contexto); verify(contexto,never()).abortWith(any());verify(filtro.acceso).recuperar(filtro.request);
    }
    @Test void noInterceptaPedidoPublico() throws Exception {
        var filtro=new RecepcionFilter(); filtro.acceso=mock(AccesoPersonal.class); filtro.recurso=mock(ResourceInfo.class);
        var contexto=mock(ContainerRequestContext.class); when(contexto.getMethod()).thenReturn("POST");
        doReturn(OrdenResource.class).when(filtro.recurso).getResourceClass();
        when(filtro.recurso.getResourceMethod()).thenReturn(OrdenResource.class.getMethod("crearPublica",String.class));
        filtro.filter(contexto);verifyNoInteractions(filtro.acceso);verify(contexto,never()).abortWith(any());
    }
}
