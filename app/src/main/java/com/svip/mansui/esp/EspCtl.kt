package com.svip.mansui.esp

import android.content.Context
import android.graphics.Point
import android.view.WindowManager
import com.svip.mansui.CHANNEL_PACKAGES
import com.svip.mansui.CloudConfig
import com.svip.mansui.IPCurbco.TClass.SPUtils
import com.svip.mansui.MansuiNative
import com.svip.mansui.MemBridge
import com.svip.mansui.lua.FuncBridge
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

// ═══════════════════════════════════════════════
//  ESP 控制面 (渲染在 native EGL+ImGui, 见 jni/esp/):
//   1) 开关状态 (位序同梦喵 isDraw: 0总 1方框 2射线 3血量 4距离 5名字 6人数 7迷你号 8击败数)
//      每次切换同步 MansuiNative.setDrawFlag (native 渲染直接消费)
//   2) 数据源装配: 云 cfg 就绪后下发 → ROOT: daemon 写文件桥 + app 读文件
//                   UID/框架: 本进程直读采集
//   3) 字体: assets/heiti.ttf → files/esp/heiti.ttf → native 加载 (中文名/标签)
// ═══════════════════════════════════════════════
object EspCtl {

    // 0..8 绘制开关, 9=过滤队友 (采集侧生效), 10=白子光环 (与总开关独立门控)
    val flags = BooleanArray(11) { false }

    @Volatile private var appCtx: Context? = null

    /** 程序同步(跨页开关镜像)时置 true, 抑制重复灵动岛提示 */
    @Volatile var tipMute = false
    @Volatile private var started = false
    private var fontReady = false

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    // ── 视角自瞄 (移植梦喵 AimCorrect; 采集侧执行; 与 ESP 总开关独立显示) ──
    @Volatile var aimOn = false; private set
    @Volatile var aimRange = 260f
    @Volatile var aimSmooth = 1f   // 默认秒锁 (每帧直接吸到目标); 可在设置区调慢
    @Volatile var aimLine = false; private set
    @Volatile private var camChain: LongArray? = null
    @Volatile private var firingChain: LongArray? = null

    /** 自瞄配置: 开自瞄自动带瞄准圈; 范围/平滑/追线由设置区调整 */
    fun setAim(on: Boolean? = null, range: Float? = null, smooth: Float? = null, line: Boolean? = null) {
        if (on != null) {
            aimOn = on
            if (on) circleOn = true
        }
        if (range != null) aimRange = range
        if (smooth != null) aimSmooth = smooth.coerceIn(0.05f, 1f)
        if (line != null) aimLine = line
        pushAim()
    }

    @Volatile private var circleOn = false

    private fun pushAim() {
        val cam = camChain ?: return
        val firing = firingChain
        MemBridge.setEspAim(aimOn, cam, firing ?: LongArray(0), aimRange, aimSmooth, true, circleOn, aimLine)
    }

    // ── 子追 (子弹追踪; 梦喵 DoBulletTracking 官方枪械 + 模组枪械 hook; 采集侧执行) ──
    @Volatile var subOn = false; private set
    @Volatile var subRadius = 300f
    @Volatile var subLine = false; private set
    @Volatile var subType = 0; private set   // 0=普通枪械 1=模组枪械

    /** 子追配置: 自动锁圈内最近目标 (line=追线, type=枪械类型, 半径 px); 辅助圈随开启固定显示 */
    fun setSub(on: Boolean? = null, radiusPx: Float? = null, line: Boolean? = null, type: Int? = null) {
        if (on != null) subOn = on
        if (radiusPx != null) subRadius = radiusPx
        if (line != null) subLine = line
        if (type != null) subType = if (type == 1) 1 else 0
        pushSub()
    }

    private fun pushSub() {
        MemBridge.setEspSub(subOn, subRadius, ring = true, subLine, subType)
    }

    // ── 坐标追踪 / 视角追踪 (梦喵 DoCoordTrack / DoSightTrack; 战斗页; 采集侧执行) ──
    @Volatile var ctrkOn = false; private set
    @Volatile var ctrkMode = 0; private set     // 0=残血优先 1=距离最近
    @Volatile var vtrkOn = false; private set
    @Volatile var vtrkMode = 0; private set
    @Volatile private var sightDelta = 0x12CL   // 视角坐标基址差 (云 sightBaseDelta)

    fun setCoord(on: Boolean? = null, mode: Int? = null) {
        if (on != null) ctrkOn = on
        if (mode != null) ctrkMode = if (mode == 1) 1 else 0
        pushCtrk()
    }

    fun setSight(on: Boolean? = null, mode: Int? = null) {
        if (on != null) vtrkOn = on
        if (mode != null) vtrkMode = if (mode == 1) 1 else 0
        pushCtrk()
    }

    private fun pushCtrk() {
        MemBridge.setEspCtrk(ctrkOn, ctrkMode, vtrkOn, vtrkMode, sightDelta)
    }

    // ── 首页: 绕过检测 / 注入引擎 (梦喵 DoLogoBypass2 / DoLuaInject 移植) ──
    // 云键: safeModuleName / luaStateBssOff / luaLoadstringOff / luaPcallOff
    @Volatile var bypassOn = false; private set
    @Volatile private var safeMod: String? = null   // 云端 safeModuleName, 未就绪不启动
    @Volatile private var luaSt = 0x13060L
    @Volatile private var luaLs = 0xA2057ECL
    @Volatile private var luaPc = 0xA1FA24CL

    /** 绕过检测: 开 → 请求 daemon 启动 safe2 3s 循环 (每进程一次, 同梦喵无关闭) */
    fun setBypass(on: Boolean) {
        bypassOn = on
        val m = safeMod ?: return
        if (on) runCatching { MansuiNative.espBypass(m) }
    }

    /** 注入引擎: 每点一次注入一次 (请求经文件桥, daemon/直读通道执行) */
    fun luaInject() {
        runCatching { MansuiNative.espLuaReq(luaSt, luaLs, luaPc) }
    }

    // ── UID 绕过闪退 (libtprt 签名检测 RET; UID 模式登录/直连后自动常驻循环) ──
    @Volatile var tprtOn = false; private set

    fun tprtBypass(on: Boolean) {
        tprtOn = on
        runCatching { MansuiNative.espTprt(on, channelPkg()) }
    }

    // ── 自动开火 (native 指令补丁; 同梦喵 AutoFirePatch) ──
    @Volatile var autoFireOn = false; private set

    fun setAutoFire(on: Boolean) {
        autoFireOn = on
        runCatching { MansuiNative.espGun(on) }
    }

    // ── 子弹穿墙 (模组枪械 hook; 同梦喵 pierce) ──
    @Volatile var pierceOn = false; private set

    fun setPierce(on: Boolean) {
        pierceOn = on
        runCatching { MansuiNative.espPierce(on) }
    }

    // ── 模组枪械五功能 (同梦喵偏移; 整体状态落文件桥) ──
    @Volatile var gunEnhOn = false; private set      // 原版枪械增强
    @Volatile var gunNoRecoilOn = false; private set
    @Volatile var gunFocusOn = false; private set
    @Volatile var gunReloadOn = false; private set
    @Volatile var gunAutoOn = false; private set

    fun setGunMod(feature: String, on: Boolean) {
        when (feature) {
            "enh" -> gunEnhOn = on
            "noRecoil" -> gunNoRecoilOn = on
            "focus" -> gunFocusOn = on
            "reload" -> gunReloadOn = on
            "auto" -> gunAutoOn = on
        }
        pushGunMods()
    }

    private fun pushGunMods() {
        val sb = StringBuilder()
        sb.append("enh=").append(if (gunEnhOn) 1 else 0).append("\n")
        sb.append("noRecoil=").append(if (gunNoRecoilOn) 1 else 0).append("\n")
        sb.append("focus=").append(if (gunFocusOn) 1 else 0).append("\n")
        sb.append("reload=").append(if (gunReloadOn) 1 else 0).append("\n")
        sb.append("auto=").append(if (gunAutoOn) 1 else 0).append("\n")
        runCatching { MansuiNative.espGunMods(sb.toString()) }
    }

    // ── 编辑器填充 (物品页; 同梦喵静态补丁 0x381264C, 值=填充物ID) ──
    @Volatile var edFillOn = false; private set
    @Volatile var edFillVal = 5            // 填充物ID (1~? 默认 5)

    fun setEdFill(on: Boolean, value: Int? = null) {
        if (value != null) edFillVal = value.coerceAtLeast(1)
        edFillOn = on
        pushEdFill()
    }

    fun pushEdFill() {
        val sb = StringBuilder()
        sb.append("on=").append(if (edFillOn) 1 else 0).append("\n")
        sb.append("value=").append(edFillVal).append("\n")
        runCatching { MansuiNative.espEdFill(sb.toString()) }
    }

    // ── 聊天气泡 (同梦喵 native; 槽位偏移本地常量, id 1~17 会话态) ──
    @Volatile var bubbleSel = 0; private set   // 0 = 未选择

    fun setBubble(id: Int) {
        bubbleSel = id.coerceIn(1, 17)
        runCatching { MansuiNative.espBubble(bubbleSel) }
    }

    // ── 属性区 native (移速/夜视/穿墙/踏空/跳远/模式/视角/头部/投掷; esp_attr.cfg) ──
    @Volatile var speedOn = false; private set
    @Volatile var speedVal = 5; private set              // 档位 1~10; 1 = 还原原速(停写)
    @Volatile var nightOn = false; private set
    @Volatile var wallOn = false; private set
    @Volatile var airOn = false; private set
    @Volatile var jumpOn = false; private set
    @Volatile var jumpVal = 15f; private set             // 2~50
    @Volatile var modeOn = false; private set
    @Volatile var modeVal = 5; private set               // 玩法5 / 编辑4
    @Volatile var viewOn = false; private set
    @Volatile var viewVal = 0; private set               // 0主 1背 2正
    @Volatile var headOn = false; private set
    @Volatile var headVal = 1; private set               // 1~50
    @Volatile var throwOn = false; private set
    @Volatile var digOn = false; private set             // 挖掘距离 (云 digCh4/digAdd)
    @Volatile var digVal = 5f; private set               // 5~100 (米), 默认 5
    @Volatile var handOn = false; private set            // 手持物品模型大小 (本地静态补丁)
    @Volatile var handVal = 1; private set               // 1~5
    @Volatile var bulletOn = false; private set          // 子弹模型大小 (本地静态补丁)
    @Volatile var bulletVal = 1; private set             // 1~50

    /** 移速: v==1 → 还原游戏原速度 (停写); v>=2 → 按档位直写并保持 */
    fun setSpeed(on: Boolean, v: Int? = null) {
        if (v != null) {
            speedVal = v.coerceIn(1, 10)
            speedOn = speedVal > 1
        } else {
            speedOn = on
        }
        pushAttr()
    }
    // 移速原值/校准日志见 espAttr (espInj 同文件 /sdcard/日志.log)
    fun setNight(on: Boolean) { nightOn = on; pushAttr() }
    fun setWall(on: Boolean) { wallOn = on; pushAttr() }
    fun setAir(on: Boolean) { airOn = on; pushAttr() }
    fun setJump(on: Boolean, v: Float? = null) { if (v != null) jumpVal = v.coerceIn(2f, 50f); jumpOn = on; pushAttr() }
    fun setMode(on: Boolean, v: Int? = null) { if (v != null) modeVal = if (v == 4) 4 else 5; modeOn = on; pushAttr() }
    fun setView(on: Boolean, v: Int? = null) { if (v != null) viewVal = v.coerceIn(0, 2); viewOn = on; pushAttr() }
    fun setHead(on: Boolean, v: Int? = null) { if (v != null) headVal = v.coerceIn(1, 50); headOn = on; pushAttr() }
    fun setThrow(on: Boolean) { throwOn = on; pushAttr() }
    /** 挖掘距离: float 5~100 (米), 关=还原默认 5 */
    fun setDig(on: Boolean, v: Float? = null) {
        if (v != null) digVal = v.coerceIn(5f, 100f)
        digOn = on
        pushAttr()
    }
    fun setHand(on: Boolean, v: Int? = null) { if (v != null) handVal = v.coerceIn(1, 5); handOn = on; pushAttr() }
    fun setBullet(on: Boolean, v: Int? = null) { if (v != null) bulletVal = v.coerceIn(1, 50); bulletOn = on; pushAttr() }

    fun pushAttr() {
        val sb = StringBuilder()
        sb.append("speed=").append(if (speedOn) speedVal else 0).append("\n")
        sb.append("night=").append(if (nightOn) 1 else 0).append("\n")
        sb.append("wall=").append(if (wallOn) 1 else 0).append("\n")
        sb.append("air=").append(if (airOn) 1 else 0).append("\n")
        sb.append("jump=").append(if (jumpOn) "%.0f".format(jumpVal) else "0").append("\n")
        sb.append("mode=").append(if (modeOn) modeVal else 0).append("\n")
        sb.append("view=").append(if (viewOn) 1 else 0).append("\n")
        sb.append("viewval=").append(viewVal).append("\n")
        sb.append("head=").append(if (headOn) 1 else 0).append("\n")
        sb.append("headval=").append(headVal).append("\n")
        sb.append("throw=").append(if (throwOn) 1 else 0).append("\n")
        sb.append("dig=").append(if (digOn) "%.0f".format(digVal) else "0").append("\n")
        sb.append("hand=").append(if (handOn) 1 else 0).append("\n")
        sb.append("handval=").append(handVal).append("\n")
        sb.append("bullet=").append(if (bulletOn) 1 else 0).append("\n")
        sb.append("bulletval=").append(bulletVal).append("\n")
        runCatching { MansuiNative.espAttr(sb.toString()) }
    }

    // ── 云服秒吃 (属性页; native hook 吃东西函数, 非 Lua 版) ──
    @Volatile var cloudEatOn = false; private set

    fun setCloudEat(on: Boolean) {
        cloudEatOn = on
        runCatching { MansuiNative.espEat(on) }
        if (on) reportEatStatus()
    }

    /** 读 native 写下的装钩结果 (files/esp_eat_status.txt) → 灵动岛回显失败原因 */
    private fun reportEatStatus() {
        val c = appCtx ?: return
        thread {
            Thread.sleep(1500)
            val txt = runCatching { File(c.filesDir, "esp_eat_status.txt").readText() }.getOrNull()
                ?: return@thread
            val code = Regex("""code=(\S+)""").find(txt)?.groupValues?.get(1) ?: "?"
            when (code) {
                "ok" -> com.svip.mansui.Island.tip(c, "云服秒吃", "已开启", 0)
                "ok-restub" -> com.svip.mansui.Island.tip(c, "云服秒吃", "已开启(接续)", 0)
                "ok-forced" -> com.svip.mansui.Island.tip(c, "云服秒吃", "已开启(按最新偏移)", 2)
                "nowxpage" -> com.svip.mansui.Island.tip(c, "云服秒吃", "失效: 需 ROOT 模式", 1)
                "noattach" -> com.svip.mansui.Island.tip(c, "云服秒吃", "失效: 未挂上游戏", 1)
                "readfail" -> com.svip.mansui.Island.tip(c, "云服秒吃", "失效: 读内存失败", 1)
                "off" -> Unit
                else -> com.svip.mansui.Island.tip(c, "云服秒吃", code, 1)
            }
        }
    }

    // ── 人物特效 (特效页; native 远端 shellcode, 目标=云 effectTargetChain) ──
    //   effectItems 表在 FloatWindow; 此处只负责下发 run/id/链
    @Volatile var effectSel = "horse_4662_3"; private set
    @Volatile private var effectChain: LongArray? = null

    /** 选择并播放一次特效 (链未就绪时只记选择, 不播放) */
    fun setEffect(id: String) {
        effectSel = id
        pushEffect(run = true)
    }

    private fun pushEffect(run: Boolean) {
        val sb = StringBuilder()
        sb.append("run=").append(if (run) 1 else 0).append("\n")
        sb.append("id=").append(effectSel).append("\n")
        effectChain?.let { c ->
            for (i in c.indices) {
                if (i >= 5) break
                sb.append("cl_e").append(i).append('=')
                    .append(java.lang.Long.toHexString(c[i])).append("\n")
            }
        }
        runCatching { MansuiNative.espEffect(sb.toString()) }
    }

    /** 云端链/末加下发 (esp_attr.cfg 的 cl_* 行): 属性区 native 的偏移全部来自云端,
     *  云端更新即生效; 缺键则对应功能不启用 (勿写死本地) */
    fun pushAttrCloud(cfg: Map<String, String>) {
        val root = cfg["skillRoot"]?.let(::parseChainArr) ?: return
        if (root.size < 3) return
        fun hx(k: String): Long = cfg[k]?.let(::hexTo) ?: 0L
        val sb = StringBuilder()
        fun push(k: String, v: Long) {
            if (v != 0L) sb.append("cl_").append(k).append('=').append(java.lang.Long.toHexString(v)).append('\n')
        }
        push("root", root[0]); push("l1", root[1]); push("l2", root[2])
        if (root.size >= 4) push("rootA", root[3])   // skillRoot 末加 (速度链: 3级解引用 + rootA + speedAdd)
        push("ent4", hx("entityCh4"))
        push("speedA", hx("speedAdd"))
        push("nightA", hx("nightVisAdd"))
        push("wallA", hx("wallAdd"))
        push("airA", hx("jumpAdd"))
        push("jumpA", hx("longJumpAdd"))
        push("modeOff", hx("modeOff"))
        push("modeAdd", hx("modeAdd"))
        push("popWin", hx("popWinOff"))
        push("popCh2", hx("popWinCh2"))
        push("cam1", hx("cam1Add"))
        push("cam2", hx("cam2Add"))
        push("headBase", hx("headSizeBase"))
        push("dig4", hx("digCh4"))    // 挖掘距离: 链第4级
        push("digA", hx("digAdd"))    // 挖掘距离: 末加
        // 挖掘距离完整链 (云 Excavatordistancehl): 前 N-1 级解引用 + 末元素相加, 优先于上面两个旧键
        cfg["Excavatordistancehl"]?.let(::parseChainArr)?.let { ch ->
            if (ch.size >= 2) {
                sb.append("cl_digChain=")
                    .append(ch.joinToString(",") { java.lang.Long.toHexString(it) })
                    .append('\n')
            }
        }
        if (sb.isNotEmpty()) runCatching { MansuiNative.espAttr(sb.toString()) }
        // 人物特效: 云 effectTargetChain (链4级+末加) → 特效页 native 播放目标
        cfg["effectTargetChain"]?.let(::parseChainArr)?.let { effectChain = it }
    }

    // 屏幕映射校准 (采集侧生效): off + scale × ndc, 默认恒等
    @Volatile private var calOX = 0f
    @Volatile private var calOY = 0f
    @Volatile private var calSX = 1f
    @Volatile private var calSY = 1f

    /** 开关切换: 状态镜像 + native 同步 (过滤队友走采集侧通道) */
    fun toggle(idx: Int, on: Boolean) {
        if (idx < 0 || idx >= flags.size) return
        flags[idx] = on
        runCatching { MansuiNative.setDrawFlag(idx, on) }
        if (idx == 9) MemBridge.setEspFilterTeam(on)
    }

    /** attach 成功 / ESP 总开关打开时调用; 幂等 + 自愈:
     *  每次调用都会检查是否已 attach 游戏, 未挂上则自动重试挂载(≤12s),
     *  成功后再装配数据源 — 不再依赖登录/直连路径是否先执行过 */
    fun ensure() {
        val c = appCtx
        if (c == null) {
//             log("ctx 未初始化")
            return
        }
        ensureFont(c)
        thread {
            try {
                val pkg = channelPkg()
                // 1) 未 attach → 自动重试挂载 (500ms 节拍 ≤12s; 锁定游戏进程要快)
                if (!MemBridge.isAttached()) {
                    var ok = false
                    repeat(24) {
                        ok = MemBridge.attach(pkg)
                        if (ok) return@repeat
                        Thread.sleep(500)
                    }
                    if (!ok) {
//                         log("attach 失败(游戏未运行?) pkg=$pkg, ESP 待命; 游戏启动后再开总开关即可")
                        started = false
                        return@thread
                    }
                }
                // 2) attach 成功: 复查投放 libDLL.so 到 离情梦/ (投放无条件, 加载由「注入DLL」开关控制)
                FuncBridge.deployDllAssets(c)
                // 3) 云配置缺失则先拉
                if (CloudConfig.lastConfig.isEmpty()) CloudConfig.fetch {}
                var waited = 0
                while (CloudConfig.lastConfig.isEmpty() && waited < 40) {
                    Thread.sleep(200)
                    waited++
                }
                val cfg = CloudConfig.lastConfig
                val world = cfg["worldChain"]?.let(::parseChainArr)
                    ?: throw IllegalStateException("云配置缺 worldChain")
                val mChain = cfg["matrixChain"]?.let(::parseChainArr)
                    ?: throw IllegalStateException("云配置缺 matrixChain")
                val self = cfg["oneselfChain"]?.let(::parseChainArr)
                    ?: throw IllegalStateException("云配置缺 oneselfChain")
                val module = cfg["moduleName"]
                    ?: throw IllegalStateException("云配置缺 moduleName")
                // 坐标/视角追踪: 视角坐标基址差 (云 sightBaseDelta, 缺省 0x12C)
                cfg["sightBaseDelta"]?.let(::hexTo)?.let { sightDelta = it }
                // 首页: 绕过检测模块名 + Lua 注入偏移 (云键, 缺省用当前版本常量)
                cfg["safeModuleName"]?.takeIf { it.isNotBlank() }?.let { safeMod = it }
                cfg["luaStateBssOff"]?.let(::hexTo)?.takeIf { it != 0L }?.let { luaSt = it }
                cfg["luaLoadstringOff"]?.let(::hexTo)?.takeIf { it != 0L }?.let { luaLs = it }
                cfg["luaPcallOff"]?.let(::hexTo)?.takeIf { it != 0L }?.let { luaPc = it }
                // 队伍过滤链 (梦喵 teamIdChain): 有则下发采集侧
                cfg["teamIdChain"]?.let(::parseChainArr)?.let { MemBridge.setEspTeamChain(it) }
                // 属性区 native 链/末加 (云端键 → daemon cl_* 行; 云更新即生效)
                pushAttrCloud(cfg)
                // 视角自瞄链 (云 cameraChain / firingChain): 有则下发并同步开关
                camChain = cfg["cameraChain"]?.let(::parseChainArr)
                firingChain = cfg["firingChain"]?.let(::parseChainArr)
                pushAim()
                MemBridge.setEspCfg(
                    module,
                    cfgLong(cfg, "bssOffset", 0xAA01000L),
                    cfgLong(cfg, "playerStride", 8L).toInt().coerceAtLeast(8),
                    world,
                    cfgLong(cfg, "matrixOff1", 0x9EB5F0L),
                    cfgLong(cfg, "matrixOff2", 0x9EB5F0L),
                    mChain,
                    self)
                val (w, h) = realScreen(c)
                // 屏幕映射校准 (云可下发 espOffX/espOffY/espScaleX/espScaleY; 默认恒等)
                calOX = cfg["espOffX"]?.toFloatOrNull() ?: 0f
                calOY = cfg["espOffY"]?.toFloatOrNull() ?: 0f
                calSX = cfg["espScaleX"]?.toFloatOrNull() ?: 1f
                calSY = cfg["espScaleY"]?.toFloatOrNull() ?: 1f
                if (MemBridge.isRootBound()) {
                    // ROOT: daemon 采集写文件桥; app 声明读文件
                    MansuiNative.espDataFile()
                    MemBridge.startEspData(w, h)
                } else {
                    // UID/框架: 本进程直读
                    MansuiNative.espDataLocal(w, h)
                }
                MemBridge.setEspCalib(calOX, calOY, calSX, calSY)
                // 状态同步: 用当前会话状态重写配置桥, 清除旧会话残留 (修复未开自瞄/子追
                // 却显示圈/追线: daemon 重启后一直沿用旧 cfg)
                pushAim()
                pushSub()
                pushCtrk()
                runCatching { MansuiNative.espGun(autoFireOn) }
                runCatching { MansuiNative.espPierce(pierceOn) }
                pushGunMods()
                pushEdFill()
                pushAttr()
                pushEffect(run = false)   // 特效链就绪下发 (不触发播放)
                runCatching { MansuiNative.espEat(cloudEatOn) }
                started = true
//                 log("数据源已装配 root=${MemBridge.isRootBound()} ${w}x${h} calib=${calOX},${calOY},${calSX},${calSY}")
            } catch (e: Exception) {
//                 log("ensure err ${e.message}")
                started = false
            }
        }
    }

    private fun channelPkg(): String {
        val idx = (SPUtils.getParam(appCtx, "channel_sel", 0) as Int)
            .coerceIn(0, CHANNEL_PACKAGES.size - 1)
        return CHANNEL_PACKAGES[idx]
    }

    /** ESP 渲染层每次打开时调用: 以"实际悬浮层尺寸"为准重推数据侧
     *  (纯自适应: 不同机型/横竖屏都按实际渲染窗口, 不写死分辨率) */
    fun onOverlay() {
        val c = appCtx ?: return
        val (w, h) = realScreen(c)
        syncOverlaySize(w, h)
    }

    /** 悬浮层实际尺寸 (surface 就绪/变化时回调) → 同步数据侧; 自适应核心 */
    fun syncOverlaySize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (MemBridge.isRootBound()) {
            MansuiNative.espDataFile()
            MemBridge.startEspData(w, h)
        } else {
            MansuiNative.espDataLocal(w, h)
        }
        MemBridge.setEspCalib(calOX, calOY, calSX, calSY)
    }

    // ── 云配置解析 (键值带 0x 前缀; 链 = 逗号分隔 hex) ──
    private fun hexTo(s: String): Long? {
        val t = s.trim()
        val h = if (t.startsWith("0x") || t.startsWith("0X")) t.substring(2) else t
        return h.toLongOrNull(16)
    }

    private fun cfgLong(m: Map<String, String>, k: String, def: Long): Long =
        hexTo(m[k] ?: "") ?: def

    private fun parseChainArr(text: String): LongArray? {
        val parts = text.split(",").mapNotNull { hexTo(it) }
        return if (parts.isEmpty()) null else parts.toLongArray()
    }

    // ── 屏幕尺寸 (与梦喵 StartDataServer 同: 真机全屏, 渲染层 MATCH_PARENT) ──
    private fun realScreen(c: Context): Pair<Int, Int> {
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val p = Point()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealSize(p)
        if (p.x <= 0 || p.y <= 0) {
            p.x = c.resources.displayMetrics.widthPixels
            p.y = c.resources.displayMetrics.heightPixels
        }
        return p.x to p.y
    }

    // ── 字体: 首次复制 assets/heiti.ttf 到 files/esp/ 供 native fopen ──
    /** 外部(灵动岛/悬浮层)在创建渲染窗前先准备字体 */
    fun prepare(context: Context) {
        ensureFont(context)
    }

    private fun ensureFont(c: Context) {
        if (fontReady) return
        try {
            val dir = File(c.filesDir, "esp").apply { mkdirs() }
            val out = File(dir, "heiti.ttf")
            if (!out.exists() || out.length() == 0L) {
                c.assets.open("heiti.ttf").use { inp ->
                    FileOutputStream(out).use { o -> inp.copyTo(o) }
                }
            }
            MansuiNative.setFontPath(out.absolutePath)
            fontReady = true
        } catch (e: Throwable) {
        }
    }
}
