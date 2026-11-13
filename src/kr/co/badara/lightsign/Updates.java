package kr.co.badara.lightsign;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.*;

final class Updates {
    private static final String RELEASES="https://github.com/kojungho/light-sign-android/releases/";
    private static final String META=RELEASES+"latest/download/update.json";
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Runnable automatic=()->check(false);
    private volatile boolean closed;
    private boolean resumed,busy,awaitingPermission,promptOpen;
    private long lastCheck=-60000;
    private int offered;
    private Spec ready;
    private AlertDialog progress;

    Updates(Activity activity){this.activity=activity;}
    void resume(){
        resumed=true;
        if(awaitingPermission){
            awaitingPermission=false;
            if(ready!=null&&activity.getPackageManager().canRequestPackageInstalls())install();
            else {ready=null;notice("설치를 계속하려면 라이트 안내판의 ‘알 수 없는 앱 설치’를 허용해주세요. 다시 확인하면 받은 파일을 사용할 수 있습니다.");}
        }else if(ready!=null&&!promptOpen)install();
        else handler.postDelayed(automatic,850);
    }
    void pause(){resumed=false;handler.removeCallbacks(automatic);}
    void close(){closed=true;handler.removeCallbacks(automatic);io.shutdownNow();if(progress!=null)progress.dismiss();}
    void check(boolean manual){
        if(closed||!resumed)return;
        if(busy||promptOpen||awaitingPermission){if(manual&&!promptOpen)notice("업데이트를 처리 중입니다.");return;}
        if(ready!=null){install();return;}
        long now=SystemClock.elapsedRealtime();
        if(!manual&&now-lastCheck<60000)return;
        lastCheck=now;busy=true;
        io.execute(()->{
            try{
                Spec spec=readSpec();
                int installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
                ui(()->{
                    busy=false;
                    if(spec.code<=installed){if(manual)notice("최신 버전입니다.");}
                    else if(resumed&&(manual||offered!=spec.code))offer(spec);
                    else if(!resumed)lastCheck=-60000;
                });
            }catch(Exception e){ui(()->{busy=false;if(manual)notice("업데이트를 확인하지 못했습니다. 인터넷 연결을 확인하고 다시 시도해주세요.");});}
        });
    }
    private void offer(Spec spec){
        offered=spec.code;promptOpen=true;
        AlertDialog d=new AlertDialog.Builder(activity).setTitle("새 버전 "+spec.version)
            .setMessage("라이트 안내판을 업데이트할 수 있습니다. 기존 설정은 유지됩니다.")
            .setNegativeButton("나중에",null)
            .setPositiveButton("다운로드 및 설치",(dialog,which)->download(spec)).create();
        d.setOnDismissListener(dialog->promptOpen=false);d.show();
    }
    private void download(Spec spec){
        busy=true;
        progress=new AlertDialog.Builder(activity).setMessage("업데이트 파일을 다운로드하고 확인하는 중…").setCancelable(false).show();
        io.execute(()->{
            File file=UpdateFile.file(activity),partial=new File(activity.getCacheDir(),"light-sign-partial.apk");
            try{
                if(!file.isFile()||file.length()!=spec.size||!digest(file).equals(spec.hash)){
                    HttpURLConnection conn=open(spec.url);
                    try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(partial)){
                        copy(in,out,spec.size);
                    }finally{conn.disconnect();}
                    if(partial.length()!=spec.size||!digest(partial).equals(spec.hash))throw new IOException("파일 검증 실패");
                    verify(partial,spec);
                    if(!partial.renameTo(file))throw new IOException("파일 저장 실패");
                }else verify(file,spec);
                ui(()->{done();ready=spec;if(resumed)install();});
            }catch(Exception e){ui(()->{done();notice("업데이트 파일을 준비하지 못했습니다. 다시 시도해주세요.");});}
            finally{partial.delete();}
        });
    }
    private void verify(File file,Spec spec)throws Exception{
        PackageManager pm=activity.getPackageManager();
        int flags=PackageManager.GET_SIGNATURES;
        PackageInfo own=pm.getPackageInfo(activity.getPackageName(),flags),next=pm.getPackageArchiveInfo(file.toString(),flags);
        if(next==null||!own.packageName.equals(next.packageName)||next.versionCode!=spec.code||next.versionCode<=own.versionCode||
            !spec.version.equals(next.versionName)||own.signatures==null||own.signatures.length==0||!Arrays.equals(own.signatures,next.signatures))
            throw new IOException("앱 서명 또는 버전 불일치");
    }
    private void install(){
        if(!resumed||ready==null||promptOpen)return;
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            promptOpen=true;
            AlertDialog d=new AlertDialog.Builder(activity).setTitle("업데이트 설치 허용")
                .setMessage("설정에서 라이트 안내판의 ‘알 수 없는 앱 설치’를 허용한 뒤 돌아오세요.")
                .setNegativeButton("나중에",(dialog,which)->ready=null)
                .setPositiveButton("설정 열기",(dialog,which)->{
                    try{awaitingPermission=true;activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
                    catch(ActivityNotFoundException|SecurityException e){awaitingPermission=false;ready=null;notice("휴대폰 설정에서 앱 설치 권한을 확인해주세요.");}
                }).create();
            d.setOnCancelListener(dialog->ready=null);d.setOnDismissListener(dialog->promptOpen=false);d.show();return;
        }
        try{
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateFile.URI,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("업데이트 APK",UpdateFile.URI));
            ready=null;activity.startActivity(intent);
        }catch(ActivityNotFoundException|SecurityException e){notice("안드로이드 설치 화면을 열지 못했습니다.");}
    }
    private void done(){busy=false;if(progress!=null){progress.dismiss();progress=null;}}
    private void notice(String s){if(resumed&&!closed)new AlertDialog.Builder(activity).setMessage(s).setPositiveButton("확인",null).show();}
    private void ui(Runnable r){handler.post(()->{if(!closed&&!activity.isFinishing())r.run();});}
    private Spec readSpec()throws Exception{
        HttpURLConnection conn=open(META);
        try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            copy(in,out,16384);return new Spec(new JSONObject(out.toString("UTF-8")));
        }finally{conn.disconnect();}
    }
    private void copy(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] b=new byte[8192];int n;long count=0;
        while((n=in.read(b))!=-1){
            count+=n;if(closed||count>limit)throw new IOException("다운로드 제한");
            out.write(b,0,n);
        }
    }
    private String digest(File file)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[8192];int n;
        try(InputStream in=new FileInputStream(file)){while((n=in.read(buf))!=-1)d.update(buf,0,n);}
        StringBuilder hash=new StringBuilder();for(byte b:d.digest()){int v=b&255;hash.append(Character.forDigit(v>>>4,16)).append(Character.forDigit(v&15,16));}
        return hash.toString();
    }
    private static HttpURLConnection open(String address)throws IOException{
        URL url=new URL(address+"?check="+System.currentTimeMillis());
        for(int i=0;i<8;i++){
            String host=url.getHost();
            if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||!(host.equals("github.com")||host.endsWith(".githubusercontent.com")))throw new IOException("잘못된 주소");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);
            c.setRequestProperty("Cache-Control","no-cache");
            int status;
            try{status=c.getResponseCode();}catch(IOException e){c.disconnect();throw e;}
            if(status==200)return c;
            String next=c.getHeaderField("Location");c.disconnect();
            if(status<300||status>=400||next==null)throw new IOException("HTTP "+status+" · "+url.getHost()+url.getPath());
            url=new URL(url,next);
        }
        throw new IOException("리디렉션 제한");
    }
    static final class Spec {
        final String version,url,hash;
        final int code;
        final long size;
        Spec(JSONObject json)throws Exception{
            version=json.getString("versionName");code=json.getInt("versionCode");
            hash=json.getString("sha256");size=json.getLong("size");
            if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||code<1||!hash.matches("[a-f0-9]{64}")||size<1||size>32*1024*1024)throw new IOException("잘못된 업데이트 정보");
            url=RELEASES+"download/v"+version+"/light-sign.apk";
        }
    }
}
