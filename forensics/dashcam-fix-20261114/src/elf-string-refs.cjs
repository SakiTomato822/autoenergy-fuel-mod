// Offline bounded AArch64 ADR / ADRP+ADD locator. Verify hits in disassembly.
const fs=require('fs'),b=fs.readFileSync(process.argv[2]),needle=Buffer.from(process.argv[3]);
const q=o=>Number(b.readBigUInt64LE(o)),n=b.readUInt16LE(60),sh=q(40),es=b.readUInt16LE(58),ss=[];
for(let i=0;i<n;i++){const p=sh+i*es;ss.push({type:b.readUInt32LE(p+4),flags:q(p+8),va:q(p+16),off:q(p+24),size:q(p+32)});}
const targets=[];for(let p=b.indexOf(needle);p>=0;p=b.indexOf(needle,p+1)){const s=ss.find(s=>s.type===1&&p>=s.off&&p<s.off+s.size);if(s)targets.push(s.va+p-s.off);}
const sign=(v,n)=>v>=2**(n-1)?v-2**n:v;
console.log('TARGETS',targets.map(x=>'0x'+x.toString(16)));
for(const s of ss.filter(s=>s.type===1&&(s.flags&4)))for(let o=s.off;o<s.off+s.size;o+=4){const ins=b.readUInt32LE(o),pc=s.va+o-s.off,mask=(ins&0x9f000000)>>>0,imm=sign(((ins>>>5)&0x7ffff)*4+((ins>>>29)&3),21);
if(mask===0x10000000&&targets.includes(pc+imm))console.log('ADR','0x'+pc.toString(16));
if(mask!==0x90000000)continue;const rd=ins&31,page=Math.floor(pc/4096)*4096+imm*4096;
for(let j=1;j<=8&&o+j*4<s.off+s.size;j++){const x=b.readUInt32LE(o+j*4);if(((x&0xffc00000)>>>0)===0x91000000&&((x>>>5)&31)===rd){const target=page+((x>>>10)&4095);if(targets.includes(target))console.log('ADRP_ADD','0x'+pc.toString(16),'0x'+(pc+j*4).toString(16),'->0x'+target.toString(16));}}
}
