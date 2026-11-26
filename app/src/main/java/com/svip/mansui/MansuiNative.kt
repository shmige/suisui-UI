package com.svip.mansui

import android.view.Surface

/**
 * 纯 UI 版本的 native 空壳。
 *
 * 原版这里加载 libmansui.so（跨进程读写 / ESP / 注入）。
 * 开源 UI 版删除全部原生能力，仅保留同名方法让 UI 编译通过：
 * 所有方法不访问任何内存、不加载 .so、不做任何系统操作。
 */
object MansuiNative {

    fun attachGame(pkg: String): Boolean = false
    fun setTargetPackage(pkg: String) {}
    fun getGamePid(): Int = 0
    fun isAttached(): Boolean = false
    fun detachGame() {}

    fun readPtr(addr: Long): Long = 0L
    fun readInt(addr: Long): Int = 0
    fun readFloat(addr: Long): Float = 0f
    fun writeInt(addr: Long, value: Int): Boolean = false
    fun writeFloat(addr: Long, value: Float): Boolean = false
    fun readBlock(addr: Long, length: Int): ByteArray? = null

    fun setEspCfg(
        module: String, bssOff: Long, stride: Int,
        world: LongArray, mOff1: Long, mOff2: Long, mChain: LongArray, self: LongArray
    ) {}

    fun setEspCalib(ox: Float, oy: Float, sx: Float, sy: Float) {}
    fun setEspTeamChain(team: LongArray) {}
    fun setEspFilterTeam(enable: Boolean) {}
    fun setIslandEnabled(enable: Boolean) {}
    fun islandNotice(title: String, content: String, type: Int) {}

    fun setEspAim(
        enabled: Boolean, cam: LongArray, firing: LongArray,
        range: Float, smooth: Float, lockHead: Boolean,
        drawCircle: Boolean, line: Boolean
    ) {}

    fun setEspSub(enabled: Boolean, radiusPx: Float, ring: Boolean, line: Boolean, type: Int) {}
    fun setEspCtrk(cOn: Boolean, cMode: Int, vOn: Boolean, vMode: Int, sightDelta: Long) {}

    fun espBypass(mod: String) {}
    fun espLuaReq(st: Long, ls: Long, pc: Long) {}
    fun espGun(on: Boolean) {}
    fun espPierce(on: Boolean) {}
    fun espGunMods(content: String) {}
    fun espEdFill(content: String) {}
    fun espBubble(id: Int) {}
    fun espAttr(content: String) {}
    fun espEat(on: Boolean) {}
    fun espEffect(content: String) {}
    fun espTprt(on: Boolean, pkg: String) {}
    fun verifyApkSign(sha256: String) {}

    fun startEspData(w: Int, h: Int) {}
    fun espDataFile() {}
    fun espDataLocal(w: Int, h: Int) {}

    fun surfaceCreate(surface: Surface) {}
    fun surfaceChanged(w: Int, h: Int) {}
    fun surfaceDestroy() {}
    fun setDrawFlag(index: Int, enable: Boolean) {}
    fun setFontPath(path: String) {}

    fun getModuleBase(module: String, index: Int): Long = 0L
    fun resolveChain(base: Long, offsets: IntArray): Long = 0L
}
