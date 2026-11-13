package com.onlangage.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.*;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.view.View;
import java.io.*;

public class MainActivity extends Activity {
 private WebView web;
 private static final int SAVE_PDF = 301;
 private File pendingPdf;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  web = new WebView(this); setContentView(web);
  WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
  web.setWebChromeClient(new WebChromeClient());
  web.addJavascriptInterface(new Object() {
   @JavascriptInterface public void saveCertificate(String html) { runOnUiThread(() -> generatePdf(html,false)); }
   @JavascriptInterface public void shareCertificate(String html) { runOnUiThread(() -> generatePdf(html,true)); }
  }, "ONLangageAndroid");
  web.setWebViewClient(new WebViewClient() {
   @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
    if(url.startsWith("file:///android_asset/")) return false;
    if(url.startsWith("https://") || url.startsWith("http://")) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
    return false;
   }
  });
  web.loadUrl("file:///android_asset/index.html");
 }
 private void message(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
 private void generatePdf(String html, boolean share) {
  if(html==null || html.trim().isEmpty()){message("Certificat indisponible");return;}
  final WebView printer = new WebView(this);
  printer.getSettings().setDefaultTextEncodingName("UTF-8");
  printer.getSettings().setJavaScriptEnabled(false);
  printer.setWebViewClient(new WebViewClient(){
   boolean started=false;
   @Override public void onPageFinished(WebView v,String url){
    if(started)return; started=true;
    printer.postDelayed(()->writePdf(printer,share),600);
   }
  });
  printer.loadDataWithBaseURL("https://on-langage.local/",html,"text/html","UTF-8",null);
 }
 private void writePdf(WebView printer, boolean share) {
  PdfDocument document = new PdfDocument();
  try {
   // Render the certificate into an A4 PDF without inaccessible Android print callbacks.
   int width = 794, height = 1123;
   printer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
     View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
   int contentHeight = Math.max(height, printer.getMeasuredHeight());
   printer.layout(0,0,width,contentHeight);
   int pageCount = Math.max(1,(int)Math.ceil((double)contentHeight/height));
   for(int p=0;p<pageCount;p++) {
    PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(width,height,p+1).create());
    Canvas canvas=page.getCanvas();
    canvas.drawColor(android.graphics.Color.WHITE);
    canvas.save(); canvas.translate(0,-p*height);
    printer.draw(canvas);
    canvas.restore(); document.finishPage(page);
   }
   File folder=new File(getCacheDir(),"certificates");
   if(!folder.exists() && !folder.mkdirs()) throw new IOException("Dosye PDF pa disponib");
   File pdf=new File(folder,"ON-LANGAGE-Certificat-"+System.currentTimeMillis()+".pdf");
   try(OutputStream out=new FileOutputStream(pdf)){document.writeTo(out);}
   pendingPdf=pdf;
   if(share) sharePdf(pdf); else chooseSave();
  }catch(Exception e){message("Erè PDF: "+e.getMessage());}
  finally{document.close();printer.destroy();}
 }
 private void chooseSave(){
  try {
   Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
   i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");
   i.putExtra(Intent.EXTRA_TITLE,"ON-LANGAGE-Certificat.pdf");
   startActivityForResult(i,SAVE_PDF);
  }catch(Exception e){message("Pa ka ouvri anrejistreman: "+e.getMessage());}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==SAVE_PDF && result==RESULT_OK && data!=null && pendingPdf!=null){
   try(InputStream in=new FileInputStream(pendingPdf);OutputStream out=getContentResolver().openOutputStream(data.getData())){
    if(out==null)throw new IOException("Destination indisponible");
    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
    message("Certificat PDF anrejistre");
   }catch(Exception e){message("Pa ka anrejistre PDF: "+e.getMessage());}
  }
 }
 private void sharePdf(File pdf){
  if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
   message("Sou Android sa a, anrejistre PDF la anvan ou pataje li.");
   chooseSave(); return;
  }
  Uri uri=null;
  try {
   ContentValues values=new ContentValues();
   values.put(MediaStore.MediaColumns.DISPLAY_NAME,pdf.getName());
   values.put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf");
   values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/ON-LANGAGE");
   uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(uri==null)throw new IOException("Pa ka kreye fichye pataj la");
   try(InputStream in=new FileInputStream(pdf);OutputStream out=getContentResolver().openOutputStream(uri)){
    if(out==null)throw new IOException("Pa ka ekri PDF la");
    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
   }
   Intent i=new Intent(Intent.ACTION_SEND);
   i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   startActivity(Intent.createChooser(i,"Pataje certificat PDF"));
  }catch(Exception e){message("Pa ka pataje PDF: "+e.getMessage());}
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
