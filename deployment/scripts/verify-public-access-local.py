from pathlib import Path
import json,os,subprocess,time,uuid,threading,hashlib,sys
from concurrent.futures import ThreadPoolExecutor
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'.tools/python-debian'))
import requests,websocket
RUNTIME=ROOT/'.tools/public-gateway/runtime';RUNTIME.mkdir(parents=True,exist_ok=True)
JAVA=Path(os.environ.get('JAVA_HOME',str(ROOT/'.tools/jdk21')))/'bin'/('java.exe' if os.name=='nt' else 'java')
G='http://127.0.0.1:8088';D='http://127.0.0.1:8089'
KEY='local-integration-test-not-a-real-secret-123456'
http=requests.Session();http.trust_env=False
processes={};handles=[];checks={}
def check(value,name):
    if not value:raise AssertionError(name)
    checks[name]=True;print('OK:',name,flush=True)
def request(base,method,path,token=None,body=None,expected=200):
    response=http.request(method,base+path,headers={'Authorization':'Bearer '+token} if token else {},json=body,timeout=35)
    if response.status_code!=expected:raise AssertionError(f'{method} {path}: {response.status_code} {response.text[:200]}')
    return response.json() if response.content else None
def wait(test,seconds=40):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            value=test()
            if value:return value
        except (requests.RequestException,ValueError):pass
        time.sleep(.1)
    raise TimeoutError('El servicio no respondió a tiempo')
def start(name):
    env=os.environ.copy();env.update({'GATEWAY_AGENT_TOKEN':KEY,'JAVA_TOOL_OPTIONS':'-Xms64m -Xmx256m'})
    if name=='gateway':source=ROOT/'gateway/build/libs/gateway.jar';env['PORT']='8088'
    else:
        source=ROOT/'backend/build/libs/billeterax.jar'
        env.update({'SERVER_ADDRESS':'127.0.0.1','SERVER_PORT':'8089','DB_URL':'jdbc:h2:mem:public-e2e;DB_CLOSE_DELAY=-1','SPRING_JPA_HIBERNATE_DDL_AUTO':'create-drop','GATEWAY_ENABLED':'true','GATEWAY_URL':G,'WALLET_GATEWAY_ALLOW_LOCAL_HTTP':'true'})
    jar=RUNTIME/(name+'-'+uuid.uuid4().hex[:6]+'.jar');jar.write_bytes(source.read_bytes())
    log=(RUNTIME/(name+'.log')).open('ab');handles.append(log)
    p=subprocess.Popen([str(JAVA),'-jar',str(jar)],cwd=RUNTIME,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    processes[name]=p
    wait(lambda:http.get((G if name=='gateway' else D)+'/actuator/health',timeout=1).status_code==200)
    return p
def stop(name):
    p=processes.pop(name,None)
    if p and p.poll() is None:
        # A gateway crash must interrupt the response immediately on Linux too.
        if name=='gateway':p.kill()
        else:p.terminate()
        try:p.wait(timeout=15)
        except subprocess.TimeoutExpired:p.kill();p.wait(timeout=10)
def register(base,label):
    username=label+uuid.uuid4().hex[:10]
    auth=request(base,'POST','/api/auth/register',body={'username':username,'displayName':'Prueba acceso público','password':'Demo-publica-123'},expected=201)
    return auth,username
def money(kind,auth,amount,**extra):
    return {'operationId':str(uuid.uuid4()),'accountId':auth['user']['accountId'],'amount':amount,**extra}
def balance(auth):return request(D,'GET','/api/accounts/'+auth['user']['accountId']+'/balance',auth['token'])['balance']
try:
    for base in (G,D):
        try:http.get(base+'/actuator/health',timeout=.3)
        except requests.ConnectionError:continue
        raise RuntimeError('Puerto de prueba ocupado; no se detuvo ningún proceso ajeno')
    start('gateway')
    offline=request(G,'POST','/api/auth/login',body={'username':'sinDebian','password':'demo'},expected=503)
    check(offline['code']=='DEBIAN_OFFLINE','auxiliar solo no permite iniciar sesión')
    start('primary');wait(lambda:request(G,'GET','/api/status')['online'])
    a,username=register(G,'web');b,_=register(D,'local')
    check(request(D,'POST','/api/auth/login',body={'username':username,'password':'Demo-publica-123'})['user']['accountId']==a['user']['accountId'],'web y servidor principal usan la misma cuenta')
    request(G,'GET','/api/accounts/'+b['user']['accountId']+'/snapshot',a['token'],expected=403)
    check(True,'aislamiento de cuentas en acceso público')
    ticket=request(G,'POST','/api/auth/ws-ticket',a['token'])['ticket']
    ws=websocket.create_connection('ws://127.0.0.1:8088/ws?ticket='+ticket,origin='http://127.0.0.1:8088',timeout=10)
    check(json.loads(ws.recv())['type']=='CONNECTED','WebSocket web autenticado con pase de un uso')
    dep=money('deposit',a,'20.00');request(G,'POST','/api/transactions/deposit',a['token'],dep)
    check(request(G,'POST','/api/transactions/deposit',a['token'],dep)['replayed'],'reintento de depósito no duplica saldo')
    request(G,'POST','/api/transactions/withdraw',a['token'],money('withdraw',a,'3.00'))
    request(G,'POST','/api/payments/services',a['token'],money('service',a,'2.00',service='WATER',contract='AGUA123'))
    request(G,'POST','/api/transactions/transfer',a['token'],money('transfer',a,'1.00',destinationAccountId=b['user']['accountId']))
    request(D,'POST','/api/transactions/deposit',b['token'],money('deposit',b,'5.00'))
    request(D,'POST','/api/transactions/transfer',b['token'],money('transfer',b,'4.00',destinationAccountId=a['user']['accountId']))
    event=None
    for _ in range(12):
        event=json.loads(ws.recv())
        if event.get('type')=='TRANSFER_RECEIVED':break
    check(event.get('type')=='TRANSFER_RECEIVED' and event['accountId']==a['user']['accountId'],'transferencia local llega al navegador por WebSocket')
    snap=request(G,'GET','/api/accounts/'+a['user']['accountId']+'/snapshot',a['token'])
    check(float(snap['account']['balance'])==18 and len(snap['transactions'])==5,'cuatro operaciones y transferencia entrante, saldo e historial correctos')
    # Native Android-style handshake keeps the existing Authorization header contract.
    native=websocket.create_connection('ws://127.0.0.1:8088/ws',header={'Authorization':'Bearer '+b['token']},origin='http://127.0.0.1:8088',timeout=10)
    check(json.loads(native.recv())['accountId']==b['user']['accountId'],'protocolo WebSocket de Android compatible')
    native.close();ws.close()
    lost=money('deposit',a,'7.00')
    pool=ThreadPoolExecutor(1)
    future=pool.submit(http.post,G+'/api/transactions/deposit',headers={'Authorization':'Bearer '+a['token']},json=lost,timeout=35)
    wait(lambda:float(balance(a))==25)
    stop('gateway')
    response_was_lost=False
    try:future.result(timeout=35)
    except requests.RequestException:response_was_lost=True
    check(response_was_lost,'respuesta HTTP interrumpida después del commit')
    pool.shutdown()
    check(float(balance(a))==25,'commit en principal sobrevive a la caída del auxiliar')
    start('gateway');wait(lambda:request(G,'GET','/api/status')['online'])
    replay=request(G,'POST','/api/transactions/deposit',a['token'],lost)
    check(replay['replayed'] and float(balance(a))==25,'respuesta perdida y reinicio del auxiliar: mismo UUID sin duplicados')
    logout_socket=websocket.create_connection('ws://127.0.0.1:8088/ws',header={'Authorization':'Bearer '+b['token']},origin='http://127.0.0.1:8088',timeout=10);logout_socket.recv()
    request(D,'POST','/api/auth/logout',b['token'],expected=204)
    closed=logout_socket.recv_frame()
    check(closed.opcode==websocket.ABNF.OPCODE_CLOSE,'logout directo revoca también WebSocket público')
    request(G,'GET','/api/auth/me',b['token'],expected=401);logout_socket.close()
    down_socket=websocket.create_connection('ws://127.0.0.1:8088/ws',header={'Authorization':'Bearer '+a['token']},origin='http://127.0.0.1:8088',timeout=10);down_socket.recv()
    stop('primary');wait(lambda:not request(G,'GET','/api/status')['online'],seconds=12)
    check(down_socket.recv_frame().opcode==websocket.ABNF.OPCODE_CLOSE,'desconexión del principal cierra WebSockets públicos')
    request(G,'POST','/api/auth/login',body={'username':username,'password':'Demo-publica-123'},expected=503)
    request(G,'POST','/api/transactions/deposit',a['token'],money('deposit',a,'100.00'),expected=503)
    check(True,'sin Debian no se aceptan login ni operaciones')
    down_socket.close()
    start('primary');wait(lambda:request(G,'GET','/api/status')['online'])
    # This disposable primary deliberately uses an in-memory DB; persistence of Debian is a separate test.
    register(G,'recover')
    check(True,'recuperación automática tras encender el principal de prueba')
    report={'date':__import__('datetime').datetime.now(__import__('datetime').timezone.utc).isoformat(),'status':'passed','environment':os.name+' loopback, actual Java gateway and Java primary; disposable in-memory DB; no router or Debian changes','checks':checks,'gateway_jar_sha256':hashlib.sha256((ROOT/'gateway/build/libs/gateway.jar').read_bytes()).hexdigest(),'backend_jar_sha256':hashlib.sha256((ROOT/'backend/build/libs/billeterax.jar').read_bytes()).hexdigest(),'limitations':['No public host deployed yet','No Android device execution in this check','Authoritative Debian persistence is covered by earlier deployment evidence, not this in-memory fixture']}
    (ROOT/'docs/evidence/public-access-local.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if '--keep-running' in sys.argv:
        (RUNTIME/'pids.json').write_text(json.dumps({n:p.pid for n,p in processes.items()}))
        print('Vista local disponible en '+G,flush=True)
        while True:time.sleep(1)
finally:
    for name in list(processes):stop(name)
    for handle in handles:handle.close()
