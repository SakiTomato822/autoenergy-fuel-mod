// Offline locator for known MCU Thumb literal references, not a full disassembler.
const fs=require('fs'),b=fs.readFileSync(process.argv[2]),lines=fs.readFileSync(process.argv[3],'utf8').split('\n'),needle=Buffer.from(process.argv[4]),base=0x420000;
for(let p=b.indexOf(needle);p>=0;p=b.indexOf(needle,p+1)){
 const end=b.indexOf(0,p),addr=base+p,ptrs=[];for(let q=0;q+4<=b.length;q+=2)if(b.readUInt32LE(q)===addr)ptrs.push(base+q);
 console.log(JSON.stringify({stringAddress:'0x'+addr.toString(16),text:b.toString('utf8',p,end),literalPointers:ptrs.map(x=>'0x'+x.toString(16))}));
 for(let i=0;i<lines.length;i++)if(ptrs.some(a=>lines[i].includes('@ (0x'+a.toString(16)+')')))console.log(lines.slice(Math.max(0,i-14),i+8).join('\n'));
}
