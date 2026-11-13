package com.onlangage.app;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  web = new WebView(this);
  setContentView(web);
  WebSettings s = web.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  s.setAllowFileAccess(true);
  web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient() {
   @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (url.startsWith("file:///android_asset/")) return false;
    if (url.startsWith("http://") || url.startsWith("https://")) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
    return false;
   }
  });
  web.loadUrl("file:///android_asset/index.html");
 }
 @Override public void onBackPressed() { if(web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
