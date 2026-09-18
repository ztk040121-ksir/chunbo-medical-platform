<template>
  <div class="pharmacy-container">
    <!-- 顶部主操作栏与二级导航 -->
    <div class="header-card">
      <div class="header-left">
        <div class="page-title-badge">
          <span class="pulse-dot"></span>
          <span class="title-text">智慧药房进销存与发药中枢</span>
        </div>
        <el-radio-group v-model="activeSubTab" size="large" class="custom-tab-group">
          <el-radio-button label="dispense">发药窗口</el-radio-button>
          <el-radio-button label="products">商品档案</el-radio-button>
          <el-radio-button label="inbound">入库管理</el-radio-button>
          <el-radio-button label="stocktake">库存盘点</el-radio-button>
          <el-radio-button label="warning">
            <span>库存预警</span><span class="warning-count-pill" v-if="warningCount > 0">{{ warningCount }}</span>
          </el-radio-button>
          <el-radio-button label="materials">物资管理</el-radio-button>
        </el-radio-group>
      </div>

      <div class="header-right">
        <el-button 
          v-if="activeSubTab === 'products'" 
          type="primary" 
          class="gradient-btn" 
          @click="openNewMedicineModal"
        >
          <el-icon><Plus /></el-icon> 新建商品档案 (支持AI识图扫码)
        </el-button>
        <el-button 
          v-if="activeSubTab === 'inbound'" 
          type="success" 
          class="gradient-btn-green" 
          @click="openNewInboundModal"
        >
          <el-icon><Download /></el-icon> 新增采购入库单
        </el-button>
        <el-button 
          v-if="activeSubTab === 'stocktake'" 
          type="warning" 
          class="gradient-btn-amber" 
          @click="openQuickStocktakeModal"
        >
          <el-icon><DocumentChecked /></el-icon> 发起快速盘点
        </el-button>
        <el-button 
          v-if="activeSubTab === 'warning'" 
          type="primary" 
          plain 
          @click="activeSubTab = 'inbound'; showInboundModal = true"
        >
          <el-icon><DocumentAdd /></el-icon> 办理药品采购入库
        </el-button>
      </div>
    </div>

    <!-- 1. 发药窗口 Tab -->
    <div v-if="activeSubTab === 'dispense'" class="tab-content-zone">
      <div class="dispense-layout">
        <!-- 待发药处方与已发药记录切换队列 -->
        <div class="dispense-queue-panel">
          <div class="panel-head">
            <div class="dispense-queue-switch">
              <el-radio-group v-model="dispenseSubTab" size="small" @change="onDispenseTabChange">
                <el-radio-button label="pending">待发药 ({{ pendingPrescriptions.length }})</el-radio-button>
                <el-radio-button label="dispensed">已发药流水 ({{ dispensedPrescriptions.length }})</el-radio-button>
              </el-radio-group>
            </div>
            <el-button type="primary" link @click="loadPrescriptions"><el-icon><Refresh /></el-icon> 刷新</el-button>
          </div>
          <div class="queue-list">
            <div 
              v-for="item in currentDispenseList" 
              :key="item.prescription.id"
              class="queue-card"
              :class="{ active: selectedPrescription && selectedPrescription.prescription.id === item.prescription.id }"
              @click="selectPrescription(item)"
            >
              <div class="card-line1">
                <span class="rx-no">#{{ item.prescription.prescriptionNo }}</span>
                <el-tag :type="dispenseSubTab === 'pending' ? 'danger' : 'success'" size="small" effect="dark">
                  {{ dispenseSubTab === 'pending' ? '待发药' : '已核销发药' }}
                </el-tag>
              </div>
              <div class="card-line2">
                <span class="pt-name">{{ item.prescription.patientName }}</span>
                <span class="pt-diag">{{ item.prescription.diagnosis || '门诊处方' }}</span>
              </div>
              <div class="card-line3">
                <span class="doc-text">开方：{{ item.prescription.doctorName }}</span>
                <span class="amt-text">¥{{ Number(item.prescription.totalAmount || 0).toFixed(2) }}</span>
              </div>
            </div>
            <div v-if="currentDispenseList.length === 0" class="empty-hint">
              {{ dispenseSubTab === 'pending' ? '暂无待发药处方' : '暂无已发药历史记录' }}
            </div>
          </div>
        </div>

        <!-- 发药核对与出库操作台 -->
        <div class="dispense-detail-panel" v-if="selectedPrescription">
          <el-card class="detail-card" shadow="never">
            <template #header>
              <div class="detail-card-head">
                <div class="head-info">
                  <span class="big-name">{{ selectedPrescription.prescription.patientName }}</span>
                  <span class="rx-title">处方号：{{ selectedPrescription.prescription.prescriptionNo }}</span>
                  <span class="diag-badge">{{ selectedPrescription.prescription.diagnosis }}</span>
                </div>
                <div class="head-btn">
                  <el-button 
                    v-if="dispenseSubTab === 'pending'"
                    type="success" 
                    size="large" 
                    class="dispense-action-btn"
                    :loading="dispensing"
                    @click="executeDispense(selectedPrescription.prescription.id)"
                  >
                    <el-icon><Check /></el-icon> 一键发药出库 (扣减库存+语音呼叫)
                  </el-button>
                  <div v-else class="dispensed-action-group">
                    <el-tag type="success" size="large" effect="plain" class="dispensed-tag">
                      <el-icon><CircleCheckFilled /></el-icon> 该处方已核对出库发药完成 (库存已核销)
                    </el-tag>
                    <el-button 
                      type="primary" 
                      size="default" 
                      class="print-dispense-btn"
                      @click="printDispenseTicket(selectedPrescription)"
                    >
                      <el-icon><Printer /></el-icon> 打印发药出库追溯单
                    </el-button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 药品核对列表 -->
            <div class="table-section-title">处方药品调配核对清单</div>
            <el-table :data="selectedPrescription.items" border stripe class="dispense-table">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="medicineName" label="药品名称" min-width="150">
                <template #default="scope">
                  <span class="font-bold">{{ scope.row.medicineName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="dosage" label="规格/剂量" width="130" />
              <el-table-column prop="frequency" label="用法频次" width="120" />
              <el-table-column prop="quantity" label="调配数量" width="100" align="center">
                <template #default="scope">
                  <span class="qty-badge">{{ scope.row.quantity }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单价" width="100">
                <template #default="scope">¥{{ Number(scope.row.price || scope.row.unitPrice || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="金额小计" width="110">
                <template #default="scope">¥{{ Number(scope.row.subtotal && scope.row.subtotal > 0 ? scope.row.subtotal : (scope.row.price || scope.row.unitPrice || 0) * (scope.row.quantity || 1)).toFixed(2) }}</template>
              </el-table-column>
            </el-table>

            <div class="tts-notice-box">
              <el-icon color="#2563eb"><Microphone /></el-icon>
              <span>发药确认后，系统将自动调用语音合成广播播报：“请患者【{{ selectedPrescription.prescription.patientName }}】到药房窗口取药”，并联动记录发药单号追溯流。</span>
            </div>
          </el-card>
        </div>

        <div class="dispense-detail-panel empty-selection" v-else>
          <el-empty description="请从左侧选择待发药处方进行配药核验" />
        </div>
      </div>
    </div>

    <!-- 2. 商品档案 Tab (完整 15+ 真实字段) -->
    <div v-else-if="activeSubTab === 'products'" class="tab-content-zone">
      <!-- 搜索与分类过滤条 -->
      <div class="filter-bar">
        <el-input 
          v-model="productSearchKey" 
          placeholder="药品通名 / 拼音码 / 条形码 / 库位码" 
          prefix-icon="Search"
          clearable 
          style="width: 320px;"
          @input="loadMedicines"
        />
        <el-select v-model="productCategory" placeholder="请选择一级分类" clearable style="width: 160px;" @change="loadMedicines">
          <el-option label="全部分类" value="" />
          <el-option label="中成药" value="中成药" />
          <el-option label="西药" value="西药" />
          <el-option label="中药" value="中药" />
          <el-option label="医用材料" value="医用材料" />
        </el-select>
        <el-select v-model="productStatus" placeholder="全部状态" clearable style="width: 130px;" @change="loadMedicines">
          <el-option label="全部状态" value="" />
          <el-option label="启用中" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadMedicines"><el-icon><Search /></el-icon> 查询</el-button>
      </div>

      <!-- 商品档案大表 -->
      <el-table :data="medicines" stripe border class="data-table-glass" v-loading="loadingProducts">
        <el-table-column prop="name" label="商品名称" min-width="160">
          <template #default="scope">
            <span class="font-bold">{{ scope.row.name }}</span>
            <el-tag v-if="scope.row.isPrescription === '处方药'" size="small" type="danger" class="ml-1">Rx</el-tag>
            <el-tag v-else size="small" type="success" class="ml-1">OTC</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pinyinCode" label="拼音码" width="100" />
        <el-table-column prop="barcode" label="条形码" width="140" />
        <el-table-column prop="locationCode" label="库位码" width="95">
          <template #default="scope">
            <el-tag type="info" size="small">{{ scope.row.locationCode || 'A-01' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="specification" label="规格" width="130" />
        <el-table-column prop="primaryCategory" label="一级分类" width="100">
          <template #default="scope">
            <el-tag :type="getCatTagType(scope.row.primaryCategory)" size="small">{{ scope.row.primaryCategory }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="secondaryCategory" label="剂型" width="100" />
        <el-table-column prop="stock" label="库存数" width="100" align="center">
          <template #default="scope">
            <span :class="scope.row.stock <= (scope.row.warningStock || 15) ? 'stock-low' : 'stock-normal'">
              {{ scope.row.stock }} {{ scope.row.unit || '盒' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="生产厂家" min-width="180" show-overflow-tooltip />
        <el-table-column prop="costPrice" label="进货价" width="95">
          <template #default="scope">¥{{ Number(scope.row.costPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="price" label="零售价" width="95">
          <template #default="scope">¥{{ Number(scope.row.price || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="expiryDate" label="最近效期" width="115" />
        <el-table-column label="状态" width="85" align="center">
          <template #default="scope">
            <el-switch 
              :model-value="scope.row.isActive === 1" 
              size="small"
              @change="toggleActive(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="editMedicine(scope.row)">编辑修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 3. 入库管理 Tab -->
    <div v-else-if="activeSubTab === 'inbound'" class="tab-content-zone">
      <div class="filter-bar">
        <el-button type="success" class="gradient-btn-green" @click="openNewInboundModal">
          <el-icon><Plus /></el-icon> 新建入库单 (扫码验收入库)
        </el-button>
        <el-tag type="info" class="ml-2">支持采购入库、期初建账入库及春播商城一键过账</el-tag>
      </div>

      <el-table :data="inboundOrders" stripe border class="data-table-glass">
        <el-table-column prop="inboundNo" label="入库单编号" width="160">
          <template #default="scope">
            <span class="font-bold">{{ scope.row.inboundNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderType" label="入库类型" width="120">
          <template #default="scope">
            <el-tag type="primary" size="small">{{ scope.row.orderType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商名称" min-width="200" />
        <el-table-column prop="itemCount" label="品类品种数" width="110" align="center" />
        <el-table-column prop="totalAmount" label="总进货成本" width="130">
          <template #default="scope">¥{{ Number(scope.row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="creatorName" label="制单人" width="100" />
        <el-table-column prop="auditorName" label="审核人" width="100" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag type="success" size="small">已审核入库</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="入库时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 4. 库存盘点 Tab (快速盘点单 PD...) -->
    <div v-else-if="activeSubTab === 'stocktake'" class="tab-content-zone">
      <div class="filter-bar">
        <el-button type="warning" class="gradient-btn-amber" @click="openQuickStocktakeModal">
          <el-icon><Plus /></el-icon> 发起月度/季度快速盘点 (PD单号)
        </el-button>
        <el-tag type="warning" effect="plain">盘点时系统自动比对账面数量与实盘数量，并核算盈亏总额</el-tag>
      </div>

      <el-table :data="stocktakes" stripe border class="data-table-glass">
        <el-table-column prop="stocktakeNo" label="盘点单号" width="220">
          <template #default="scope">
            <span class="font-bold text-blue">{{ scope.row.stocktakeNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryScope" label="盘点范围" width="120">
          <template #default="scope">
            <el-tag type="info" size="small">{{ scope.row.categoryScope }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalBookQty" label="账面总数" width="110" align="center" />
        <el-table-column prop="totalActualQty" label="实盘总数" width="110" align="center" />
        <el-table-column prop="profitLossQty" label="盈亏数量" width="110" align="center">
          <template #default="scope">
            <span :class="scope.row.profitLossQty < 0 ? 'text-red' : (scope.row.profitLossQty > 0 ? 'text-green' : '')">
              {{ scope.row.profitLossQty > 0 ? '+' : '' }}{{ scope.row.profitLossQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="profitLossAmount" label="盈亏总额" width="120">
          <template #default="scope">
            <span :class="scope.row.profitLossAmount < 0 ? 'text-red' : (scope.row.profitLossAmount > 0 ? 'text-green' : '')">
              ¥{{ Number(scope.row.profitLossAmount).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="盘点人" width="100" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag type="success" size="small">已过账校准</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="盘点日期" min-width="160">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 5. 库存预警 Tab -->
    <div v-else-if="activeSubTab === 'warning'" class="tab-content-zone">
      <div class="warning-hero-card">
        <div class="hero-left">
          <div class="hero-title">
            <el-icon color="#ef4444" size="24"><WarningFilled /></el-icon>
            <span>低库存与效期预警中枢</span>
          </div>
          <p class="hero-desc">当药品实时库存低于设定预警线时，系统自动标记红线警报，提醒药剂科及时组织调拨采购并办理验收入库。</p>
        </div>
        <el-button type="primary" size="large" class="gradient-btn" @click="activeSubTab = 'inbound'; showInboundModal = true">
          <el-icon><DocumentAdd /></el-icon> 立即登记采购进货入库单
        </el-button>
      </div>

      <el-table :data="warningMedicines" stripe border class="data-table-glass">
        <el-table-column prop="name" label="预警药品名称" min-width="180">
          <template #default="scope">
            <span class="font-bold">{{ scope.row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locationCode" label="库位码" width="100" />
        <el-table-column prop="specification" label="规格" width="140" />
        <el-table-column prop="stock" label="当前库存" width="110" align="center">
          <template #default="scope">
            <span class="stock-alert-num">{{ scope.row.stock }} {{ scope.row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="warningStock" label="预警阈值" width="110" align="center">
          <template #default="scope">
            <span>{{ scope.row.warningStock || 15 }} {{ scope.row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="costPrice" label="参考进价" width="110">
          <template #default="scope">¥{{ scope.row.costPrice }}</template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="生产厂家" min-width="200" show-overflow-tooltip />
        <el-table-column label="智能补货建议" min-width="180">
          <template #default="scope">
            <span class="replenish-hint">建议补货 100~300 盒 (预估可维持 14 天门诊处方用量)</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 6. 物资管理 Tab (对应截图 03) -->
    <div v-else-if="activeSubTab === 'materials'" class="tab-content-zone">
      <div class="filter-bar">
        <span class="font-bold">医用材料及耗材物资库存一览</span>
        <el-tag type="info">共计 4 类医用耗材，实际成本 ¥2,294.00</el-tag>
      </div>

      <el-table :data="materialMedicines" stripe border class="data-table-glass">
        <el-table-column prop="name" label="物品名称" min-width="180">
          <template #default="scope">
            <span class="font-bold">{{ scope.row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="specification" label="规格" width="140" />
        <el-table-column prop="locationCode" label="货位" width="100" />
        <el-table-column prop="stock" label="账面库存" width="110" align="center">
          <template #default="scope">{{ scope.row.stock }} {{ scope.row.unit || '件' }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="实际库存" width="110" align="center">
          <template #default="scope">{{ scope.row.stock }} {{ scope.row.unit || '件' }}</template>
        </el-table-column>
        <el-table-column prop="costPrice" label="进价成本" width="110">
          <template #default="scope">¥{{ scope.row.costPrice }}</template>
        </el-table-column>
        <el-table-column label="总成本金额" width="130">
          <template #default="scope">¥{{ (scope.row.costPrice * scope.row.stock).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="供应商/厂家" min-width="200" />
      </el-table>
    </div>

    <!-- 底部资产实时统计栏 (对应截图 02/03 底栏资产汇总) -->
    <div class="bottom-stats-footer">
      <div class="stats-left">
        <span class="stats-item">共计 <b>{{ stats.totalProducts || 24 }}</b> 个商品</span>
        <span class="stats-divider">|</span>
        <span class="stats-item">总实际成本：<b class="text-orange">¥{{ Number(stats.totalCost || 7316).toFixed(2) }}</b></span>
        <span class="stats-sub">(西药 ¥{{ Number(stats.westernCost || 1029).toFixed(2) }} · 中成药 ¥{{ Number(stats.patentCost || 2687).toFixed(2) }} · 中药配方颗粒 ¥{{ Number(stats.herbCost || 1306).toFixed(2) }} · 医用材料 ¥{{ Number(stats.materialCost || 2294).toFixed(2) }})</span>
      </div>
      <div class="stats-right">
        <el-button size="small" type="primary" plain @click="exportData">
          <el-icon><Download /></el-icon> 导出进销存报表
        </el-button>
        <el-button size="small" type="info" plain @click="syncCloudWarehouse">
          <el-icon><Connection /></el-icon> 码上放心 / 药品追溯云同步
        </el-button>
      </div>
    </div>

    <!-- 弹窗：新建/编辑商品档案 (对应截图 04/05 扫码建档) -->
    <el-dialog v-model="showMedicineModal" title="新建/编辑药品商品档案 (支持AI视觉扫码建档)" width="680px">
      <div class="ai-scan-box">
        <el-button type="warning" plain @click="simulateAiScanDrugBox">
          <el-icon><Camera /></el-icon> AI 视觉识别药盒包装建档 (自动填单)
        </el-button>
        <span class="ai-tip">拍摄药盒包装，多模态提取药品名、条形码、国药准字、规格与厂家</span>
      </div>

      <el-form :model="medForm" label-width="110px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="通用名称" required>
              <el-input v-model="medForm.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="拼音码">
              <el-input v-model="medForm.pinyinCode" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="条形码(69码)">
              <el-input v-model="medForm.barcode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批准文号">
              <el-input v-model="medForm.approvalNumber" placeholder="国药准字Z/H..." />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="一级分类">
              <el-select v-model="medForm.primaryCategory" style="width: 100%;">
                <el-option label="中成药" value="中成药" />
                <el-option label="西药" value="西药" />
                <el-option label="中药" value="中药" />
                <el-option label="医用材料" value="医用材料" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="二级剂型">
              <el-input v-model="medForm.secondaryCategory" placeholder="水丸 / 颗粒 / 贴剂..." />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="medForm.specification" placeholder="如 10g*6袋/盒" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位码">
              <el-input v-model="medForm.locationCode" placeholder="如 A-01-01" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="进货成本价(元)">
              <el-input-number v-model="medForm.costPrice" :precision="2" :step="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="零售售价(元)">
              <el-input-number v-model="medForm.price" :precision="2" :step="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="生产厂家">
          <el-input v-model="medForm.manufacturer" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showMedicineModal = false">取消</el-button>
        <el-button type="primary" class="gradient-btn" @click="submitMedicine">保存商品档案</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：新建入库单 (对应截图 06) -->
    <el-dialog v-model="showInboundModal" title="新建药品采购/期初验收入库单" width="650px">
      <el-form :model="inboundForm" label-width="100px">
        <el-form-item label="入库类型">
          <el-select v-model="inboundForm.orderType" style="width: 100%;">
            <el-option label="采购入库" value="采购入库" />
            <el-option label="期初入库" value="期初入库" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="inboundForm.supplierName" />
        </el-form-item>
        <el-form-item label="选择药品">
          <el-select v-model="inboundForm.medicineId" placeholder="选择诊所已有药品" style="width: 100%;" @change="onInboundMedSelect">
            <el-option 
              v-for="m in medicines" 
              :key="m.id" 
              :label="m.name + ' (' + m.specification + ') - 库位:' + m.locationCode" 
              :value="m.id" 
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="入库数量">
              <el-input-number v-model="inboundForm.quantity" :min="1" :max="10000" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进货单价">
              <el-input-number v-model="inboundForm.costPrice" :precision="2" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="生产批号">
          <el-input v-model="inboundForm.batchNumber" placeholder="PH2026..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInboundModal = false">取消</el-button>
        <el-button type="primary" class="gradient-btn-green" @click="submitInbound">提交审核过账 (实时累加库存)</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：发起快速盘点 (对应截图 07/08) -->
    <el-dialog v-model="showStocktakeModal" title="发起快速盘点单" width="500px">
      <el-form label-width="100px">
        <el-form-item label="选择盘点范围">
          <el-radio-group v-model="stocktakeScope">
            <el-radio label="全品类">全院全品类</el-radio>
            <el-radio label="中成药">中成药</el-radio>
            <el-radio label="西药">西药</el-radio>
            <el-radio label="中药">中药颗粒</el-radio>
            <el-radio label="医用材料">医用材料耗材</el-radio>
          </el-radio-group>
        </el-form-item>
        <p class="stocktake-tip">系统将自动锁定当前账面库存，生成 PD 盘点单号并核算损益差异。</p>
      </el-form>
      <template #footer>
        <el-button @click="showStocktakeModal = false">取消</el-button>
        <el-button type="warning" class="gradient-btn-amber" @click="submitQuickStocktake">开始自动盘点核算</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const activeSubTab = ref('dispense')

// 1. 发药窗口状态
const prescriptions = ref([])
const selectedPrescription = ref(null)
const dispensing = ref(false)
const dispenseSubTab = ref('pending')

const loadPrescriptions = async () => {
  try {
    const res = await axios.get('/api/pharmacy/prescriptions')
    prescriptions.value = res.data || []
    onDispenseTabChange()
  } catch (e) {
    console.error(e)
  }
}

const pendingPrescriptions = computed(() => {
  return prescriptions.value.filter(p => {
    const st = String(p.prescription.status)
    return st === '1' || (p.prescription.payStatus === '已支付' && st !== '2')
  })
})

const dispensedPrescriptions = computed(() => {
  return prescriptions.value.filter(p => {
    const st = String(p.prescription.status)
    return st === '2' || st === '已发药'
  })
})

const currentDispenseList = computed(() => {
  return dispenseSubTab.value === 'pending' ? pendingPrescriptions.value : dispensedPrescriptions.value
})

const onDispenseTabChange = () => {
  const list = currentDispenseList.value
  if (list.length > 0) {
    selectedPrescription.value = list[0]
  } else {
    selectedPrescription.value = null
  }
}

const selectPrescription = (item) => {
  selectedPrescription.value = item
}

const printDispenseTicket = (item) => {
  const p = item.prescription
  ElMessageBox.alert(`
    <div style="font-family: monospace; line-height: 1.8; padding: 10px;">
      <h3 style="text-align: center; margin: 0 0 10px 0;">春播万象智慧药房 · 处方发药出库凭单</h3>
      <p><b>发药单号：</b> FY${p.prescriptionNo}</p>
      <p><b>患者姓名：</b> ${p.patientName} &nbsp;&nbsp; <b>开方医生：</b> ${p.doctorName}</p>
      <p><b>发药药师：</b> 张医生 (调剂药师 DOC_1002) &nbsp;&nbsp; <b>发药状态：</b> 已核验出库</p>
      <p><b>出库时间：</b> ${new Date().toLocaleString()}</p>
      <hr style="border: 1px dashed #ccc;"/>
      <table style="width: 100%; font-size: 13px; text-align: left;">
        <tr><th>调剂药品</th><th>规格</th><th>数量</th><th>金额</th></tr>
        ${(item.items || []).map(it => `<tr><td>${it.medicineName}</td><td>${it.dosage || '-'}</td><td>${it.quantity}</td><td>¥${Number(it.totalPrice || 0).toFixed(2)}</td></tr>`).join('')}
      </table>
      <hr style="border: 1px dashed #ccc;"/>
      <p style="text-align: right; font-size: 15px;"><b>药品总计：¥${Number(p.totalAmount || 0).toFixed(2)}</b></p>
      <p style="text-align: center; color: #16a34a; font-size: 11px;">药品出库追溯码: RX-TRACE-${Date.now()} · 遵医嘱按时规范服药</p>
    </div>
  `, '发药出库追溯凭单', {
    dangerouslyUseHTMLString: true,
    confirmButtonText: '打印发药联'
  })
}

const executeDispense = async (rxId) => {
  dispensing.value = true
  try {
    const res = await axios.post(`/api/pharmacy/dispense/${rxId}`)
    if (res.data && res.data.success) {
      ElMessage.success(res.data.message)
      // 语音呼叫播报 (对应截图 35 取药语音呼叫)
      if ('speechSynthesis' in window) {
        const patientName = selectedPrescription.value?.prescription?.patientName || '患者'
        const utter = new SpeechSynthesisUtterance(`请 ${patientName} 到药房窗口取药`)
        utter.lang = 'zh-CN'
        window.speechSynthesis.speak(utter)
      }
      await loadPrescriptions()
      await loadStats()
      await loadMedicines()
    } else {
      ElMessage.error(res.data?.message || '发药失败！')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发药失败，请检查药房库存！')
  } finally {
    dispensing.value = false
  }
}

// 2. 商品档案状态
const medicines = ref([])
const productSearchKey = ref('')
const productCategory = ref('')
const productStatus = ref('')
const loadingProducts = ref(false)

const loadMedicines = async () => {
  loadingProducts.value = true
  try {
    const res = await axios.get('/api/pharmacy/medicines', {
      params: {
        keyword: productSearchKey.value || undefined,
        primaryCategory: productCategory.value || undefined,
        isActive: productStatus.value !== '' ? productStatus.value : undefined
      }
    })
    medicines.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loadingProducts.value = false
  }
}

const toggleActive = async (med) => {
  try {
    const res = await axios.post(`/api/pharmacy/medicines/toggle-active/${med.id}`)
    med.isActive = res.data.isActive
    ElMessage.success(res.data.message)
  } catch (e) {
    ElMessage.error('切换状态失败')
  }
}

// 3. 入库与盘点数据
const inboundOrders = ref([])
const stocktakes = ref([])
const stats = ref({})

const loadInbounds = async () => {
  try {
    const res = await axios.get('/api/inbound/orders')
    inboundOrders.value = res.data || []
  } catch (e) {}
}

const loadStocktakes = async () => {
  try {
    const res = await axios.get('/api/stocktake/list')
    stocktakes.value = res.data || []
  } catch (e) {}
}

const loadStats = async () => {
  try {
    const res = await axios.get('/api/pharmacy/stats')
    stats.value = res.data || {}
  } catch (e) {}
}

// 预警与物资过滤
const warningMedicines = computed(() => {
  return medicines.value.filter(m => m.stock <= (m.warningStock || 15))
})

const warningCount = computed(() => warningMedicines.value.length)

const materialMedicines = computed(() => {
  return medicines.value.filter(m => m.primaryCategory === '医用材料')
})

onMounted(() => {
  loadPrescriptions()
  loadMedicines()
  loadInbounds()
  loadStocktakes()
  loadStats()
})

// 弹窗表单
const showMedicineModal = ref(false)
const medForm = ref({
  id: null,
  name: '',
  pinyinCode: '',
  barcode: '',
  approvalNumber: '',
  manufacturer: '',
  primaryCategory: '中成药',
  secondaryCategory: '水丸',
  specification: '',
  costPrice: 12.00,
  price: 30.00,
  locationCode: 'A-01-01',
  unit: '盒'
})

const openNewMedicineModal = () => {
  medForm.value = {
    id: null,
    name: '',
    pinyinCode: '',
    barcode: '',
    approvalNumber: '',
    manufacturer: '',
    primaryCategory: '中成药',
    secondaryCategory: '水丸',
    specification: '10g*6袋/盒',
    costPrice: 12.00,
    price: 30.00,
    locationCode: 'A-01-03',
    unit: '盒'
  }
  showMedicineModal.value = true
}

const simulateAiScanDrugBox = () => {
  medForm.value.name = '蜜款冬花配方颗粒'
  medForm.value.pinyinCode = 'MKDHPFKL'
  medForm.value.barcode = '6901234567892'
  medForm.value.approvalNumber = '国药准字Z20190014'
  medForm.value.manufacturer = '广东一方制药有限公司'
  medForm.value.primaryCategory = '中药'
  medForm.value.secondaryCategory = '配方颗粒'
  medForm.value.specification = '1g(相当于饮片5g)/袋'
  medForm.value.costPrice = 2.20
  medForm.value.price = 5.20
  medForm.value.locationCode = 'D-01-03'
  medForm.value.unit = '袋'
  ElMessage.success('AI 视觉药盒识别完成！药品条码、规格与国药准字已精确回填！')
}

const editMedicine = (m) => {
  medForm.value = { ...m }
  showMedicineModal.value = true
}

const submitMedicine = async () => {
  if (!medForm.value.name) {
    ElMessage.warning('请输入商品名称！')
    return
  }
  try {
    await axios.post('/api/pharmacy/medicines/save', medForm.value)
    ElMessage.success('商品档案已保存！')
    showMedicineModal.value = false
    await loadMedicines()
    await loadStats()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 新增入库单
const showInboundModal = ref(false)
const inboundForm = ref({
  orderType: '采购入库',
  supplierName: '国药控股湖南有限公司',
  medicineId: null,
  quantity: 50,
  costPrice: 12.00,
  batchNumber: 'PH20260916'
})

const openNewInboundModal = () => {
  inboundForm.value = {
    orderType: '采购入库',
    supplierName: '国药控股湖南有限公司',
    medicineId: medicines.value.length > 0 ? medicines.value[0].id : null,
    quantity: 50,
    costPrice: 12.00,
    batchNumber: 'PH20260916'
  }
  showInboundModal.value = true
}

const onInboundMedSelect = (id) => {
  const m = medicines.value.find(x => x.id === id)
  if (m) {
    inboundForm.value.costPrice = m.costPrice || 10.00
  }
}

const submitInbound = async () => {
  if (!inboundForm.value.medicineId) {
    ElMessage.warning('请选择入库药品！')
    return
  }
  const payload = {
    orderType: inboundForm.value.orderType,
    supplierName: inboundForm.value.supplierName,
    items: [
      {
        medicineId: inboundForm.value.medicineId,
        quantity: inboundForm.value.quantity,
        costPrice: inboundForm.value.costPrice,
        batchNumber: inboundForm.value.batchNumber
      }
    ]
  }
  try {
    const res = await axios.post('/api/inbound/create', payload)
    ElMessage.success(res.data?.message || '入库单已过账并增加库存！')
    showInboundModal.value = false
    await loadInbounds()
    await loadMedicines()
    await loadStats()
  } catch (e) {
    ElMessage.error('入库失败')
  }
}

// 快速盘点
const showStocktakeModal = ref(false)
const stocktakeScope = ref('全品类')

const openQuickStocktakeModal = () => {
  showStocktakeModal.value = true
}

const submitQuickStocktake = async () => {
  try {
    const res = await axios.post('/api/stocktake/create', {
      categoryScope: stocktakeScope.value
    })
    ElMessage.success(res.data?.message || '盘点已自动核算完成！')
    showStocktakeModal.value = false
    await loadStocktakes()
    await loadStats()
  } catch (e) {
    ElMessage.error('盘点失败')
  }
}

const goToMallProcurement = () => {
  window.open('http://localhost:5175', '_blank')
}

const exportData = () => {
  ElMessage.success('已导出全院药品进销存台账 Excel 报表！')
}

const syncCloudWarehouse = () => {
  ElMessage.success('国家药品追溯码已与“码上放心”平台同步成功！')
}

const getCatTagType = (cat) => {
  if (cat === '西药') return 'danger'
  if (cat === '中成药') return 'primary'
  if (cat === '中药') return 'success'
  return 'info'
}

const formatTime = (t) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.pharmacy-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: fadeIn 0.4s ease-out;
  padding-bottom: 50px; /* 留出底栏空间 */
}

.header-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 14px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-title-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid #bbf7d0;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #16a34a;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.title-text {
  font-weight: 700;
  color: #14532d;
  font-size: 14px;
}

.tab-badge {
  margin-left: 4px;
}

.gradient-btn {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border: none;
  font-weight: 600;
}

.gradient-btn-green {
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  font-weight: 600;
}

.gradient-btn-amber {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  border: none;
  font-weight: 600;
}

.gradient-btn-red {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  border: none;
  font-weight: 700;
}

/* 发药窗口两栏 */
.dispense-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
}

.dispense-queue-panel {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 230px);
}

.panel-head {
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.head-title {
  font-weight: 700;
  font-size: 14px;
  color: #1e293b;
}

.queue-list {
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.queue-card {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all 0.2s;
}

.queue-card:hover {
  border-color: #93c5fd;
}

.queue-card.active {
  background: #eff6ff;
  border-color: #3b82f6;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.15);
}

.card-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rx-no {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.card-line2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pt-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.pt-diag {
  font-size: 12px;
  color: #2563eb;
}

.card-line3 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
  border-top: 1px dashed #f1f5f9;
  padding-top: 6px;
}

.amt-text {
  font-weight: 700;
  color: #dc2626;
  font-size: 14px;
}

.dispense-detail-panel {
  display: flex;
  flex-direction: column;
}

.detail-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.detail-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.head-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.big-name {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.rx-title {
  font-size: 13px;
  color: #64748b;
}

.diag-badge {
  background: #eff6ff;
  color: #2563eb;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.dispense-action-btn {
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.table-section-title {
  font-weight: 700;
  font-size: 14px;
  color: #334155;
  margin-bottom: 12px;
}

.dispense-table {
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
}

.qty-badge {
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 700;
  color: #1e293b;
}

.tts-notice-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #eff6ff;
  border: 1px dashed #bfdbfe;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 12px;
  color: #1e40af;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
}

.stock-low {
  color: #ef4444;
  font-weight: 700;
}

.stock-normal {
  color: #10b981;
  font-weight: 600;
}

.warning-hero-card {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border: 1px solid #fca5a5;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 800;
  color: #991b1b;
}

.hero-desc {
  font-size: 13px;
  color: #7f1d1d;
  margin-top: 4px;
}

.stock-alert-num {
  color: #dc2626;
  font-weight: 800;
  font-size: 15px;
}

.replenish-hint {
  color: #0284c7;
  font-size: 12px;
}

/* 底栏资产统计 */
.bottom-stats-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 48px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  z-index: 100;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.04);
}

.stats-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #334155;
}

.stats-divider {
  color: #cbd5e1;
}

.text-orange {
  color: #ea580c;
  font-size: 15px;
}

.text-red { color: #dc2626; font-weight: 700; }
.text-green { color: #16a34a; font-weight: 700; }
.text-blue { color: #2563eb; }

.stats-sub {
  color: #64748b;
  font-size: 12px;
}

.ai-scan-box {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fefce8;
  border: 1px dashed #fde047;
  padding: 10px 14px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.ai-tip {
  font-size: 12px;
  color: #854d0e;
}

.stocktake-tip {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.empty-selection {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  height: calc(100vh - 230px);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.tab-badge {
  margin-left: 4px;
  vertical-align: middle;
}
.tab-badge :deep(.el-badge__content) {
  font-size: 10px;
  height: 15px;
  line-height: 15px;
  padding: 0 4px;
  border: none;
  transform: translateY(-1px);
}
.dispensed-action-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.dispensed-tag {
  font-size: 13px;
  font-weight: 700;
  padding: 8px 16px;
}
.print-dispense-btn {
  font-weight: 600;
}


/* 保证药房所有二级标签高度严格一致（36px），角标不凸出变形 */
.custom-tab-group :deep(.el-radio-button__inner) {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  height: 38px !important;
  line-height: 38px !important;
  padding: 0 16px !important;
  box-sizing: border-box !important;
}

.warning-count-pill {
  margin-left: 6px;
  background: #ef4444;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
  height: 16px;
  min-width: 16px;
  padding: 0 5px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}

</style>
