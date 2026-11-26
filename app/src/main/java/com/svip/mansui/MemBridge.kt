package com.svip.mansui

import android.content.Context

/**
 * 纯 UI 版本：原版的内存路由（ROOT 服务 / 本进程 native）已删除。
 * 这里只保留同名空实现，让 UI 代码可以编译。
 */
object MemBridge {

    fun isRootBound(): Boolean = false
    fun bindRoot(context: Context) {}
    fun setTarget(pkg: String) {}
    fun attach(pkg: String): Boolean = false
    fun isAttached(): Boolean = false

    fun readPtr(addr: Long): Long = 0L
    fun readBlock(addr: Long, length: Int): ByteArray? = null
    fun readInt(addr: Long): Int = 0
    fun readFloat(addr: Long): Float = 0f
    fun writeInt(addr: Long, value: Int): Boolean = false
    fun writeFloat(addr: Long, value: Float): Boolean = false
    fun moduleBase(module: String, index: Int): Long = 0L

    fun setEspCfg(
        module: String, bssOff: Long, stride: Int, world: LongArray,
        mOff1: Long, mOff2: Long, mChain: LongArray, self: LongArray
    ) {}

    fun setEspCalib(ox: Float, oy: Float, sx: Float, sy: Float) {}
    fun setEspTeamChain(team: LongArray) {}
    fun setEspFilterTeam(enable: Boolean) {}

    fun setEspAim(
        enabled: Boolean, cam: LongArray, firing: LongArray,
        range: Float, smooth: Float, lockHead: Boolean, drawCircle: Boolean, line: Boolean
    ) {}

    fun setEspSub(enabled: Boolean, radiusPx: Float, ring: Boolean, line: Boolean, type: Int) {}
    fun setEspCtrk(cOn: Boolean, cMode: Int, vOn: Boolean, vMode: Int, sightDelta: Long) {}
    fun startEspData(w: Int, h: Int) {}
}
