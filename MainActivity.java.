package com.onlangage.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.Canvas;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import android.widget.FrameLayout;
import android.view.View;
import android.widget.Toast;
import java.io.*;

public class MainActivity extends Activity {
 private WebView web;
 private FrameLayout root;
 private TextToSpeech tts;
 private boolean voiceReady=false;
 private static final int SAVE_PDF = 301;
 private File pendingPdf;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  root=new FrameLayout(this);setContentView(root);
  web = new WebView(this); root.addView(web,new FrameLayout.LayoutParams(-1,-1));
  tts=new TextToSpeech(this,status->{voiceReady=status==TextToSpeech.SUCCESS;});
  WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
  web.setWebChromeClient(new WebChromeClient());
  web.addJavascriptInterface(new Object() {
   @JavascriptInterface public void speak(String text,String code){runOnUiThread(()->speakNative(text,code));}
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
 private void speakNative(String text,String code){
  if(tts==null||!voiceReady){message("Audio poko pare. Verifye Text-to-Speech sou telefòn nan.");return;}
  Locale locale;
  switch(code==null?"":code){case "es":locale=new Locale("es","ES");break;case "pt":locale=new Locale("pt","BR");break;case "fr":locale=Locale.FRENCH;break;case "ht":locale=new Locale("fr","FR");break;default:locale=Locale.US;}
  int result=tts.setLanguage(locale);
  if(result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED){message("Vwa lang sa a pa enstale sou telefòn nan.");return;}
  tts.setSpeechRate(0.88f);tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"onlangage-lesson");
 }
 @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
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
  // A print WebView needs a measured layout to avoid clipped or blank PDF pages.
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(1122,794);
  printer.setAlpha(0.01f);
  root.addView(printer,lp);
  String printCss="<style>@page{size:A4 landscape;margin:0}html,body{margin:0!important;padding:0!important;overflow:hidden!important;width:297mm!important;height:210mm!important}.sheet{margin:0!important;transform:none!important;box-shadow:none!important;page-break-inside:avoid!important}</style>";
  String printHtml=html.contains("</head>")?html.replace("</head>",printCss+"</head>"):html;
  printer.loadDataWithBaseURL("https://on-langage.local/",printHtml,"text/html","UTF-8",null);
 }
 private void writePdf(WebView printer, boolean share){
  PdfDocument document=new PdfDocument();
  try{
   File folder=new File(getCacheDir(),"certificates");
   if(!folder.exists() && !folder.mkdirs())throw new IOException("Pa ka kreye dosye PDF");
   File pdf=new File(folder,"ON-LANGAGE-Certificat-"+System.currentTimeMillis()+".pdf");
   // A4 landscape, measured in PDF points (72 points per inch).
   final int pageWidth=842, pageHeight=595;
   PdfDocument.PageInfo pageInfo=new PdfDocument.PageInfo.Builder(pageWidth,pageHeight,1).create();
   PdfDocument.Page page=document.startPage(pageInfo);
   Canvas canvas=page.getCanvas();
   canvas.drawColor(android.graphics.Color.WHITE);
   int viewWidth=printer.getWidth(), viewHeight=printer.getHeight();
   if(viewWidth<=0 || viewHeight<=0)throw new IOException("Paj sètifika a poko pare");
   float scale=Math.min((float)pageWidth/viewWidth,(float)pageHeight/viewHeight);
   canvas.save();
   canvas.translate((pageWidth-viewWidth*scale)/2f,(pageHeight-viewHeight*scale)/2f);
   canvas.scale(scale,scale);
   printer.draw(canvas);
   canvas.restore();
   document.finishPage(page);
   try(FileOutputStream out=new FileOutputStream(pdf)){document.writeTo(out);}
   if(pdf.length()==0)throw new IOException("PDF vid");
   pendingPdf=pdf;
   if(share)sharePdf(pdf);else chooseSave();
  }catch(Exception e){message("Erè PDF: "+e.getMessage());}
  finally{
   document.close();
   root.removeView(printer);
   printer.destroy();
  }
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
