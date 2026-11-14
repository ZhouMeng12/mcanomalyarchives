#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;
uniform float Age, Strength, CenterX, CenterY, FieldRadius, OffsetX, OffsetY, OffsetZ;
uniform float Inverse0;
uniform float Inverse1;
uniform float Inverse2;
uniform float Inverse3;
uniform float Inverse4;
uniform float Inverse5;
uniform float Inverse6;
uniform float Inverse7;
uniform float Inverse8;
uniform float Inverse9;
uniform float Inverse10;
uniform float Inverse11;
uniform float Inverse12;
uniform float Inverse13;
uniform float Inverse14;
uniform float Inverse15;

in vec2 texCoord;
out vec4 fragColor;
void main(){
 mat4 inverseVP=mat4(Inverse0,Inverse1,Inverse2,Inverse3,Inverse4,Inverse5,Inverse6,Inverse7,Inverse8,Inverse9,Inverse10,Inverse11,Inverse12,Inverse13,Inverse14,Inverse15);
 float depth=texture(DepthSampler,texCoord).r;
 vec4 location=inverseVP*vec4(texCoord*2.-1.,depth*2.-1.,1.);
 vec3 relative=location.xyz/max(.000001,abs(location.w))*sign(location.w);
 float distanceToField=length(relative-vec3(OffsetX,OffsetY,OffsetZ));
 float mask=(1.-smoothstep(FieldRadius*.18,FieldRadius,distanceToField))*step(depth,.99999);
 // Keep localized pre-blast distortion; stop it on the impact frame.
 float collapse=max(.20,Strength)*mask*(1.-step(600.,Age));
 vec2 delta=texCoord-vec2(CenterX,CenterY);
 delta*=min(1.,.3/max(.001,length(delta)));
 vec3 rgb=vec3(0.); float total=0.;
 for(int i=0;i<12;i++) {
  float t=float(i)/11.; float weight=exp(-t*2.5);
  rgb+=texture(DiffuseSampler,clamp(texCoord+delta*collapse*(.24+t*.65),vec2(.001),vec2(.999))).rgb*weight;
  total+=weight;
 }
 rgb/=total;
 float charge=(1.-step(600.,Age))*(.30+.70*min(1.,Age/600.));
 rgb+=vec3(.65,.43,.018)*mask*charge;
 float afterglow=exp(-max(0.,Age-600.)/52.)*(1.-smoothstep(730.,800.,Age));
 rgb+=mix(vec3(.42,.32,.018),vec3(.40,.12,.012),smoothstep(610.,700.,Age))*Strength*mask*afterglow;
 float flash=step(600.,Age)*(1.-smoothstep(604.,622.,Age));
 float nearViewer=1.-smoothstep(128.,512.,length(vec3(OffsetX,OffsetY,OffsetZ)));
 rgb=mix(rgb,vec3(1.,.94,.65),flash*max(mask,max(.08,nearViewer))*.95);
 rgb=mix(rgb,rgb*vec3(.92,.9,.81),Strength*.28);
 // Edge-based impact plates: keep the world readable instead of a solid black orb.
 float impact=Age-600.;
 if(impact>=0. && impact<30.) {
  vec2 px=1./max(OutSize,vec2(1.));
  vec3 weights=vec3(.299,.587,.114);
  float lum=dot(texture(DiffuseSampler,texCoord).rgb,weights);
  float gx=dot(texture(DiffuseSampler,texCoord+vec2(px.x,0.)).rgb-texture(DiffuseSampler,texCoord-vec2(px.x,0.)).rgb,weights);
  float gy=dot(texture(DiffuseSampler,texCoord+vec2(0.,px.y)).rgb-texture(DiffuseSampler,texCoord-vec2(0.,px.y)).rgb,weights);
  float dz=abs(texture(DepthSampler,texCoord+vec2(px.x,0.)).r-depth)+abs(texture(DepthSampler,texCoord+vec2(0.,px.y)).r-depth);
  float edge=smoothstep(.035,.22,length(vec2(gx,gy))+min(.6,dz/max(.00002,1.-depth)*.12));
  vec2 pixel=texCoord*OutSize;
  float grain=fract(sin(dot(floor(pixel/2.)+floor(impact/3.),vec2(12.9898,78.233)))*43758.5453);
  float dots=step(.77,length(fract(pixel/4.)-.5)*1.5);
  vec3 dark=vec3(.006,.005,.004), paper=vec3(1.,.96,.84), gold=vec3(1.,.79,.38);
  vec3 plate=mix(dark,mix(gold,paper,edge),edge);
  if(impact>=4.) plate=mix(paper,dark,clamp(edge*.88+dots*.14+(1.-lum)*.12+step(.97,grain)*.35,0.,1.));
  vec2 ray=texCoord-vec2(CenterX,CenterY); ray.x*=OutSize.x/max(1.,OutSize.y);
  float stars=0.;
  for(int i=0;i<4;i++) {
   vec2 q=ray-vec2(sin(float(i)*2.4)*.035,float(i)*.105);
   float crossLight=max(exp(-abs(q.x)*1100.)*exp(-abs(q.y)*7.),exp(-abs(q.y)*1100.)*exp(-abs(q.x)*9.));
   stars=max(stars,max(crossLight,exp(-length(q)*250.)));
  }
  plate=mix(plate,impact<4.?paper:dark,clamp(stars,0.,1.));
  float envelope=1.-smoothstep(11.,16.,impact);
  rgb=mix(rgb,plate,envelope*nearViewer);
  rgb+=gold*stars*nearViewer*(1.-envelope)*(1.-smoothstep(16.,30.,impact));
 }
 // A world-space dust volume hides clearing only through the blast footprint, including the sky behind it.
 vec3 origin=vec3(OffsetX,OffsetY,OffsetZ);
 vec3 direction=normalize(relative);
 float along=dot(direction,origin);
 float perpendicular2=max(0.,dot(origin,origin)-along*along);
 float radius=180.;
 float halfChord=sqrt(max(0.,radius*radius-perpendicular2));
 float enter=max(0.,along-halfChord), leave=min(length(relative),along+halfChord);
 float thickness=max(0.,leave-enter)*step(perpendicular2,radius*radius);
 float cover=(1.-exp(-thickness*.12))*Dust*smoothstep(608.,616.,Age);
 float smoke=.5+.25*sin(texCoord.x*31.+Age*.09)*sin(texCoord.y*23.-Age*.06)
                  +.13*sin((texCoord.x+texCoord.y)*79.+Age*.15);
 vec3 ash=mix(vec3(.12,.105,.085),vec3(.48,.36,.22),smoke);
 ash+=vec3(.7,.38,.10)*exp(-max(0.,Age-616.)/35.);
 rgb=mix(rgb,ash,clamp(cover,0.,1.));
 fragColor=vec4(clamp(rgb,0.,1.),1.);
}
