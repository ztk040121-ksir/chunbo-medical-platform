# 春播万象智慧医疗 - 移动安卓客户端 (Chunbo Medical Android)

## 一、项目概述
本项目是**春播万象智慧医疗与综合运营中台**的配套原生安卓端应用（Android App），专为移动就诊、掌上医生接诊工作台、社区医疗耗材商城及 AI 临床 CDSS 决策助手量身定制。

本工程严格遵照开发规范：
- **工程目录独立**：在 `E:\Demo\Chunbo_Wanxiang_Medical\chunbo-medical-android` 下完整搭建。
- **依赖隔离（红线守则）**：所有 Gradle 依赖、编译缓存与构建文件严格限定并下载于本工程目录内的 `.gradle`，无任何未经允许的外部系统侵入。
- **真机测试就绪**：已构建打包出可直接安装的完整 Debug APK 文件。

---

## 二、四大核心功能模块

### 1. 🩺 医生移动工作台 (`DoctorFragment`)
- **门诊数据看板**：实时汇总门诊候诊待诊数、正在就诊数、今日已诊完成数。
- **候诊队列与呼叫管理**：对接后端 `/api/registration/queue`，直观展示患者姓名、年龄、挂号单号、分诊等级与主诉。
- **一键叫号接诊与就诊完成**：支持医生移动端一键发起叫号（`/api/registration/call/{id}`）与完成接诊（`/api/registration/finish/{id}`）。
- **门诊处方开具记录**：无缝切换查看患者处方明细、诊断结论与药品清单。

### 2. 🏥 便民就医与预约挂号 (`PatientFragment`)
- **手机在线预约挂号**：就诊患者可在线选择科室（中医内科、针灸推拿科、儿科贴敷中心等），录入主诉并实时分配排队号。
- **当前就诊排队即时跟踪**：实时显示“我的候诊排队卡片”，动态提示前序等待人数与医生叫号广播。
- **既往就医记录回溯**：历史门诊记录与就诊状态随时可查。

### 3. 🛍️ 春播健康商城与耗材集采 (`MallFragment`)
- **精选特色贴敷耗材**：直连门诊特色贴敷（小儿咳喘贴、三伏贴、穴位敷贴等）供应链商品库（`/api/mall/products`）。
- **移动端一键集采下单**：支持设置采购数量、收件人信息并直接生成订单（`/api/mall/order/create`）。
- **订单履约状态跟踪**：采购订单列表实时回显支付与发货状态。

### 4. 🤖 AI 临床决策与多智能体协同 (`CopilotFragment`)
- **临床 CDSS 处方审查**：实时咨询配伍禁忌、十八反十九畏、超量用药风险。
- **特色贴敷方案推荐**：针对咳喘、痹症等常见慢病智能给出中药贴敷穴位与疗程指引。
- **危急重症红旗征拦截**：急重症首诊快速筛查处置指引。
- **快捷建议胶囊**：提供常见临床提问预设，一触即发。

---

## 三、APK 文件位置与真机测试指引

### 1. 编译输出文件
- **APK 完整路径**：
  `E:\Demo\Chunbo_Wanxiang_Medical\chunbo-medical-android\app\build\outputs\apk\debug\app-debug.apk`
- **应用包名**：`com.chunbo.medical`
- **目标 SDK**：API 34 (Android 14)，最低兼容 API 26 (Android 8.0+)

### 2. 安卓手机真机测试步骤

#### 方式 A：一键脚本自动化安装（最推荐）
1. 用 USB 数据线将安卓手机连接到电脑，手机上开启【开发者选项】并打开【USB 调试】。
2. 保持 Spring Boot 后端处于运行状态（端口 8080）。
3. 双击运行工程根目录下的脚本：
   ```cmd
   E:\Demo\Chunbo_Wanxiang_Medical\chunbo-medical-android\install_to_phone.bat
   ```
4. 脚本将自动：
   - 识别检测连接的真机设备
   - 配置端口映射 `adb reverse tcp:8080 tcp:8080`
   - 将 APK 快速安装到手机并自动唤起主界面

#### 方式 B：手动命令行安装
```powershell
# 1. 检查设备连接
& "E:\Study_Cache\AndroidSDK\platform-tools\adb.exe" devices

# 2. 映射电脑后端端口到手机
& "E:\Study_Cache\AndroidSDK\platform-tools\adb.exe" reverse tcp:8080 tcp:8080

# 3. 安装 APK
& "E:\Study_Cache\AndroidSDK\platform-tools\adb.exe" install -r "E:\Demo\Chunbo_Wanxiang_Medical\chunbo-medical-android\app\build\outputs\apk\debug\app-debug.apk"
```

#### 方式 C：文件直传手机安装
- 将电脑上的 `app-debug.apk` 发送或拷贝至手机内部存储，在手机“文件管理”中点击安装。

---

## 四、服务端地址灵活切换说明
为了方便真机在不同网络环境下测试，App 内置了**动态服务器地址配置**功能：
1. 点击 App 顶部右上角 **设置图标 ⚙️**。
2. 弹出的设置框中已预置常用网络：
   - **局域网 Wi-Fi 直连**（推荐）：`http://192.168.0.238:8080`（手机与电脑连接同一 Wi-Fi）
   - **USB 数据线直连代理**：`http://127.0.0.1:8080`（配合 `adb reverse tcp:8080 tcp:8080`）
   - **安卓模拟器**：`http://10.0.2.2:8080`
   - **自定义地址**：可随时手动输入任意电脑 IP 与端口。
3. 点击“保存并连接”，无需重新编译即可即时生效！
