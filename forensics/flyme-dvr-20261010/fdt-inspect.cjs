// Read-only flattened device tree parser; validates bounds, resolves phandles.
const fs=require('fs'),b=fs.readFileSync(process.argv[2]),u=o=>b.readUInt32BE(o),align=o=>(o+3)&~3;
if(u(0)!==0xd00dfeed||u(4)>b.length)throw Error('invalid FDT');
const so=u(8),st=u(12),end=so+u(36),nodes=[],stack=[];let p=so;
while(p<end){let t=u(p);p+=4;if(t===1){let z=b.indexOf(0,p),name=b.toString('utf8',p,z);p=align(z+1);let n={path:(stack.at(-1)?.path||'')+'/'+name,props:{}};n.path=n.path.replace(/^\/\//,'/');nodes.push(n);stack.push(n);}else if(t===2)stack.pop();else if(t===3){let len=u(p),no=u(p+4);p+=8;if(p+len>end)throw Error('property bounds');let z=b.indexOf(0,st+no),name=b.toString('utf8',st+no,z);stack.at(-1).props[name]=b.subarray(p,p+len);p=align(p+len);}else if(t===4)continue;else if(t===9)break;else throw Error('bad token '+t);}
const ph=new Map();for(const n of nodes)for(const k of ['phandle','linux,phandle'])if(n.props[k]?.length===4)ph.set(n.props[k].readUInt32BE(),n.path);
function decode(k,v){if(!v.length)return true;if(v.at(-1)===0&&[...v].some(x=>x>=32&&x<=126)&&[...v].every(x=>x===0||x>=32&&x<=126))return v.toString().split('\0').filter(Boolean);let a=v.length%4===0?Array.from({length:v.length/4},(_,i)=>v.readUInt32BE(i*4)):null;if(k==='remote-endpoint'&&a)return {cells:a,paths:a.map(x=>ph.get(x)||'unknown')};return a?a.map(x=>'0x'+x.toString(16)):v.toString('hex');}
const re=new RegExp(process.argv[3]||'camera|cif|csi|vicam|lvds|dvr|ub9|max967|video','i');
for(const n of nodes)if(re.test(n.path)||Object.entries(n.props).some(([k,v])=>/compatible|label|status/.test(k)&&re.test(v.toString())))console.log(JSON.stringify({path:n.path,props:Object.fromEntries(Object.entries(n.props).map(([k,v])=>[k,decode(k,v)]))}));
console.error(JSON.stringify({bytes:b.length,declared:u(4),nodes:nodes.length,phandles:ph.size}));
