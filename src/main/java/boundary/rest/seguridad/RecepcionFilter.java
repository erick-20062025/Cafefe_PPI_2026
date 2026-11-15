package boundary.rest.seguridad;

import boundary.rest.*;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

/** Permisos sobre el recurso resuelto por JAX-RS, no sobre cadenas de URL. */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RecepcionFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Inject AccesoPersonal acceso;
    @Context HttpServletRequest request;
    @Context ResourceInfo recurso;

    private boolean administracion() {
        Class<?> tipo = recurso.getResourceClass();
        return tipo != null && (EmpleadoResource.class.isAssignableFrom(tipo)
                || EmpleadoRolResource.class.isAssignableFrom(tipo) || RolResource.class.isAssignableFrom(tipo));
    }
    private String operacionOrden() {
        Class<?> tipo = recurso.getResourceClass();
        return tipo != null && OrdenResource.class.isAssignableFrom(tipo) && recurso.getResourceMethod() != null
                ? recurso.getResourceMethod().getName() : "";
    }
    @Override public void filter(ContainerRequestContext contexto) {
        if ("OPTIONS".equals(contexto.getMethod())) return;
        String orden = operacionOrden();
        boolean pendientes = "pendientes".equals(orden);
        boolean detalle = "productos".equals(orden) && "GET".equals(contexto.getMethod());
        if (!pendientes && !detalle && !administracion()) return;
        if (administracion() && !"GET".equals(contexto.getMethod())
                && !SesionResource.ORIGEN.equals(request.getHeader("Origin"))) {
            contexto.abortWith(SesionResource.error(403, "Origen no permitido.")); return;
        }
        try {
            AccesoPersonal.Identidad identidad = acceso.recuperar(request);
            if (identidad == null) contexto.abortWith(SesionResource.error(401, "Inicia sesión para consultar este recurso."));
            else if (pendientes && identidad.getRoles().stream().noneMatch(rol -> rol.equals("barista") || rol.equals("cocinero")))
                contexto.abortWith(SesionResource.error(403, "Tu rol no permite acceder a Recepción."));
        } catch (RuntimeException ex) { contexto.abortWith(SesionResource.error(503, "No fue posible comprobar el acceso.")); }
    }
    @Override public void filter(ContainerRequestContext solicitud, ContainerResponseContext respuesta) {
        if (administracion() || "pendientes".equals(operacionOrden()) || "productos".equals(operacionOrden()))
            respuesta.getHeaders().putSingle("Cache-Control", "no-store");
    }
}
