package validation;

import entity.Descuento;
import java.time.ZoneId;

public final class DescuentoValidator {

    private DescuentoValidator() {
    }

    public static void validar(Descuento descuento) {

        if (descuento == null) {
            throw new IllegalArgumentException(
                    "El descuento no puede ser nulo");
        }

        if (descuento.getIdDescuento() == null) {
            throw new IllegalArgumentException(
                    "El identificador del descuento es obligatorio");
        }

        if (descuento.getIdTipoDescuento() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un tipo de descuento");
        }

        String nombre = descuento.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe ingresar el nombre del descuento");
        }

        if (nombre.length() > 155) {
            throw new IllegalArgumentException(
                    "El nombre del descuento no puede superar 155 caracteres");
        }

        if (descuento.getFechaDesde() == null) {
            throw new IllegalArgumentException(
                    "La fecha desde es obligatoria");
        }

        if (descuento.getFechaHasta() != null) {

            ZoneId zona = ZoneId.systemDefault();

            var fechaDesde = descuento.getFechaDesde()
                    .toInstant()
                    .atZone(zona)
                    .toLocalDate();

            var fechaHasta = descuento.getFechaHasta()
                    .toInstant()
                    .atZone(zona)
                    .toLocalDate();

            if (fechaHasta.isBefore(fechaDesde)) {
                throw new IllegalArgumentException(
                        "La fecha hasta no puede ser anterior a la fecha desde");
            }
        }

    }
}
