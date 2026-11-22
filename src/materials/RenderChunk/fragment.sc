$input v_color0, v_color1, v_fog, v_refl, v_texcoord0, v_lightmapUV, v_extra, v_wpos, v_wgeo, v_sun

#include <bgfx_shader.sh>
#include <newb/main.sh>

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);

#ifdef NLX_PIXEL_WATER
// slope (dh/dx, dh/dz) of five travelling waves: big slow swells down to small fast ripples
vec2 nlxWaveSlope(vec2 p, float t) {
  vec2 s = vec2(0.0, 0.0);
  vec2 d;
  d = vec2(0.97, 0.24);   s += d*(1.9*0.050)*cos(dot(d, p)*1.9 + t*1.7);
  d = vec2(-0.45, 0.89);  s += d*(2.7*0.032)*cos(dot(d, p)*2.7 + t*2.1);
  d = vec2(0.31, -0.95);  s += d*(4.3*0.018)*cos(dot(d, p)*4.3 + t*2.9);
  d = vec2(-0.83, -0.56); s += d*(7.1*0.009)*cos(dot(d, p)*7.1 + t*3.7);
  d = vec2(0.6, 0.8);     s += d*(11.3*0.005)*cos(dot(d, p)*11.3 + t*4.6);
  return s;
}
#endif

void main() {
  #if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY) || defined(INSTANCING)
    gl_FragColor = vec4(1.0,1.0,1.0,1.0);
    return;
  #endif

  vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
  vec4 color = v_color0;

  #ifdef ALPHA_TEST
    if (diffuse.a < 0.6) {
      discard;
    }
  #endif

  #if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
    diffuse.rgb *= mix(vec3(1.0,1.0,1.0), texture2D(s_SeasonsTexture, v_color1.xy).rgb * 2.0, v_color1.z);
  #endif

  vec3 glow = nlGlow(s_MatTexture, v_texcoord0, v_extra.a);

  diffuse.rgb *= diffuse.rgb;

  #if defined(TRANSPARENT) && !(defined(SEASONS) || defined(RENDER_AS_BILLBOARDS))
    if (v_extra.b > 0.9) {
      diffuse.rgb = vec3_splat(1.0 - NL_WATER_TEX_OPACITY*(1.0 - diffuse.b*1.8));
      diffuse.a = color.a;
    }
  #else
    diffuse.a = 1.0;
  #endif

  diffuse.rgb *= color.rgb;
  diffuse.rgb += glow;

  if (v_extra.b > 0.9) {
    #ifdef NLX_PIXEL_WATER
      vec3 V = normalize(v_wpos.xyz); // towards the camera
      float dist = length(v_wpos.xyz);
      vec3 N = vec3(0.0, 1.0, 0.0);
      if (v_wgeo.z > 0.5) { // top surface: waves fade out with distance so far water does not shimmer
        float fade = clamp(1.0 - dist/96.0, 0.15, 1.0);
        vec2 s = nlxWaveSlope(v_wgeo.xy, v_wpos.w*NL_WATER_WAVE_SPEED)*NLX_WATER_WAVE_STRENGTH*fade;
        N = normalize(vec3(-s.x, 1.0, -s.y));
      } else { // sides (waterfalls): face the camera horizontally
        N = normalize(vec3(V.x, 0.0, V.z) + vec3(0.0001, 0.0, 0.0));
      }
      float cosT = max(dot(N, V), 0.0);
      float F = 0.02 + 0.98*pow(1.0 - cosT, 5.0); // Schlick fresnel
      vec3 R = reflect(-V, N);
      vec3 skyR = v_refl.rgb*(0.8 + 0.4*clamp(R.y, 0.0, 1.0));
      float glint = pow(max(dot(R, v_sun.xyz), 0.0), NLX_WATER_GLINT_SHARPNESS);
      vec3 sunCol = mix(vec3(1.0, 0.45, 0.15), vec3(1.0, 0.92, 0.8), clamp(v_sun.y*3.0, 0.0, 1.0));
      diffuse.rgb = diffuse.rgb*(1.0 - F) + skyR*F + sunCol*glint*NLX_WATER_GLINT*v_sun.w;
      diffuse.a = mix(diffuse.a, 1.0, F);
    #else
      diffuse.rgb += v_refl.rgb*v_refl.a;
    #endif
  } else if (v_refl.a > 0.0) {
    // reflective effect - only on xz plane
    float dy = abs(dFdy(v_extra.g));
    if (dy < 0.0002) {
      float mask = v_refl.a*(clamp(v_extra.r*10.0,8.2,8.8)-7.8);
      diffuse.rgb *= 1.0 - 0.6*mask;
      diffuse.rgb += v_refl.rgb*mask;
    }
  }

  diffuse.rgb = mix(diffuse.rgb, v_fog.rgb, v_fog.a);

  diffuse.rgb = colorCorrection(diffuse.rgb);

  gl_FragColor = diffuse;
}
