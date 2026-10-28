// Offline ELF/disassembly annotation. No process attachment or device IO.
const fs=require('fs'),b=fs.readFileSync(process.argv[2]),lines=fs.readFileSync(process.argv[3],'utf8').split('\n');
const raw=process.env.RAW_BASE!==undefined;
const q=raw?0:Number(b.readBigUInt64LE(40)),n=raw?0:b.readUInt16LE(60),e=raw?0:b.readUInt16LE(58),sections=[];
if(raw)sections.push({a:Number(process.env.RAW_BASE),o:0,n:b.length,t:1});
for(let i=0;i<n;i++){let o=q+i*e;sections.push({a:Number(b.readBigUInt64LE(o+16)),o:Number(b.readBigUInt64LE(o+24)),n:Number(b.readBigUInt64LE(o+32)),t:b.readUInt32LE(o+4)});}
function off(a){let s=sections.find(s=>s.t===1&&a>=s.a&&a<s.a+s.n);return s?s.o+a-s.a:null;}
function string(a){let o=off(a);if(o===null)return '';let end=b.indexOf(0,o);if(end<o||end-o>250)return '';let s=b.toString('utf8',o,end);return /^[\x20-\x7e\r\n\t]{3,}$/.test(s)?JSON.stringify(s):'';}
const regs={};
const symbols=new Map();if(process.env.KERNEL_SYMBOLS)for(const l of fs.readFileSync(process.env.KERNEL_SYMBOLS,'utf8').split('\n')){let m=l.match(/^([a-f0-9]+) \S (\S+)/);if(m){let a=BigInt('0x'+m[1])-0xffff800010000000n;if(a>=0n&&a<0x4000000n)symbols.set(Number(a),m[2]);}}
for(let l of lines){let m=l.match(/^\s*([0-9a-f]+):\s+[0-9a-f]{8}\s+adrp\s+x(\d+),\s*(?:0x)?([0-9a-f]+)/);if(m)regs[m[2]]=parseInt(m[3],16);
m=l.match(/^\s*([0-9a-f]+):\s+[0-9a-f]{8}\s+add\s+x(\d+),\s*x(\d+),\s*#0x([0-9a-f]+)/);if(m&&regs[m[3]]!==undefined){let a=regs[m[3]]+parseInt(m[4],16),s=string(a);if(s)l+=' // STATIC_STRING@'+a.toString(16)+' '+s;}
let call=l.match(/\bbl\s+0x([0-9a-f]+)/);if(call){let name=symbols.get(parseInt(call[1],16));if(name)l+=' // KERNEL_SYMBOL '+name;}console.log(l);}
if(process.argv[4]==='render-jumptable'){let o=off(0x10a8a);for(let i=0;i<=94;i++)console.log('TRANSACTION',i+20,'entry','0x'+(0x1a270+4*b.readUInt16LE(o+2*i)).toString(16));}
