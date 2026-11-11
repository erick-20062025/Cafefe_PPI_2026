
package validation;

import entity.Caracteristica;
import entity.TipoCaracteristica;

public final class CaracteristicaValidator {

    private CaracteristicaValidator() {
    }

    public static void validar(Caracteristica caracteristica) {

        if (caracteristica == null) {
            throw new IllegalArgumentException(
                    "La característica no puede ser nula");
        }

        String nombre = caracteristica.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre es obligatorio");
        }

        if (nombre.length() > 155) {
            throw new IllegalArgumentException(
                    "El nombre no puede superar 155 caracteres");
        }

        if (caracteristica.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar si la característica está activa o inactiva");
        }

        TipoCaracteristica tipo =
                caracteristica.getIdTipoCaracteristica();

        if (tipo == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un tipo de característica");
        }

        if (!Boolean.TRUE.equals(tipo.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede asignar un tipo de característica inactivo");
        }
    }
}
