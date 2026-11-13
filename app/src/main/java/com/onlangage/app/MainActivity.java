package com.onlangage.app;
https://github.com/donaldfamilly101-bot/ON-LANGAGE/delete/main/app/src/main/java/com/onlangage/app/MainActivity%282%29.java
import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.print.*;
import android.content.Context;
import android.widget.Toast;
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
  WebView printer = new WebView(this);
  printer.getSettings().setDefaultTextEncodingName("UTF-8");
  printer.getSettings().setJavaScriptEnabled(false);
  printer.setWebViewClient(new WebViewClient(){
   boolean started=false;
   @Override public void onPageFinished(WebView v,String url){
    if(started)return; started=true;
    printer.postDelayed(()->writePdf(printer,share),500);
   }
  });
  printer.loadDataWithBaseURL("https://on-langage.local/",html,"text/html","UTF-8",null);
 }
 private void writePdf(WebView printer,boolean share){
  try{
   File folder=new File(getCacheDir(),"certificates"); if(!folder.exists())folder.mkdirs();
   File pdf=new File(folder,"ON-LANGAGE-Certificat-"+System.currentTimeMillis()+".pdf");
   PrintDocumentAdapter adapter=printer.createPrintDocumentAdapter("ON-LANGAGE-Certificat");
   PrintAttributes attrs=new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
    .setResolution(new PrintAttributes.Resolution("pdf","PDF",300,300))
    .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
   adapter.onStart();
   adapter.onLayout(null,attrs,new CancellationSignal(),new PrintDocumentAdapter.LayoutResultCallback(){
    @Override public void onLayoutFinished(PrintDocumentInfo info,boolean changed){
     try{
      ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE);
      adapter.onWrite(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES},fd,new CancellationSignal(),new PrintDocumentAdapter.WriteResultCallback(){
       @Override public void onWriteFinished(android.print.PageRange[] pages){
        try{fd.close();}catch(Exception ignored){}
        adapter.onFinish(); printer.destroy();
        if(pdf.length()==0){message("PDF vid. Eseye ankò.");return;}
        pendingPdf=pdf;
        if(share)sharePdf(pdf);else chooseSave();
       }
       @Override public void onWriteFailed(CharSequence error){try{fd.close();}catch(Exception ignored){} adapter.onFinish();printer.destroy();message("Erè PDF: "+error);}
       @Override public void onWriteCancelled(){try{fd.close();}catch(Exception ignored){}adapter.onFinish();printer.destroy();message("PDF anile");}
      });
     }catch(Exception e){message("Erè PDF: "+e.getMessage());}
    }
    @Override public void onLayoutFailed(CharSequence error){adapter.onFinish();printer.destroy();message("Erè PDF: "+error);}
   },null);
  }catch(Exception e){printer.destroy();message("Erè PDF: "+e.getMessage());}
 }
 private void chooseSave(){
  Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
  i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");
  i.putExtra(Intent.EXTRA_TITLE,"ON-LANGAGE-Certificat.pdf");
  startActivityForResult(i,SAVE_PDF);
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
  try{
   Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pdf);
   Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");
   i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   startActivity(Intent.createChooser(i,"Compartir certificado PDF"));
  }catch(Exception e){message("Pa ka pataje PDF: "+e.getMessage());}
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
