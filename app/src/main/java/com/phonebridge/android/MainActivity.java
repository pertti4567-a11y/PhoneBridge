package com.phonebridge.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  static final int PICK=10, SAVE=11, UPLOAD=12;
  Uri rootUri; Server server; String token, host="", peerToken="", path="", pending="";
  int port=8765; TextView info, listing; EditText address, key; LinearLayout rows;
  ExecutorService io=Executors.newSingleThreadExecutor();

  public void onCreate(Bundle b){super.onCreate(b); token=random(); ui();}
  Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
  void ui(){
    ScrollView sc=new ScrollView(this); LinearLayout p=new LinearLayout(this);p.setPadding(22,18,22,22);p.setOrientation(1);sc.addView(p);
    TextView title=new TextView(this);title.setText("PhoneBridge — "+BuildConfig.ROLE.toUpperCase(Locale.ROOT));title.setTextSize(25);p.addView(title);
    info=new TextView(this);info.setTextIsSelectable(true);p.addView(info);
    Button pick=btn("Choose shared folder");pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(3|64);startActivityForResult(i,PICK);});p.addView(pick);
    Button start=btn("Start sharing");start.setOnClickListener(v->startServer());p.addView(start);
    address=new EditText(this);address.setHint("Target IP or IP:port");p.addView(address);
    key=new EditText(this);key.setHint("Target pairing token");p.addView(key);
    Button connect=btn("Connect and browse");connect.setOnClickListener(v->connect());p.addView(connect);
    listing=new TextView(this);listing.setText("Not connected");p.addView(listing);
    rows=new LinearLayout(this);rows.setOrientation(1);p.addView(rows);
    boolean target=BuildConfig.ROLE.equals("target");
    if(target){address.setVisibility(View.GONE);key.setVisibility(View.GONE);connect.setVisibility(View.GONE);listing.setVisibility(View.GONE);rows.setVisibility(View.GONE);}
    else {pick.setVisibility(View.GONE);start.setVisibility(View.GONE);info.setVisibility(View.GONE);}
    setContentView(sc);
    if(target)info.setText("Choose a folder, then start sharing.\nPairing token: "+token+"\nUse trusted Wi-Fi only.");
  }
  void startServer(){
    if(rootUri==null){info.setText("Choose a folder first.");return;}
    if(server!=null)server.stop();server=new Server();server.start();
  }
  void connect(){
    try{String a=address.getText().toString().trim();String[] x=a.split(":",2);host=x[0];port=x.length==2?Integer.parseInt(x[1]):8765;peerToken=key.getText().toString().trim();path="";browse();}
    catch(Exception e){listing.setText("Invalid address or port.");}
  }
  void browse(){
    io.execute(()->{try{JSONObject j=req("GET","/list?path="+enc(path),null);JSONArray a=j.getJSONArray("items");runOnUiThread(()->{
      listing.setText(j.optString("name","Shared folder")+" /"+path);rows.removeAllViews();
      if(!path.isEmpty()){Button up=btn("↑ Parent");up.setOnClickListener(v->{int n=path.lastIndexOf('/');path=n<0?"":path.substring(0,n);browse();});rows.addView(up);}
      Button upload=btn("Upload file here");upload.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,UPLOAD);});rows.addView(upload);
      for(int n=0;n<a.length();n++){JSONObject o=a.optJSONObject(n);String name=o.optString("name");boolean dir=o.optBoolean("directory");Button f=btn((dir?"📁 ":"📄 ")+name);f.setAllCaps(false);f.setOnClickListener(v->{if(dir){path=path.isEmpty()?name:path+"/"+name;browse();}else actions(name);});rows.addView(f);}
    });}catch(Exception e){runOnUiThread(()->listing.setText("Connection failed: "+e.getMessage()));}});
  }
  void actions(String name){new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Download","Rename","Delete"},(d,w)->{if(w==0)download(name);else if(w==1){EditText t=new EditText(this);t.setText(name);new AlertDialog.Builder(this).setTitle("New name").setView(t).setPositiveButton("Rename",(x,y)->mutate("rename",name,t.getText().toString())).setNegativeButton("Cancel",null).show();}else new AlertDialog.Builder(this).setMessage("Delete "+name+"?").setPositiveButton("Delete",(x,y)->mutate("delete",name,null)).setNegativeButton("Cancel",null).show();}).show();}
  void mutate(String op,String name,String nn){io.execute(()->{try{JSONObject j=new JSONObject().put("path",path).put("name",name);if(nn!=null)j.put("newName",nn);req("POST","/"+op,j.toString().getBytes(StandardCharsets.UTF_8));runOnUiThread(this::browse);}catch(Exception e){runOnUiThread(()->listing.setText(e.getMessage()));}});}
  void download(String name){pending=path.isEmpty()?name:path+"/"+name;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SAVE);}
  protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;if(r==PICK){rootUri=d.getData();getContentResolver().takePersistableUriPermission(rootUri,d.getFlags()&3);info.setText("Sharing folder: "+DocumentFile.fromTreeUri(this,rootUri).getName()+"\nToken: "+token);}if(r==SAVE){Uri u=d.getData();io.execute(()->{try{byte[] b=reqBytes("GET","/download?path="+enc(pending),null);try(OutputStream o=getContentResolver().openOutputStream(u)){o.write(b);}runOnUiThread(()->listing.setText("Downloaded "+pending));}catch(Exception e){runOnUiThread(()->listing.setText(e.getMessage()));}});}if(r==UPLOAD){Uri u=d.getData();io.execute(()->{try{String name="upload.bin";try(android.database.Cursor q=getContentResolver().query(u,null,null,null,null)){if(q!=null&&q.moveToFirst()){int ix=q.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0)name=q.getString(ix);}}ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(u)){in.transferTo(b);}JSONObject m=new JSONObject().put("path",path).put("name",name);req("POST","/upload?meta="+enc(m.toString()),b.toByteArray());runOnUiThread(this::browse);}catch(Exception e){runOnUiThread(()->listing.setText(e.getMessage()));}});}}
  JSONObject req(String m,String p,byte[] b)throws Exception{return new JSONObject(new String(reqBytes(m,p,b),StandardCharsets.UTF_8));}
  byte[] reqBytes(String m,String p,byte[] b)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("http://"+host+":"+port+p).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(30000);c.setRequestMethod(m);c.setRequestProperty("X-PhoneBridge-Token",peerToken);if(b!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(b.length);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();in.transferTo(out);in.close();c.disconnect();if(code>=400)throw new IOException("HTTP "+code+": "+out.toString("UTF-8"));return out.toByteArray();}
  String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
  String random(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x));return s.toString();}
  class Server{
    volatile boolean run=true;ServerSocket sock;
    void start(){new Thread(()->{try{sock=new ServerSocket(8765);runOnUiThread(()->info.setText("Sharing at "+ip()+":8765\nToken: "+token+"\nKeep app open."));while(run){Socket s=sock.accept();new Thread(()->handle(s)).start();}}catch(Exception e){if(run)runOnUiThread(()->info.setText(e.toString()));}}).start();}
    void stop(){run=false;try{sock.close();}catch(Exception ignored){}}
    void handle(Socket s){try(Socket c=s){c.setSoTimeout(30000);BufferedInputStream in=new BufferedInputStream(c.getInputStream());String line=read(in);if(line==null)return;String[] first=line.split(" ");Map<String,String> h=new HashMap<>();while((line=read(in))!=null&&!line.isEmpty()){int k=line.indexOf(':');if(k>0)h.put(line.substring(0,k).toLowerCase(Locale.ROOT),line.substring(k+1).trim());}if(!token.equals(h.get("x-phonebridge-token"))){send(c,401,"{\"error\":\"unauthorized\"}");return;}String target=first[1],query=target.contains("?")?target.substring(target.indexOf('?')+1):"";Map<String,String> q=params(query);DocumentFile root=DocumentFile.fromTreeUri(MainActivity.this,rootUri);String p=q.getOrDefault("path","");if(target.startsWith("/list")){DocumentFile dir=resolve(root,p);JSONArray a=new JSONArray();for(DocumentFile f:dir.listFiles())a.put(new JSONObject().put("name",f.getName()).put("directory",f.isDirectory()));send(c,200,new JSONObject().put("name",dir.getName()).put("items",a).toString());}else if(target.startsWith("/download")){DocumentFile f=resolve(root,p);try(InputStream src=getContentResolver().openInputStream(f.getUri())){OutputStream o=c.getOutputStream();o.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n".getBytes());src.transferTo(o);}}else{int len=Integer.parseInt(h.getOrDefault("content-length","0"));if(len<0||len>100*1024*1024)throw new IOException("Upload limit 100 MB");byte[] b=in.readNBytes(len);if(target.startsWith("/upload")){JSONObject meta=new JSONObject(q.get("meta"));DocumentFile dir=resolve(root,meta.optString("path",""));String name=safe(meta.optString("name","upload.bin"));DocumentFile f=dir.createFile("application/octet-stream",name);try(OutputStream o=getContentResolver().openOutputStream(f.getUri())){o.write(b);}send(c,200,"{\"ok\":true}");}else{JSONObject j=new JSONObject(new String(b,StandardCharsets.UTF_8));DocumentFile dir=resolve(root,j.optString("path",""));DocumentFile f=dir.findFile(safe(j.optString("name","")));if(target.startsWith("/delete"))f.delete();else f.renameTo(safe(j.optString("newName","")));send(c,200,"{\"ok\":true}");}}}catch(Exception e){try{send(s,500,new JSONObject().put("error",e.toString()).toString());}catch(Exception ignored){}}}
    DocumentFile resolve(DocumentFile r,String p){DocumentFile f=r;if(p!=null&&!p.isEmpty())for(String part:p.split("/")){if(part.equals("..")||part.isEmpty())return null;f=f.findFile(part);if(f==null)return null;}return f;}
    String safe(String s){return s==null||s.trim().isEmpty()||s.contains("/")||s.contains("\\")||s.equals(".")||s.equals("..")?"":s.trim();}
    String read(InputStream i)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();int x,prev=-1;while((x=i.read())!=-1){if(prev==13&&x==10)break;if(prev!=-1)b.write(prev);prev=x;}return b.toString("UTF-8");}
    Map<String,String> params(String q)throws Exception{Map<String,String> m=new HashMap<>();for(String x:q.split("&")){String[] a=x.split("=",2);if(a.length==2)m.put(URLDecoder.decode(a[0],"UTF-8"),URLDecoder.decode(a[1],"UTF-8"));}return m;}
    void send(Socket s,int code,String body)throws Exception{byte[] b=body.getBytes(StandardCharsets.UTF_8);OutputStream o=s.getOutputStream();o.write(("HTTP/1.1 "+code+" OK\r\nContent-Type: application/json\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n").getBytes());o.write(b);}
    String ip(){try{Enumeration<NetworkInterface> n=NetworkInterface.getNetworkInterfaces();while(n.hasMoreElements()){Enumeration<InetAddress>a=n.nextElement().getInetAddresses();while(a.hasMoreElements()){InetAddress x=a.nextElement();if(x instanceof Inet4Address&&!x.isLoopbackAddress())return x.getHostAddress();}}}catch(Exception ignored){}return "Wi-Fi IP unavailable";}
  }
}
