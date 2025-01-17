package com.nover.interactivewebview

import androidx.annotation.NonNull

import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class CallMethod {
    setOptions, evalJavascript, loadHTML, loadUrl
}

class InteractiveWebviewPlugin: FlutterPlugin, ActivityAware, MethodCallHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    companion object {
        lateinit var channel: MethodChannel
    }
    
    private var webView : WebView? = null
    private var webClient : InteractiveWebViewClient? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
      channel = MethodChannel(flutterPluginBinding.binaryMessenger, "interactive_webview")
      channel.setMethodCallHandler(this)
      webView = WebView(flutterPluginBinding.applicationContext)
      webClient = InteractiveWebViewClient(listOf())
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
      channel.setMethodCallHandler(null)
      webView = null
      webClient = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      val params = FrameLayout.LayoutParams(0, 0)
      val decorView = binding.activity.window.decorView as FrameLayout
      decorView.addView(webView!!, params)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          if (0 != (binding.activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
              WebView.setWebContentsDebuggingEnabled(true)
          }
      }

      webView!!.visibility = View.GONE
      webView!!.settings.javaScriptEnabled = true
      webView!!.settings.domStorageEnabled = true
      webView!!.settings.allowFileAccessFromFileURLs = true
      webView!!.addJavascriptInterface(JsInterface(), "native")
      webView!!.webViewClient = webClient!!    
    }

    override fun onDetachedFromActivity() {
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.onAttachedToActivity(binding)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        val method = CallMethod.valueOf(call.method)
        when (method) {
            CallMethod.setOptions -> setOptions(call)
            CallMethod.evalJavascript -> evalJavascript(call)
            CallMethod.loadHTML -> loadHTML(call)
            CallMethod.loadUrl -> loadUrl(call)
        }

        result.success(null)
    }

    private fun setOptions(call: MethodCall) {
        (call.arguments as? HashMap<*, *>)?.let {
            val restrictedSchemes = it["restrictedSchemes"]
            if (restrictedSchemes is Array<*>)
                webClient!!.restrictedSchemes = restrictedSchemes.filterIsInstance<String>()
        }
    }

    private fun evalJavascript(call: MethodCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            (call.arguments as? HashMap<*, *>)?.let { arguments ->
                (arguments["script"] as? String)?.let {
                    webView!!.evaluateJavascript(it, null)
                }
            }
        }
    }

    private fun loadHTML(call: MethodCall) {
        (call.arguments as? HashMap<*, *>)?.let { arguments ->
            val html = arguments["html"] as String
            if (arguments.containsKey("baseUrl")) {
                (arguments["baseUrl"] as? String)?.let {
                    webView!!.loadDataWithBaseURL(it, html, "text/html", "UTF-8", null)
                }
            } else {
                webView!!.loadData(html, "text/html", "UTF-8")
            }
        }
    }

    private fun loadUrl(call: MethodCall) {
        (call.arguments as? HashMap<*, *>)?.let { arguments ->
            val url = arguments["url"] as String
            webView!!.loadUrl(url)
        }
    }
}

class InteractiveWebViewClient(var restrictedSchemes: List<String>): WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        val data = hashMapOf<String, Any>()
        data["url"] = url!!
        data["type"] = "didStart"
        InteractiveWebviewPlugin.channel.invokeMethod("stateChanged", data)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        val data = hashMapOf<String, Any>()
        data["url"] = url!!
        data["type"] = "didFinish"
        InteractiveWebviewPlugin.channel.invokeMethod("stateChanged", data)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return shouldOverrideUrlLoading(url)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url.toString()
        return shouldOverrideUrlLoading(url)

    }

    private fun shouldOverrideUrlLoading(url: String?): Boolean {
        for (l in restrictedSchemes) {
            if (url != null && url.contains(l))
                return false
        }

        return true
    }
}

class JsInterface {

    @JavascriptInterface
    fun postMessage(data: String?) {
        data?.let {
            val message = hashMapOf<String, Any>()
            message["name"] = "native"

            try {
                when (it[0]) {
                    '{' -> {
                        val jsonObj = JSONObject(it)
                        message["data"] = toMap(jsonObj)
                    }
                    '[' -> {
                        val jsonArray = JSONArray(it)
                        message["data"] = toList(jsonArray)
                    }
                    else -> message["data"] = it
                }
            } catch (e: JSONException) {
                message["data"] = it
            }

            Handler(Looper.getMainLooper()).post {
                InteractiveWebviewPlugin.channel.invokeMethod("didReceiveMessage", message)
            }
        }
    }

    @Throws(JSONException::class)
    private fun toMap(obj: JSONObject): Map<String, Any> {
        val map = HashMap<String, Any>()

        val keysItr = obj.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value = obj.get(key)

            if (value is JSONArray) {
                value = toList(value)
            } else if (value is JSONObject) {
                value = toMap(value)
            }
            map[key] = value
        }
        return map
    }

    @Throws(JSONException::class)
    private fun toList(array: JSONArray): List<Any> {
        val list = ArrayList<Any>()
        for (i in 0 until array.length()) {
            var value = array.get(i)
            if (value is JSONArray) {
                value = toList(value)
            } else if (value is JSONObject) {
                value = toMap(value)
            }
            list.add(value)
        }
        return list
    }
}








