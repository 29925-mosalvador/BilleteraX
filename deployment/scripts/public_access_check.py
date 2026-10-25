"""Verify the public auxiliary against Debian after deployment; never store credentials."""
import json
import time
from urllib.parse import urlsplit
import requests
import websocket


def verify_public_access(http, public, origin, username, password, account, run, wait_health, secrets, checks):
    def require(ok, message):
        if not ok:
            raise RuntimeError(message)

    def wait_online(expected):
        for _ in range(90):
            try:
                state = http.get(public + '/api/status', timeout=5)
                if state.status_code == 200 and state.json().get('online') is expected:
                    return
            except (requests.RequestException, ValueError):
                pass
            time.sleep(1)
        raise RuntimeError('El auxiliar no refleja el estado de Debian; revisar URL y clave de conexión.')

    wait_online(True)
    logged = http.post(public + '/api/auth/login', json={'username': username, 'password': password}, timeout=40)
    require(logged.status_code == 200 and logged.json()['user']['accountId'] == account,
            'El acceso público no utiliza la misma cuenta de Debian.')
    token = logged.json()['token']; secrets.append(token)
    snapshot = http.get(public + '/api/accounts/' + account + '/snapshot',
        headers={'Authorization': 'Bearer ' + token, 'Origin': origin}, timeout=40)
    require(snapshot.status_code == 200 and snapshot.json()['account']['balance'] == 0
            and snapshot.headers.get('Access-Control-Allow-Origin') == origin,
            'El snapshot público o CORS de Pages no coincide.')
    ticket_response = http.post(public + '/api/auth/ws-ticket', headers={
        'Authorization': 'Bearer ' + token, 'Origin': origin}, timeout=40)
    require(ticket_response.status_code == 200, 'No se pudo crear el pase WSS público.')
    ticket = ticket_response.json()['ticket']; secrets.append(ticket)
    sock = websocket.create_connection('wss://' + urlsplit(public).netloc + '/ws?ticket=' + ticket,
        origin=origin, timeout=15)
    try:
        require(json.loads(sock.recv()).get('accountId') == account, 'WSS público no corresponde a la cuenta.')
    finally:
        sock.close()
    checks['public_https_same_debian_account_and_pages_cors'] = True
    checks['public_wss_authenticated'] = True
    offline_ticket_response = http.post(public + '/api/auth/ws-ticket',
        headers={'Authorization': 'Bearer ' + token, 'Origin': origin}, timeout=40)
    require(offline_ticket_response.status_code == 200, 'No se pudo preparar WSS para la prueba de desconexión.')
    offline_ticket = offline_ticket_response.json()['ticket']; secrets.append(offline_ticket)
    offline_socket = websocket.create_connection('wss://' + urlsplit(public).netloc + '/ws?ticket=' + offline_ticket,
        origin=origin, timeout=15)
    try:
        require(json.loads(offline_socket.recv()).get('accountId') == account, 'WSS de desconexión no corresponde a la cuenta.')
        # Only the wallet service is paused. Recovery runs even if the verification fails.
        run('systemctl stop billeterax')
        wait_online(False)
        notice = json.loads(offline_socket.recv())
        require(notice.get('type') == 'SERVER_UNAVAILABLE' and notice.get('accountId') == account,
                'La conexión pública no recibió el aviso de Debian desconectado.')
        checks['public_socket_notified_when_debian_is_offline'] = True
        denied = http.post(public + '/api/auth/login', json={'username': username, 'password': password}, timeout=40)
        require(denied.status_code == 503 and denied.json().get('code') == 'DEBIAN_OFFLINE',
                'El auxiliar debe rechazar el login mientras Debian no esté disponible.')
        checks['public_login_unavailable_without_debian'] = True
    finally:
        try:
            offline_socket.close()
        finally:
            run('systemctl start billeterax')
            wait_health()
    wait_online(True)
    require(http.get(public + '/api/auth/me', headers={'Authorization': 'Bearer ' + token}, timeout=40).status_code == 200,
            'El acceso público no se recuperó con la sesión guardada en Debian.')
    require(http.post(public + '/api/auth/logout', headers={'Authorization': 'Bearer ' + token}, timeout=40).status_code == 204,
            'No se pudo cerrar la sesión pública de prueba.')
    checks['public_automatic_recovery_after_primary_restart'] = True
