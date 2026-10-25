"""Temporary HTTPS demo access to the existing Debian HTTP service, no router changes."""
import argparse
import hashlib
import ipaddress
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote
import requests

ROOT = Path(__file__).resolve().parents[2]
VERSION = '2026.10.3'
DOWNLOAD = f'https://github.com/cloudflare/cloudflared/releases/download/{VERSION}/cloudflared-windows-amd64.exe'
SHA256 = '76b2805253d0d6ffacb092a7acfbee99e7af6917835c0c5410e23f3ef5052da2'
EXE = ROOT / '.tools/cloudflared/cloudflared.exe'
PAGES = 'https://29925-mosalvador.github.io/BilleteraX/'

def prepare(http):
    if EXE.exists() and hashlib.sha256(EXE.read_bytes()).hexdigest() == SHA256:
        return
    EXE.parent.mkdir(parents=True, exist_ok=True)
    temp = EXE.with_suffix('.download')
    print(f'Descargando cloudflared {VERSION} desde Cloudflare en GitHub...')
    try:
        digest = hashlib.sha256()
        with http.get(DOWNLOAD, stream=True, timeout=(15, 90)) as response:
            response.raise_for_status()
            with temp.open('wb') as output:
                for chunk in response.iter_content(1024 * 1024):
                    digest.update(chunk)
                    output.write(chunk)
        if digest.hexdigest() != SHA256:
            raise RuntimeError('El SHA-256 no coincide; no se ejecutará el archivo descargado.')
        temp.replace(EXE)
    finally:
        temp.unlink(missing_ok=True)
    print('Descarga verificada. No se instaló un servicio de Windows.')

def main():
    parser = argparse.ArgumentParser(description='Túnel HTTPS temporal para la banca web BilleteraX.')
    parser.add_argument('--host', default='192.168.18.76')
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--download-only', action='store_true')
    args = parser.parse_args()
    ipaddress.IPv4Address(args.host)
    if not 1 <= args.port <= 65535:
        raise ValueError('Puerto inválido')
    http = requests.Session()
    http.trust_env = False
    prepare(http)
    if args.download_only:
        return
    origin = f'http://{args.host}:{args.port}'
    health = http.get(origin + '/actuator/health', timeout=10)
    health.raise_for_status()
    page = http.get(origin + '/', timeout=10)
    if health.json().get('status') != 'UP' or page.status_code != 200 or 'BilleteraX · Banca web' not in page.text:
        raise RuntimeError('Primero actualiza el backend Debian con deploy-debian.py --skip-packages. Aún no sirve la banca web.')
    print('Se publicará temporalmente el servicio BilleteraX de Debian a través de Cloudflare.')
    print('Las cuentas requieren registro/login. El túnel no publica SSH ni la base H2.')
    print('Mantén esta ventana abierta. Ctrl+C cierra el túnel y termina el acceso público.')
    process = subprocess.Popen([str(EXE), 'tunnel', '--no-autoupdate', '--url', origin],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding='utf-8', errors='replace')
    shown = False
    try:
        for line in process.stdout:
            match = re.search(r'https://[a-z0-9-]+\.trycloudflare\.com', line)
            if match and not shown:
                base = match.group(0)
                print('\nSERVIDOR HTTPS: ' + base)
                print('ABRE LA BANCA WEB: ' + PAGES + '?servidor=' + quote(base, safe=''))
                print('Si Pages todavía no está publicado, termina antes el push y la activación de Pages.\n', flush=True)
                shown = True
            elif 'ERR ' in line:
                print(line.strip(), flush=True)
        code = process.wait()
        if code:
            raise RuntimeError(f'cloudflared terminó con código {code}. Comprueba Internet y vuelve a intentarlo.')
    except KeyboardInterrupt:
        print('\nCerrando el túnel de la demostración...')
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()

if __name__ == '__main__':
    try:
        main()
    except (Exception,) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        sys.exit(1)
