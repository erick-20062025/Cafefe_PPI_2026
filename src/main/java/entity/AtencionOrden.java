package entity;

/** Identifica a quien tomó la orden, nunca al cajero de su factura. */
public final class AtencionOrden {
    private AtencionOrden() { }
    public static String describir(Orden orden, String asignacionVirtual) {
        if (orden == null || orden.getIdEmpleadoRol() == null) return null;
        EmpleadoRol asignacion = orden.getIdEmpleadoRol();
        if (asignacion.getIdEmpleadoRol() != null && asignacion.getIdEmpleadoRol().toString().equals(asignacionVirtual))
            return "Pedido web";
        if (asignacion.getIdRol() == null || !"camarero".equalsIgnoreCase(asignacion.getIdRol().getNombre())) return null;
        Empleado e = asignacion.getIdEmpleado();
        if (e == null) return null;
        String nombre = ((e.getNombre() == null ? "" : e.getNombre()) + " " + (e.getApellido() == null ? "" : e.getApellido())).strip();
        return nombre.isBlank() ? null : nombre;
    }
}
