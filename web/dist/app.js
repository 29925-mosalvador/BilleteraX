import {hydrateIcons,icon} from './icons.js';
import {ApiError,normalizeBase,parseAmount,centsOf,money,totals,makeOperation,endpoints,createClient,savePending,loadPending,clearPending} from './core.js';
const $=id=>document.getElementById(id);
const all=selector=>document.querySelectorAll(selector);
const state={base:'',auth:null,snapshot:null,view:'home',mode:'login',socket:null,reconnect:null,epoch:0,operation:null,review:false,busy:false,hiddenBalance:false,refreshing:false,refreshAgain:false};
const labels={deposit:'Depositar dinero',withdraw:'Retirar dinero',transfer:'Transferir dinero',service:'Pagar un servicio'};
const services={ELECTRICITY:'Electricidad',WATER:'Agua',INTERNET:'Internet'};
const transactionLabels={DEPOSIT:'Depósito',WITHDRAW:'Retiro',TRANSFER_IN:'Transferencia recibida',TRANSFER_OUT:'Transferencia enviada',SERVICE:'Pago de servicio'};
let toastTimer;
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,5000);}
function errorAt(id,error){$(id).textContent=error?.message||String(error);$(id).hidden=false;}
function storeRead(storage,key){try{return storage.getItem(key);}catch{return null;}}
function setTheme(theme){document.documentElement.dataset.theme=theme;try{localStorage.setItem('billeterax.theme',theme);}catch{}all('.theme-toggle').forEach(b=>{b.innerHTML=icon(theme==='dark'?'sun':'moon');b.setAttribute('aria-label',theme==='dark'?'Activar modo claro':'Activar modo oscuro');});}
hydrateIcons();setTheme(storeRead(localStorage,'billeterax.theme')|| (matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'));
all('.theme-toggle').forEach(b=>b.addEventListener('click',()=>setTheme(document.documentElement.dataset.theme==='dark'?'light':'dark')));
function closeSocket(){clearTimeout(state.reconnect);state.reconnect=null;const ws=state.socket;state.socket=null;if(ws){ws.onclose=null;ws.close();}}
function connection(kind,text){$('connection').className=`connection ${kind}`;$('connection').lastElementChild.textContent=text;}
function client(){return createClient(state.base,state.auth?.token);}
function saveSession(auth){sessionStorage.setItem('billeterax.session',JSON.stringify({base:state.base,...auth}));}
function showAccess(message){
  state.epoch++;state.auth=null;state.snapshot=null;closeSocket();sessionStorage.removeItem('billeterax.session');
  document.querySelector('.skip').href='#main';$('bank').hidden=true;$('access').hidden=false;$('password').value='';$('confirm-password').value='';$('transactions').replaceChildren();$('balance').textContent='—';
  if($('operation-dialog').open)$('operation-dialog').close();
  authMode('login');
  if(message) errorAt('auth-error',message);
}
function sessionFailure(error){if(error.status===401){showAccess('Tu sesión terminó. Inicia sesión de nuevo.');return true;}return false;}
function showServer(){ $('server-error').hidden=true;$('server-url').value=state.base;$('server-dialog').showModal();}
function setBase(base){state.base=base;try{localStorage.setItem('billeterax.server',base);}catch{}$('server-caption').textContent=`Servidor: ${new URL(base).host}`;}
$('server-button').addEventListener('click',showServer);$('close-server').addEventListener('click',()=>$('server-dialog').close());
$('server-form').addEventListener('submit',async e=>{e.preventDefault();$('server-error').hidden=true;$('save-server').disabled=true;try{const base=normalizeBase($('server-url').value,location.protocol);await createClient(base)('/api/auth/me').then(()=>{throw new Error('Este servidor permite acceso sin iniciar sesión. Elige el backend BilleteraX actualizado.');},err=>{if(err.status!==401)throw err;});setBase(base);sessionStorage.removeItem('billeterax.session');$('server-dialog').close();toast('Conexión preparada. Ya puedes iniciar sesión.');}catch(err){errorAt('server-error',err);}finally{$('save-server').disabled=false;}});
function authMode(mode){
  state.mode=mode;const register=mode==='register';$('auth-form').reset();$('auth-error').hidden=true;
  $('name-field').hidden=!register;$('confirm-field').hidden=!register;$('password-help').hidden=!register;$('display-name').required=register;$('confirm-password').required=register;
  $('password').minLength=register?8:1;$('password').autocomplete=register?'new-password':'current-password';
  $('auth-title').textContent=register?'Empieza algo bueno.':'Qué bueno verte.';
  $('auth-subtitle').textContent=register?'Crea tu cuenta y úsala también en Android.':'Ingresa con la misma cuenta que usas en Android.';
  $('auth-submit').innerHTML=`${register?'Crear mi cuenta':'Entrar a mi cuenta'} ${icon('arrow-right')}`;
  for(const id of ['login','register']){$(`${id}-tab`).classList.toggle('selected',id===mode);$(`${id}-tab`).setAttribute('aria-pressed',String(id===mode));}
}
$('login-tab').addEventListener('click',()=>authMode('login'));$('register-tab').addEventListener('click',()=>authMode('register'));
$('show-password').addEventListener('click',()=>{const input=$('password');input.type=input.type==='password'?'text':'password';$('show-password').setAttribute('aria-label',input.type==='password'?'Mostrar contraseña':'Ocultar contraseña');});
$('auth-form').addEventListener('submit',async e=>{
  e.preventDefault();$('auth-error').hidden=true;if(!state.base){showServer();return;}
  const mode=state.mode,body={username:$('username').value.trim(),password:$('password').value};
  if(mode==='register'){
    if(body.password!==$('confirm-password').value){errorAt('auth-error','Las contraseñas no coinciden.');return;}
    if(new TextEncoder().encode(body.password).length>72){errorAt('auth-error','La contraseña admite un máximo de 72 bytes.');return;}
    body.displayName=$('display-name').value.trim();
  }
  $('auth-submit').disabled=true;$('login-tab').disabled=true;$('register-tab').disabled=true;$('server-button').disabled=true;
  try{const auth=await createClient(state.base)(`/api/auth/${mode}`,{method:'POST',body});saveSession(auth);await enter(auth);$('auth-form').reset();}
  catch(err){errorAt('auth-error',err);}finally{$('auth-submit').disabled=false;$('login-tab').disabled=false;$('register-tab').disabled=false;$('server-button').disabled=false;}
});
async function enter(auth){
  state.epoch++;closeSocket();state.auth=auth;state.snapshot=null;state.hiddenBalance=false;
  document.querySelector('.skip').href='#bank-main';$('access').hidden=true;$('bank').hidden=false;$('bank-error').hidden=true;
  const user=auth.user;$('avatar-button').textContent=user.displayName.split(/\s+/).slice(0,2).map(n=>n[0]).join('').toUpperCase();
  $('profile-name').textContent=user.displayName;$('profile-username').textContent=`@${user.username}`;$('profile-account').textContent=user.accountId;$('account-id').textContent=user.accountId;
  $('today').textContent=new Date().toLocaleDateString('es-EC',{weekday:'long',day:'numeric',month:'long'}).toUpperCase();
  setView('home');pendingBanner();connectSocket(state.epoch);await refresh();
}
async function refresh(){
  if(!state.auth)return;
  if(state.refreshing){state.refreshAgain=true;return;}
  state.refreshing=true;const epoch=state.epoch;$('refresh').disabled=true;
  try{
    const data=await client()(`/api/accounts/${state.auth.user.accountId}/snapshot`);
    if(epoch!==state.epoch)return;
    if(!state.snapshot || data.account.version>=state.snapshot.account.version){state.snapshot=data;renderSnapshot();}
    $('bank-error').hidden=true;
  }catch(err){if(epoch===state.epoch && !sessionFailure(err))errorAt('bank-error',err);}
  finally{state.refreshing=false;$('refresh').disabled=false;if(state.refreshAgain){state.refreshAgain=false;refresh();}}
}
async function connectSocket(epoch,attempt=0){
  if(epoch!==state.epoch || !state.auth)return;
  connection('','Conectando');
  try{
    const {ticket}=await client()('/api/auth/ws-ticket',{method:'POST'});
    if(epoch!==state.epoch)return;
    const url=new URL('/ws',state.base);url.protocol=url.protocol==='https:'?'wss:':'ws:';url.searchParams.set('ticket',ticket);
    const ws=new WebSocket(url);state.socket=ws;
    ws.onopen=()=>{if(epoch!==state.epoch){ws.close();return;}connection('online','En línea');refresh();};
    ws.onmessage=e=>{if(epoch!==state.epoch)return;try{const event=JSON.parse(e.data);if(event.type==='TRANSFER_RECEIVED')toast('Recibiste una transferencia. Tu saldo se está actualizando.');refresh();}catch{}};
    ws.onerror=()=>{};
    ws.onclose=()=>{if(epoch!==state.epoch)return;state.socket=null;connection('offline','Reconectando');state.reconnect=setTimeout(()=>connectSocket(epoch,attempt+1),Math.min(15000,1000*2**Math.min(attempt,4)));};
  }catch(err){if(epoch!==state.epoch)return;if(sessionFailure(err))return;connection('offline','Sin conexión');state.reconnect=setTimeout(()=>connectSocket(epoch,attempt+1),Math.min(15000,1000*2**Math.min(attempt,4)));}
}
function setView(view){
  state.view=view;const profile=view==='profile';$('home-view').hidden=view!=='home';$('history-view').hidden=profile;$('profile-view').hidden=!profile;$('history-filters').hidden=view!=='history';$('all-history').hidden=view==='history';
  const name={home:'Resumen',history:'Movimientos',profile:'Mi cuenta'}[view];$('breadcrumb').textContent=name;
  $('greeting').textContent=view==='home'?`Hola, ${state.auth.user.displayName.split(' ')[0]}.`:view==='history'?'Cada movimiento cuenta.':'Un espacio para ti.';
  $('page-description').textContent={home:'Todo lo que necesitas, en un solo lugar.',history:'Consulta lo que entra, lo que sale y lo que sigue.',profile:'Tu información, siempre a mano.'}[view];
  $('history-title').textContent=view==='history'?'Todos tus movimientos':'Últimos movimientos';
  all('[data-view]').forEach(b=>{b.classList.toggle('active',b.dataset.view===view);if(b.dataset.view===view)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
  if(state.snapshot)renderTransactions();
}
all('[data-view]').forEach(b=>b.addEventListener('click',()=>setView(b.dataset.view)));$('all-history').addEventListener('click',()=>setView('history'));$('avatar-button').addEventListener('click',()=>setView('profile'));$('refresh').addEventListener('click',refresh);
function renderSnapshot(){
  const data=state.snapshot;$('balance').textContent=state.hiddenBalance?'••••••':money(data.account.balance);const sums=totals(data.transactions);$('income').textContent=money(sums.income);$('expenses').textContent=money(sums.expenses);
  $('last-sync').textContent=`Sincronizado ${new Date(data.syncedAt).toLocaleTimeString('es-EC',{hour:'2-digit',minute:'2-digit'})}`;renderTransactions();
}
function detail(t){if(t.type==='SERVICE')return `${services[t.service]||'Servicio básico'} · ${t.contract}`;if(t.counterparty)return `${t.type==='TRANSFER_IN'?'Desde':'Hacia'} ${t.counterparty}`;return 'Cuenta BilleteraX';}
function el(tag,className,text){const e=document.createElement(tag);if(className)e.className=className;if(text!==undefined)e.textContent=text;return e;}
function renderTransactions(){
  const list=state.snapshot.transactions;let filtered=list;
  if(state.view==='history'){
    const search=$('search').value.trim().toLowerCase(),direction=$('direction').value;
    filtered=list.filter(t=>(direction==='all'|| (direction==='income'?centsOf(t.amount)>0n:centsOf(t.amount)<0n)) && `${transactionLabels[t.type]||t.type} ${detail(t)} ${t.amount}`.toLowerCase().includes(search));
  }else filtered=list.slice(0,5);
  $('transactions').replaceChildren();$('empty-history').hidden=filtered.length>0;
  $('history-count').textContent=`${list.length} ${list.length===1?'movimiento registrado':'movimientos registrados'}${state.view==='history' && filtered.length!==list.length?` · ${filtered.length} en esta búsqueda`:''}`;
  for(const t of filtered){
    const row=el('tr'),type=el('td'),wrap=el('div','transaction-type'),sym=el('span','row-icon');sym.innerHTML=icon(t.type==='SERVICE'?'bolt':centsOf(t.amount)>0n?'arrow-down-left':'arrow-up-right');const desc=el('div');desc.append(el('strong','',transactionLabels[t.type]||'Movimiento'),el('small','',detail(t)));wrap.append(sym,desc);type.append(wrap);
    const date=el('td','date-cell',new Date(t.occurredAt).toLocaleDateString('es-EC',{day:'2-digit',month:'short',year:'numeric'}));date.append(el('small','',new Date(t.occurredAt).toLocaleTimeString('es-EC',{hour:'2-digit',minute:'2-digit'})));
    const status=el('td');status.append(el('span','confirmed','Confirmado'));
    const amount=el('td',`amount-cell ${centsOf(t.amount)>0n?'credit':'debit'}`,`${centsOf(t.amount)>0n?'+':''}${money(t.amount)}`);row.append(type,date,status,amount);$('transactions').append(row);
  }
}
$('search').addEventListener('input',renderTransactions);$('direction').addEventListener('change',renderTransactions);
$('hide-balance').addEventListener('click',()=>{state.hiddenBalance=!state.hiddenBalance;$('hide-balance').setAttribute('aria-label',state.hiddenBalance?'Mostrar saldo':'Ocultar saldo');if(state.snapshot)renderSnapshot();});
$('copy-account').addEventListener('click',async()=>{try{await navigator.clipboard.writeText(state.auth.user.accountId);toast('Número de cuenta copiado.');}catch{toast(`Tu cuenta: ${state.auth.user.accountId}`);}});
$('logout').addEventListener('click',async()=>{
  $('logout').disabled=true;try{await client()('/api/auth/logout',{method:'POST'});showAccess();toast('Cerraste sesión en este navegador.');}catch(err){if(!sessionFailure(err))errorAt('bank-error','No se pudo cerrar la sesión en el servidor. Comprueba la conexión y reintenta.');}finally{$('logout').disabled=false;}
});
function pendingBanner(){const pending=loadPending(sessionStorage,state.base,state.auth.user.accountId);$('pending-banner').hidden=!pending;return pending;}
function openOperation(type,pending=null){
  if(!state.auth)return;const existing=pendingBanner();if(existing && !pending){toast('Primero comprueba la operación pendiente.');return;}
  state.operation=pending;state.review=!!pending;state.busy=false;$('operation-form').reset();$('operation-error').hidden=true;$('operation-review').hidden=true;
  $('operation-title').textContent=labels[type];$('operation-description').textContent={deposit:'Añade saldo de prueba a tu cuenta.',withdraw:'Retira saldo disponible de tu billetera.',transfer:'Envía dinero a otra cuenta BilleteraX.',service:'Paga electricidad, agua o internet.'}[type];
  $('operation-icon').innerHTML=icon(type==='service'?'bolt':type==='deposit'?'plus':type==='withdraw'?'arrow-up-right':'arrows');
  $('destination-field').hidden=type!=='transfer';$('destination').required=type==='transfer';$('service-fields').hidden=type!=='service';$('contract').required=type==='service';
  $('operation-form').dataset.type=type;
  for(const id of ['amount','destination','service','contract'])$(id).disabled=!!pending;
  $('operation-submit').disabled=false;$('close-operation').disabled=false;
  if(pending){const b=pending.body;$('amount').value=b.amount;$('destination').value=b.destinationAccountId||'';if(b.service)$('service').value=b.service;$('contract').value=b.contract||'';reviewOperation(pending,true);}else $('operation-submit').textContent='Continuar';
  $('operation-dialog').showModal();
}
function reviewOperation(op,retry=false){
  state.operation=op;state.review=true;$('operation-review').hidden=false;
  const b=op.body;$('operation-review').textContent=`${retry?'Comprobar la misma operación':'Confirma los datos antes de continuar'}\n${labels[op.type]}: ${money(b.amount)}${b.destinationAccountId?`\nDestino: ${b.destinationAccountId}`:''}${b.service?`\n${services[b.service]} · ${b.contract}`:''}${retry?'\nEl servidor evitará aplicar el movimiento dos veces.':''}`;
  $('operation-submit').textContent=retry?'Reintentar la misma operación':'Confirmar operación';
}
all('[data-operation]').forEach(b=>b.addEventListener('click',()=>openOperation(b.dataset.operation)));
$('retry-pending').addEventListener('click',()=>{const pending=pendingBanner();if(pending)openOperation(pending.type,pending);});
$('close-operation').addEventListener('click',()=>{if(!state.busy)$('operation-dialog').close();});
$('operation-dialog').addEventListener('cancel',e=>{if(state.busy)e.preventDefault();});
for(const id of ['amount','destination','service','contract'])$(id).addEventListener('input',()=>{if(!state.busy && !pendingBanner()){state.review=false;state.operation=null;$('operation-review').hidden=true;$('operation-submit').textContent='Continuar';}});
$('operation-form').addEventListener('submit',async e=>{
  e.preventDefault();if(state.busy || !state.auth)return;$('operation-error').hidden=true;
  if(!state.review){try{const op=makeOperation(e.currentTarget.dataset.type,state.auth.user.accountId,{amount:$('amount').value,destination:$('destination').value,service:$('service').value,contract:$('contract').value});reviewOperation(op);}catch(err){errorAt('operation-error',err);}return;}
  const op=state.operation,base=state.base,account=state.auth.user.accountId,epoch=state.epoch;
  try{savePending(sessionStorage,base,account,op);}catch{errorAt('operation-error','El navegador no permite conservar la operación. Habilita el almacenamiento de esta pestaña antes de continuar.');return;}
  state.busy=true;$('operation-submit').disabled=true;$('close-operation').disabled=true;for(const id of ['amount','destination','service','contract'])$(id).disabled=true;
  try{
    const receipt=await client()(endpoints[op.type],{method:'POST',body:op.body});clearPending(sessionStorage,base,account);
    if(epoch!==state.epoch)return;$('operation-dialog').close();toast(`${receipt.replayed?'Operación ya confirmada':'Operación confirmada'} · ${money(op.body.amount)}`);pendingBanner();await refresh();
  }catch(err){
    if(epoch!==state.epoch)return;
    if(err instanceof ApiError && err.status>=400 && err.status<500 && ![401,408,429].includes(err.status)){
      clearPending(sessionStorage,base,account);state.review=false;state.operation=null;$('operation-review').hidden=true;$('operation-submit').textContent='Continuar';for(const id of ['amount','destination','service','contract'])$(id).disabled=false;
    }else{$('operation-submit').textContent='Reintentar la misma operación';}
    if(!sessionFailure(err)){errorAt('operation-error',err);pendingBanner();}
  }finally{state.busy=false;$('operation-submit').disabled=false;$('close-operation').disabled=false;}
});
window.addEventListener('online',()=>{if(state.auth){closeSocket();connectSocket(state.epoch);refresh();}});
document.addEventListener('visibilitychange',()=>{if(!document.hidden && state.auth){refresh();if(!state.socket){clearTimeout(state.reconnect);connectSocket(state.epoch);}}});
async function start(){
  try{
    const urlBase=new URLSearchParams(location.search).get('servidor');
    const base=urlBase||storeRead(localStorage,'billeterax.server')||window.BILLETERAX_CONFIG?.apiBase||(!location.hostname.endsWith('github.io')?location.origin:'');
    if(base)setBase(normalizeBase(base,location.protocol));
    if(urlBase)history.replaceState(null,'',location.pathname);
    const saved=JSON.parse(storeRead(sessionStorage,'billeterax.session')||'null');
    if(saved && saved.base===state.base && new Date(saved.expiresAt)>new Date()){
      const profile=await createClient(state.base,saved.token)('/api/auth/me');saved.user=profile;await enter(saved);
    }else sessionStorage.removeItem('billeterax.session');
  }catch(err){showAccess();if(err.status!==401)errorAt('auth-error',err);}
}
start();
