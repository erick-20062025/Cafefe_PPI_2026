package entity;

/** Convención descriptiva existente de HU2; no almacena estados ni importes. */
public final class DescripcionOrden {
    private DescripcionOrden() { }
    public static String conCliente(String nombre, String nota) {
        if (nombre == null || nombre.isBlank()) return nota;
        return "Cliente: " + nombre.strip() + (nota == null || nota.isBlank() ? "" : " | " + nota);
    }
    public static String cliente(String nota) {
        if (nota == null || !nota.startsWith("Cliente: ")) return null;
        String nombre = nota.substring(9).split("\\s*\\|\\s*|\\r?\\n", 2)[0].strip();
        return nombre.isBlank() ? null : nombre;
    }
}
