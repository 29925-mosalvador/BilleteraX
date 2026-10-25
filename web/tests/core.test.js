import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeBase,parseAmount,centsOf,money,totals,makeOperation,savePending,loadPending,clearPending,createClient,ApiError} from '../dist/core.js';

test('HTTPS pages reject mixed content and credentials in server addresses',()=>{
  assert.equal(normalizeBase('https://demo.trycloudflare.com/'),'https://demo.trycloudflare.com');
  for(const url of ['http://192.168.18.76:8080','https://user:pass@example.com','https://example.com/api','javascript:alert(1)','https://example.com?token=secret'])assert.throws(()=>normalizeBase(url));
  assert.equal(normalizeBase('http://127.0.0.1:8086','http:'),'http://127.0.0.1:8086');
});
test('amounts remain exact cents including comma input and maximum amount',()=>{
  assert.equal(parseAmount(' 0,01 '),'0.01');assert.equal(parseAmount('0012.1'),'12.10');assert.equal(parseAmount('999999999999.99'),'999999999999.99');
  for(const value of ['0','-1','1.001','1e2','Infinity','1000000000000.00','abc'])assert.throws(()=>parseAmount(value));
  assert.equal(money(centsOf('999999999999.99')),'$999,999,999,999.99');
  assert.deepEqual(totals([{amount:'0.10'},{amount:'0.20'},{amount:'-0.03'}]),{income:30n,expenses:3n});
});
test('transfer and service validation protects confirmation data',()=>{
  assert.throws(()=>makeOperation('transfer','CTA001',{amount:'2',destination:'CTA001'}));
  assert.throws(()=>makeOperation('service','CTA001',{amount:'2',service:'WATER',contract:'123'}));
  const transfer=makeOperation('transfer','CTA001',{amount:'2,50',destination:' cta002 '});assert.equal(transfer.body.amount,'2.50');assert.equal(transfer.body.destinationAccountId,'CTA002');
  assert.match(transfer.body.operationId,/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
});
test('response loss and reload preserve the exact operation; accounts and servers stay isolated',()=>{
  const data=new Map();const storage={setItem:(k,v)=>data.set(k,v),getItem:k=>data.get(k),removeItem:k=>data.delete(k)};
  const op=makeOperation('deposit','CTA001',{amount:'5.00'});savePending(storage,'https://one.example','CTA001',op);
  assert.deepEqual(loadPending(storage,'https://one.example','CTA001'),op);
  assert.equal(loadPending(storage,'https://one.example','CTA002'),null);assert.equal(loadPending(storage,'https://two.example','CTA001'),null);
  clearPending(storage,'https://one.example','CTA001');assert.equal(loadPending(storage,'https://one.example','CTA001'),null);
});
test('REST uses bearer headers, never cookies, and retains API error information',async()=>{
  let actual;
  const fetcher=async(url,options)=>{actual={url,options};return {status:422,ok:false,json:async()=>({code:'INSUFFICIENT_FUNDS',message:'Fondos insuficientes'})};};
  await assert.rejects(createClient('https://server.example','session-token',fetcher)('/api/transactions/withdraw',{method:'POST',body:{amount:'2.00'}}),e=>e.status===422 && e.code==='INSUFFICIENT_FUNDS');
  assert.equal(actual.options.headers.Authorization,'Bearer session-token');assert.equal(actual.options.credentials,'omit');assert.equal(actual.options.referrerPolicy,'no-referrer');assert.ok(!actual.url.includes('session-token'));
});
test('network and unreadable responses do not pretend an operation was rejected',async()=>{
  await assert.rejects(createClient('https://server.example','',async()=>{throw new Error('offline');})('/api/auth/me'),e=>e instanceof ApiError && e.status===0);
  await assert.rejects(createClient('https://server.example','',async()=>({status:502,ok:false,json:async()=>{throw new Error('html');}}))('/api/auth/me'),e=>e.status===502);
});
