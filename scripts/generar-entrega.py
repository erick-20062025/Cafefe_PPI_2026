#!/usr/bin/env python3
"""Genera un ZIP preliminar verificable. No compila, publica ni modifica Git."""
import argparse
from io import BytesIO
from pathlib import Path
import subprocess
from zipfile import ZipFile, ZIP_DEFLATED
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EXTENSIONES = {'.js', '.html', '.css', '.png', '.jpg', '.jpeg', '.webp', '.svg', '.ico', '.webmanifest'}


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()


def validar_war(war):
    with ZipFile(war) as archivo:
        if archivo.testzip():
            raise ValueError('WAR dañado')
        if any(p.startswith('WEB-INF/lib/') and p.endswith('.jar') for p in archivo.namelist()):
            raise ValueError('El WAR debe usar dependencias provided suministradas por Liberty')
        xml = ET.fromstring(archivo.read('WEB-INF/classes/META-INF/persistence.xml'))
        props = {p.attrib['name']: p.attrib['value'] for p in xml.iter() if p.tag.endswith('property')}
        if props.get('jakarta.persistence.schema-generation.database.action') != 'none' or props.get('eclipselink.ddl-generation') != 'none':
            raise ValueError('JPA no debe crear/modificar esquema')


def generar(frontend, war, plantilla, salida):
    validar_war(war)
    src = frontend / 'src'
    requeridos = ['index.html', 'boundary/app.js', 'config/api.js', 'sw.js', 'manifest.webmanifest']
    for name in requeridos:
        if not (src / name).is_file():
            raise ValueError(f'Falta src/{name}')
    interno = BytesIO()
    with ZipFile(interno, 'w', ZIP_DEFLATED) as z:
        for p in sorted(src.rglob('*')):
            if p.is_symlink():
                raise ValueError(f'No se empaquetan enlaces simbólicos: {p}')
            if not p.is_file():
                continue
            relativo = p.relative_to(frontend)
            if any(parte.startswith('.') for parte in relativo.parts) or p.suffix.lower() not in EXTENSIONES:
                raise ValueError(f'Revisar archivo no permitido en src: {relativo}')
            z.writestr(str(relativo), p.read_bytes())
    texto = plantilla.read_text()
    for token, valor in {
        '{{COMMIT_BACKEND}}': git(ROOT, 'rev-parse', 'HEAD'),
        '{{COMMIT_FRONTEND}}': git(frontend, 'rev-parse', 'HEAD'),
        '{{URL_BACKEND}}': 'https://github.com/erick-20062025/Cafefe_PPI_2026',
        '{{URL_FRONTEND}}': 'https://github.com/erick-20062025/Cafefe_Fronten_2026',
    }.items():
        texto = texto.replace(token, valor)
    texto += '\nPRELIMINAR: requiere pruebas finales, nombres completos, Docker Hub y commits definitivos.\n'
    for repo, nombre in [(ROOT, 'Backend'), (frontend, 'Frontend')]:
        # Excepción explícita: configuración personal jamás incluida.
        cambios = git(repo, 'status', '--porcelain', '--', '.', ':!nb-configuration.xml')
        texto += f'{nombre}: {"cambios locales pendientes; commit no representa todos los archivos" if cambios else "árbol de trabajo limpio"}.\n'
    salida.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(salida, 'w', ZIP_DEFLATED) as z:
        z.write(war, 'cafefe.war')
        z.writestr('frontend-src.zip', interno.getvalue())
        z.writestr('integrantes.txt', texto)
    with ZipFile(salida) as z:
        assert set(z.namelist()) == {'cafefe.war', 'frontend-src.zip', 'integrantes.txt'}
        assert z.testzip() is None
        with ZipFile(BytesIO(z.read('frontend-src.zip'))) as frontzip:
            assert frontzip.testzip() is None
            assert all(name.startswith('src/') for name in frontzip.namelist())
    print(f'ZIP preliminar verificado: {salida}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frontend', type=Path, default=ROOT.parent / 'Cafefe_Fronten_2026')
    parser.add_argument('--war', type=Path, default=ROOT / 'target/Cafefe_PPI_2026-1.0-SNAPSHOT.war')
    parser.add_argument('--plantilla', type=Path, default=ROOT / 'docker/integrantes.template.txt')
    parser.add_argument('--salida', type=Path, default=ROOT / 'target/entrega/entrega-cafefe-preliminar.zip')
    args = parser.parse_args()
    generar(args.frontend, args.war, args.plantilla, args.salida)
