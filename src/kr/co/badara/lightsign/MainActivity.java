package kr.co.badara.lightsign;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.os.SystemClock;

public final class MainActivity extends Activity {
    private static final int MAX_MARQUEE_SCALE=180;
    private static final String DEFAULT_TEXT = "라이트를 켜시오";
    private static final int DEFAULT_INK = 0xffffe36b, DEFAULT_BG = Color.BLACK;
    private SharedPreferences prefs;
    private Updates updates;
    private SignView preview;
    private EditText editor;
    private TextView percent, speedLabel;
    private int ink, bg, scale, speed;
    private boolean editing, paper, marquee;
    private Spectrum spectrum;
    private Button inkTab, bgTab;
    private View safeRoot;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("sign", MODE_PRIVATE);
        updates=new Updates(this);
        showSign();
    }
    @Override protected void onResume(){super.onResume();updates.resume();}
    @Override protected void onPause(){updates.pause();super.onPause();}
    @Override protected void onDestroy(){updates.close();super.onDestroy();}

    private int dp(float n) { return Math.round(getResources().getDisplayMetrics().density*n); }
    private void full(boolean on) {
        if(on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null) {
                if(on) { c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); c.hide(WindowInsets.Type.systemBars()); }
                else c.show(WindowInsets.Type.systemBars());
            }
        } else getWindow().getDecorView().setSystemUiVisibility(on?5894:0);
    }
    private void safeInsets(View view) {
        if(Build.VERSION.SDK_INT>=30) view.setOnApplyWindowInsetsListener((v,i)->{
            android.graphics.Insets in=i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(in.left,in.top,in.right,in.bottom); return i;
        });
        view.requestApplyInsets();
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus); if(focus) full(!editing);
    }
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c); if(safeRoot!=null)safeRoot.requestApplyInsets();
    }
    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private Button button(String title,int background,int foreground) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);
        b.setTextColor(foreground);b.setBackground(rounded(background,12));return b;
    }
    private void showSign() {
        editing=false;
        FrameLayout root=new FrameLayout(this);
        SignView sign=new SignView();
        sign.update(prefs.getString("message",DEFAULT_TEXT),prefs.getInt("ink",DEFAULT_INK),prefs.getInt("bg",DEFAULT_BG),prefs.getInt("scale",100),prefs.getBoolean("marquee",false),savedSpeed());
        root.setBackgroundColor(sign.bg);
        root.addView(sign,new FrameLayout.LayoutParams(-1,-1));
        Button gear=button("⚙",0xff263040,Color.WHITE);gear.setContentDescription("안내판 설정");
        gear.setTextSize(20);gear.setAlpha(.68f);gear.setPadding(0,0,0,0);gear.setOnClickListener(v->showSettings());
        FrameLayout.LayoutParams place=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END);place.setMargins(dp(8),dp(8),dp(12),dp(10));root.addView(gear,place);
        safeRoot=root;setContentView(root);safeInsets(root);full(true);
    }
    private int savedSpeed() { return Math.max(25,Math.min(250,prefs.getInt("scrollSpeed",100))); }
    private void showSettings() {
        editing=true;paper=false;
        ink=prefs.getInt("ink",DEFAULT_INK);bg=prefs.getInt("bg",DEFAULT_BG);scale=prefs.getInt("scale",100);marquee=prefs.getBoolean("marquee",false);speed=savedSpeed();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff121722);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);outer.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(outer,new ScrollView.LayoutParams(-1,-2));
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(14),dp(20),dp(20));outer.addView(box,new LinearLayout.LayoutParams(-1,-2));
        TextView title=new TextView(this);title.setText("안내판 설정");title.setTextSize(25);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(Color.WHITE);box.addView(title);
        preview=new SignView();box.addView(preview,new LinearLayout.LayoutParams(-1,dp(108)));
        label(box,"문구");
        editor=new EditText(this);editor.setText(prefs.getString("message",DEFAULT_TEXT));editor.setTextColor(Color.WHITE);
        editor.setTextSize(17);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setMinLines(1);editor.setMaxLines(3);editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        editor.setPadding(dp(12),dp(9),dp(12),dp(9));editor.setBackground(rounded(0xff242d3d,10));box.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){refresh();} public void afterTextChanged(Editable e){}});
        percent=label(box,"");
        SeekBar size=new SeekBar(this);size.setMax((marquee?MAX_MARQUEE_SCALE:100)-30);size.setProgress(Math.min(scale,marquee?MAX_MARQUEE_SCALE:100)-30);size.setContentDescription("글자 크기");
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){scale=30+p;refresh();}});box.addView(size);
        Switch motion=new Switch(this);motion.setText("한 줄 흐르기");motion.setTextColor(Color.WHITE);
        motion.setMinHeight(dp(48));motion.setChecked(marquee);
        box.addView(motion);
        label(box,"글자를 크게 한 줄로 표시하고, 너비를 넘을 때만 흐릅니다.");
        speedLabel=label(box,"흐르기 속도 · "+speed+"%");
        SeekBar speedSlider=new SeekBar(this);
        speedSlider.setContentDescription("흐르기 속도");
        speedSlider.setMax(45);speedSlider.setProgress((speed-25)/5);
        speedSlider.setEnabled(marquee);box.addView(speedSlider);
        speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int p,boolean user){
                speed=25+5*p;speedLabel.setText("흐르기 속도 · "+speed+"%");preview.setSpeed(speed);
            }
        });
        motion.setOnCheckedChangeListener((v,enabled)->{marquee=enabled;int selected=scale;size.setMax((enabled?MAX_MARQUEE_SCALE:100)-30);size.setProgress(Math.min(selected,enabled?MAX_MARQUEE_SCALE:100)-30);scale=selected;speedSlider.setEnabled(enabled);refresh();});
        LinearLayout tabs=row(box);
        inkTab=button("글자 색상",0xff343d4e,Color.WHITE);bgTab=button("배경 색상",0xff343d4e,Color.WHITE);
        addButton(tabs,inkTab);addButton(tabs,bgTab);
        spectrum=new Spectrum();box.addView(spectrum,new LinearLayout.LayoutParams(-1,dp(124)));
        inkTab.setOnClickListener(v->selectColor(false));bgTab.setOnClickListener(v->selectColor(true));selectColor(false);
        LinearLayout actions=row(box);
        Button cancel=button("취소",0xff343d4e,Color.WHITE);cancel.setOnClickListener(v->leave());
        Button save=button("저장하고 표시",DEFAULT_INK,0xff151b29);save.setOnClickListener(v->{
            String text=editor.getText().toString().trim();
            if(text.isEmpty()){editor.setError("표시할 문구를 입력하세요.");return;}
            prefs.edit().putString("message",text).putInt("scale",scale).putInt("ink",ink).putInt("bg",bg).putBoolean("marquee",marquee).putInt("scrollSpeed",speed).apply();leave();
        });addButton(actions,cancel);addButton(actions,save);
        Button update=button("지금 업데이트 확인",0xff343d4e,Color.WHITE);
        update.setOnClickListener(v->updates.check(true));box.addView(update,new LinearLayout.LayoutParams(-1,dp(48)));
        box.setFocusableInTouchMode(true);box.requestFocus();
        safeRoot=scroll;setContentView(scroll);safeInsets(scroll);full(false);refresh();
    }
    private LinearLayout row(LinearLayout box) {
        LinearLayout r=new LinearLayout(this);box.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;
    }
    private void addButton(LinearLayout row,Button button) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
        p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(button,p);
    }
    private TextView label(LinearLayout box,String text) {
        TextView t=new TextView(this);t.setText(text);t.setTextColor(0xffd4dce9);t.setTextSize(14);t.setPadding(0,dp(8),0,dp(5));box.addView(t);return t;
    }
    private void selectColor(boolean background) {
        paper=background;spectrum.load(paper?bg:ink);
        inkTab.setBackground(rounded(paper?0xff343d4e:0xff536483,12));
        bgTab.setBackground(rounded(paper?0xff536483:0xff343d4e,12));
        inkTab.setSelected(!paper);bgTab.setSelected(paper);
    }
    private void refresh() {
        if(preview==null||editor==null||percent==null)return;
        percent.setText("글자 크기 · "+Math.min(scale,marquee?MAX_MARQUEE_SCALE:100)+"%");
        String message=editor.getText().toString();
        preview.update(message.trim().isEmpty()?DEFAULT_TEXT:message,ink,bg,scale,marquee,speed);
    }
    private void leave() {
        if(editor!=null)getSystemService(InputMethodManager.class).hideSoftInputFromWindow(editor.getWindowToken(),0);
        showSign();
    }
    @Override public void onBackPressed() { if(editing)leave();else super.onBackPressed(); }

    private final class SignView extends View {
        private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private String text=DEFAULT_TEXT;
        private int bg=DEFAULT_BG,scale=100;
        private StaticLayout layout;
        private boolean singleLine, overflow;
        private long scrollStart;
        private int lineWidth, speed=100;
        SignView(){super(MainActivity.this);paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        void update(String text,int ink,int bg,int scale,boolean singleLine,int speed) {
            this.text=text;this.bg=bg;this.scale=scale;this.singleLine=singleLine;this.speed=speed;paint.setColor(ink);setContentDescription(text);layout=null;invalidate();
        }
        void setSpeed(int next) {
            if(speed==next)return;
            long now=SystemClock.uptimeMillis(),elapsed=now-scrollStart-750;
            if(layout!=null&&overflow&&elapsed>0)scrollStart=now-750-Math.round(elapsed*(double)speed/next);
            speed=next;invalidate();
        }
        private StaticLayout at(int width,float pixels) {
            paint.setTextSize(pixels);
            return StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setIncludePad(false)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0,1.02f).build();
        }
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){layout=null;}
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if(visibility==VISIBLE){scrollStart=SystemClock.uptimeMillis();invalidate();}
        }
        @Override protected void onDraw(Canvas c) {
            c.drawColor(bg);
            int pad=dp(12),width=getWidth()-2*pad,height=getHeight()-2*pad;
            if(width<1||height<1)return;
            if(singleLine) {
                drawSingleLine(c,pad,width,height);
                return;
            }
            if(layout==null) {
                overflow=false;
                int low=1,high=height,best=1;
                while(low<=high) {
                    int mid=(low+high)/2;StaticLayout candidate=at(width,mid);
                    boolean fits=candidate.getHeight()<=height;
                    for(int i=0;fits&&i<candidate.getLineCount();i++) fits=candidate.getLineWidth(i)<=width+.5f;
                    if(fits){best=mid;low=mid+1;}else high=mid-1;
                }
                layout=at(width,Math.max(1,best*Math.min(scale,100)/100f));
            }
            c.save();c.translate(pad,(getHeight()-layout.getHeight())/2f);layout.draw(c);c.restore();
        }
    
        private StaticLayout singleLineLayout(String line) {
            lineWidth=Math.max(1,(int)Math.ceil(Layout.getDesiredWidth(line,paint))+2);
            return StaticLayout.Builder.obtain(line,0,line.length(),paint,lineWidth)
                .setIncludePad(true).setMaxLines(1).build();
        }
        private void drawSingleLine(Canvas c,int pad,int width,int height) {
            if(layout==null) {
                // Fit the type to the available height, then measure its natural width.
                String line=text.replaceAll("[\\r\\n\\u2028\\u2029]+"," ");
                paint.setTextSize(Math.max(1,height*.56f*scale/100f));
                layout=singleLineLayout(line);
                if(layout.getHeight()>height){
                    paint.setTextSize(Math.max(1,paint.getTextSize()*(height-1f)/layout.getHeight()));
                    layout=singleLineLayout(line);
                }
                overflow=lineWidth-2>width;
                scrollStart=SystemClock.uptimeMillis();
            }
            float y=(getHeight()-layout.getHeight())/2f;
            c.save();c.clipRect(pad,0,pad+width,getHeight());
            if(!overflow) {
                c.translate(pad+(width-lineWidth)/2f,y);
                layout.draw(c);
            } else {
                float cycle=lineWidth+dp(56);
                float elapsed=Math.max(0,SystemClock.uptimeMillis()-scrollStart-750)/1000f;
                float offset=(elapsed*dp(105)*speed/100f)%cycle;
                c.translate(pad-offset,y);layout.draw(c);
                c.translate(cycle,0);layout.draw(c);
                if(getWindowVisibility()==VISIBLE&&isShown())postInvalidateOnAnimation();
            }
            c.restore();
        }
    }

    private final class Spectrum extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv={0,1,1};
        private boolean hueDrag;
        private int pointer=-1;
        Spectrum(){super(MainActivity.this);setContentDescription("색상 스펙트럼: 위쪽에서 채도와 밝기, 아래쪽에서 색조 조절");}
        void load(int color){Color.colorToHSV(color,hsv);invalidate();}
        private float bottom(){return getHeight()-dp(35);}
        private float left(){return dp(8);}
        private float right(){return Math.max(left()+1,getWidth()-dp(8));}
        private float clip(float n){return Math.max(0,Math.min(1,n));}
        @Override protected void onDraw(Canvas c) {
            float l=left(),r=right(),top=dp(7),b=bottom(),y=b+dp(10),end=y+dp(19);
            int pure=Color.HSVToColor(new float[]{hsv[0],1,1});
            p.setShader(new LinearGradient(l,0,r,0,Color.WHITE,pure,Shader.TileMode.CLAMP));c.drawRect(l,top,r,b,p);
            p.setShader(new LinearGradient(0,top,0,b,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));c.drawRect(l,top,r,b,p);
            p.setShader(new LinearGradient(l,0,r,0,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null,Shader.TileMode.CLAMP));c.drawRoundRect(l,y,r,end,dp(4),dp(4),p);p.setShader(null);
            marker(c,l+(r-l)*hsv[1],top+(b-top)*(1-hsv[2]));
            marker(c,l+(r-l)*hsv[0]/360,(y+end)/2);
        }
        private void marker(Canvas c,float x,float y){
            p.setStyle(Paint.Style.STROKE);p.setColor(Color.BLACK);p.setStrokeWidth(dp(4));c.drawCircle(x,y,dp(6),p);
            p.setColor(Color.WHITE);p.setStrokeWidth(dp(2));c.drawCircle(x,y,dp(6),p);p.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){pointer=e.getPointerId(0);hueDrag=e.getY()>bottom()+dp(3);getParent().requestDisallowInterceptTouchEvent(true);}
            else if(action==MotionEvent.ACTION_CANCEL){pointer=-1;getParent().requestDisallowInterceptTouchEvent(false);return true;}
            else if(action!=MotionEvent.ACTION_MOVE&&action!=MotionEvent.ACTION_UP)return true;
            int index=e.findPointerIndex(pointer);if(index<0)return true;
            float x=clip((e.getX(index)-left())/(right()-left()));
            if(hueDrag){hsv[0]=Math.min(359.99f,360*x);if(hsv[1]<.02f||hsv[2]<.02f){hsv[1]=1;hsv[2]=1;}}
            else {hsv[1]=x;hsv[2]=1-clip((e.getY(index)-dp(7))/(bottom()-dp(7)));}
            if(paper)bg=Color.HSVToColor(hsv);else ink=Color.HSVToColor(hsv);
            invalidate();refresh();
            if(action==MotionEvent.ACTION_UP){pointer=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
