'use strict';
// Bounded observation only. Never change arguments or return values.
const mod = Process.getModuleByName('dashcamd');
const mmap = new NativeFunction(Module.getGlobalExportByName('mmap'), 'pointer', ['pointer','ulong','int','int','int','long']);
const munmap = new NativeFunction(Module.getGlobalExportByName('munmap'), 'int', ['pointer','ulong']);
let seen = 0;
const listener = Interceptor.attach(mod.base.add(0x19d14), {
  onEnter(args) {
    const b = args[1];
    const dims = [];
    for (let i=0;i<8;i++) dims.push(b.add(i*4).readU32());
    const h = b.add(32).readPointer();
    console.log('BufferDesc words=' + JSON.stringify(dims) + ' handle=' + h);
    if (!h.isNull()) {
      const nf = h.add(4).readS32(), ni = h.add(8).readS32();
      const ints=[];
      for(let i=0;i<Math.min(nf+ni,20);i++) ints.push(h.add(12+i*4).readS32());
      console.log('native_handle numFds='+nf+' numInts='+ni+' data='+JSON.stringify(ints));
      const length=dims[1]*dims[2]*2;
      if(seen===0 && nf>0 && length>0 && length<=16*1024*1024) {
        const p=mmap(ptr(0),length,1,1,ints[0],0);
        console.log('read-only mmap='+p+' length='+length);
        if(!p.equals(ptr(-1))) {
          const f=new File('/data/local/tmp/dashcam-localfix-test/apa-first-frame.raw','wb');
          f.write(p.readByteArray(length));f.close();munmap(p,length);
          console.log('ONE_FRAME_SAVED');
        }
      }
    }
    seen++;
    if(seen>=3) { listener.detach(); console.log('OBSERVATION_COMPLETE'); }
  }
});
setTimeout(()=>{listener.detach();console.log('OBSERVATION_TIMEOUT_DETACHED');},5000);
