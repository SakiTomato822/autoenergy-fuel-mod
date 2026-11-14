const fs=require('fs'),cp=require('child_process'),crypto=require('crypto'),path=require('path');
const {readZip,writeZip,patchManifest}=require('./apk.cjs');
const root=path.resolve(__dirname,'..'),home=path.resolve(root,'../..');
const orig=path.resolve(root,'../flyme-dvr-20261010/raw/dashcam-viewer-20261114');
const bin='/data/data/com.termux/files/usr/bin/',sdk=process.env.DVR_ANDROID_SDK||path.join(process.env.HOME,'.build-tools/android-sdk');
function run(tool,args){cp.execFileSync(bin+tool,args,{cwd:root,stdio:'inherit'});}
function mkdir(p){fs.mkdirSync(path.join(root,p),{recursive:true});}
mkdir('out');mkdir('out/java');mkdir('out/helper-dex');
run('node',[path.join(root,'src/patch-elf.cjs')]);
run('javac',['-source','8','-target','8','-cp',path.join(sdk,'platforms/android-34/android.jar'),'-d','out/java','src/c.java','src/FixInstaller.java']);
run('java',['-cp',path.join(sdk,'build-tools/34.0.0/lib/d8.jar'),'com.android.tools.r8.D8','--lib',path.join(sdk,'platforms/android-34/android.jar'),'--min-api','26','--output','out/helper-dex','out/java/FixInstaller.class']);
run('java',['-cp','tools/*','org.jf.baksmali.Main','disassemble',path.join(orig,'classes.dex'),'-o','out/smali']);
run('java',['-cp','tools/*','org.jf.baksmali.Main','disassemble','out/helper-dex/classes.dex','-o','out/smali-helper']);
fs.cpSync(path.join(root,'out/smali-helper'),path.join(root,'out/smali'),{recursive:true});
const settings=path.join(root,'out/smali/com/dashcam/viewer/SettingsActivity.smali');
let s=fs.readFileSync(settings,'utf8');const re=/\.method private lambda\$runInstaller\$18\(ZLandroid\/widget\/TextView;\)V[\s\S]*?\.end method/;
if(!re.test(s))throw Error('Installer method missing');s=s.replace(re,'.method private lambda$runInstaller$18(ZLandroid/widget/TextView;)V\n    .registers 3\n    invoke-static {p0, p1, p2}, LFixInstaller;->run(Landroid/app/Activity;ZLandroid/widget/TextView;)V\n    return-void\n.end method');fs.writeFileSync(settings,s);
const statusRe=/\.method private lambda\$refreshSysStatus\$12\(Landroid\/widget\/TextView;\)V[\s\S]*?\.end method/;
if(!statusRe.test(s))throw Error('Status method missing');
s=s.replace(statusRe,'.method private lambda$refreshSysStatus$12(Landroid/widget/TextView;)V\n    .registers 2\n    invoke-static {p0, p1}, LFixInstaller;->status(Landroid/app/Activity;Landroid/widget/TextView;)V\n    return-void\n.end method');fs.writeFileSync(settings,s);
const fingerprint=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'out/localfix-cert.der'))).digest('hex');
const d=path.join(root,'out/smali/d.smali');s=fs.readFileSync(d,'utf8');const old='67da5ecbfdcf9dce9e0675ebe7a8a3018ac8013e05fb765320dd98347a895848';if(!s.includes(old))throw Error('Signer pin missing');fs.writeFileSync(d,s.replace(old,fingerprint));
function patchPaths(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())patchPaths(p);else if(p.endsWith('.smali')){let s=fs.readFileSync(p,'utf8');s=s.replace(/^(\s*const-string(?:\/jumbo)?[^\n]+)$/gm,m=>m.replace(/com\.dashcam\.viewer/g,'com.dashcam.fixv11'));fs.writeFileSync(p,s);}}}
patchPaths(path.join(root,'out/smali'));
run('java',['-cp','tools/*','org.jf.smali.Main','assemble','out/smali','-o','out/classes.dex']);
let entries=readZip(fs.readFileSync(path.join(orig,'base.apk'))).filter(e=>!/^META-INF\/(?:MANIFEST\.MF|.*\.(?:SF|RSA|DSA|EC))$/i.test(e.name));
for(const e of entries){if(e.name==='AndroidManifest.xml')e.data=patchManifest(e.data);if(e.name==='classes.dex')e.data=fs.readFileSync(path.join(root,'out/classes.dex'));if(e.name==='assets/dashcamd'||e.name==='assets/libevsproxy_preload.so')e.data=fs.readFileSync(path.join(root,'out',path.basename(e.name)));}
fs.writeFileSync(path.join(root,'out/dashcam-localfix3-unsigned.apk'),writeZip(entries));
run('java',['-jar',path.join(sdk,'build-tools/34.0.0/lib/apksigner.jar'),'sign','--ks','out/localfix-signing.p12','--ks-type','PKCS12','--ks-key-alias','localfix','--ks-pass','file:out/localfix-signing.pass','--min-sdk-version','26','--out','out/dashcam-localfix3.apk','out/dashcam-localfix3-unsigned.apk']);
run('java',['-jar',path.join(sdk,'build-tools/34.0.0/lib/apksigner.jar'),'verify','--verbose','--print-certs','out/dashcam-localfix3.apk']);
run('aapt2',['dump','badging','out/dashcam-localfix3.apk']);
