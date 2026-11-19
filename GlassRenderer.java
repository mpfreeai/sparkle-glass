package com.sparkle.glass;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GlassRenderer implements GLSurfaceView.Renderer {

    // User-controlled settings (written from UI thread, read on GL thread)
    public volatile float cx = -1, cy = -1;
    public volatile float blur = 10, opac = 0.22f, thick = 34, size = 220, speed = 1f;
    public volatile int shape = 0, mode = 1;
    public volatile float[] tint = {0.81f, 0.90f, 1f};

    private final float dens;
    private float w, h, animTime;
    private long last = 0;
    private int prog;
    private int uR, uC, uS, uT, uRad, uBlur, uOpac, uThick, uMode, uTint;
    private FloatBuffer quad;

    public GlassRenderer(float density) {
        dens = density;
        float[] v = {-1, -1, 3, -1, -1, 3};
        quad = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(v).position(0);
    }

    private static final String VS =
        "attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}";

    private static final String FS = """
precision highp float;
uniform vec2 R,C,S;
uniform float T,rad,blur,opac,thick,mode;
uniform vec3 tint;
float h21(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
vec3 bg(vec2 p){
 vec2 u=p/R.y;float ar=R.x/R.y;
 vec3 c=vec3(.07,.05,.10);
 c+=vec3(.95,.14,.08)*exp(-2.6*length(u-vec2(ar*.2,.85)));
 c+=vec3(.12,.05,.02)*exp(-2.0*length(u-vec2(ar*.1,.95)));
 c+=vec3(1.,.48,.04)*exp(-3.2*length(u-vec2(ar*.55,.55)));
 c+=vec3(.04,.30,1.)*exp(-3.4*length(u-vec2(ar*.95,.25)));
 c+=vec3(.9,.1,.05)*exp(-3.0*length(u-vec2(ar*.3,.1)));
 vec2 f=abs(fract(u*9.)-.5);
 float ln=smoothstep(.485,.5,max(f.x,f.y));
 float dt=smoothstep(.09,.0,length(fract(u*27.)-.5));
 return c+ln*.28+dt*.18;
}
float sdf(vec2 q){
 vec2 h=S*.5;float r=min(rad,min(h.x,h.y));
 vec2 b=abs(q)-h+r;
 return length(max(b,0.))+min(max(b.x,b.y),0.)-r;
}
void main(){
 vec2 p=vec2(gl_FragCoord.x,R.y-gl_FragCoord.y);
 vec3 base=bg(p);vec3 col=base;
 vec2 q=p-C;float d=sdf(q);
 if(d>0.){col*=1.-.38*exp(-d/(thick*.8+10.));}
 if(d<1.5){
  vec2 n=normalize(vec2(sdf(q+vec2(1.,0.))-sdf(q-vec2(1.,0.)),sdf(q+vec2(0.,1.))-sdf(q-vec2(0.,1.)))+1e-5);
  float depth=clamp(-d/thick,0.,1.);
  float bend=pow(1.-depth,2.2);
  vec2 off=-n*bend*thick*1.1;
  vec3 g;
  if(blur<.5){
   g=vec3(bg(p+off).r,bg(p+off*1.06).g,bg(p+off*1.12).b);
  }else{
   vec3 a=vec3(0.);
   for(int i=0;i<14;i++){
    float an=float(i)*2.39996;
    float rr=sqrt((float(i)+.5)/14.)*blur;
    a+=bg(p+off+vec2(cos(an),sin(an))*rr);
   }
   g=a/14.;
  }
  col=mix(g,tint,opac*.55)+opac*.05;
  if(mode>.5){
   float sp=0.;
   for(int L=0;L<3;L++){
    float fl=float(L);
    float sc=(10.+fl*7.)*1.0;
    vec2 gg=p/sc+vec2(0.,-T*.05*(1.+fl*.6));
    vec2 id=floor(gg);vec2 f=fract(gg)-.5;
    float r=h21(id+fl*7.3);
    vec2 j=vec2(h21(id+3.1),h21(id+9.7))-.5;
    float ds=length(f-j*.7);
    float tw=pow(.5+.5*sin(T*(1.2+3.*r)+r*31.4),5.);
    sp+=smoothstep(.13,.0,ds)*tw*step(.42,r);
   }
   if(mode<1.5){col+=sp*vec3(1.,.96,.88)*1.3;}
   else{
    float ph=(q.x*.012+q.y*.02)-T*.9;
    float w=pow(.5+.5*sin(ph*2.+sin(q.y*.025+T*.7)*2.2),9.);
    col+=w*.34*vec3(1.,.97,.92)+sp*.25;
   }
  }
  float rim=exp(-(-d)/2.2);
  float l=dot(n,normalize(vec2(-.6,-.8)));
  col+=rim*(.28+.7*max(l,0.))+rim*.22*max(-l,0.);
  col+=exp(-(-d)/(thick*.7))*.10*max(l,0.)*vec3(1.,.97,.9);
  col=mix(base,col,smoothstep(1.2,-.8,d));
 }
 gl_FragColor=vec4(col,1.);
}
""";

    private int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) Log.e("SparkleGlass", "Shader error: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, compile(GLES20.GL_VERTEX_SHADER, VS));
        GLES20.glAttachShader(prog, compile(GLES20.GL_FRAGMENT_SHADER, FS));
        GLES20.glLinkProgram(prog);
        GLES20.glUseProgram(prog);
        uR = GLES20.glGetUniformLocation(prog, "R");
        uC = GLES20.glGetUniformLocation(prog, "C");
        uS = GLES20.glGetUniformLocation(prog, "S");
        uT = GLES20.glGetUniformLocation(prog, "T");
        uRad = GLES20.glGetUniformLocation(prog, "rad");
        uBlur = GLES20.glGetUniformLocation(prog, "blur");
        uOpac = GLES20.glGetUniformLocation(prog, "opac");
        uThick = GLES20.glGetUniformLocation(prog, "thick");
        uMode = GLES20.glGetUniformLocation(prog, "mode");
        uTint = GLES20.glGetUniformLocation(prog, "tint");
        int a = GLES20.glGetAttribLocation(prog, "a");
        GLES20.glEnableVertexAttribArray(a);
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, quad);
        last = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        w = width;
        h = height;
        GLES20.glViewport(0, 0, width, height);
        if (cx < 0) {
            cx = w / 2f;
            cy = h * 0.33f;
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min((now - last) / 1e9f, 0.1f);
        last = now;
        animTime += dt * speed;

        float s = size * dens, sx = s, sy = s, r = s * 0.28f;
        if (shape == 0) { sx = s * 1.45f; r = s * 0.3f; }
        else if (shape == 1) { r = s; }
        else { sx = s * 1.8f; sy = s * 0.6f; r = s; }

        float[] t = tint;
        GLES20.glUniform2f(uR, w, h);
        GLES20.glUniform2f(uC, cx, cy);
        GLES20.glUniform2f(uS, sx, sy);
        GLES20.glUniform1f(uT, animTime);
        GLES20.glUniform1f(uRad, r);
        GLES20.glUniform1f(uBlur, blur * dens);
        GLES20.glUniform1f(uOpac, opac);
        GLES20.glUniform1f(uThick, thick * dens);
        GLES20.glUniform1f(uMode, mode);
        GLES20.glUniform3f(uTint, t[0], t[1], t[2]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
    }
}
