'use strict';
// Node 22.13+; one SQLite-backed development/pilot instance, behind HTTPS remotely.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {DatabaseSync}=require('node:sqlite');
const {randomBytes,createHash,timingSafeEqual}=require('node:crypto');
const COUNTERS=['transactions','reviewed','duplicates','corrections','opens','helpful','unhelpful','unknown','excluded','receipts','events'];
const ISSUERS=new Set(['SBI','ICICI','Kotak','IDFC','IDBI','UCO','HDFC','Canara','Airtel Payments Bank','Axis','YES Bank','KVB','Paytm Payments Bank','PNB','Bank of India','Union Bank','Bank of Baroda','Telangana Grameena Bank','Other']);
const ISSUES=new Set(['wrong_amount','wrong_direction','wrong_name','wrong_category','not_transaction','missing_transaction','duplicate','missed_recurring','user_correction','other']);
const STATES=new Set(['new','investigating','test_added','released','dismissed']);
const hash=s=>createHash('sha256').update(s).digest('hex');
const equal=(a,b)=>typeof a==='string'&&typeof b==='string'&&timingSafeEqual(Buffer.from(hash(a)),Buffer.from(hash(b)));
// Exactly the finite vocabulary used on-device: arbitrary words, IDs and numbers are rejected.
const VOCAB=new Set(('inr rs ₹ debited credited paid received sent transferred transfer to from at on by for via upi upiref ref refno reference no txn transactionid id imps neft rtgs account ac bal balance available avl otp failed declined due will be your has been of is with amount transaction payment purchase using ending bank a c card credit debit atm cash withdrawn withdrawal refund refunded reversed reversal salary interest dividend emi loan mandate autopay subscription bill minimum total recharge successful successfully success pending request requested scheduled limit outstanding if already ignore charges fee tax cheque chq returned return rrn creditedto towards deposited deposit reward cashback creditedwith cancelled canceled rejected insufficient funds acct not processed presented maximum ceiling').split(' '));
function shape(s){if(typeof s!=='string'||s.length<1||s.length>1600)throw Error('Invalid masked shape');for(const t of s.split(' '))if(!['<word>','<number>'].includes(t)&&!VOCAB.has(t))throw Error('Unmasked token rejected');return s;}
function exact(o,keys){if(!o||typeof o!=='object'||Array.isArray(o)||Object.keys(o).sort().join()!==[...keys].sort().join())throw Error('Unexpected fields');}
function integer(n,max=1000000000){if(!Number.isSafeInteger(n)||n<0||n>max)throw Error('Invalid count');return n;}
function snapshot(o){
 exact(o,['schema','parserVersion','appVersion','exportedAt','budgetEnabled','captureEnabled','smsEnabled','notificationConnected','counters','patterns']);
 if(o.schema!==2||typeof o.budgetEnabled!=='boolean'||typeof o.captureEnabled!=='boolean'||typeof o.smsEnabled!=='boolean'||typeof o.notificationConnected!=='boolean'||!/^\d+\.\d+\.\d+$/.test(o.appVersion))throw Error('Invalid snapshot');
 integer(o.parserVersion,99999);integer(o.exportedAt,1e14);if(Math.abs(Date.now()-o.exportedAt)>7*864e5)throw Error('Check device clock');exact(o.counters,COUNTERS);for(const k of COUNTERS)integer(o.counters[k]);if(o.counters.reviewed>o.counters.transactions)throw Error('Review count');
 if(!Array.isArray(o.patterns)||o.patterns.length>100)throw Error('Too many patterns');const seen=new Set();
 for(const p of o.patterns){exact(p,Object.hasOwn(p,'header')?['shape','issuer','outcome','count','header']:['shape','issuer','outcome','count']);if(p.header!==undefined&&(typeof p.header!=='string'||!/^([A-Z0-9]{6})?$/.test(p.header)))throw Error('Invalid registered header');shape(p.shape);if(!ISSUERS.has(p.issuer)||!['movement','receipt','event','unmatched'].includes(p.outcome))throw Error('Invalid pattern');integer(p.count);const key=[p.shape,p.issuer,p.header||'',p.outcome].join('|');if(seen.has(key))throw Error('Repeated pattern');seen.add(key);}
 return o;
}
function report(o){exact(o,['id','parserVersion','issue','expected','shape','issuer']);if(!/^[a-f0-9-]{36}$/.test(o.id)||!ISSUES.has(o.issue)||!['debit','credit','receipt','event','ignore','unknown'].includes(o.expected)||!ISSUERS.has(o.issuer))throw Error('Invalid report');integer(o.parserVersion,99999);shape(o.shape);return o;}
function createPilot(options={}){
 const root=path.resolve(__dirname,'..'),data=options.dataDir||path.join(root,'artifacts','pilot-service');fs.mkdirSync(data,{recursive:true});
 const secretFile=path.join(data,'secrets.json');let saved={};if(fs.existsSync(secretFile))saved=JSON.parse(fs.readFileSync(secretFile,'utf8'));
 const secrets={adminToken:options.adminToken||process.env.PILOT_ADMIN_TOKEN||saved.adminToken||randomBytes(32).toString('hex'),enrollCode:options.enrollCode||process.env.PILOT_ENROLL_CODE||saved.enrollCode||randomBytes(12).toString('hex')};
 if(secrets.adminToken.length<24||secrets.enrollCode.length<12)throw Error('Use strong pilot secrets');fs.writeFileSync(secretFile,JSON.stringify(secrets,null,2),{mode:0o600});
 const db=new DatabaseSync(path.join(data,'pilot.sqlite'));db.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;');
 db.exec(`CREATE TABLE IF NOT EXISTS installs(id TEXT PRIMARY KEY,token TEXT UNIQUE NOT NULL,created INTEGER NOT NULL,lastSeen INTEGER NOT NULL,revoked INTEGER DEFAULT 0);
 CREATE TABLE IF NOT EXISTS snapshots(installation TEXT PRIMARY KEY REFERENCES installs(id) ON DELETE CASCADE,received INTEGER NOT NULL,version INTEGER NOT NULL,data TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS reports(id TEXT PRIMARY KEY,installation TEXT NOT NULL REFERENCES installs(id) ON DELETE CASCADE,received INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'new',fixedVersion INTEGER NOT NULL DEFAULT 0,data TEXT NOT NULL); CREATE INDEX IF NOT EXISTS report_state ON reports(state);`);
 const expire=()=>db.prepare('DELETE FROM installs WHERE lastSeen<?').run(Date.now()-90*864e5);
 expire();const retentionTimer=setInterval(expire,24*3600e3);retentionTimer.unref();
 const rate=new Map();let calls=0;
 function allow(key,limit){const now=Date.now();if(++calls%100===0)for(const [k,v] of rate)if(v.reset<now)rate.delete(k);let r=rate.get(key);if(!r||r.reset<now){if(rate.size>5000)return false;r={count:0,reset:now+3600e3};rate.set(key,r);}return ++r.count<=limit;}
 function json(res,status,value){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; frame-ancestors 'none'"});res.end(JSON.stringify(value));}
 async function body(req){if(req.headers['content-type']!=='application/json')throw Error('Use application/json');let size=0,chunks=[];for await(const c of req){size+=c.length;if(size>192*1024){throw Error('Payload too large');}chunks.push(c);}return JSON.parse(Buffer.concat(chunks).toString('utf8'));}
 // Only the signed, non-debuggable release build is offered to phones; a debug APK is never served.
 const apk=path.join(options.apkDir||path.join(root,'app','build','outputs','apk','release'),'app-release.apk');
 // release.json is written by Gradle only after apksigner verified the APK; it records that APK's hash and signer.
 // An APK whose bytes do not match that record (rebuilt unsigned, swapped, half-copied) is never offered.
 // The hash is computed once per file version, not on every status request.
 let hashed={key:'',sha256:null};
 function release(){
  let metadata={versionCode:0,versionName:'0.0.0',parserVersion:0};try{metadata=JSON.parse(fs.readFileSync(path.join(path.dirname(apk),'release.json'),'utf8'));}catch{}
  let sha256=null;try{const st=fs.statSync(apk),key=st.size+':'+st.mtimeMs;if(hashed.key!==key)hashed={key,sha256:hash(fs.readFileSync(apk))};sha256=hashed.sha256;}catch{}
  const verified=!!sha256&&metadata.apkSha256===sha256&&typeof metadata.signerSha256==='string'&&metadata.debuggable===false;
  return {versionCode:metadata.versionCode,versionName:metadata.versionName,parserVersion:metadata.parserVersion,signerSha256:verified?metadata.signerSha256:null,downloadPath:'/v1/app.apk',sha256:verified?sha256:null,verified};
 }

 const server=http.createServer(async(req,res)=>{
  try{
   const route=new URL(req.url,'http://localhost').pathname,ip=req.socket.remoteAddress||'unknown';
   if(route==='/health'&&req.method==='GET')return json(res,200,{ok:true});
   if(route==='/v1/release'&&req.method==='GET')return json(res,200,release());
   if(route==='/v1/app.apk'&&req.method==='GET'){const r=release();if(!r.verified)return json(res,404,{error:'Build unavailable'});let bytes;try{bytes=fs.readFileSync(apk);}catch{return json(res,404,{error:'Build unavailable'});}
    // Serve exactly the bytes that were verified: a rebuild between the check and the read is refused, not half-served.
    if(hash(bytes)!==r.sha256)return json(res,409,{error:'Build changed; retry'});res.writeHead(200,{'Content-Type':'application/vnd.android.package-archive','Content-Disposition':'attachment; filename="prardhana-'+r.versionName+'.apk"','Content-Length':bytes.length,'X-Content-Type-Options':'nosniff','X-Apk-Sha256':r.sha256});return res.end(bytes);}
   if(route==='/v1/enroll'&&req.method==='POST'){
    if(!allow('enroll:'+ip,30))return json(res,429,{error:'Try enrollment later'});const b=await body(req);exact(b,['code']);if(!equal(b.code,secrets.enrollCode))return json(res,401,{error:'Invalid enrollment code'});
    const token=randomBytes(32).toString('hex'),id=randomBytes(16).toString('hex'),now=Date.now();db.prepare('INSERT INTO installs(id,token,created,lastSeen) VALUES(?,?,?,?)').run(id,hash(token),now,now);return json(res,201,{installation:id,token});
   }
   const token=(req.headers.authorization||'').replace(/^Bearer /,'');
   if(route.startsWith('/v1/admin/')){
    if(!allow('admin:'+ip,600))return json(res,429,{error:'Rate limit'});if(!equal(token,secrets.adminToken))return json(res,401,{error:'Sign in to admin'});
    if(route==='/v1/admin/overview'&&req.method==='GET'){
     db.prepare('DELETE FROM installs WHERE lastSeen<?').run(Date.now()-90*864e5);
     const installs=db.prepare('SELECT id,created,lastSeen,revoked FROM installs ORDER BY lastSeen DESC LIMIT 2000').all();
     const snapshots=db.prepare('SELECT installation,received,data FROM snapshots ORDER BY received DESC LIMIT 2000').all().map(r=>({...r,data:JSON.parse(r.data)}));
     const reports=db.prepare('SELECT id,installation,received,state,fixedVersion,data FROM reports ORDER BY received DESC LIMIT 1000').all().map(r=>({...r,data:JSON.parse(r.data)}));return json(res,200,{installs,snapshots,reports,release:release(),retentionDays:90});
    }
    if(route==='/v1/admin/enrollment'&&req.method==='GET')return json(res,200,{code:secrets.enrollCode});
    if(route==='/v1/admin/report'&&req.method==='POST'){const b=await body(req);exact(b,['id','state','fixedVersion']);if(!/^[a-f0-9-]{36}$/.test(b.id)||!STATES.has(b.state))throw Error('Invalid transition');integer(b.fixedVersion,99999);if(b.state==='released'&&b.fixedVersion<1)throw Error('Set fixed parser version');const r=db.prepare('UPDATE reports SET state=?,fixedVersion=? WHERE id=?').run(b.state,b.fixedVersion,b.id);return json(res,r.changes?200:404,{ok:!!r.changes});}
    if(route==='/v1/admin/remove-install'&&req.method==='POST'){const b=await body(req);exact(b,['id']);if(typeof b.id!=='string'||!/^[a-f0-9]{32}$/.test(b.id))throw Error('Invalid installation');db.prepare('DELETE FROM installs WHERE id=?').run(b.id);return json(res,200,{ok:true});}
   }else if(route.startsWith('/v1/')){
    const install=db.prepare('SELECT id FROM installs WHERE token=? AND revoked=0').get(hash(token));if(!install)return json(res,401,{error:'Enrollment required'});
    if(!allow('device:'+install.id,120))return json(res,429,{error:'Try sync later'});
    if(route==='/v1/snapshot'&&req.method==='POST'){const b=snapshot(await body(req)),old=db.prepare('SELECT data FROM snapshots WHERE installation=?').get(install.id);if(!old||JSON.parse(old.data).exportedAt<b.exportedAt)db.prepare('INSERT INTO snapshots(installation,received,version,data) VALUES(?,?,?,?) ON CONFLICT(installation) DO UPDATE SET received=excluded.received,version=excluded.version,data=excluded.data').run(install.id,Date.now(),b.parserVersion,JSON.stringify(b));db.prepare('UPDATE installs SET lastSeen=? WHERE id=?').run(Date.now(),install.id);return json(res,200,{ok:true});}
    if(route==='/v1/report'&&req.method==='POST'){const b=report(await body(req));db.prepare('INSERT OR IGNORE INTO reports(id,installation,received,data) VALUES(?,?,?,?)').run(b.id,install.id,Date.now(),JSON.stringify(b));return json(res,200,{ok:true});}
    if(route==='/v1/status'&&req.method==='GET'){const reports=db.prepare('SELECT id,state,fixedVersion FROM reports WHERE installation=? ORDER BY received DESC LIMIT 100').all(install.id);return json(res,200,{reports,release:release()});}
    if(route==='/v1/me'&&req.method==='DELETE'){db.prepare('DELETE FROM installs WHERE id=?').run(install.id);return json(res,200,{ok:true});}
   }
   if(req.method==='GET'&&['/','/app.js','/pilot.js'].includes(route)){
    const file=path.join(root,'admin',route==='/'?'index.html':route.slice(1));res.writeHead(200,{'Content-Type':route.endsWith('.js')?'text/javascript; charset=utf-8':'text/html; charset=utf-8','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",'Referrer-Policy':'no-referrer'});return fs.createReadStream(file).pipe(res);
   }
   json(res,404,{error:'Not found'});
  }catch(e){if(!res.headersSent)json(res,400,{error:e instanceof SyntaxError?'Invalid JSON':String(e.message).slice(0,140)});else res.end();}
 });
 server.requestTimeout=15000;server.headersTimeout=10000;
 server.on('close',()=>clearInterval(retentionTimer));
 return {server,db,secrets,close(){clearInterval(retentionTimer);server.close();db.close();}};
}
if(require.main===module){const app=createPilot();const port=Number(process.env.PORT||4173);app.server.listen(port,process.env.HOST||'127.0.0.1',()=>console.log(`Prardhana pilot: http://127.0.0.1:${port} · credentials in artifacts/pilot-service/secrets.json`));}
module.exports={createPilot,snapshot,report};
