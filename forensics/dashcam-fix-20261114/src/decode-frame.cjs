const fs=require('fs'),z=require('zlib'),{crc32}=require('./apk.cjs');
const input=fs.readFileSync(process.argv[2]),w=1920,h=896;
function chunk(n,b){const a=Buffer.alloc(8);a.writeUInt32BE(b.length);a.write(n,4);const c=Buffer.alloc(4);c.writeUInt32BE(crc32(Buffer.concat([a.subarray(4),b])));return Buffer.concat([a,b,c]);}
for(const layout of ['UYVY','YUYV']) {
const b=Buffer.alloc(h*(w*3+1));
function pixel(o,y,u,v){y=1.164*(y-16);u-=128;v-=128;for(const [i,a]of[y+1.596*v,y-.392*u-.813*v,y+2.017*u].entries())b[o+i]=Math.max(0,Math.min(255,Math.round(a)));}
for(let y=0;y<h;y++)for(let x=0;x<w;x+=2){let i=(y*w+x)*2,o=y*(w*3+1)+1+x*3;let u,y1,v,y2;if(layout==='UYVY')[u,y1,v,y2]=input.subarray(i,i+4);else[y1,u,y2,v]=input.subarray(i,i+4);pixel(o,y1,u,v);pixel(o+3,y2,u,v);}
const ih=Buffer.alloc(13);ih.writeUInt32BE(w);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=2;
fs.writeFileSync(process.argv[2]+'.'+layout+'.png',Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',ih),chunk('IDAT',z.deflateSync(b)),chunk('IEND',Buffer.alloc(0))]));
}
