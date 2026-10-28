const fs=require('fs'),b=fs.readFileSync(process.argv[2]),base=0x32800000,re=new RegExp(process.argv[3]||'ap rpmsg dlen|UARTTX DATA HEAD|apupgrade rpmsg|rpmsg_create_AP0|r52_sendto_ap0','i');
const targets=[];for(const m of b.toString('latin1').matchAll(/[ -~]{5,}/g))if(re.test(m[0]))targets.push({addr:base+m.index,text:m[0]});
if(process.env.TARGET_ADDR)targets.push({addr:Number(process.env.TARGET_ADDR),text:'explicit address, not string'});
const hex=n=>'0x'+n.toString(16),starts=[];for(let o=0;o<b.length-4;o+=4){const x=b.readUInt32LE(o);if((x&0xffff4000)>>>0===0xe92d4000)starts.push(base+o);}
function start(a){return starts.findLast(s=>s<=a);}
console.log('TARGETS',targets);
if(process.env.CALL_TARGET)for(let o=0;o<b.length-4;o+=4){const x=b.readUInt32LE(o);if(((x&0x0f000000)>>>0)!==0x0b000000)continue;let v=x&0xffffff;if(v>=0x800000)v-=0x1000000;let a=base+o+8+v*4;if(a===Number(process.env.CALL_TARGET))console.log('BL_CALLER',hex(base+o),'possible_start',hex(start(base+o)||0));}
for(let o=0;o<b.length-4;o+=4){const x=b.readUInt32LE(o),t=targets.find(t=>t.addr===x);if(t)console.log('ABS_POINTER',hex(base+o),t);if(((x&0x0ff00000)>>>0)!==0x03000000)continue;const rd=(x>>>12)&15,lo=((x>>>4)&0xf000)|(x&0xfff);for(let j=1;j<9&&o+j*4<b.length-4;j++){const y=b.readUInt32LE(o+j*4);if(((y&0x0ff00000)>>>0)===0x03400000&&((y>>>12)&15)===rd){const hi=((y>>>4)&0xf000)|(y&0xfff),a=hi*65536+lo,t=targets.find(t=>t.addr===a);if(t)console.log('MOVW_MOVT',hex(base+o),hex(base+o+j*4),'possible_start',hex(start(base+o)||0),t);break;}}}
