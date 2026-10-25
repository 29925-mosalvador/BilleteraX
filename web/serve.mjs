import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=path.resolve(fileURLToPath(new URL('./dist/',import.meta.url)));
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml'};
http.createServer(async(req,res)=>{try{const url=new URL(req.url,'http://localhost');const name=decodeURIComponent(url.pathname)==='/'?'index.html':decodeURIComponent(url.pathname).slice(1);const full=path.resolve(root,name);if(!full.startsWith(root+path.sep) && full!==path.join(root,'index.html')){res.writeHead(403).end();return;}const data=await readFile(full);res.writeHead(200,{'Content-Type':types[path.extname(full)]||'application/octet-stream','Cache-Control':'no-store'}).end(data);}catch{res.writeHead(404).end('No encontrado');}}).listen(5173,'127.0.0.1',()=>console.log('Local: http://127.0.0.1:5173'));
