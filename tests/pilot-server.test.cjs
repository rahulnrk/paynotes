'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {createPilot}=require('../server/pilot.cjs');
(async()=>{
 const dataDir=fs.mkdtempSync(path.join(os.tmpdir(),'prardhana-api-test-'));
 const app=createPilot({dataDir,adminToken:'test-admin-secret-abcdefghijklmnopqrstuvwxyz',enrollCode:'test-enrollment-123456'});
 await new Promise(r=>app.server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+app.server.address().port;
 const request=async(route,method='GET',body,token)=>{const r=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,body:await r.json()};};
 try{
  assert.equal((await request('/health')).status,200);
  assert.equal((await request('/v1/admin/overview')).status,401);
  assert.equal((await request('/v1/enroll','POST',{code:'wrong'})).status,401);
  const enrollment=await request('/v1/enroll','POST',{code:'test-enrollment-123456'});assert.equal(enrollment.status,201);const {token,installation}=enrollment.body;
  const counters=Object.fromEntries(['transactions','reviewed','duplicates','corrections','opens','helpful','unhelpful','unknown','excluded','receipts','events'].map(k=>[k,k==='transactions'?10:0]));
  const snapshot={schema:2,parserVersion:6,appVersion:'0.6.0',exportedAt:Date.now(),budgetEnabled:false,captureEnabled:true,smsEnabled:true,notificationConnected:false,counters,patterns:[{shape:'your account debited inr <number> on <number>',issuer:'IDFC',outcome:'movement',count:5}]};
  assert.equal((await request('/v1/snapshot','POST',snapshot,token)).status,200);
  assert.equal((await request('/v1/snapshot','POST',snapshot,token)).status,200);
  assert.equal((await request('/v1/snapshot','POST',{...snapshot,amount:500},token)).status,400);
  assert.equal((await request('/v1/snapshot','POST',{...snapshot,patterns:[{...snapshot.patterns[0],shape:'paid <number> to Alice'}]},token)).status,400);
  assert.equal((await request('/v1/snapshot','POST',{...snapshot,patterns:[{...snapshot.patterns[0],shape:'paid 500'}]},token)).status,400);
  const issue={id:'11111111-2222-3333-4444-555555555555',parserVersion:6,issue:'wrong_direction',expected:'ignore',shape:'credited <number> to your <word> account',issuer:'Other'};
  assert.equal((await request('/v1/report','POST',issue,token)).status,200);
  assert.equal((await request('/v1/report','POST',issue,token)).status,200);
  assert.equal((await request('/v1/report','POST',{...issue,body:'private SMS'},token)).status,400);
  let overview=await request('/v1/admin/overview','GET',undefined,app.secrets.adminToken);assert.equal(overview.body.snapshots.length,1);assert.equal(overview.body.reports.length,1);assert.equal(overview.body.installs[0].id,installation);assert.ok(!JSON.stringify(overview.body).includes(token));
  assert.equal((await request('/v1/admin/report','POST',{id:issue.id,state:'released',fixedVersion:0},app.secrets.adminToken)).status,400);
  assert.equal((await request('/v1/admin/report','POST',{id:issue.id,state:'test_added',fixedVersion:0},app.secrets.adminToken)).status,200);
  assert.equal((await request('/v1/status','GET',undefined,token)).body.reports[0].state,'test_added');
  assert.equal((await request('/v1/admin/overview','GET',undefined,token)).status,401);
  const newer={...snapshot,exportedAt:snapshot.exportedAt+1000,counters:{...counters,transactions:20}};await request('/v1/snapshot','POST',newer,token);await request('/v1/snapshot','POST',snapshot,token);
  overview=await request('/v1/admin/overview','GET',undefined,app.secrets.adminToken);assert.equal(overview.body.snapshots[0].data.counters.transactions,20);
  const androidFixture=path.join(__dirname,'../artifacts/v06-tests/android-snapshot.json');if(fs.existsSync(androidFixture)){const actual=JSON.parse(fs.readFileSync(androidFixture,'utf8'));actual.exportedAt=Date.now()+2000;assert.equal((await request('/v1/snapshot','POST',actual,token)).status,200);}
  const enrollment2=(await request('/v1/enroll','POST',{code:'test-enrollment-123456'})).body;assert.equal((await request('/v1/status','GET',undefined,enrollment2.token)).body.reports.length,0);
  assert.equal((await request('/v1/me','DELETE',undefined,token)).status,200);assert.equal((await request('/v1/status','GET',undefined,token)).status,401);
  overview=await request('/v1/admin/overview','GET',undefined,app.secrets.adminToken);assert.equal(overview.body.snapshots.length,0);assert.equal(overview.body.reports.length,0);
  console.log('Pilot API passed: enrollment, auth isolation, privacy validation, idempotency, stale snapshots, triage, deletion.');
 }finally{await new Promise(r=>app.server.close(r));app.db.close();fs.rmSync(dataDir,{recursive:true,force:true});}
})().catch(e=>{console.error(e);process.exitCode=1;});
