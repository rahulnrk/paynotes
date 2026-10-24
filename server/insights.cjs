'use strict';
const http=require('node:http');
const {timingSafeEqual}=require('node:crypto');
const types={budget:['spentPaise','budgetPaise'],recurring:['payments','intervalDays','latestPaise'],price:['previousPaise','latestPaise'],bill:['daysUntil','estimatedPaise'],anomaly:['amountPaise','medianPaise','baselineCount']};
function validatePayload(input){
  if(!input||Object.keys(input).join()!=='findings'||!Array.isArray(input.findings)||input.findings.length<1||input.findings.length>12)throw Error('Invalid findings');
  const ids=new Set();
  return {findings:input.findings.map(r=>{
    if(!r||Object.keys(r).sort().join()!=='facts,id,type'||typeof r.id!=='string'||!/^finding-\d{1,2}$/.test(r.id)||ids.has(r.id)||!Object.hasOwn(types,r.type)||!r.facts||typeof r.facts!=='object'||Array.isArray(r.facts))throw Error('Invalid finding');
    ids.add(r.id);const expected=types[r.type];
    if(Object.keys(r.facts).sort().join()!==[...expected].sort().join())throw Error('Unexpected fact fields');
    for(const key of expected)if(!Number.isSafeInteger(r.facts[key])||r.facts[key]<0||r.facts[key]>1e14)throw Error('Invalid fact value');
    return {id:r.id,type:r.type,facts:Object.fromEntries(expected.map(k=>[k,r.facts[k]]))};
  })};
}
function validateOutput(result,payload){
  if(!result||Object.keys(result).join()!=='explanations'||!Array.isArray(result.explanations)||result.explanations.length!==payload.findings.length)throw Error('Invalid AI output');
  const expected=new Set(payload.findings.map(r=>r.id));
  for(const row of result.explanations){if(!row||Object.keys(row).sort().join()!=='id,text'||!expected.delete(row.id)||typeof row.text!=='string'||!row.text.trim()||row.text.length>1200)throw Error('Invalid AI explanation');}
  return result;
}
async function generate(payload,{apiKey,model,fetchImpl=fetch}){
  const response=await fetchImpl('https://api.openai.com/v1/responses',{
    method:'POST',headers:{Authorization:'Bearer '+apiKey,'Content-Type':'application/json'},signal:AbortSignal.timeout(35000),
    body:JSON.stringify({model,store:false,max_output_tokens:2400,
      instructions:'Explain the supplied spending-pattern findings, one short explanation per id. Facts are computed on device. Do not invent facts, merchants, dates, amounts, or additional findings. Use plain language without repeating numeric values. Recurring payments are possible subscriptions or bills, never confirmed. Price differences may reflect a plan change or one-off fee. Bill dates are forecasts. Anomalies do not establish fraud. Budget findings refer only to recorded transactions, not complete finances. Do not give investment, idle-cash, lending, or debt advice. No tools or actions. Keep each explanation under 600 characters.',
      input:JSON.stringify(payload),text:{format:{type:'json_schema',name:'spending_explanations',strict:true,schema:{type:'object',properties:{explanations:{type:'array',items:{type:'object',properties:{id:{type:'string',enum:payload.findings.map(r=>r.id)},text:{type:'string'}},required:['id','text'],additionalProperties:false}}},required:['explanations'],additionalProperties:false}}}
    })
  });
  if(!response.ok)throw Error('Provider request failed');
  const data=await response.json();if(data.status!=='completed')throw Error('Incomplete provider response');
  const text=(data.output||[]).flatMap(r=>r.content||[]).filter(r=>r.type==='output_text').map(r=>r.text).join('');
  return validateOutput(JSON.parse(text),payload);
}
function createServer(options={}){
  const apiKey=options.apiKey??process.env.OPENAI_API_KEY,model=options.model??process.env.OPENAI_MODEL,token=options.token??process.env.PILOT_ACCESS_TOKEN;
  if(!token||token.length<24)throw Error('Set PILOT_ACCESS_TOKEN to a random value of at least 24 characters.');
  let requests=0,windowStart=Date.now(),active=0;
  return http.createServer(async(req,res)=>{
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
    const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
    if(req.url==='/health'&&req.method==='GET')return send(200,{ready:!!apiKey&&!!model});
    if(req.url!=='/insights'||req.method!=='POST')return send(404,{error:'Not found'});
    const provided=Buffer.from(req.headers.authorization||''),expected=Buffer.from('Bearer '+token);
    if(provided.length!==expected.length||!timingSafeEqual(provided,expected))return send(401,{error:'Unauthorized'});
    if(req.headers.origin)return send(403,{error:'Browser requests are not supported'});
    if(!/^application\/json(?:;|$)/i.test(req.headers['content-type']||''))return send(415,{error:'JSON required'});
    if(!apiKey||!model)return send(503,{error:'AI provider is not configured'});
    if(Date.now()-windowStart>=3600000){windowStart=Date.now();requests=0;}
    if(requests>=30||active>=2)return send(429,{error:'Pilot request limit reached'});
    requests++;active++;
    try{
      let length=0;const chunks=[];
      for await(const chunk of req){length+=chunk.length;if(length>16384){send(413,{error:'Payload too large'});req.resume();return;}chunks.push(chunk);}
      let payload;try{payload=validatePayload(JSON.parse(Buffer.concat(chunks).toString('utf8')));}catch{return send(400,{error:'Invalid insight facts'});}
      const result=await generate(payload,{apiKey,model,fetchImpl:options.fetchImpl});send(200,result);
    }catch{if(!res.headersSent)send(502,{error:'AI explanation unavailable. Local insights remain available.'});}
    finally{active--;}
  });
}
if(require.main===module){try{const server=createServer();server.requestTimeout=45000;server.headersTimeout=10000;server.listen(4180,'127.0.0.1',()=>console.log('Pilot AI service listening on 127.0.0.1:4180; configure an HTTPS reverse proxy for Android.'));}catch(e){console.error(e.message);process.exitCode=1;}}
module.exports={createServer,validatePayload,validateOutput,generate};
