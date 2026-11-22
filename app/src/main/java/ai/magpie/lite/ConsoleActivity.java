package ai.magpie.lite;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * magpie's own console, full screen: providers, routing, usage, plugins — plus
 * the Android add-on (gateway on/off, self-served API keys, WorkBuddy, agents).
 * No wrapping UI of our own.
 */
public class ConsoleActivity extends Activity {

    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GatewayService.start(this);
        CheckinScheduler.sync(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        WebView.setWebContentsDebuggingEnabled(true);
        web.addJavascriptInterface(new HostBridge(this), "MagpieHost");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) retry();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) retry();
            }
        });
        setContentView(web);
        load();
    }

    private void load() {
        web.loadUrl(CoreRunner.get(this).consoleUrl(getIntent() == null ? null : getIntent().getStringExtra("view")));
    }

    /** The first paint can land before the gateway listens: ask again. */
    private void retry() {
        if (tries++ > 40) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() { load(); }
        }, 700);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
