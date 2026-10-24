'use strict';
const $ = id => document.getElementById(id);
const fields = ['transactions','reviewed','duplicates','corrections','opens','helpful','unhelpful'];
let records = [];
try { records = JSON.parse(localStorage.getItem('prardhana-pilot') || '[]'); if (!Array.isArray(records)) records=[]; } catch { records=[]; }
function validate(r) {
  if (!r || r.schema !== 1 || typeof r.participant !== 'string' || !/^[\w-]{1,64}$/.test(r.participant) || !Number.isSafeInteger(r.exportedAt) || r.exportedAt<=0 || typeof r.budgetEnabled!=='boolean') throw Error('Not a valid Prardhana pilot export.');
  for (const f of fields) if (!Number.isSafeInteger(r[f]) || r[f] < 0) throw Error('Invalid metric: '+f);
  if(r.reviewed>r.transactions)throw Error('Reviewed count exceeds transaction count.');
  return Object.fromEntries(['schema','participant','exportedAt','budgetEnabled',...fields].map(f=>[f,r[f]]));
}
try { records=records.map(validate); } catch { records=[]; }
function save(next){localStorage.setItem('prardhana-pilot',JSON.stringify(next));records=next;render();}
function render(){
  const sum=f=>records.reduce((s,r)=>s+r[f],0), total=sum('transactions'), votes=sum('helpful')+sum('unhelpful');
  $('users').textContent=records.length;$('transactions').textContent=total.toLocaleString();$('reviewed').textContent=total?Math.round(sum('reviewed')/total*100)+'%':'—';$('helpful').textContent=votes?Math.round(sum('helpful')/votes*100)+'%':'—';
  for(const f of ['duplicates','corrections','opens'])$(f).textContent=sum(f).toLocaleString();$('budgets').textContent=records.filter(r=>r.budgetEnabled).length;
  $('people').replaceChildren();if(!records.length){const p=document.createElement('p');p.className='empty';p.textContent='No testers imported yet. Share the Android build with your first pilot group, then import their metrics here.';$('people').append(p);}
  for(const r of records){const div=document.createElement('div');div.className='row';const name=document.createElement('strong');name.textContent=r.participant;const detail=document.createElement('span');detail.textContent=r.transactions+' transactions · '+r.opens+' opens · '+new Date(r.exportedAt).toLocaleDateString();div.append(name,detail);$('people').append(div);}
}
function ingest(raw){try{const parsed=JSON.parse(raw), incoming=(Array.isArray(parsed)?parsed:[parsed]).map(validate);if(!incoming.length)throw Error('Export contains no participants.');const next=[...records];for(const r of incoming){const i=next.findIndex(x=>x.participant===r.participant);if(i<0)next.push(r);else if(r.exportedAt>=next[i].exportedAt)next[i]=r;}save(next);$('payload').value='';$('status').textContent='Imported. Latest snapshots retained; repeated imports are not added twice.';}catch(e){$('status').textContent=e.message;}}
$('importButton').onclick=()=>ingest($('payload').value);
$('fileButton').onclick=()=>$('file').click();$('file').onchange=async e=>{if(e.target.files[0])ingest(await e.target.files[0].text());e.target.value='';};
$('export').onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify(records,null,2)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='prardhana-pilot.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
$('clear').onclick=()=>{if(confirm('Remove all imported pilot metrics from this browser?'))save([]);};render();
