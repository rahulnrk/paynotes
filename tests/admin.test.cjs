const vm=require('node:vm'),fs=require('node:fs'),assert=require('node:assert/strict');
const elements=new Map();
const element=()=>({textContent:'',value:'',replaceChildren(){},append(){},click(){}});
const context={document:{getElementById(id){if(!elements.has(id))elements.set(id,element());return elements.get(id);},createElement:element},localStorage:{getItem:()=>null,setItem(){}},console};
vm.createContext(context);vm.runInContext(fs.readFileSync('admin/app.js','utf8'),context);
const record={schema:1,participant:'tester-1',exportedAt:1000,budgetEnabled:true,transactions:10,reviewed:5,duplicates:2,corrections:1,opens:3,helpful:2,unhelpful:1};
function ingest(r){context.raw=JSON.stringify(r);vm.runInContext('ingest(raw)',context);}
ingest(record);assert.equal(elements.get('users').textContent,1);assert.equal(elements.get('reviewed').textContent,'50%');
ingest(record);assert.equal(elements.get('transactions').textContent,'10');
ingest({...record,exportedAt:999,transactions:99});assert.equal(elements.get('transactions').textContent,'10');
ingest({...record,exportedAt:2000,transactions:20});assert.equal(elements.get('transactions').textContent,'20');
ingest({...record,participant:'<script>'});assert.equal(elements.get('users').textContent,1);
ingest([{...record,participant:'tester-2'},{...record,participant:'invalid',transactions:-1}]);assert.equal(elements.get('users').textContent,1);
console.log('Admin checks passed: aggregates, duplicate/stale imports, updates, validation, atomic batch imports');
