package boundary.rest.seguridad;

import boundary.rest.SesionResource;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;

/** Evita que las vistas JSF heredadas permitan saltarse los permisos REST. */
@WebFilter(urlPatterns = {"*.xhtml", "*.jsf", "*.faces", "/faces/*", "/paginas/*"})
public class VistasInternasFilter implements Filter {
    @Inject AccesoPersonal acceso;

    @Override public void doFilter(ServletRequest solicitud, ServletResponse respuesta, FilterChain siguiente)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) solicitud;
        HttpServletResponse response = (HttpServletResponse) respuesta;
        String ruta = request.getServletPath() + Objects.toString(request.getPathInfo(), "");
        if (ruta.startsWith("/jakarta.faces.resource/") || ruta.startsWith("/faces/jakarta.faces.resource/")) {
            siguiente.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
                && !SesionResource.ORIGEN.equals(request.getHeader("Origin"))) {
            response.sendError(403); return;
        }
        AccesoPersonal.Identidad identidad;
        try { identidad = acceso.recuperar(request); }
        catch (RuntimeException ex) { response.sendError(503); return; }
        if (identidad == null) { response.sendError(401); return; }
        boolean permitido;
        if (ruta.matches(".*/(Factura|Pago)\\.(xhtml|jsf|faces)")) {
            permitido = identidad.getRoles().contains("cajero");
        } else if (ruta.matches(".*/Orden\\.(xhtml|jsf|faces)")) {
            permitido = identidad.getRoles().contains("camarero") || identidad.getRoles().contains("cajero");
        } else {
            permitido = identidad.getRoles().contains("administrador");
        }
        if (!permitido) { response.sendError(403); return; }
        siguiente.doFilter(request, response);
    }
}
