'use strict';
const {createPilot}=require('../server/pilot.cjs');
const app=createPilot();
app.server.listen(Number(process.env.PORT||4173),process.env.HOST||'127.0.0.1',()=>console.log('Prardhana service: http://127.0.0.1:'+(process.env.PORT||4173)+'; credentials: artifacts/pilot-service/secrets.json'));
