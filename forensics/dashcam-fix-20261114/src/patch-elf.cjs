const fs = require('fs'), cp = require('child_process'), crypto = require('crypto');
const path = require('path');
const task = path.resolve(__dirname, '..');
const original = path.resolve(task, '../flyme-dvr-20261010/raw/dashcam-viewer-20261114');
const bin = '/data/data/com.termux/files/usr/bin/';
function hash(b) { return crypto.createHash('sha256').update(b).digest('hex'); }
function patch(name, expected, stubName, defs, hooks, extraBss=0) {
  let b=fs.readFileSync(path.join(original,name));
  if(hash(b)!==expected) throw Error('Unexpected input '+name);
  const u64=o=>Number(b.readBigUInt64LE(o));
  const phoff=u64(32), phsize=b.readUInt16LE(54), phnum=b.readUInt16LE(56);
  const shoff=u64(40), shsize=b.readUInt16LE(58), shnum=b.readUInt16LE(60);
  const ph=Array.from({length:phnum},(_,i)=>{let o=phoff+i*phsize;return {o,type:b.readUInt32LE(o),flags:b.readUInt32LE(o+4),off:u64(o+8),addr:u64(o+16),size:u64(o+32),mem:u64(o+40),align:u64(o+48)};});
  const rx=ph.find(p=>p.type===1 && p.flags===5);
  const insert=rx.off+rx.size, cave=rx.addr+rx.size, amount=0x4000;
  const next=ph.filter(p=>p.type===1&&p.addr>rx.addr).sort((a,b)=>a.addr-b.addr)[0];
  if(cave+amount>next.addr) throw Error('No virtual address space for patch');
  const stem=path.join(task,'out',stubName);
  cp.execFileSync(bin+'as',['-o',stem+'.o',path.join(task,'src',stubName+'.S')]);
  cp.execFileSync(bin+'ld.bfd',[`-Ttext=0x${cave.toString(16)}`,'-e',stubName==='proxy-stubs'?'patch_open':'patch_retry',...Object.entries(defs).map(([k,v])=>`--defsym=${k}=0x${v.toString(16)}`),'-o',stem+'.elf',stem+'.o']);
  cp.execFileSync(bin+'gobjcopy',['-O','binary','-j','.text',stem+'.elf',stem+'.bin']);
  const st=fs.readFileSync(stem+'.bin'); if(st.length>amount)throw Error('Stub too large');
  const syms=cp.execFileSync(bin+'gnm',['--defined-only',stem+'.elf'],{encoding:'utf8'});
  fs.writeFileSync(stem+'.symbols.txt',syms);
  const symbols=Object.fromEntries([...syms.matchAll(/^([0-9a-f]+) \w (\S+)$/gm)].map(m=>[m[2],parseInt(m[1],16)]));
  const out=Buffer.concat([b.subarray(0,insert),Buffer.alloc(amount),b.subarray(insert)]);
  const w64=(o,v)=>out.writeBigUInt64LE(BigInt(v),o);
  st.copy(out,insert);
  w64(40,shoff+amount);
  for(const p of ph){
    if(p.off>=insert)w64(p.o+8,p.off+amount);
    if(p===rx){w64(p.o+32,p.size+amount);w64(p.o+40,p.mem+amount);}
  }
  const last=ph.filter(p=>p.type===1).sort((a,b)=>a.addr-b.addr).at(-1);
  if(extraBss)w64(last.o+40,last.mem+extraBss);
  const shstrIndex=b.readUInt16LE(62),strHdr=shoff+shstrIndex*shsize,strBase=u64(strHdr+24);
  for(let i=0;i<shnum;i++){
    const old=shoff+i*shsize, now=old+amount,off=u64(old+24),addr=u64(old+16),sz=u64(old+32);
    let np=strBase+b.readUInt32LE(old);const nm=b.toString('utf8',np,b.indexOf(0,np));
    if(off>=insert)w64(now+24,off+amount);
    if(nm==='.plt'){if(addr+sz!==cave)throw Error('Unexpected plt boundary');w64(now+32,sz+amount);}
    if(extraBss && nm==='.bss'){if(addr+sz!==last.addr+last.mem)throw Error('Unexpected bss boundary');w64(now+32,sz+extraBss);}
  }
  const changes=[];
  for(const [addr,oldword,label]of hooks){
    const p=ph.find(p=>p.type===1&&addr>=p.addr&&addr<p.addr+p.size);
    const off=p.off+addr-p.addr;if(b.readUInt32LE(off)!==oldword)throw Error('Hook input mismatch '+addr.toString(16));
    const target=symbols[label],delta=target-addr;
    if(delta%4||Math.abs(delta)>=0x8000000)throw Error('Bad branch target');
    out.writeUInt32LE((0x14000000|((delta/4)&0x3ffffff))>>>0,off);
    changes.push({address:'0x'+addr.toString(16),oldword:'0x'+oldword.toString(16),target:'0x'+target.toString(16),label});
  }
  const output=path.join(task,'out',name);fs.writeFileSync(output,out);
  if(name==='dashcamd') {
    const before=Buffer.from('com.dashcam.viewer'),after=Buffer.from('com.dashcam.fixv11');
    let pos=out.indexOf(before);if(pos<0||before.length!==after.length)throw Error('Package path missing');
    while(pos>=0){after.copy(out,pos);pos=out.indexOf(before,pos+after.length);}
    // Preserve original default avm/video6cif; apa is a composite interface.
    fs.writeFileSync(output,out);
  }
  const meta={name,originalSha256:hash(b),patchedSha256:hash(out),insert,cave,amount,extraBss,changes};
  fs.writeFileSync(output+'.patch.json',JSON.stringify(meta,null,2)+'\n');
  console.log(meta);
}
patch('libevsproxy_preload.so','86b81f73c692ebc0d3dee2ab48ca625093f2d83d8d4c05fab3d949e6680be7b0','proxy-stubs',
  {hidl_c_str:0x1af80,strcmp_plt:0x1b2b0,original_open_rest:0x168e4,original_close_rest:0x16eb0,proxy_camera_vptr:0x1f7d0},
  [[0x168e0,0xd10383ff,'patch_open'],[0x16eac,0xd10143ff,'patch_close']]);
patch('dashcamd','64696d87575eec127b717cd98a03a746b1ad83a2d82acad361126470d2911abf','daemon-stub',
  {retry_counter:0x25b20,sleep_plt:0x1c290,original_start_call:0xc060,original_cleanup:0xc1b0,inc_strong:0x1c2c0,return_status_destructor:0x1c2d0},
  [[0xc1ac,0x52800093,'patch_retry']],16);
