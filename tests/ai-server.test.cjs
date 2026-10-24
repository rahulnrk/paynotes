const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createServer,validatePayload,validateOutput}=require('../server/insights.cjs');
const payload={findings:[{id:'finding-0',type:'budget',facts:{spentPaise:80000,budgetPaise:100000}}]};
const output={explanations:[{id:'finding-0',text:'Recorded spending is approaching the limit. Some transactions may be missing.'}]};
test('payload excludes arbitrary text, hidden fields, duplicate ids and invalid numbers',()=>{
  assert.deepEqual(validatePayload(payload),payload);
  for(const bad of [{...payload,sms:'private'}, {findings:[{...payload.findings[0],merchant:'private'}]}, {findings:[{...payload.findings[0],facts:{spentPaise:-1,budgetPaise:1}}]}, {findings:[...payload.findings,...payload.findings]}])assert.throws(()=>validatePayload(bad));
  assert.throws(()=>validateOutput({explanations:[{id:'invented',text:'wrong'}]},payload));
  assert.throws(()=>validateOutput({explanations:[]},payload));
});
test('authenticated endpoint invokes provider with minimal facts and structured output',async()=>{
  let sent;
  const server=createServer({token:'test-only-token-not-a-real-secret',apiKey:'fake-for-test',model:'test-model',fetchImpl:async(url,options)=>{sent={url,body:JSON.parse(options.body)};return {ok:true,json:async()=>({status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify(output)}]}]})};}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const url='http://127.0.0.1:'+server.address().port;
  try{
    assert.equal((await fetch(url+'/insights',{method:'POST'})).status,401);
    const headers={Authorization:'Bearer test-only-token-not-a-real-secret','Content-Type':'application/json'};
    assert.equal((await fetch(url+'/insights',{method:'POST',headers,body:'{}'})).status,400);
    const response=await fetch(url+'/insights',{method:'POST',headers,body:JSON.stringify(payload)});assert.equal(response.status,200);assert.deepEqual(await response.json(),output);
    assert.equal(sent.url,'https://api.openai.com/v1/responses');assert.equal(sent.body.store,false);assert.equal(sent.body.text.format.strict,true);assert.deepEqual(JSON.parse(sent.body.input),payload);
    assert.equal((await fetch(url+'/insights',{method:'POST',headers:{...headers,Origin:'https://untrusted.example'},body:JSON.stringify(payload)})).status,403);
  }finally{await new Promise(resolve=>server.close(resolve));}
});
test('missing provider config and invalid provider output fail clearly',async()=>{
  for(const configured of [false,true]){
    const server=createServer({token:'test-only-token-not-a-real-secret',apiKey:configured?'fake':'',model:'test',fetchImpl:async()=>({ok:true,json:async()=>({status:'completed',output:[{content:[{type:'output_text',text:'{"explanations":[]}'}]}]})})});
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
    try{const response=await fetch('http://127.0.0.1:'+server.address().port+'/insights',{method:'POST',headers:{Authorization:'Bearer test-only-token-not-a-real-secret','Content-Type':'application/json'},body:JSON.stringify(payload)});assert.equal(response.status,configured?502:503);}finally{await new Promise(resolve=>server.close(resolve));}
  }
});
