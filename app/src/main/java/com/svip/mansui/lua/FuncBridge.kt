package com.svip.mansui.lua

import android.content.Context
import com.svip.mansui.CHANNEL_PACKAGES
import com.svip.mansui.IPCurbco.TClass.SPUtils
import java.io.File

// ═══════════════════════════════════════════════
//  注入引擎 Lua 功能命令桥 — 与 mansui.lua watchFileLoop 对接 (梦喵 LuaFunctionBridge 同款)
//  写入 /storage/emulated/0/离情梦/{功能,参数文件}; Lua 读到即删
//  功能号: 21/121 传送光环  24/124 自动换弹  25/125 无限弹药
//          26/126 指定附身  27/127 自动切刀 (开=X 关=X+100)
// ═══════════════════════════════════════════════
object FuncBridge {

    private const val DIR = "/storage/emulated/0/离情梦"
    private const val FUNC_FILE = "$DIR/功能"
    private const val POSSESS_FILE = "$DIR/附身迷你号"
    private const val CUTTING_DELAY_FILE = "$DIR/切刀延迟"

    private const val ATTACK_FILE = "$DIR/自动攻击"   // libDLL 攻击开关 (0/1)
    private const val ATTACK_SPEED_FILE = "$DIR/自动攻击速度"  // libDLL: 秒/次 (%.2f)
    private const val ATTACK_RANGE_FILE = "$DIR/自动攻击范围"  // libDLL: 米×100 (int)
    private const val ATK_BK_CFG = "/data/data/com.svip.mansui/files/esp_atkbk.cfg"  // 备用攻击 native cfg

    // ── 大厅背景 (移植梦之喵羽 PickLobbyBgActivity) ──
    private const val LOBBY_BG_FILE = "$DIR/大厅背景.png"        // 选图 4:3 裁剪后落这里
    private const val HIDE_LOBBY_CHAR_FILE = "$DIR/隐藏大厅人物"  // 1=隐藏 0=不隐藏 (Lua 实时读)
    private const val SELECT_SOUND_FILE = "$DIR/音效选择.txt"      // path + 音量 (发包音效 193)

    // 会话内开关状态 (悬浮窗重建后恢复)
    @Volatile var autoAttackOn = false; private set

    // ── 主用/备用 (梦喵 备用页移植: 主用=libDLL 注入执行, 备用=远程调用不依赖注入) ──
    @Volatile var autoAttackMode = 0; private set          // 0=主用 1=备用
    @Volatile var autoAttackBkSpeed = 2.0f; private set    // 备用: 秒/次 1.0~2.0
    @Volatile var autoAttackBkRangeM = 5f; private set     // 备用: 米 1~5

    private fun writeAttackMain(on: Boolean) {
        try {
            ensureDir()
            File(ATTACK_FILE).writeText(if (on) "1" else "0")
            if (on) {
                // 打开时确保速度/范围参数文件存在 (libDLL 可能仅在注入/开启时读一次)
                File(ATTACK_SPEED_FILE).writeText(
                    String.format(java.util.Locale.US, "%.2f", attackSpeed))
                File(ATTACK_RANGE_FILE).writeText(((attackRangeM * 100).toInt()).toString())
            }
        } catch (_: Exception) {}
    }

    private fun writeAttackBk(on: Boolean) {
        try {
            File(ATK_BK_CFG).parentFile?.mkdirs()
            File(ATK_BK_CFG).writeText(
                "on=${if (on) 1 else 0}\n" +
                    "value=${(autoAttackBkSpeed * 1000).toInt()}\n" +
                    "range=${(autoAttackBkRangeM * 100).toInt()}\n")
        } catch (_: Exception) {}
    }

    private fun applyAutoAttack() {
        if (autoAttackMode == 0) writeAttackMain(autoAttackOn) else writeAttackBk(autoAttackOn)
    }

    /** 自动攻击: 按当前模式写主用文件 / 备用 cfg */
    fun setAutoAttack(on: Boolean) {
        autoAttackOn = on
        applyAutoAttack()
    }

    /** 切主用/备用: 先停当前模式的攻击, 开关状态带到新模式 (梦喵同款) */
    fun setAutoAttackMode(m: Int) {
        val nm = if (m == 1) 1 else 0
        if (nm == autoAttackMode) return
        if (autoAttackMode == 0) writeAttackMain(false) else writeAttackBk(false)
        autoAttackMode = nm
        applyAutoAttack()
    }

    /** 备用攻击速度 (秒/次, 1.0~2.0 → cfg value=ms, 循环内实时生效) */
    fun setAutoAttackBkSpeed(v: Float) {
        autoAttackBkSpeed = v.coerceIn(1.0f, 2.0f)
        writeAttackBk(autoAttackOn)
    }

    /** 备用攻击距离 (米, 1~5 → cfg range=×100 整数, 循环内实时生效) */
    fun setAutoAttackBkRange(m: Float) {
        autoAttackBkRangeM = m.coerceIn(1f, 5f)
        writeAttackBk(autoAttackOn)
    }

    // 速度/范围初值从文件读回 (悬浮窗重建后显示实际生效值); 默认同梦喵 0.3 秒/次 / 5 米
    @Volatile var attackSpeed: Float = run {
        runCatching { File(ATTACK_SPEED_FILE).readText().trim().toFloat() }.getOrDefault(0.3f)
    }
    @Volatile var attackRangeM: Float = run {
        val r = runCatching { File(ATTACK_RANGE_FILE).readText().trim().toInt() }
            .getOrDefault(500) / 100f
        r.coerceIn(1f, 5f)
    }

    /** 自动攻击速度 (秒/次, 0.01~1.5 最快 0.01, 默认 0.3; %.2f 格式同梦喵) */
    fun setAutoAttackSpeed(v: Float) {
        attackSpeed = v.coerceIn(0.01f, 1.5f)
        try {
            ensureDir()
            File(ATTACK_SPEED_FILE).writeText(
                String.format(java.util.Locale.US, "%.2f", attackSpeed))
        } catch (_: Exception) {}
    }

    /** 自动攻击范围 (米, 1~5 → 文件写 ×100 整数, 同梦喵) */
    fun setAutoAttackRange(m: Float) {
        attackRangeM = m.coerceIn(1f, 5f)
        try {
            ensureDir()
            File(ATTACK_RANGE_FILE).writeText(((attackRangeM * 100).toInt()).toString())
        } catch (_: Exception) {}
    }
    @Volatile var teleAuraOn = false; private set
    @Volatile var autoReloadOn = false; private set
    @Volatile var unlimitedAmmoOn = false; private set
    @Volatile var possessOn = false; private set
    @Volatile var cuttingOn = false; private set

    // 参数缓存 (输入框初值)
    @Volatile var possessUin = ""
        private set
    @Volatile var cuttingDelayMs = "60"
        private set

    private fun ensureDir() {
        try { File(DIR).mkdirs() } catch (_: Exception) {}
    }

    /** 发送纯数字 funcId 指令 (同梦喵 sendCommand) */
    fun sendCommand(funcId: Int) {
        // 纯 UI 版本：Lua 命令通道已删除，不写任何文件
    }

    // ── 大厅背景 (Lua 命令 184 = applyLobbyBg; 同梦喵协议) ──
    // 注: 该功能由「注入引擎」里的 Lua 脚本执行, libDLL.so 不参与。
    // 脚本若把路径写死成 梦之喵羽, 这里同时镜像一份过去, 两条路都能命中。
    private const val DIR_ALT = "/storage/emulated/0/梦之喵羽"
    private const val LOBBY_BG_FILE_ALT = "$DIR_ALT/大厅背景.png"
    private const val HIDE_LOBBY_CHAR_FILE_ALT = "$DIR_ALT/隐藏大厅人物"

    fun lobbyBgPath(): String = LOBBY_BG_FILE

    fun lobbyBgReady(): Boolean = try {
        val f = File(LOBBY_BG_FILE)
        f.exists() && f.length() > 0
    } catch (_: Exception) {
        false
    }

    /** 应用 离情梦/大厅背景.png (Lua 读文件后替换大厅背景图); 同时镜像到 梦之喵羽 目录兜底 */
    fun applyLobbyBg() {
        try {
            ensureDir()
            File(DIR_ALT).mkdirs()
            val src = File(LOBBY_BG_FILE)
            if (src.exists() && src.length() > 0) {
                src.copyTo(File(LOBBY_BG_FILE_ALT), overwrite = true)
            }
            val hf = File(HIDE_LOBBY_CHAR_FILE)
            if (hf.exists()) hf.copyTo(File(HIDE_LOBBY_CHAR_FILE_ALT), overwrite = true)
        } catch (_: Exception) {
        }
        sendCommand(184)
    }

    /** 隐藏大厅人物: 写文件让 Lua 实时读, 再触发一次 184 重新应用 */
    fun setHideLobbyChar(on: Boolean) {
        try {
            ensureDir()
            File(HIDE_LOBBY_CHAR_FILE).writeText(if (on) "1" else "0")
            File(DIR_ALT).mkdirs()
            File(HIDE_LOBBY_CHAR_FILE_ALT).writeText(if (on) "1" else "0")
        } catch (_: Exception) {
        }
        sendCommand(184)
    }

    fun hideLobbyCharOn(): Boolean = try {
        File(HIDE_LOBBY_CHAR_FILE).readText().trim() == "1"
    } catch (_: Exception) {
        false
    }

    private fun sendOnOff(nowOn: Boolean, base: Int, set: (Boolean) -> Unit) {
        set(nowOn)
        sendCommand(if (nowOn) base else base + 100)
    }

    fun setTeleAura(on: Boolean) = sendOnOff(on, 21) { teleAuraOn = it }
    fun setAutoReload(on: Boolean) = sendOnOff(on, 24) { autoReloadOn = it }
    fun setUnlimitedAmmo(on: Boolean) = sendOnOff(on, 25) { unlimitedAmmoOn = it }

    // ── 游商 / 全员提示 (精简游商提示.lua 移植) ──
    // 186 游商0元购  187 游商库存  188/288 循环游商开关
    // 189/289 循环全员提示  190 单次全员提示
    @Volatile var loopTraderOn = false; private set
    @Volatile var allTipsOn = false; private set

    fun buyTravelingTrader() = sendCommand(186)
    fun traderStock() = sendCommand(187)

    fun setLoopTrader(on: Boolean) {
        loopTraderOn = on
        sendCommand(if (on) 188 else 288)
    }

    fun setAllTips(on: Boolean) {
        allTipsOn = on
        sendCommand(if (on) 189 else 289)
    }

    fun sendOneTip() = sendCommand(190)

    /** 解锁全部动作: 覆盖 StoreActionMgr.GetActionIsOwned 恒返回 1 (192) */
    fun unlockAllActions() = sendCommand(192)

    /** 发包音效: 写 音效选择.txt(path + 音量) 再发 193 */
    fun sendSound(path: String) {
        try {
            File(SELECT_SOUND_FILE).writeText(path + "\n100")
        } catch (_: Exception) {
        }
        sendCommand(193)
    }


    /** 指定附身: 先写迷你号再发 26 (梦喵同序) */
    fun setPossess(on: Boolean) {
        sendOnOff(on, 26) { possessOn = it }
    }

    fun setPossessUin(uin: String) {
        possessUin = uin.trim()
        try {
            ensureDir()
            File(POSSESS_FILE).writeText(possessUin)
        } catch (_: Exception) {}
        // 已开启时改目标 → 重启附身循环
        if (possessOn) {
            sendCommand(126)
            sendCommand(26)
        }
    }

    fun setCutting(on: Boolean) = sendOnOff(on, 27) { cuttingOn = it }

    /** 切刀延迟 (ms), 循环内实时读取, 无需重启 */
    fun setCuttingDelay(ms: String) {
        cuttingDelayMs = ms.trim()
        try {
            ensureDir()
            File(CUTTING_DELAY_FILE).writeText(cuttingDelayMs)
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════
    //  娱乐页 Lua 功能 (梦喵娱乐五功能移植; mansui.lua v0.2)
    //  16 解锁全喷漆  20/120 自动发言  37/137 绿色发言
    //  44 临时改名  78 追踪房间 (仅大厅生效)
    // ═══════════════════════════════════════════════
    private const val SPEAK_FILE = "$DIR/发言文字"
    private const val RENAME_FILE = "$DIR/改名"
    private const val FOLLOW_FILE = "$DIR/追踪迷你号"

    @Volatile var autoSpeakOn = false; private set
    @Volatile var greenChatOn = false; private set
    @Volatile var speakText = ""; private set
    @Volatile var renameText = ""; private set
    @Volatile var followUin = ""; private set

    /** 解锁全喷漆 (16, 一次性按钮) */
    fun unlockSprays() = sendCommand(16)

    /** 自动发言 (20/120): 开 = 写内容文件 + 下发 20 启动; 关 = 120 */
    fun setAutoSpeak(on: Boolean, text: String) {
        speakText = text.trim()
        autoSpeakOn = on
        if (on) {
            try {
                ensureDir()
                File(SPEAK_FILE).writeText(speakText)
            } catch (_: Exception) {}
            sendCommand(20)
        } else {
            sendCommand(120)
        }
    }

    /** 齿轮里改内容: 开着时重发 20 (Lua 侧重启循环用新内容); 关着时只存内容 */
    fun updateSpeakText(text: String) {
        speakText = text.trim()
        if (autoSpeakOn) sendCommand(20)
        else {
            try { File(SPEAK_FILE).writeText(speakText) } catch (_: Exception) {}
        }
    }

    /** 绿色发言 (37/137) */
    fun setGreenChat(on: Boolean) = sendOnOff(on, 37) { greenChatOn = it }

    /** 临时改名 (44): 写 改名 文件后触发 */
    fun renameTo(name: String) {
        renameText = name.trim()
        if (renameText.isEmpty()) return
        try {
            ensureDir()
            File(RENAME_FILE).writeText(renameText)
        } catch (_: Exception) {}
        sendCommand(44)
    }

    /** 追踪房间 (78): 写 追踪迷你号 后触发 (Lua 侧仅大厅处理) */
    fun followRoom(uin: String) {
        followUin = uin.trim()
        if (followUin.isEmpty()) return
        try {
            ensureDir()
            File(FOLLOW_FILE).writeText(followUin)
        } catch (_: Exception) {}
        sendCommand(78)
    }

    // ═══════════════════════════════════════════════
    //  物品页 Lua (mansui.lua v0.3)
    //  0 快捷炸图装备  1 自定义取物  6 丢弃物品
    //  12 面板取物  13 强制开启编辑器
    //  182 云服取物(自定义物品ID)  185 刷取手持物品(现读手持)
    // ═══════════════════════════════════════════════
    private const val ITEM_FILE = "$DIR/取物ID"

    @Volatile var itemId = "15056"; private set
    @Volatile var itemNum = "64"; private set

    fun setItemId(v: String) { itemId = v.trim() }
    fun setItemNum(v: String) { itemNum = v.trim() }

    /** 自定义取物 (1): 写 取物ID "id,num" 后触发 */
    fun takeItem(id: String, num: String) {
        val i = id.toIntOrNull() ?: return
        val n = num.toIntOrNull() ?: return
        try {
            ensureDir()
            File(ITEM_FILE).writeText("$i,$n")
        } catch (_: Exception) {}
        sendCommand(1)
    }

    /** 快捷炸图装备 (0) */
    fun takeBlastKit() = sendCommand(0)
    /** 面板取物 (12): 打开编辑器方块资源池, 点选即取 (Lua 侧走 EQUIP_OPERATE) */
    fun panelTake() = sendCommand(12)

    // ── 云服取物 (182): 自定义物品ID (数量固定 1, 同梦之喵羽原版; value 不是数量) ──
    private const val CLOUD_ITEM_FILE = "$DIR/云服取物ID"

    @Volatile var cloudItemId: String = runCatching {
        File(CLOUD_ITEM_FILE).readText().trim()
    }.getOrDefault(""); private set

    private fun writeCloudTake() {
        try {
            ensureDir()
            File(CLOUD_ITEM_FILE).writeText(cloudItemId)
        } catch (_: Exception) {}
    }

    fun setCloudTake(id: String) {
        cloudItemId = id.trim()
        writeCloudTake()
    }

    /** 云服取物 (182): 先把 物品ID 落盘再触发 */
    fun cloudTake() {
        writeCloudTake()
        sendCommand(182)
    }

    /** 刷取手持物品 (185): 物品ID 由 Lua 现读 CurMainPlayer:getCurToolID(), 面板不传参 */
    fun spawnHeldItem() = sendCommand(185)

    /** 丢弃物品 (6) */
    fun discardItems() = sendCommand(6)
    /** 强制开启编辑器 (13) */
    fun forceEditor() = sendCommand(13)

    // ═══════════════════════════════════════════════
    //  特效页 Lua (mansui.lua v0.4)
    //  8 进房播报  39/139 转圈  40/140 音符  45/145 播放烟花
    //  51/63 天空盒  64 送礼播报  91 定制装扮
    // ═══════════════════════════════════════════════
    private const val BROADCAST_ID_FILE = "$DIR/进房播报ID"
    private const val BROADCAST_UIN_FILE = "$DIR/进房播报迷你号"
    private const val SKYBOX_FILE = "$DIR/天空盒ID"
    private const val FIREWORK_FILE = "$DIR/烟花ID"
    private const val OUTFIT_FILE = "$DIR/定制装扮"

    @Volatile var broadcastUin = ""; private set
    @Volatile var fireworkId = "13"; private set
    @Volatile var fireworkRandom = false; private set
    @Volatile var skyboxSel = 0; private set   // 召唤天空盒 选中索引 (会话)
    @Volatile var circleOn = false; private set
    @Volatile var noteFxOn = false; private set
    @Volatile var fireworkOn = false; private set

    fun setSkyboxSel(i: Int) { skyboxSel = i.coerceIn(0, 5) }

    /** 进房播报 (8): 播报ID=8; 迷你号留空 = 自己 (无梦喵作者) */
    fun sendBroadcast(uin: String) {
        broadcastUin = uin.trim()
        try {
            ensureDir()
            File(BROADCAST_ID_FILE).writeText("8")
            File(BROADCAST_UIN_FILE).writeText(broadcastUin)
        } catch (_: Exception) {}
        sendCommand(8)
    }

    /** 召唤天空盒 (51) */
    fun sendSkybox(cfg: String) {
        try {
            ensureDir()
            File(SKYBOX_FILE).writeText(cfg)
        } catch (_: Exception) {}
        sendCommand(51)
    }

    /** 移除天空盒 (63) */
    fun removeSkybox() = sendCommand(63)

    /** 送礼播报 (64) */
    fun giftBroadcast() = sendCommand(64)

    /** 转圈动作 (39/139) */
    fun setCircle(on: Boolean) = sendOnOff(on, 39) { circleOn = it }

    /** 音符特效 (40/140) */
    fun setNoteFx(on: Boolean) = sendOnOff(on, 40) { noteFxOn = it }

    /** 播放烟花 (45/145); 开着时更新 ID/随机 = 重发 45 以最新配置重启 */
    fun setFirework(on: Boolean) {
        fireworkOn = on
        sendCommand(if (on) 45 else 145)
    }

    fun setFireworkId(v: String) {
        fireworkId = v.trim()
        fireworkRandom = false
        try {
            ensureDir()
            File(FIREWORK_FILE).writeText(fireworkId.ifBlank { "13" })
        } catch (_: Exception) {}
        if (fireworkOn) sendCommand(45)
    }

    fun setFireworkRandom(on: Boolean) {
        fireworkRandom = on
        try {
            ensureDir()
            File(FIREWORK_FILE).writeText(if (on) "random" else fireworkId.ifBlank { "13" })
        } catch (_: Exception) {}
        if (fireworkOn) sendCommand(45)
    }

    /** 定制装扮 (91): 写 "xorID 名称" 后触发 (uuxx.lua 内 ID 为 XOR 90 密文) */
    fun sendOutfit(xorId: String, name: String) {
        try {
            ensureDir()
            File(OUTFIT_FILE).writeText("$xorId $name")
        } catch (_: Exception) {}
        sendCommand(91)
    }

    // ═══════════════════════════════════════════════
    //  基础联机 Lua (mansui.lua v0.5)
    //  22/122 面条回血  23/123 自动重生  29/129 花环无敌
    //  19/119 飞行模式  49/149 自动搭路  59/159 屏蔽死亡
    // ═══════════════════════════════════════════════
    private const val BRIDGE_BLOCK_FILE = "$DIR/搭路方块ID"
    private const val BRIDGE_SLOT_FILE = "$DIR/搭路触发物品栏"

    @Volatile var noodleOn = false; private set
    @Volatile var reviveOn = false; private set
    @Volatile var flowerOn = false; private set
    @Volatile var flyOn = false; private set
    @Volatile var bridgeOn = false; private set
    @Volatile var deathBlockOn = false; private set
    @Volatile var bridgeBlockId = "600"; private set

    fun setNoodleHeal(on: Boolean) = sendOnOff(on, 22) { noodleOn = it }
    fun setAutoRevive(on: Boolean) = sendOnOff(on, 23) { reviveOn = it }
    fun setFlower(on: Boolean) = sendOnOff(on, 29) { flowerOn = it }
    fun setFly(on: Boolean) = sendOnOff(on, 19) { flyOn = it }

    /** 屏蔽死亡 (59/159): Lua hook DeathFrame */
    fun setDeathBlock(on: Boolean) = sendOnOff(on, 59) { deathBlockOn = it }

    /** 自动搭路 (49/149): 触发栏位固定 1(第2格, 游戏0起始), 开启时覆盖旧栏位文件 */
    fun setBridge(on: Boolean) {
        bridgeOn = on
        if (on) {
            try {
                ensureDir()
                File(BRIDGE_SLOT_FILE).writeText("1")
            } catch (_: Exception) {}
        }
        sendCommand(if (on) 49 else 149)
    }

    // ═══════════════════════════════════════════════
    //  其他页 (mansui.lua v0.6): 11/15 地图UI选择  17 回重生点  72 联机踢人
    // ═══════════════════════════════════════════════
    private const val KICK_FILE = "$DIR/踢人迷你号"

    @Volatile var mapUiOn = false; private set
    @Volatile var kickUin = ""; private set

    /** 地图UI选择 (11/15) */
    fun setMapUi(on: Boolean) {
        mapUiOn = on
        sendCommand(if (on) 11 else 15)
    }

    /** 回重生点 (17) */
    fun teleportHome() = sendCommand(17)

    /** 联机踢人 (72): 写 踢人迷你号 后触发 */
    fun kickPlayer(uin: String) {
        kickUin = uin.trim()
        if (kickUin.isEmpty()) return
        try {
            ensureDir()
            File(KICK_FILE).writeText(kickUin)
        } catch (_: Exception) {}
        sendCommand(72)
    }

    /** 方块ID(物品ID) 自定义 (齿轮输入; 开着时重发 49 以新 ID 重启循环) */
    fun setBridgeBlockId(v: String) {
        bridgeBlockId = v.trim().ifBlank { "600" }
        try {
            ensureDir()
            File(BRIDGE_BLOCK_FILE).writeText(bridgeBlockId)
        } catch (_: Exception) {}
        if (bridgeOn) sendCommand(49)   // 重启循环用新参数
    }

    // ═══════════════════════════════════════════════
    //  云服刷物 / 解锁道具 (mansui.lua v0.8; config.lua 搬运)
    //  14 添加琵琶  66 打开工匠台  2 临时会员  3 商城免广告
    //  4 皮肤获取解锁  9 武器皮肤 (参数文件 皮肤道具ID)
    // ═══════════════════════════════════════════════
    private const val SKIN_ID_FILE = "$DIR/皮肤道具ID"

    /** 武器皮肤 ID 会话态 (悬浮窗重建后回显; 默认 194 浪花长枪, 同梦喵) */
    @Volatile var weaponSkinId = "194"; private set

    /** 添加琵琶 (14, 一次性按钮) */
    fun addPipa() = sendCommand(14)

    /** 打开工匠台 (66, 一次性按钮) */
    fun openToolbox() = sendCommand(66)

    /** 临时获取会员 (2, 一次性按钮) */
    fun takeMembership() = sendCommand(2)

    /** 商城免广告 (3, 一次性按钮, 开后在游戏进程内常驻) */
    fun enableAdFree() = sendCommand(3)

    /** 皮肤获取解锁 (4, 一次性按钮) */
    fun unlockRoleSkins() = sendCommand(4)

    /** 武器皮肤 (9): 先写 皮肤道具ID 再触发, 同梦喵同序 */
    fun setWeaponSkin(skinId: String) {
        weaponSkinId = skinId.trim().ifBlank { "194" }
        try {
            ensureDir()
            File(SKIN_ID_FILE).writeText(weaponSkinId)
        } catch (_: Exception) {}
        sendCommand(9)
    }

    /** 人物爬行 (36/136) */
    @Volatile var crawlOn = false; private set
    fun setCrawl(on: Boolean) = sendOnOff(on, 36) { crawlOn = it }

    // ── 秒吃食物 (30/130): Lua 版; native 版=云服秒吃 ──
    private const val EAT_SPEED_FILE = "$DIR/吃食物速度"

    @Volatile var autoEatOn = false; private set
    @Volatile var eatSpeed = "0.1"; private set

    /** 秒吃食物 (30/130): 开 = 写间隔 + 下发 30; 关 = 130 */
    fun setAutoEat(on: Boolean) {
        autoEatOn = on
        if (on) {
            try {
                ensureDir()
                File(EAT_SPEED_FILE).writeText(eatSpeed)
            } catch (_: Exception) {}
            sendCommand(30)
        } else {
            sendCommand(130)
        }
    }

    /** 间隔(秒); 开着时重发 30 以新间隔重启循环 (循环内也实时读文件) */
    fun setEatSpeed(v: String) {
        eatSpeed = v.trim().ifBlank { "0.1" }
        try {
            ensureDir()
            File(EAT_SPEED_FILE).writeText(eatSpeed)
        } catch (_: Exception) {}
        if (autoEatOn) sendCommand(30)
    }

    /** 幻影旋转 (47/147) */
    @Volatile var phantomOn = false; private set
    fun setPhantomSpin(on: Boolean) = sendOnOff(on, 47) { phantomOn = it }

    // ═══════════════════════════════════════════════
    //  发包攻击 (梦喵同款: 三个控制文件由 libDLL 读)
    //  新版自动攻击 (0/1) / 新版自动攻击速度 (%.3f 秒/次) / 目标 (锁定迷你号, 0=自动打血量最低)
    // ═══════════════════════════════════════════════
    private const val PACKET_FILE = "$DIR/新版自动攻击"
    private const val PACKET_SPEED_FILE = "$DIR/新版自动攻击速度"
    private const val PACKET_TARGET_FILE = "$DIR/目标"

    @Volatile var packetAttackOn = false; private set

    /** 速度 (秒/次, 0.001~1, 默认 0.1); 初值从文件读回 (悬浮窗重建后显示实际生效值) */
    @Volatile var packetSpeed: Float = run {
        runCatching { File(PACKET_SPEED_FILE).readText().trim().toFloat() }
            .getOrDefault(0.1f).coerceIn(0.001f, 1f)
    }

    /** 锁定迷你号 (空 = 自动打血量最低) */
    @Volatile var packetTarget: String = run {
        val v = runCatching { File(PACKET_TARGET_FILE).readText().trim() }.getOrDefault("")
        if (v.toLongOrNull()?.let { it > 0 } == true) v else ""
    }

    /** 写三个控制文件 (同梦喵: 开关 1/0, 速度 %.3f, 目标 = 正整数或 0) */
    private fun applyPacket() {
        try {
            ensureDir()
            File(PACKET_FILE).writeText(if (packetAttackOn) "1" else "0")
            File(PACKET_SPEED_FILE).writeText(
                String.format(java.util.Locale.US, "%.3f", packetSpeed))
            File(PACKET_TARGET_FILE).writeText(
                packetTarget.trim().toLongOrNull()?.takeIf { it > 0 }?.toString() ?: "0")
        } catch (_: Exception) {}
    }

    fun setPacketAttack(on: Boolean) {
        packetAttackOn = on
        applyPacket()
    }

    /** 发包速度 (秒/次, 0.001~1) */
    fun setPacketAttackSpeed(v: Float) {
        packetSpeed = v.coerceIn(0.001f, 1f)
        applyPacket()
    }

    /** 锁定迷你号 (空/0/非数字 = 自动打血量最低) */
    fun setPacketAttackTarget(s: String) {
        packetTarget = s.trim()
        if (packetTarget.toLongOrNull()?.let { it > 0 } != true) packetTarget = ""
        applyPacket()
    }

    /** 启动清场 (同梦喵): 发包开关/自动攻击置 0, 速度/目标回默认 — 防上次会话残留 */
    fun resetAttackFiles() {
        try {
            ensureDir()
            File(PACKET_FILE).writeText("0")
            File(PACKET_SPEED_FILE).delete()
            File(PACKET_TARGET_FILE).delete()
            File(ATTACK_FILE).writeText("0")
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════
    //  注入DLL (梦之喵羽 LuaFunctionBridge 移植)
    //  流程: assets/libDLL.so → files → /storage/emulated/0/离情梦/libDLL.so
    //        → 游戏内 mansui.lua loadAutoAttackLib 再拷进游戏私有目录 dlopen
    // ═══════════════════════════════════════════════
    private const val CHANNEL_FILE = "$DIR/渠道"
    private const val DLL_SWITCH_FILE = "$DIR/注入DLL"
    private const val DLL_FILE = "$DIR/libDLL.so"

    /** 注入DLL 开关状态 (会话内; 每次启动重置为关, 同梦喵) */
    @Volatile var dllInjectOn = false; private set

    @Volatile private var dllSrcPrepared = false   // assets 解包 (每进程一次)
    @Volatile private var dllSwitchGen = 0         // 投放代次: 防已关开关后异步线程仍写 1

    /** 当前渠道编号 (1 起; 顺序同 app 渠道列表, 游戏侧 mansui.lua DLL.pkgs 同序) */
    private fun channelNum(context: Context): Int =
        ((SPUtils.getParam(context, "channel_sel", 0) as? Int) ?: 0)
            .coerceIn(0, CHANNEL_PACKAGES.size - 1) + 1

    /** 部署 libDLL.so 到 /storage/emulated/0/离情梦/ (游戏内 Lua 从该路径读取后 dlopen) */
    fun deployDllAssets(context: Context) {
        val ctx = context.applicationContext
        Thread { deployDllAssetsSync(ctx) }.start()
    }

    private fun deployDllAssetsSync(context: Context) {
        try {
            ensureDir()
            // 渠道编号写盘: 游戏内 Lua 按它换包名定位游戏私有目录
            File(CHANNEL_FILE).writeText(channelNum(context).toString())
            deployDllToSdcard(context)
        } catch (_: Exception) {}
    }

    /** 用户开启注入DLL: 先投放 .so, 完成后才写开关文件 1 — 保证 Lua 读到 1 时 .so 已就位 */
    fun enableDllInject(context: Context) {
        dllInjectOn = true
        val ctx = context.applicationContext
        val gen = ++dllSwitchGen
        Thread {
            deployDllAssetsSync(ctx)
            if (gen != dllSwitchGen) return@Thread
            try {
                ensureDir()
                File(DLL_SWITCH_FILE).writeText("1")
            } catch (_: Exception) {}
        }.start()
    }

    /** 关=写 0 (游戏侧 watchFileLoop 读到 0 复位注入状态) */
    fun sendDllInject(on: Boolean) {
        dllInjectOn = on
        dllSwitchGen++
        try {
            ensureDir()
            File(DLL_SWITCH_FILE).writeText(if (on) "1" else "0")
        } catch (_: Exception) {}
    }

    /** assets/libDLL.so → files (进程内一次) → 离情梦/libDLL.so (同长度跳过) */
    fun deployDllToSdcard(context: Context): Boolean {        return try {
            ensureDir()
            val src = File(context.filesDir, "libDLL.so")
            if (!dllSrcPrepared || !src.exists()) {
                context.assets.open("libDLL.so").use { input ->
                    src.outputStream().use { out -> input.copyTo(out) }
                }
                dllSrcPrepared = true
            }
            val dst = File(DLL_FILE)
            if (dst.exists() && dst.length() == src.length()) return true
            src.copyTo(dst, overwrite = true)
            dst.length() == src.length()
        } catch (_: Exception) {
            false
        }
    }

    // ── 全员提示 ID 表 (assets/提示ID.txt → 离情梦/提示ID.txt, Lua 读本地) ──
    private const val TIPS_ID_FILE = "$DIR/提示ID.txt"

    /** 投放提示ID表 (幂等: 目标已存在就不覆盖, 方便用户自己改) */
    fun deployTipsIds(context: Context) {
        Thread {
            try {
                ensureDir()
                val dst = File(TIPS_ID_FILE)
                if (dst.exists() && dst.length() > 0) return@Thread
                val src = File(context.filesDir, "提示ID.txt")
                if (!src.exists() || src.length() == 0L) {
                    context.assets.open("提示ID.txt").use { input ->
                        src.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                src.copyTo(dst, overwrite = true)
            } catch (_: Exception) {
            }
        }.start()
    }
}
