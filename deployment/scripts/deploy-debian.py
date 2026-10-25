"""Run from Windows. Password stays in memory; existing SSH host key is required."""
import argparse
from datetime import datetime, timezone
import getpass
import hashlib
import ipaddress
import json
from urllib.parse import urlsplit
from pathlib import Path
import shlex
import secrets
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '.tools' / 'python-debian'))
import paramiko
import requests
import websocket


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description='Desplegar BilleteraX en Debian por SSH y comprobar el servicio.')
    parser.add_argument('--host', default='192.168.18.76')
    parser.add_argument('--skip-packages', action='store_true', help='Conservar Java/curl instalados durante una actualizacion.')
    parser.add_argument('--check-local', action='store_true', help='Validar archivos/dependencias sin conectar ni instalar.')
    parser.add_argument('--gateway-url', help='Dirección HTTPS fija del auxiliar público.')
    args = parser.parse_args()
    if args.gateway_url:
        parsed = urlsplit(args.gateway_url)
        require(parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password
                and parsed.port in (None, 443) and parsed.path in ('', '/') and not parsed.query and not parsed.fragment,
                'El auxiliar requiere una dirección HTTPS sin rutas, credenciales ni parámetros.')
        args.gateway_url = args.gateway_url.rstrip('/')
    ipaddress.IPv4Address(args.host)
    jar = ROOT / 'backend/build/libs/billeterax.jar'
    assets = [ROOT / p for p in ('deployment/debian/install.sh',
              'deployment/debian/billeterax.env.example', 'deployment/systemd/billeterax.service')]
    require(jar.is_file() and all(p.is_file() for p in assets), 'Faltan el JAR o archivos de deployment.')
    with zipfile.ZipFile(jar) as archive:
        require(archive.testzip() is None, 'El JAR esta dañado.')
    for path in (assets[0], assets[2]):
        require(b'\r' not in path.read_bytes(), 'Se requieren finales LF: ' + str(path))
    known = Path.home() / '.ssh' / 'known_hosts'
    require(known.is_file(), 'Primero conecta por SSH y verifica la huella de Debian.')
    jar_hash = hashlib.sha256(jar.read_bytes()).hexdigest()
    if args.check_local:
        print('OK: dependencias, JAR, archivos LF y known_hosts. No se conecto a Debian.')
        return

    report = {'date': datetime.now(timezone.utc).isoformat(), 'host': args.host,
              'topology': 'VirtualBox bridged; Windows -> Debian TCP 22/8080',
              'jar_sha256': jar_hash, 'status': 'in_progress', 'checks': {}}
    evidence = ROOT / 'docs/evidence/debian-deployment.json'
    ssh = paramiko.SSHClient()
    ssh.load_host_keys(str(known))
    ssh.set_missing_host_key_policy(paramiko.RejectPolicy())
    tokens = []

    def run(command, allowed=(0,), timeout=900):
        stdin, stdout, stderr = ssh.exec_command(command, timeout=timeout)
        stdin.close()
        # Drain both streams so apt cannot block on a full channel.
        channel = stdout.channel
        output, errors = bytearray(), bytearray()
        deadline = time.monotonic() + timeout
        while True:
            if channel.recv_ready():
                output.extend(channel.recv(65536))
            if channel.recv_stderr_ready():
                errors.extend(channel.recv_stderr(65536))
            if channel.exit_status_ready() and not channel.recv_ready() and not channel.recv_stderr_ready():
                break
            require(time.monotonic() < deadline, 'Tiempo agotado en Debian; revisar el servicio desde la consola.')
            time.sleep(0.1)
        code = channel.recv_exit_status()
        out, err = output.decode(errors='replace'), errors.decode(errors='replace')
        require(code in allowed, f'Comando remoto fallo ({code}):\n{out[-3500:]}\n{err[-3500:]}')
        return out.strip(), code

    http = requests.Session()
    http.trust_env = False
    base = f'http://{args.host}:8080'

    def wait_health():
        for _ in range(60):
            try:
                response = http.get(base + '/actuator/health', timeout=3)
                if response.status_code == 200 and response.json().get('status') == 'UP':
                    return
            except (requests.RequestException, ValueError):
                pass
            time.sleep(2)
        raise RuntimeError('El backend no responde UP desde Windows. Revisar journalctl -u billeterax y la red.')

    def snapshot(account, token):
        response = http.get(base + f'/api/accounts/{account}/snapshot',
                            headers={'Authorization': 'Bearer ' + token}, timeout=10)
        require(response.status_code == 200, f'Snapshot {account}: HTTP {response.status_code}')
        return response.json()

    try:
        password = getpass.getpass(f'Contraseña de root@{args.host} (no se guarda): ')
        try:
            ssh.connect(args.host, username='root', password=password, look_for_keys=False,
                        allow_agent=False, timeout=10, auth_timeout=15)
        finally:
            del password
        print('1/6 SSH conectado. Inspeccionando Debian...', flush=True)
        identity, _ = run('id -u; hostname; cat /etc/os-release; ip -4 -br address; ip route; df -h /')
        require(identity.splitlines()[0] == '0', 'Se necesita root para instalar el servicio.')
        require('ID=debian' in identity and 'VERSION_ID="13"' in identity, 'Se esperaba Debian 13. No se modifico el sistema.')
        require(args.host + '/' in identity, 'La IP solicitada no pertenece al invitado conectado.')
        report['inspection'] = identity
        network, _ = run('nmcli -g ipv4.method,ipv4.addresses,ipv4.gateway connection show "Wired connection 1"', allowed=(0,10))
        report['network_profile'] = network
        listeners, _ = run("ss -H -ltn 'sport = :8080'")
        if listeners:
            _, unit_status = run('systemctl is-active --quiet billeterax.service', allowed=(0,3,4))
            require(unit_status == 0, 'El puerto 8080 esta ocupado por otro servicio; no se modifico.')
        _, _ = run('command -v python3')
        pre_upgrade = None
        _, existing_env = run('test -f /etc/billeterax/billeterax.env', allowed=(0,1))
        if listeners and existing_env == 0:
            with ssh.open_sftp() as sftp:
                with sftp.open('/etc/billeterax/billeterax.env') as file:
                    previous = dict(line.split('=',1) for line in file.read().decode().splitlines()
                                    if line and not line.startswith('#') and '=' in line)
            tokens.extend([previous['WALLET_TOKEN_A'], previous['WALLET_TOKEN_B'], previous['DB_PASSWORD']])
            pre_upgrade = {account: snapshot(account, token) for account, token in
                           zip(('CTA001','CTA002'), (previous['WALLET_TOKEN_A'],previous['WALLET_TOKEN_B']))}
            report['pre_upgrade_accounts'] = {key: value['account'] for key,value in pre_upgrade.items()}

        if not args.skip_packages:
            print('2/6 Instalando Java 21 y curl (puede tardar unos minutos)...', flush=True)
            run('apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jre-headless curl')
        else:
            print('2/6 Conservando Java y paquetes existentes...', flush=True)
            run('command -v java; command -v curl')
        java, _ = run('java -version 2>&1')
        require('"21.' in java, 'Java por defecto no es 21; revisar alternatives antes de continuar.')
        report['java'] = java
        staging, _ = run('mktemp -d /var/tmp/billeterax-deploy.XXXXXXXX')
        require(staging.startswith('/var/tmp/billeterax-deploy.') and '\n' not in staging, 'Directorio temporal inesperado.')
        report['staging_directory'] = staging
        run(f'mkdir -p {shlex.quote(staging + "/deployment/debian")} {shlex.quote(staging + "/deployment/systemd")}')
        print('3/6 Transfiriendo JAR e instalador existente...', flush=True)
        with ssh.open_sftp() as sftp:
            sftp.put(str(jar), staging + '/billeterax.jar')
            for path in assets:
                sftp.put(str(path), staging + '/' + path.relative_to(ROOT).as_posix())
        digest, _ = run(f'sha256sum {shlex.quote(staging + "/billeterax.jar")}')
        require(digest.split()[0] == jar_hash, 'El hash del JAR recibido no coincide.')
        run(f'sh -n {shlex.quote(staging + "/deployment/debian/install.sh")}')
        report['checks']['transfer_sha256_and_shell_syntax'] = True
        install = f'sh {shlex.quote(staging + "/deployment/debian/install.sh")} {shlex.quote(staging + "/billeterax.jar")}'
        _, missing = run('test -f /etc/billeterax/billeterax.env', allowed=(0,1))
        if missing:
            _, initial = run(install, allowed=(2,))
        if args.gateway_url:
            # A private SFTP payload avoids putting infrastructure credentials in SSH command arguments.
            gateway_key = getpass.getpass('GATEWAY_AGENT_TOKEN del auxiliar (no se guarda en Windows): ')
            require(32 <= len(gateway_key) <= 256 and all(c.isascii() and (c.isalnum() or c in '_-+/=') for c in gateway_key),
                    'Copia la clave de conexión de al menos 32 caracteres generada por el alojamiento.')
            tokens.append(gateway_key)
            with ssh.open_sftp() as sftp:
                with sftp.open(staging + '/public-access.json', 'w') as remote:
                    remote.chmod(0o600)
                    remote.write(json.dumps({'url': args.gateway_url, 'token': gateway_key}))
            gateway_key = None
        # Generate secrets on Debian. Preserve all existing real credentials and DB data.
        configure = '''import os, pathlib, secrets, shutil, time, json
p = pathlib.Path('/etc/billeterax/billeterax.env')
raw = p.read_text()
values = dict(line.split('=', 1) for line in raw.splitlines() if line and not line.startswith('#') and '=' in line)
keys = ('DB_PASSWORD', 'WALLET_TOKEN_A', 'WALLET_TOKEN_B')
pending = [key for key in keys if values.get(key, '').startswith('REPLACE_')]
if pending:
    if len(pending) != 3 or pathlib.Path('/var/lib/billeterax/wallet.mv.db').exists():
        raise RuntimeError('Configuracion parcial/base existente: revisar sin regenerar credenciales')
    for key in keys:
        values[key] = secrets.token_hex(32)
if not all(values.get(key) for key in keys):
    raise RuntimeError('Faltan credenciales; se conserva la configuracion actual')
values['SPRING_PROFILES_ACTIVE'] = 'debian'
values['SERVER_ADDRESS'] = HOST
values['SERVER_PORT'] = '8080'
if GATEWAY_FILE:
    access_path = pathlib.Path(GATEWAY_FILE)
    access = json.loads(access_path.read_text())
    values.update(GATEWAY_ENABLED='true', GATEWAY_URL=access['url'],
                  GATEWAY_AGENT_TOKEN=access['token'], GATEWAY_NODE_ID='debian-primary')
    access_path.unlink()
shutil.copy2(p, p.with_name('billeterax.env.backup-' + str(time.time_ns())))
tmp = p.with_name('billeterax.env.new-' + str(time.time_ns()))
fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
with os.fdopen(fd, 'w') as f:
    f.write(''.join(k + '=' + v + '\\n' for k, v in values.items()))
os.chown(tmp, p.stat().st_uid, p.stat().st_gid)
os.replace(tmp, p)
print('Configuracion preparada; credenciales conservadas o generadas localmente')
'''
        run("python3 - <<'BILLETERAX_CONFIG'\nHOST = " + repr(args.host) + '\nGATEWAY_FILE = ' + repr(staging + '/public-access.json' if args.gateway_url else None) + '\n' + configure + '\nBILLETERAX_CONFIG')
        print('4/6 Instalando el servicio systemd...', flush=True)
        installed, _ = run(install)
        report['installation'] = installed
        run('systemd-analyze verify /etc/systemd/system/billeterax.service')
        wait_health()
        state, _ = run('systemctl is-enabled billeterax; systemctl is-active billeterax; systemctl show billeterax -p User -p MainPID')
        require('enabled' in state and 'active' in state and 'User=billeterax' in state, 'Estado systemd inesperado.')
        report['service'] = state
        report['checks']['health_from_windows'] = True
        print('5/6 Comprobando REST y WebSocket autenticados...', flush=True)
        with ssh.open_sftp() as sftp:
            with sftp.open('/etc/billeterax/billeterax.env') as file:
                config = dict(line.split('=', 1) for line in file.read().decode().splitlines()
                              if line and not line.startswith('#') and '=' in line)
        tokens.extend([config['WALLET_TOKEN_A'], config['WALLET_TOKEN_B'], config['DB_PASSWORD']])
        before = {account: snapshot(account, token) for account, token in zip(('CTA001', 'CTA002'), (config['WALLET_TOKEN_A'], config['WALLET_TOKEN_B']))}
        if pre_upgrade is not None:
            for account in pre_upgrade:
                require(pre_upgrade[account]['account'] == before[account]['account'] and
                        pre_upgrade[account]['transactions'] == before[account]['transactions'],
                        'Los datos cambiaron durante la actualizacion; comprobar actividad concurrente o respaldo.')
            report['checks']['existing_accounts_and_history_preserved_during_upgrade'] = True
        denied = http.get(base + '/api/accounts/CTA001/balance', timeout=10)
        require(denied.status_code == 401, 'Se esperaba 401 sin credencial.')
        ws = websocket.create_connection(f'ws://{args.host}:8080/ws',
                 header=['Authorization: Bearer ' + config['WALLET_TOKEN_A']], suppress_origin=True, timeout=10,
                 http_no_proxy=[args.host])
        try:
            greeting = json.loads(ws.recv())
            require(greeting.get('type') == 'CONNECTED' and greeting.get('accountId') == 'CTA001', 'Saludo WebSocket inesperado.')
        finally:
            ws.close()
        report['checks']['rest_authorized_and_unauthorized'] = True
        report['checks']['websocket_authenticated_handshake'] = True
        print('Comprobando registro, login y sesión de usuario...', flush=True)
        test_username = 'check_' + secrets.token_hex(6)
        test_password = secrets.token_urlsafe(24)
        tokens.append(test_password)
        registered = http.post(base + '/api/auth/register', json={
            'displayName': 'Verificación de acceso', 'username': test_username, 'password': test_password}, timeout=20)
        require(registered.status_code == 201, 'Fallo el registro de usuario tras actualizar.')
        auth = registered.json()
        tokens.append(auth['token'])
        test_account = auth['user']['accountId']
        require(snapshot(test_account, auth['token'])['account']['balance'] == 0, 'La nueva cuenta no inicia en cero.')
        auth_ws = websocket.create_connection(f'ws://{args.host}:8080/ws',
            header=['Authorization: Bearer ' + auth['token']], suppress_origin=True, timeout=10,
            http_no_proxy=[args.host])
        try:
            require(json.loads(auth_ws.recv()).get('accountId') == test_account, 'WebSocket no corresponde al usuario registrado.')
        finally:
            auth_ws.close()
        report['checks']['registration_and_user_websocket'] = True

        print('Comprobando banca web, CORS y pase WebSocket de un solo uso...', flush=True)
        origin = 'https://29925-mosalvador.github.io'
        page = http.get(base + '/', timeout=10)
        require(page.status_code == 200 and 'BilleteraX · Banca web' in page.text, 'No se sirve la banca web desde el JAR.')
        preflight = http.options(base + '/api/auth/ws-ticket', headers={
            'Origin': origin, 'Access-Control-Request-Method': 'POST',
            'Access-Control-Request-Headers': 'authorization'}, timeout=10)
        require(preflight.status_code == 200 and preflight.headers.get('Access-Control-Allow-Origin') == origin,
                'GitHub Pages no tiene el origen CORS permitido.')
        ticket_response = http.post(base + '/api/auth/ws-ticket', headers={
            'Authorization': 'Bearer ' + auth['token'], 'Origin': origin}, timeout=10)
        require(ticket_response.status_code == 200, 'No se pudo obtener el pase WebSocket del navegador.')
        ticket = ticket_response.json()['ticket']; tokens.append(ticket)
        browser_ws = websocket.create_connection(f'ws://{args.host}:8080/ws?ticket={ticket}',
            origin=origin, timeout=10, http_no_proxy=[args.host])
        try:
            require(json.loads(browser_ws.recv()).get('accountId') == test_account, 'El pase no pertenece a la cuenta esperada.')
        finally:
            browser_ws.close()
        try:
            duplicate = websocket.create_connection(f'ws://{args.host}:8080/ws?ticket={ticket}',
                origin=origin, timeout=10, http_no_proxy=[args.host])
        except websocket.WebSocketBadStatusException as denied_ticket:
            require(denied_ticket.status_code == 401, 'Código inesperado al reutilizar el pase.')
        else:
            duplicate.close()
            raise RuntimeError('El pase WebSocket se pudo reutilizar.')
        report['checks']['web_page_and_pages_cors'] = True
        report['checks']['browser_websocket_single_use_ticket'] = True

        print('6/6 Reiniciando solo el servicio y verificando persistencia...', flush=True)
        run('systemctl restart billeterax')
        wait_health()
        after = {account: snapshot(account, token) for account, token in zip(('CTA001', 'CTA002'), (config['WALLET_TOKEN_A'], config['WALLET_TOKEN_B']))}
        for account in before:
            require(before[account]['account'] == after[account]['account'] and
                    before[account]['transactions'] == after[account]['transactions'],
                    'El snapshot cambio al reiniciar; revisar actividad concurrente o persistencia.')
        report['checks']['snapshot_preserved_after_service_restart'] = True
        profile = http.get(base + '/api/auth/me', headers={'Authorization': 'Bearer ' + auth['token']}, timeout=10)
        require(profile.status_code == 200 and profile.json().get('username') == test_username,
                'La sesión o usuario no sobrevivió al reinicio del servicio.')
        logged = http.post(base + '/api/auth/login', json={'username': test_username.upper(), 'password': test_password}, timeout=20)
        require(logged.status_code == 200 and logged.json()['user']['accountId'] == test_account,
                'El login no conserva el usuario y su cuenta al reiniciar.')
        login_token = logged.json()['token']; tokens.append(login_token)
        require(http.post(base + '/api/auth/logout', headers={'Authorization': 'Bearer ' + login_token}, timeout=10).status_code == 204,
                'No se pudo cerrar sesión.')
        require(http.get(base + '/api/auth/me', headers={'Authorization': 'Bearer ' + login_token}, timeout=10).status_code == 401,
                'La sesión cerrada todavía permite acceso.')
        report['checks']['user_login_session_and_password_survive_restart'] = True
        report['checks']['logout_revokes_session'] = True
        report['auth_smoke_user'] = {'username': test_username, 'accountId': test_account, 'balance': 0}
        # The smoke user remains a zero-balance test record. Its random password is not saved.

        if args.gateway_url:
            print('Comprobando acceso público HTTPS/WSS y dependencia de Debian...', flush=True)
            from public_access_check import verify_public_access
            verify_public_access(http, args.gateway_url, origin, test_username, test_password,
                                 test_account, run, wait_health, tokens, report['checks'])
            report['public_gateway'] = {'url': args.gateway_url, 'node': 'debian-primary', 'database': 'Debian only'}
        report['accounts'] = {key: value['account'] for key, value in after.items()}
        report['pending'] = ([] if args.gateway_url else ['Activar/verificar acceso público HTTPS fijo']) + ['Reinicio completo de la VM', 'Perfilado prolongado']
        report['status'] = 'passed'
        print('OK: backend disponible en ' + base + '/. Evidencia: ' + str(evidence))
    except Exception as exc:
        message = str(exc)
        for secret in tokens:
            if secret:
                message = message.replace(secret, '[REDACTED]')
        report['status'] = 'failed'
        report['error'] = message
        print('ERROR: ' + message, file=sys.stderr)
    finally:
        ssh.close()
        http.close()
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if report['status'] != 'passed':
        sys.exit(1)


if __name__ == '__main__':
    main()
