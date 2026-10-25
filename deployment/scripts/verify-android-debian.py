"""Interactive Windows runner: SSH password in memory, Android integration and offline UI."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal
import getpass
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '.tools/python-debian'))
import paramiko
import requests

HOST = '192.168.18.76'
BASE = f'http://{HOST}:8080/'
PACKAGE = 'ec.edu.espe.billeterax'
ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
EVIDENCE = ROOT / 'docs/evidence'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', help='Opcional si existe mas de un dispositivo Android.')
    parser.add_argument('--check-local', action='store_true')
    args = parser.parse_args()
    apk = ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk'
    test_apk = ROOT / 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    require(ADB.is_file() and apk.is_file() and test_apk.is_file(), 'Faltan ADB o APK compilados.')
    if args.check_local:
        print('OK: dependencias, ADB y ambos APK disponibles. No se ejecutaron pruebas Android.')
        return

    secrets = []
    report = {'date': datetime.now(timezone.utc).isoformat(), 'server': BASE, 'status': 'running', 'checks': {}}
    ssh = paramiko.SSHClient()
    http = requests.Session()
    http.trust_env = False
    service_stopped = False

    def redact(text):
        for secret in secrets:
            if secret:
                text = text.replace(secret, '[REDACTED]')
        return text

    def adb(*parts, timeout=60, binary=False):
        prefix = [str(ADB)] + (['-s', args.serial] if args.serial else [])
        result = subprocess.run(prefix + list(parts), capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError('ADB: ' + redact(result.stderr.decode(errors='replace')))
        return result.stdout if binary else result.stdout.decode(errors='replace')

    def remote(command):
        stdin, stdout, stderr = ssh.exec_command(command, timeout=60)
        stdin.close()
        output, error = stdout.read().decode(), stderr.read().decode()
        require(stdout.channel.recv_exit_status() == 0, 'Debian: ' + redact(error))
        return output

    def snapshot():
        response = http.get(BASE + 'api/accounts/CTA001/snapshot',
                            headers={'Authorization': 'Bearer ' + secrets[0]}, timeout=10)
        require(response.status_code == 200, 'No se obtuvo snapshot CTA001.')
        return response.json()

    def wait_health():
        until = time.monotonic() + 100
        while time.monotonic() < until:
            try:
                if http.get(BASE + 'actuator/health', timeout=3).json().get('status') == 'UP':
                    return
            except (requests.RequestException, ValueError):
                pass
            time.sleep(2)
        raise RuntimeError('Debian no recupero la salud HTTP.')

    def screen():
        adb('shell', 'uiautomator', 'dump', '/sdcard/billeterax-check.xml', timeout=30)
        xml = adb('shell', 'cat', '/sdcard/billeterax-check.xml')
        return ET.fromstring(xml[xml.index('<?xml'):])

    def texts(tree):
        return [n.get('text', '') for n in tree.iter('node') if n.get('text')]

    def wait_text(text, timeout=70):
        until = time.monotonic() + timeout
        while time.monotonic() < until:
            tree = screen()
            if any(text in value for value in texts(tree)):
                return tree
            time.sleep(1)
        raise RuntimeError('No aparecio en Android: ' + text)

    def tap(node):
        coords = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        require(len(coords) == 4 and coords[2] > coords[0] and coords[3] > coords[1], 'Control sin area visible.')
        adb('shell', 'input', 'tap', str((coords[0] + coords[2]) // 2), str((coords[1] + coords[3]) // 2))

    def find_text(text, scroll=False):
        for _ in range(8 if scroll else 1):
            tree = screen()
            matches = [n for n in tree.iter('node') if n.get('text') == text or n.get('content-desc') == text]
            if matches:
                return matches[0]
            if scroll:
                bounds = list(map(int, re.findall(r'\d+', list(tree)[0].get('bounds', ''))))
                require(len(bounds) == 4, 'No se pudo determinar el area de pantalla.')
                w, h = bounds[2], bounds[3]
                adb('shell', 'input', 'swipe', str(w // 2), str(h * 4 // 5), str(w // 2), str(h // 3), '350')
        raise RuntimeError('Control no visible: ' + text)

    def launch():
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity')

    def capture(name):
        (EVIDENCE / name).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

    try:
        devices = [line.split()[0] for line in adb('devices').splitlines()[1:] if '\tdevice' in line]
        if args.serial:
            require(args.serial in devices, 'El dispositivo elegido no esta conectado.')
        else:
            require(len(devices) == 1, 'Encender un solo emulador o indicar --serial.')
            args.serial = devices[0]
        require(adb('shell', 'getprop', 'sys.boot_completed').strip() == '1', 'Android todavia esta arrancando.')
        report['device'] = args.serial
        # Detect APK/device installation problems before requesting SSH credentials.
        print('1/5 Instalando APK actual y pruebas, conservando datos...', flush=True)
        adb('install', '-r', str(apk), timeout=150)
        adb('install', '-r', str(test_apk), timeout=150)
        report['checks']['apk_updates_preserve_data'] = True
        ssh.load_host_keys(str(Path.home() / '.ssh/known_hosts'))
        ssh.set_missing_host_key_policy(paramiko.RejectPolicy())
        password = getpass.getpass(f'Contraseña de root@{HOST} (no se guarda): ')
        try:
            ssh.connect(HOST, username='root', password=password, allow_agent=False, look_for_keys=False, timeout=10)
        finally:
            del password
        with ssh.open_sftp() as sftp:
            with sftp.open('/etc/billeterax/billeterax.env') as file:
                env = dict(line.split('=', 1) for line in file.read().decode().splitlines() if line and not line.startswith('#') and '=' in line)
        secrets.extend([env['WALLET_TOKEN_A'], env['WALLET_TOKEN_B']])
        wait_health()
        print('2/5 Ejecutando Room y flujo Retrofit/WebSocket contra Debian...', flush=True)
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'serverUrl', BASE,
                     '-e', 'tokenA', secrets[0], '-e', 'tokenB', secrets[1], '-e', 'configureApp', 'true',
                     PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=240)
        (EVIDENCE / 'android-debian-instrumentation.log').write_text(redact(result), encoding='utf-8')
        require('OK (2 tests)' in result, 'Fallo instrumentacion; revisar android-debian-instrumentation.log.')
        report['checks']['room_and_distributed_flow'] = True
        launch()
        wait_text('WebSocket · En línea')
        before = snapshot()
        balance = Decimal(str(before['account']['balance']))
        wait_text(f'${balance:,.2f}')
        capture('android-debian-online.png')
        print('3/5 Deteniendo solo BilleteraX y verificando Room sin conexion...', flush=True)
        service_stopped = True
        remote('systemctl stop billeterax')
        launch()
        wait_text('Último saldo sincronizado')
        wait_text(f'${balance:,.2f}')
        capture('android-debian-offline.png')
        report['checks']['cached_balance_after_process_restart_offline'] = True
        tap(find_text('Depositar'))
        tree = screen()
        inputs = [n for n in tree.iter('node') if n.get('class') == 'android.widget.EditText']
        require(len(inputs) == 1, 'Se esperaba un campo de monto.')
        tap(inputs[0])
        adb('shell', 'input', 'text', '1.00')
        adb('shell', 'input', 'keyevent', '4')
        tap(find_text('Confirmar'))
        # The pending card may be below the visible dashboard.
        find_text('Verificar / reintentar', scroll=True)
        tree = screen()
        operation_id = next((value for value in texts(tree) if re.fullmatch(r'[0-9a-f-]{36}', value)), None)
        require(operation_id is not None, 'No se encontro UUID pendiente.')
        report['pending_operation_id'] = operation_id
        capture('android-debian-pending.png')
        launch()
        wait_text('Último saldo sincronizado')
        find_text('Verificar / reintentar', scroll=True)
        require(operation_id in texts(screen()), 'El UUID pendiente no sobrevivio al cierre de Android.')
        report['checks']['pending_uuid_survives_process_restart'] = True
        print('4/5 Recuperando servicio y reintentando la misma operacion...', flush=True)
        remote('systemctl start billeterax')
        service_stopped = False
        wait_health()
        tap(find_text('Verificar / reintentar', scroll=True))
        until = time.monotonic() + 60
        while time.monotonic() < until:
            after = snapshot()
            if any(t['operationId'] == operation_id for t in after['transactions']):
                break
            time.sleep(2)
        else:
            raise RuntimeError('No se confirmo el reintento en Debian.')
        require(Decimal(str(after['account']['balance'])) == balance + 1, 'Saldo inesperado tras reintento.')
        require(sum(t['operationId'] == operation_id for t in after['transactions']) == 1, 'Asiento duplicado.')
        replay = http.post(BASE + 'api/transactions/deposit', headers={'Authorization': 'Bearer ' + secrets[0]},
                           json={'operationId': operation_id, 'accountId': 'CTA001', 'amount': '1.00'}, timeout=10)
        require(replay.status_code == 200 and replay.json().get('replayed') is True, 'No se reconocio UUID repetido.')
        require(snapshot()['account'] == after['account'], 'El replay modifico el saldo/version.')
        report['checks']['recovery_and_idempotent_replay'] = True
        print('5/5 Verificando pantalla final conectada...', flush=True)
        launch()
        wait_text('WebSocket · En línea')
        wait_text(f'${balance + 1:,.2f}')
        capture('android-debian-recovered.png')
        report['final_account'] = after['account']
        report['checks']['final_ui_online'] = True
        report['status'] = 'passed'
        report['pending'] = ['Perdida de respuesta despues del commit', 'Reinicio completo de VM', 'Perfilado prolongado']
        print('OK: Android conectado a Debian; pruebas y capturas guardadas en docs/evidence.')
    except Exception as exc:
        report['status'] = 'failed'
        # Avoid logging command arguments, which contain short-lived token values.
        report['error'] = 'Tiempo agotado en ADB' if isinstance(exc, subprocess.TimeoutExpired) else redact(str(exc))
        print('ERROR: ' + report['error'], file=sys.stderr)
    finally:
        if service_stopped:
            try:
                remote('systemctl start billeterax')
                report['service_restored_after_failure'] = True
            except Exception:
                report['service_restored_after_failure'] = False
                print('Reiniciar manualmente el servicio: systemctl start billeterax', file=sys.stderr)
        ssh.close()
        http.close()
        EVIDENCE.mkdir(parents=True, exist_ok=True)
        (EVIDENCE / 'android-debian-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if report['status'] != 'passed':
        sys.exit(1)


if __name__ == '__main__':
    main()
