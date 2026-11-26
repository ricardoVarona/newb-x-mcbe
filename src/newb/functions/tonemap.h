#ifndef TONEMAP_H
#define TONEMAP_H

#include "utils.h"

vec3 colorCorrection(vec3 col) {
  #ifdef NL_EXPOSURE
    col *= NL_EXPOSURE;
  #endif

  // ref - https://64.github.io/tonemapping/
  #if NL_TONEMAP_TYPE == 3
    // extended reinhard tonemap
    const float whiteScale = 0.063;
    col = col*(1.0+col*whiteScale)/(1.0+col);
  #elif NL_TONEMAP_TYPE == 4
    // aces tonemap
    const float a = 1.04;
    const float b = 0.03;
    const float c = 0.93;
    const float d = 0.56;
    const float e = 0.14;
    col *= 0.85;
    col = clamp((col*(a*col + b)) / (col*(c*col + d) + e), 0.0, 1.0);
  #elif NL_TONEMAP_TYPE == 2
    // simple reinhard tonemap
    col = col/(1.0+col);
  #elif NL_TONEMAP_TYPE == 1
    // exponential tonemap
    col = 1.0-exp(-col*0.8);
  #endif

  // gamma correction
  col = pow(col, vec3_splat(1.0/NL_GAMMA));

  #ifdef NL_SATURATION
    col = mix(vec3_splat(luminance(col)), col, NL_SATURATION);
  #endif

  #ifdef NLX_ORANGE_TAME
    // pull orange colors (hue about 7 to 47 degrees) toward gray, other hues stay as they are
    float maxChannel = max(col.r, max(col.g, col.b));
    float chroma = maxChannel - min(col.r, min(col.g, col.b));
    float hue = (col.g - col.b)/max(chroma, 0.0001);   // 0.0 red ~ 1.0 yellow when red is the biggest channel
    float orange = step(maxChannel, col.r)*clamp(1.0 - 3.0*abs(hue - 0.45), 0.0, 1.0);
    col = mix(vec3_splat(luminance(col)), col, 1.0 - NLX_ORANGE_TAME*orange);
  #endif

  #ifdef NL_TINT
    col *= mix(NL_TINT_LOW, NL_TINT_HIGH, col);
  #endif

  return col;
}

// inv used in fogcolor for nether
vec3 colorCorrectionInv(vec3 col) {
  #ifdef NL_TINT
    col /= mix(NL_TINT_LOW, NL_TINT_HIGH, col); // not accurate inverse
  #endif

  #ifdef NL_SATURATION
    col = mix(vec3_splat(dot(col,vec3(0.21, 0.71, 0.08))), col, 1.0/NL_SATURATION);
  #endif

  // incomplete
  // extended reinhard only
  float ws = 0.7966;
  col = pow(col, vec3_splat(NL_GAMMA));
  col = col*(ws + col)/(ws + col*(1.0 - ws));

  #ifdef NL_EXPOSURE
    col /= NL_EXPOSURE;
  #endif

  return col;
}

#endif
