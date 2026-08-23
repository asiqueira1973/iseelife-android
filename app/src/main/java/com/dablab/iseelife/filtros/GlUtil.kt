// GlUtil.kt — utilitários OpenGL ES 2.0 compartilhados (épico 8.2): compilar/linkar shaders,
// checar erros e o quad de tela cheia usado por preview e captura.

package com.dablab.iseelife.filtros

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {

    // Quad de tela cheia: 2 triângulos cobrindo o clip-space [-1,1] com coords de textura [0,1].
    // Ordem das linhas: x, y, u, v. A textura tem V invertido (origem do GL embaixo) — tratado
    // aqui para a imagem sair na orientação certa no caminho 2D.
    private val VERTICES = floatArrayOf(
        -1f, -1f, 0f, 0f,
         1f, -1f, 1f, 0f,
        -1f,  1f, 0f, 1f,
         1f,  1f, 1f, 1f,
    )

    fun criarBufferQuad(): FloatBuffer =
        ByteBuffer.allocateDirect(VERTICES.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(VERTICES); position(0) }

    // Compila um shader e devolve o handle; lança em caso de erro (com o log do compilador).
    fun compilarShader(tipo: Int, fonte: String): Int {
        val shader = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(shader, fonte)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Falha ao compilar shader: $log")
        }
        return shader
    }

    // Linka um programa a partir dos fontes de vertex e fragment.
    fun criarPrograma(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compilarShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compilarShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
        // Shaders já podem ser descartados após o link.
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("Falha ao linkar programa: $log")
        }
        return prog
    }

    // Lança se o último comando GL deixou erro (usado após operações sensíveis).
    fun checar(tag: String) {
        val e = GLES20.glGetError()
        if (e != GLES20.GL_NO_ERROR) throw RuntimeException("Erro GL em $tag: 0x${Integer.toHexString(e)}")
    }
}
