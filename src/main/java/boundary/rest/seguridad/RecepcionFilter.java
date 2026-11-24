package boundary.rest.seguridad;

import boundary.rest.*;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;

/** Permisos sobre clases y métodos resueltos, independientes de alias de URL. */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RecepcionFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Inject AccesoPersonal acceso;
    @Context HttpServletRequest request;
    @Context ResourceInfo recurso;

    private Set<String> roles(String metodo) {
        Class<?> tipo = recurso.getResourceClass();
        boolean lectura = "GET".equals(metodo) || "HEAD".equals(metodo);
        if (tipo == SesionResource.class) return Set.of(); // Valida su propio ciclo de sesión.
        if (tipo == CobrosResource.class || tipo == FacturaResource.class || tipo == PagoResource.class)
            return Set.of("cajero");
        if (tipo == EmpleadoResource.class || tipo == EmpleadoRolResource.class || tipo == RolResource.class)
            return Set.of("administrador");
        if (tipo == OrdenResource.class) {
            String operacion = recurso.getResourceMethod() == null ? "" : recurso.getResourceMethod().getName();
            return switch (operacion) {
                case "crearPublica", "cotizacion" -> Set.of();
                case "pendientes" -> Set.of("barista", "cocinero");
                case "productosFacturables" -> Set.of("cajero");
                case "productos" -> Set.of("barista", "cocinero", "camarero", "cajero");
                case "listar", "buscar", "promociones" -> Set.of("camarero", "cajero");
                default -> Set.of("camarero");
            };
        }
        // Solo lecturas del catálogo son públicas; sus escrituras son administrativas.
        Set<Class<?>> catalogo = Set.of(CajaResource.class, CaracteristicaResource.class,
            DescuentoProductoResource.class, DescuentoResource.class, ProductoCaracteristicaResource.class,
            ProductoResource.class, ProductoTipoProductoResource.class, TipoCaracteristicaResource.class,
            TipoDescuentoResource.class, TipoProductoResource.class);
        if (lectura && catalogo.contains(tipo)) return Set.of();
        return Set.of("administrador"); // Recurso nuevo: privado por defecto.
    }

    @Override public void filter(ContainerRequestContext contexto) {
        if ("OPTIONS".equals(contexto.getMethod())) return;
        Set<String> permitidos = roles(contexto.getMethod());
        if (permitidos.isEmpty()) return;
        if (!Set.of("GET", "HEAD").contains(contexto.getMethod())
                && !SesionResource.origenPermitido(request)) {
            contexto.abortWith(SesionResource.error(403, "Origen no permitido.")); return;
        }
        try {
            AccesoPersonal.Identidad identidad = acceso.recuperar(request);
            if (identidad == null)
                contexto.abortWith(SesionResource.error(401, "Inicia sesión para consultar este recurso."));
            else if (identidad.getRoles().stream().noneMatch(permitidos::contains))
                contexto.abortWith(SesionResource.error(403, "Tu rol no permite acceder a este recurso."));
        } catch (RuntimeException ex) {
            contexto.abortWith(SesionResource.error(503, "No fue posible comprobar el acceso."));
        }
    }
    @Override public void filter(ContainerRequestContext solicitud, ContainerResponseContext respuesta) {
        if (!roles(solicitud.getMethod()).isEmpty()) respuesta.getHeaders().putSingle("Cache-Control", "no-store");
    }
}
