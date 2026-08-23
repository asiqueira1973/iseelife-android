// ShaderSources.kt — código GLSL ES 2.0 do pipeline de filtro (regra 11.6). É a tradução do
// RecipePipeline.swift (Core Image) para um shader de fragmento. O MESMO shader roda no preview
// ao vivo (textura externa da câmera, épico 8.3) e na captura offscreen (textura 2D do bitmap,
// épico 8.6) — garantindo o WYSIWYG (princípio 0.2).
//
// Dois "sabores" do fragment shader: `external=true` amostra samplerExternalOES (SurfaceTexture
// da CameraX); `external=false` amostra sampler2D (bitmap). O corpo é idêntico.

package com.dablab.iseelife.filtros

object ShaderSources {

    // Vertex: desenha um quad de tela cheia. uTexMatrix transforma as coords de textura (usado
    // pela textura externa da câmera; identidade no caminho 2D/offscreen).
    val VERTEX = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTex;
        void main() {
            gl_Position = aPosition;
            vTex = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    // Monta o fragment shader. `external` escolhe o tipo de sampler.
    fun fragment(external: Boolean): String {
        val header = if (external)
            "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n" +
                "uniform samplerExternalOES uTexture;\n"
        else
            "precision highp float;\nuniform sampler2D uTexture;\n"

        return header + FRAGMENT_BODY
    }

    // Corpo comum. Estágios na MESMA ordem do iOS: WB → shift R/B → curva de tom →
    // saturação/mono → color chrome → clareza → nitidez → grão.
    private val FRAGMENT_BODY = """
        varying vec2 vTex;
        uniform vec2 uTexSize;      // tamanho em px (p/ amostrar vizinhança em clareza/nitidez)
        uniform int  uNeutra;       // 1 = receita crua: early-return
        uniform vec3 uWbGain;       // ganho por canal do WB (temp/tinta)
        uniform vec3 uRbGain;       // ganho por canal do shift R/B (v1.1)
        uniform float uShadowShift;
        uniform float uHighlightShift;
        uniform int  uMono;         // 1 = monocromático/preview P&B
        uniform vec3 uMonoWeights;
        uniform float uSaturation;
        uniform float uChrome;
        uniform float uClarity;     // [-1..1] (lado + já com intensidade embutida)
        uniform float uSharpness;   // [-1..1]
        uniform float uGrainOpacity;
        uniform float uGrainScale;
        uniform float uSeed;        // desloca o grão por frame (não "gruda")

        const vec3 LUMA601 = vec3(0.299, 0.587, 0.114);
        const vec3 LUMA709 = vec3(0.2126, 0.7152, 0.0722);

        // Amostra a textura (samplerExternalOES e sampler2D usam texture2D em ES 2.0).
        vec3 amostra(vec2 uv) { return texture2D(uTexture, uv).rgb; }

        // Peso da curva de tom em torno de um ponto (aprox. da spline de 5 pontos do iOS).
        float pesoTom(float v, float centro) {
            float d = (v - centro) / 0.18;
            return exp(-d * d);
        }

        // Soft light por canal (mistura do grão preservando a tonalidade) — igual ao iOS.
        float softLight(float b, float s) {
            if (s < 0.5) return b - (1.0 - 2.0 * s) * b * (1.0 - b);
            float d = (b < 0.25) ? (((16.0 * b - 12.0) * b + 4.0) * b) : sqrt(b);
            return b + (2.0 * s - 1.0) * (d - b);
        }

        // Ruído pseudo-aleatório p/ o grão.
        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }

        void main() {
            vec3 col = amostra(vTex);

            // Receita "Neutra": frame cru, sem nenhuma etapa (regra 11.3).
            if (uNeutra == 1) { gl_FragColor = vec4(col, 1.0); return; }

            // WB (temperatura/tinta) + shift R/B — ganhos por canal.
            col *= uWbGain;
            col *= uRbGain;
            col = clamp(col, 0.0, 1.0);

            // Curva de tom (highlight/shadow) — levanta sombras (0.25) e ajusta altas luzes (0.75).
            float lTom = dot(col, LUMA709);
            col += uShadowShift * pesoTom(lTom, 0.25);
            col += uHighlightShift * pesoTom(lTom, 0.75);
            col = clamp(col, 0.0, 1.0);

            // Saturação OU monocromático + filtro colorido.
            if (uMono == 1) {
                float g = dot(col, uMonoWeights);
                col = vec3(g);
            } else {
                float l = dot(col, LUMA709);
                col = clamp(mix(vec3(l), col, uSaturation), 0.0, 1.0);
            }

            // Color chrome — comprime a saturação (aprox. global, como no iOS).
            {
                float l = dot(col, LUMA709);
                col = mix(vec3(l), col, uChrome);
            }

            // Clareza (contraste local) — amostra vizinhança larga (aprox. do unsharp raio grande).
            if (abs(uClarity) > 0.001) {
                vec2 px = 1.0 / uTexSize;
                float r = 6.0;
                vec3 blur = (amostra(vTex + vec2(r, 0.0) * px) + amostra(vTex - vec2(r, 0.0) * px)
                           + amostra(vTex + vec2(0.0, r) * px) + amostra(vTex - vec2(0.0, r) * px)) * 0.25;
                if (uClarity > 0.0) col = clamp(col + (col - blur) * uClarity, 0.0, 1.0);
                else col = mix(col, blur, -uClarity);
            }

            // Nitidez — unsharp de raio pequeno (>0) ou leve blur (<0).
            if (abs(uSharpness) > 0.001) {
                vec2 px = 1.0 / uTexSize;
                vec3 blurS = (amostra(vTex + vec2(1.0, 0.0) * px) + amostra(vTex - vec2(1.0, 0.0) * px)
                            + amostra(vTex + vec2(0.0, 1.0) * px) + amostra(vTex - vec2(0.0, 1.0) * px)) * 0.25;
                if (uSharpness > 0.0) col = clamp(col + (col - blurS) * uSharpness, 0.0, 1.0);
                else col = mix(col, blurS, -uSharpness);
            }

            // Grão procedural + soft light (opacidade e escala vindas da receita).
            if (uGrainOpacity > 0.001) {
                float n = hash(floor(vTex * uTexSize / uGrainScale) + uSeed);
                float s = clamp(0.5 + (n - 0.5) * uGrainOpacity * 2.0, 0.0, 1.0);
                col = vec3(softLight(col.r, s), softLight(col.g, s), softLight(col.b, s));
            }

            gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
        }
    """.trimIndent()
}
