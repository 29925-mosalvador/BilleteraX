export class ApiError extends Error {
  constructor(message,status=0,code='NETWORK_ERROR'){ super(message); this.status=status; this.code=code; }
}
export function normalizeBase(value,pageProtocol='https:') {
  let u; try {u=new URL(value);} catch {throw new Error('Introduce una dirección válida, por ejemplo https://servidor.trycloudflare.com');}
  if(!['http:','https:'].includes(u.protocol) || u.username || u.password || u.search || u.hash || !['','/'].includes(u.pathname)) throw new Error('Usa solo la dirección del servidor, sin rutas, contraseñas ni parámetros.');
  if(pageProtocol==='https:' && u.protocol!=='https:') throw new Error('Esta página necesita un servidor HTTPS. Usa el enlace del túnel.');
  return u.origin;
}
export function parseAmount(value){
  const text=value.trim().replace(',','.');
  if(!/^\d{1,12}(\.\d{1,2})?$/.test(text)) throw new Error('Escribe un monto positivo con un máximo de dos decimales.');
  const [whole,decimal='']=text.split('.');
  const cents=BigInt(whole)*100n+BigInt(decimal.padEnd(2,'0'));
  if(cents<=0n || cents>99999999999999n) throw new Error('El monto debe estar entre $0.01 y $999,999,999,999.99.');
  return `${cents/100n}.${String(cents%100n).padStart(2,'0')}`;
}
export function centsOf(value){
  const text=String(value); const negative=text.startsWith('-'); const [whole,decimal='']=text.replace(/^-/,'').split('.');
  if(!/^\d+$/.test(whole) || !/^\d{0,2}$/.test(decimal)) throw new Error('Monto recibido no válido');
  return (negative?-1n:1n)*(BigInt(whole)*100n+BigInt(decimal.padEnd(2,'0')));
}
export function money(value){
  const cents=typeof value==='bigint'?value:centsOf(value);
  const absolute=cents<0n?-cents:cents;
  return `${cents<0n?'-':''}$${(absolute/100n).toLocaleString('en-US')}.${String(absolute%100n).padStart(2,'0')}`;
}
export function totals(transactions){
  return transactions.reduce((a,t)=>{const c=centsOf(t.amount);a[c>=0n?'income':'expenses']+=c>=0n?c:-c;return a;},{income:0n,expenses:0n});
}
export function uuid(){
  const a=crypto.getRandomValues(new Uint8Array(16)); a[6]=(a[6]&15)|64;a[8]=(a[8]&63)|128;
  const h=Array.from(a,v=>v.toString(16).padStart(2,'0')).join('');
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;
}
export const endpoints={deposit:'/api/transactions/deposit',withdraw:'/api/transactions/withdraw',transfer:'/api/transactions/transfer',service:'/api/payments/services'};
export function makeOperation(type,accountId,fields,operationId=uuid()){
  if(!endpoints[type]) throw new Error('Operación no válida');
  const body={operationId,accountId,amount:parseAmount(fields.amount)};
  if(type==='transfer'){
    body.destinationAccountId=fields.destination.trim().toUpperCase();
    if(!/^CTA[0-9]{3,12}$/.test(body.destinationAccountId) || body.destinationAccountId===accountId) throw new Error('Introduce otra cuenta de destino válida.');
  }
  if(type==='service'){
    body.service=fields.service;body.contract=fields.contract.trim();
    if(!['ELECTRICITY','WATER','INTERNET'].includes(body.service) || !/^[A-Za-z0-9-]{6,20}$/.test(body.contract)) throw new Error('Elige un servicio y un contrato de 6 a 20 letras, números o guiones.');
  }
  return {type,body};
}
export function pendingKey(base,account){return `billeterax.pending:${base}:${account}`;}
export function savePending(storage,base,account,operation){storage.setItem(pendingKey(base,account),JSON.stringify(operation));}
export function loadPending(storage,base,account){
  try{
    const value=JSON.parse(storage.getItem(pendingKey(base,account))||'null');
    if(!value || !endpoints[value.type] || value.body.accountId!==account || !/^[a-f0-9-]{36}$/.test(value.body.operationId)) return null;
    const checked=makeOperation(value.type,account,{amount:value.body.amount,destination:value.body.destinationAccountId,service:value.body.service,contract:value.body.contract},value.body.operationId);
    return checked;
  }catch{return null;}
}
export function clearPending(storage,base,account){storage.removeItem(pendingKey(base,account));}
export function createClient(base,token='',fetcher=fetch){
  return async (path,{method='GET',body,timeout=15000}={})=>{
    const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),timeout);
    try{
      const headers={Accept:'application/json'};
      if(token) headers.Authorization=`Bearer ${token}`;
      if(body!==undefined) headers['Content-Type']='application/json';
      const r=await fetcher(base+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:controller.signal,credentials:'omit',cache:'no-store',referrerPolicy:'no-referrer'});
      if(r.status===204) return null;
      const data=await r.json().catch(()=>null);
      if(!r.ok) throw new ApiError(data?.message || `El servidor respondió con un error (${r.status}).`,r.status,data?.code);
      if(!data) throw new ApiError('El servidor no devolvió una respuesta válida.',r.status,'INVALID_RESPONSE');
      return data;
    }catch(e){
      if(e instanceof ApiError) throw e;
      throw new ApiError('No pudimos contactar al servidor. Comprueba que Debian y el túnel estén activos.');
    }finally{clearTimeout(timer);}
  };
}
