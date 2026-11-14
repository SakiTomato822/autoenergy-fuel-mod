'use strict';
// Passive, bounded observation of an existing parser; no request or argument changes.
const m=Process.getModuleByName('android.hardware.automotive.vehicle@2.0-service');
const seen=new Set();let count=0,states={};
const hook=Interceptor.attach(m.base.add(0x32a380),{onEnter(args){
  count++;const state=args[1].toInt32(),len=args[3].toUInt32()&255;states[state]=(states[state]||0)+1;
  if(len>8||len<1||args[2].isNull())return;
  const bytes=Array.from(new Uint8Array(args[2].readByteArray(len)));
  const hex=bytes.map(x=>x.toString(16).padStart(2,'0')).join(' '),key=state+':'+hex;
  if(!seen.has(key)&&seen.size<12){seen.add(key);console.log(JSON.stringify({event:'parse_0x6E4_Data',state,len,hex,ascii:bytes.map(x=>x>=32&&x<127?String.fromCharCode(x):'.').join('')}));}
}});
setTimeout(()=>{hook.detach();console.log(JSON.stringify({event:'DETACHED',calls:count,states,uniqueLogged:seen.size,scope:'parser invocations; does not alone prove new physical CAN RX'}));},12000);
