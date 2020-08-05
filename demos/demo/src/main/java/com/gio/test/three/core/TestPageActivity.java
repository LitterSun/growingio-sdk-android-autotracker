package com.gio.test.three.core;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.gio.test.R;
import com.growingio.android.sdk.track.GrowingTracker;

import java.util.HashMap;
import java.util.Map;

public class TestPageActivity extends Activity {

    private int count = 0;
    private String WELCOME_WORDS = "当前已点击图片：";
    private ImageView img = null;
    private ImageView imgg = null;
    private ImageView imggg = null;
    private ImageView imp = null;
    private ImageView impp = null;
    private WebView mFeedWebView = null;
    private LinearLayout testPageLayout = null;
    private SpHelper mSpHelper;
    private String IMG_OPEN_CNT = "imgOpenCnt";
    private int INDEX = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpHelper = new SpHelper(this);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        String toastStr = "";
        if (bundle == null) {
            toastStr = "没有携带参数";
        } else {
            for (String key : bundle.keySet()) {
                toastStr = toastStr + key + " = " + bundle.getString(key) + "; ";
            }
        }
        showToast(toastStr,800);


        Map<String,String> visitor = new HashMap<>();
        setContentView(R.layout.activity_test_page);
        Map<String,String> map = new HashMap<>();
        map.put("pageName","TestPage");
        GrowingTracker.getInstance().trackCustomEvent("TestPageOpen",map);

        testPageLayout =  findViewById(R.id.test_page);
        img =  findViewById(R.id.img);
        imgg =  findViewById(R.id.imgg);
        imggg =  findViewById(R.id.imggg);
        imp =  findViewById(R.id.imp);
        impp =  findViewById(R.id.impp);
        setmFeedWebView();


//        图片点击
        img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count += 1;
                mSpHelper.saveImgOpenCnt(count);
                setTextandImg(mSpHelper,img,0);

                visitor.put(IMG_OPEN_CNT,String.valueOf(mSpHelper.getImgOpenCnt()));
                //更新登录用户属性--触达图片点击次数
                GrowingTracker.getInstance().setLoginUserAttributes(visitor);
                //更新访问用户属性--触达图片点击次数
                GrowingTracker.getInstance().setVisitorAttributes(visitor);
                //更新GTouch-touch1事件的「触达图片点击次数」维度的纬度值
                GrowingTracker.getInstance().trackCustomEvent("impOpen",map);

            }
        });

//        闯关关卡数
        imgg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count += 1;
                mSpHelper.saveImgOpenCnt(count);
                setTextandImg(mSpHelper,imgg,1);
                //更新登录用户属性--触达图片点击次数
//                    GrowingIO.getInstance().setPeopleVariable(IMG_OPEN_CNT,mSpHelper.getImgOpenCnt());
//                    更新访问用户属性--触达图片点击次数
//                    GrowingIO.getInstance().setVisitor(visitor);
                //更新GTouch-touch1事件的「触达图片点击次数」维度的纬度值
                GrowingTracker.getInstance().trackCustomEvent("winSuccess",map);
            }
        });

// 生成订单，楼层数
        imggg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count += 1;
                mSpHelper.saveImgOpenCnt(count);
                setTextandImg(mSpHelper,imp,2);
            }
        });

        //   支付订单成功   支付金额总数
        imp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count += 1;
                mSpHelper.saveImgOpenCnt(count);
                setTextandImg(mSpHelper,imp,3);
            }
        });


// touch1
        impp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count += 1;
                mSpHelper.saveImgOpenCnt(count);
                setTextandImg(mSpHelper,impp,4);
            }
        });
    }


    private void setTextandImg(SpHelper mSpHelper, ImageView img, int index) {
        if(index != INDEX){
            INDEX = index;
            count = 1;
            mSpHelper.saveImgOpenCnt(count);
        }
        int cnt = mSpHelper.getImgOpenCnt();
        StringBuilder POINT_NUM = new StringBuilder(WELCOME_WORDS).append(cnt).append("次");
        showToast(POINT_NUM.toString(),500);

        img.setImageResource(1 == cnt % 2 ? R.mipmap.u1 : R.mipmap.u2);
        Log.i(IMG_OPEN_CNT, POINT_NUM.toString());
    }


    public void showToast(final String word, final long time){
        this.runOnUiThread(new Runnable() {
            public void run() {
                final Toast toast = Toast.makeText(TestPageActivity.this, word, Toast.LENGTH_LONG);
                toast.show();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        toast.cancel();
                    }
                }, time);
            }
        });
    }

    public void setmFeedWebView(){
        mFeedWebView = (WebView) findViewById(R.id.feed_webView);
//        mFeedWebView.loadUrl("file:///android_asset/gio_hybrid.html");
        mFeedWebView.loadUrl("http://test-browser.growingio.com/push/saas/gio_hybrid_nosrc.html");
        //  加载JS界面  如果不写  有的界面加载不进来
        mFeedWebView.getSettings().setJavaScriptEnabled(true);
        mFeedWebView.getSettings().setDomStorageEnabled(true);

        //  保证打开的界面在自己的客户端，不是启动手机的浏览器
        mFeedWebView.setWebChromeClient(new WebChromeClient(){
            public void onReceivedTitle(WebView view,String title){
                super.onReceivedTitle(view, title);
            }
        });

        //  保证打开的界面在自己的客户端，不是启动手机的浏览器
        mFeedWebView.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,String url){
                mFeedWebView.loadUrl(url);
                return super.shouldOverrideUrlLoading(view, url);
            }
        });
    }
}