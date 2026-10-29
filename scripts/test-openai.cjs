'use strict';
// Explicit live smoke test: two synthetic requests, no retries, no real financial records.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createServer}=require('../server/insights.cjs');

async function main(){
  if(!process.env.OPENAI_API_KEY||!process.env.OPENAI_MODEL||!process.env.PILOT_ACCESS_TOKEN)throw Error('Missing OpenAI test configuration. No API request was sent.');
  const usages=[];
  const server=createServer({fetchImpl:async(url,options)=>{
    const response=await fetch(url,options);
    if(response.ok){const body=await response.clone().json();usages.push({inputTokens:body.usage?.input_tokens??null,outputTokens:body.usage?.output_tokens??null});}
    else console.error(`OpenAI returned HTTP ${response.status}; check project access, billing, model availability or quota. Response body is intentionally not logged.`);
    return response;
  }});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const facts={period:'Month',recordedOutflowPaise:120000,previousOutflowPaise:140000,moneyInPaise:300000,cashPaise:0,transfersPaise:0,entries:12,previousEntries:10,unreviewed:2,targetPaise:0};
  const cases=[
    {name:'missing-income-context',question:'Help me make a realistic savings plan. I have not told you my income or commitments yet.',check:r=>assert.equal(r.proposal??null,null)},
    {name:'reviewable-category-limit',question:'Prepare a draft Food & drink category limit of 2500 Indian rupees per calendar month. Do not save it; I will review it in the app.',check:r=>assert.deepEqual(r.proposal,{type:'limit',name:'Food & drink',amountPaise:250000,date:null})}
  ];
  const results=[];
  try{
    for(const test of cases){
      const start=Date.now();
      const response=await fetch(`http://127.0.0.1:${server.address().port}/chat`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${process.env.PILOT_ACCESS_TOKEN}`},body:JSON.stringify({messages:[{role:'user',content:test.question}],facts}),signal:AbortSignal.timeout(45000)});
      assert.equal(response.status,200,`Case ${test.name}: server status ${response.status}`);
      const result=await response.json();assert.ok(result.reply.trim());test.check(result);
      results.push({name:test.name,passed:true,latencyMs:Date.now()-start,usage:usages.at(-1)});
      console.log(`PASS ${test.name}`);
    }
    const destination=path.join(__dirname,'../artifacts/openai-test-results.json');
    fs.mkdirSync(path.dirname(destination),{recursive:true});
    fs.writeFileSync(destination,JSON.stringify({at:new Date().toISOString(),model:process.env.OPENAI_MODEL,syntheticDataOnly:true,results},null,2));
    console.log('Live API smoke test passed. This does not establish financial-advice quality.');
  }finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
}
if(require.main===module)main().catch(error=>{console.error(error.message);process.exitCode=1;});
module.exports={main};
