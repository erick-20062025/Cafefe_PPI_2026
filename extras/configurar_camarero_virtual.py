#!/usr/bin/env python3
"""Alta idempotente del empleado técnico autorizado y configuración local Liberty.
Requiere --aplicar. No modifica empleados, roles o asignaciones ajenos.
"""
import argparse
from pathlib import Path
from types import SimpleNamespace
from probar_api_facturas import Pruebas

SQL = """
BEGIN;
SELECT pg_advisory_xact_lock(1152026);
DO $$
DECLARE r uuid; e uuid; n integer;
BEGIN
 SELECT count(*) INTO n FROM rol WHERE lower(trim(nombre))='camarero' AND activo=true;
 IF n <> 1 THEN RAISE EXCEPTION 'Se requiere un único rol camarero activo existente'; END IF;
 SELECT id_rol INTO r FROM rol WHERE lower(trim(nombre))='camarero' AND activo=true;
 SELECT count(*) INTO n FROM empleado WHERE comentarios='CAFEFE_EMPLEADO_TECNICO_WEB_V1';
 IF n > 1 THEN RAISE EXCEPTION 'Empleado técnico duplicado; revisar manualmente'; END IF;
 IF n = 0 THEN
   IF EXISTS (SELECT 1 FROM empleado WHERE nombre='Cafefe Web') THEN
     RAISE EXCEPTION 'Existe un nombre coincidente sin marcador; no se reutiliza';
   END IF;
   INSERT INTO empleado(id_empleado,nombre,apellido,activo,comentarios)
     VALUES(gen_random_uuid(),'Cafefe Web','Servicio de pedidos',true,'CAFEFE_EMPLEADO_TECNICO_WEB_V1') RETURNING id_empleado INTO e;
 ELSE
   SELECT id_empleado INTO e FROM empleado WHERE comentarios='CAFEFE_EMPLEADO_TECNICO_WEB_V1' AND activo=true;
   IF e IS NULL THEN RAISE EXCEPTION 'Empleado técnico desactivado; no se reactiva automáticamente'; END IF;
 END IF;
 SELECT count(*) INTO n FROM empleado_rol WHERE id_empleado=e AND id_rol=r;
 IF n > 1 THEN RAISE EXCEPTION 'Asignación duplicada; revisar manualmente'; END IF;
 IF n = 0 THEN
   INSERT INTO empleado_rol(id_empleado_rol,id_empleado,id_rol,activo,observaciones)
     VALUES(gen_random_uuid(),e,r,true,'CAFEFE_ASIGNACION_WEB_V1');
 ELSIF NOT EXISTS(SELECT 1 FROM empleado_rol WHERE id_empleado=e AND id_rol=r AND activo=true AND observaciones='CAFEFE_ASIGNACION_WEB_V1') THEN
   RAISE EXCEPTION 'Asignación existente no compatible; no se modifica';
 END IF;
END $$;
SELECT er.id_empleado_rol FROM empleado_rol er JOIN empleado e USING(id_empleado)
 WHERE e.comentarios='CAFEFE_EMPLEADO_TECNICO_WEB_V1' AND er.observaciones='CAFEFE_ASIGNACION_WEB_V1';
COMMIT;
"""

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aplicar',action='store_true'); args=parser.parse_args()
    if not args.aplicar:
        print('Preparado: crea solo Cafefe Web y su asignación al camarero existente. Ejecuta --aplicar tras autorización.'); return
    db=Pruebas(SimpleNamespace(raiz='http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'))
    asignacion=db.sql(SQL).splitlines()[-1].strip()
    import uuid
    asignacion=str(uuid.UUID(asignacion))
    root=Path(__file__).resolve().parents[1]
    for relative in ['src/main/liberty/config/server.env','target/liberty/wlp/usr/servers/defaultServer/server.env']:
        path=root/relative; path.parent.mkdir(parents=True,exist_ok=True)
        lines=path.read_text().splitlines() if path.exists() else []
        lines=[line for line in lines if not line.startswith('CAFEFE_CAMARERO_VIRTUAL_ID=')]
        lines.append('CAFEFE_CAMARERO_VIRTUAL_ID='+asignacion)
        path.write_text('\n'.join(lines)+'\n'); path.chmod(0o600)
    print('Empleado técnico y asignación verificados. Configuración local guardada; reiniciar Liberty. UUID no expuestos.')

if __name__=='__main__': main()
