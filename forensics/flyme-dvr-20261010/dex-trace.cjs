const fs=require('fs'),b=fs.readFileSync(process.argv[2]),u32=o=>b.readUInt32LE(o),u16=o=>b.readUInt16LE(o);
if(b.toString('ascii',0,3)!=='dex')throw Error('DEX required');
const leb=p=>{let n=0,s=0,x;do{x=b[p.o++];n+=(x&127)*2**s;s+=7;}while(x&128);return n;};
const st=Array.from({length:u32(56)},(_,i)=>{const p={o:u32(u32(60)+4*i)};leb(p);return b.toString('utf8',p.o,b.indexOf(0,p.o));});
const ty=Array.from({length:u32(64)},(_,i)=>st[u32(u32(68)+4*i)]);
const fld=Array.from({length:u32(80)},(_,i)=>{const p=u32(84)+8*i;return ty[u16(p)]+'->'+st[u32(p+4)]+':'+ty[u16(p+2)];});
const met=Array.from({length:u32(88)},(_,i)=>{const p=u32(92)+8*i;return ty[u16(p)]+'->'+st[u32(p+4)];});
const sizes=new Uint8Array(256).fill(1);for(const x of [2,5,8,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23,0x29,0xfe,0xff])sizes[x]=2;for(const x of [3,6,9,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c,0xfc,0xfd])sizes[x]=3;sizes[0x18]=5;sizes[0xfa]=sizes[0xfb]=4;
for(const [l,h,v]of [[0x2d,0x3d,2],[0x44,0x6d,2],[0x6e,0x72,3],[0x74,0x78,3],[0x90,0xaf,2],[0xd0,0xe2,2]])for(let i=l;i<=h;i++)sizes[i]=v;
const re=new RegExp(process.argv[3]||'DVR|CarConfig','i'),mode=process.argv[4]||'refs';
for(let ci=0;ci<u32(96);ci++){const p=u32(100)+ci*32,cls=ty[u32(p)],off=u32(p+24);if(!off)continue;const c={o:off},cnt=Array.from({length:4},()=>leb(c));for(const count of cnt.slice(0,2)){let idx=0;for(let i=0;i<count;i++){idx+=leb(c);leb(c);}}
for(const count of cnt.slice(2)){let idx=0;for(let i=0;i<count;i++){idx+=leb(c);leb(c);const code=leb(c);if(!code)continue;const n=u32(code+12),base=code+16,lines=[];let matched=mode==='class'&&re.test(cls);
for(let j=0;j<n;){const w=u16(base+2*j),op=w&255,nn=u16(base+2*j+2);let len=sizes[op],desc='';
if(op===0&&w===0x100)len=4+u16(base+2*j+2)*2;else if(op===0&&w===0x200)len=2+u16(base+2*j+2)*4;else if(op===0&&w===0x300)len=4+Math.ceil(u16(base+2*j+2)*u32(base+2*j+4)/2);
if(op>=0x52&&op<=0x6d){desc=fld[nn]||'';if(re.test(desc))matched=true;}
if(op>=0x6e&&op<=0x72||op>=0x74&&op<=0x78){desc=met[nn]||'';if(re.test(desc))matched=true;}
if(op===0x1a||op===0x1b){desc=JSON.stringify(st[op===0x1a?nn:u32(base+2*j+2)]);if(re.test(desc))matched=true;}
if(op===0x14)desc='const '+b.readInt32LE(base+2*j+2)+' / 0x'+u32(base+2*j+2).toString(16);
if(op===0x13||op===0x16)desc='const/16 '+b.readInt16LE(base+2*j+2);
if(op===0x12)desc='const/4 v'+((w>>>8)&15)+' '+((w>>>12)>=8?(w>>>12)-16:w>>>12);
if(op>=0x32&&op<=0x3d)desc='if target '+(j+b.readInt16LE(base+2*j+2));
if(op===0x2b||op===0x2c)desc='switch payload '+(j+b.readInt32LE(base+2*j+2));
lines.push(j.toString().padStart(4)+' '+Array.from({length:Math.min(len,8)},(_,k)=>u16(base+2*(j+k)).toString(16).padStart(4,'0')).join(' ')+' '+desc);j+=len;}
if(matched){console.log('\nMETHOD '+met[idx]+' code='+code+' regs='+u16(code)+' ins='+u16(code+2));console.log(lines.join('\n'));}
}}}
