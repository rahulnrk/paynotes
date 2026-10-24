'use strict';
(() => {
 let token='',timer;
 const node=(tag,text)=>{const n=document.createElement(tag);n.textContent=text;return n;};
 async function api(path,body){const res=await fetch('/v1/admin/'+path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,cache:'no-store'});const result=await res.json();if(!res.ok)throw Error(result.error||'Request failed');return result;}
 async function refresh(){if(!token)return;try{
  const data=await api('overview');
  records=data.snapshots.map(r=>({schema:1,participant:r.installation,exportedAt:r.data.exportedAt,budgetEnabled:r.data.budgetEnabled,...r.data.counters}));render();
  $('serverStatus').textContent=data.installs.length+' installations · '+data.snapshots.length+' snapshots · refreshed '+new Date().toLocaleTimeString()+'. Data expires after '+data.retentionDays+' inactive days.';
  $('formatRows').replaceChildren();const grouped=new Map();
  for(const snap of data.snapshots)for(const p of snap.data.patterns){const key=[snap.data.parserVersion,p.issuer,p.header||'',p.outcome,p.shape].join('|');let row=grouped.get(key);if(!row){row={...p,version:snap.data.parserVersion,count:0,devices:0};grouped.set(key,row);}row.count+=p.count;row.devices++;}
  for(const p of [...grouped.values()].sort((a,b)=>(a.outcome==='unmatched'?-1:0)-(b.outcome==='unmatched'?-1:0)||b.devices-a.devices).slice(0,60)){
   const div=node('div','');div.className='row';const detail=node('div','');detail.append(node('strong',p.issuer+(p.header?' / '+p.header:'')+' · '+p.outcome+' · parser '+p.version),node('p',p.shape));div.append(detail,node('span',p.count+' observations / '+p.devices+' devices'));$('formatRows').append(div);
  }
  if(!grouped.size)$('formatRows').append(node('p','Waiting for an opted-in phone to sync masked format counts.'));
  $('reportRows').replaceChildren();for(const r of data.reports){
   const box=node('div','');box.className='row triage';const left=node('div','');left.className='case-detail';left.append(node('strong',r.data.issue.replaceAll('_',' ')+' · '+r.data.issuer+' · parser '+r.data.parserVersion),node('p','Expected: '+r.data.expected+' · '+r.data.shape),node('small','Case '+r.id));
   const controls=node('div',''),select=document.createElement('select');for(const state of ['new','investigating','test_added','released','dismissed']){const o=node('option',state.replaceAll('_',' '));o.value=state;select.append(o);}select.value=r.state;select.setAttribute('aria-label','Report status');controls.className='case-actions';
   const version=document.createElement('input');version.type='number';version.setAttribute('aria-label','Fixed parser version');version.min='0';version.placeholder='Fixed parser version';version.value=r.fixedVersion||'';version.style.width='130px';
   const save=node('button','Save status');save.onclick=async()=>{try{await api('report',{id:r.id,state:select.value,fixedVersion:Number(version.value)||0});await refresh();}catch(e){$('serverStatus').textContent=e.message;}};
   const download=node('button','Download regression case');download.className='secondary';download.onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify({caseId:r.id,parserVersion:r.data.parserVersion,issuer:r.data.issuer,shape:r.data.shape,issue:r.data.issue,expected:r.data.expected,note:'Masked grammar is not a runnable financial fixture. Reconstruct a synthetic example and verify before changing parser rules.'},null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='case-'+r.id+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};controls.append(select,version,save,download);box.append(left,controls);$('reportRows').append(box);
  }
  if(!data.reports.length)$('reportRows').append(node('p','No reported extraction issues yet. Testers can submit one from the app.'));
  const rel=data.release;$('releaseInfo').replaceChildren(node('p','App '+rel.versionName+' · parser '+rel.parserVersion+' · version code '+rel.versionCode));if(rel.sha256){const a=node('a','Download APK');a.href=rel.downloadPath;$('releaseInfo').append(a,node('p','SHA-256: '+rel.sha256));}
  // Deletion revokes credentials and cascades report/snapshot removal.
  for(const install of data.installs){const row=node('div','');row.className='row';const remove=node('button','Remove installation');remove.className='secondary';remove.onclick=async()=>{if(!confirm('Remove this installation, its reports and server snapshots? The phone must enroll again.'))return;await api('remove-install',{id:install.id});await refresh();};row.append(node('span',install.id+' · last seen '+new Date(install.lastSeen).toLocaleString()),remove);$('people').append(row);}
 }catch(e){$('serverStatus').textContent=e.message;}}
 $('connect').onclick=async()=>{token=$('adminToken').value.trim();$('adminToken').value='';await refresh();clearInterval(timer);timer=setInterval(refresh,60000);};
 $('disconnect').onclick=()=>{token='';clearInterval(timer);$('enrollment').replaceChildren();$('formatRows').replaceChildren();$('reportRows').replaceChildren();$('releaseInfo').replaceChildren();records=[];render();$('serverStatus').textContent='Signed out.';};
 $('refreshPilot').onclick=refresh;
 $('showEnrollment').onclick=async()=>{try{const r=await api('enrollment');$('enrollment').replaceChildren(node('p','On the phone: Settings → Join development pilot. Use your HTTPS tunnel address and enrollment code: '+r.code));}catch(e){$('serverStatus').textContent=e.message;}};
})();
