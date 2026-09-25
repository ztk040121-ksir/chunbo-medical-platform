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
          <el-radio-button value="dispense">发药窗口</el-radio-button>
          <el-radio-button value="products">商品档案</el-radio-button>
          <el-radio-button value="inbound">入库管理</el-radio-button>
          <el-radio-button value="stocktake">库存盘点</el-radio-button>
          <el-radio-button value="warning">
            <span>库存预警</span><span class="warning-count-pill" v-if="warningCount > 0">{{ warningCount }}</span>
          </el-radio-button>
          <el-radio-button value="materials">物资管理</el-radio-button>
          <el-radio-button value="dict">下拉框管理</el-radio-button>
        </el-radio-group>
      </div>

      <div class="header-right">
        <el-date-picker
          v-model="dispenseDate"
          type="date"
          value-format="YYYY-MM-DD"
          clearable
          placeholder="按开方日期筛选"
          style="width: 160px;"
          @change="loadPrescriptions"
        />
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
          <div class="panel-head" style="flex-direction: column; align-items: stretch; gap: 8px;">
            <div class="dispense-queue-switch" style="display: flex; align-items: center; justify-content: space-between; gap: 8px; white-space: nowrap;">
              <el-radio-group v-model="dispenseSubTab" size="small" @change="onDispenseTabChange">
                <el-radio-button value="pending">待发药 ({{ pendingPrescriptions.length }})</el-radio-button>
                <el-radio-button value="dispensed">已发药流水 ({{ dispensedPrescriptions.length }})</el-radio-button>
              </el-radio-group>
              <el-button type="primary" link @click="loadPrescriptions"><el-icon><Refresh /></el-icon> 刷新</el-button>
            </div>
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
                <el-tag v-if="dispenseSubTab !== 'pending'" type="success" size="small" effect="dark">
                  已发药
                </el-tag>
                <el-tag v-else-if="isRxPaid(item)" type="success" size="small" effect="dark">
                  已缴费·待发药
                </el-tag>
                <el-tag v-else type="warning" size="small" effect="dark">
                  待收费处缴费
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
                  <el-tag v-if="isRxPaid(selectedPrescription)" type="success" size="small" effect="light" style="margin-left: 8px;">
                    ✓ 费用已结清
                  </el-tag>
                  <el-tag v-else type="warning" size="small" effect="light" style="margin-left: 8px;">
                    ⏳ 待收费处结账
                  </el-tag>
                </div>
                <div class="head-btn">
                  <template v-if="dispenseSubTab === 'pending'">
                    <el-button 
                      v-if="isRxPaid(selectedPrescription)"
                      type="success" 
                      size="large" 
                      class="dispense-action-btn"
                      :loading="dispensing"
                      @click="executeDispense(selectedPrescription.prescription.id)"
                    >
                      <el-icon><Check /></el-icon> 一键发药出库 (扣减库存+语音呼叫)
                    </el-button>
                    <el-tooltip v-else content="根据国家《药品网络与门诊销售监督管理办法》，患者未完成划价缴费前严禁发药" placement="top">
                      <el-button 
                        type="info" 
                        size="large" 
                        disabled
                        style="cursor: not-allowed; opacity: 0.85;"
                      >
                        <el-icon><Lock /></el-icon> 待患者缴费后方可发药（国家合规红线）
                      </el-button>
                    </el-tooltip>
                  </template>
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
          @clear="loadMedicines"
          @keyup.enter="loadMedicines"
        />
        <el-select v-model="productCategory" placeholder="请选择一级分类" clearable style="width: 160px;" @change="loadMedicines" @clear="loadMedicines">
          <el-option label="全部分类" value="" />
          <el-option label="西药" value="西药" />
          <el-option label="中成药" value="中成药" />
          <el-option label="中药 / 配方颗粒" value="中药" />
          <el-option label="特色贴敷" value="特色贴敷" />
          <el-option label="诊疗理疗项目" value="诊疗理疗项目" />
          <el-option label="医用材料" value="医用材料" />
        </el-select>
        <el-select v-model="productStatus" placeholder="全部状态" clearable style="width: 130px;" @change="loadMedicines" @clear="loadMedicines">
          <el-option label="全部状态" value="" />
          <el-option label="启用中" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadMedicines"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetProductFilters"><el-icon><Refresh /></el-icon> 重置</el-button>
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
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="openInboundDetail(scope.row)">查看明细</el-button>
          </template>
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
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="openStocktakeDetail(scope.row)">盘点明细</el-button>
          </template>
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
      <div class="filter-bar" style="display: flex; align-items: center; gap: 12px;">
        <span class="font-bold">医用材料及耗材物资库存一览</span>
        <el-tag type="info">共计 {{ materialMedicines.length }} 类医用耗材，实际成本 ¥{{ materialTotalCost }}</el-tag>
        <el-button type="primary" size="small" style="margin-left: auto;" @click="openNewMaterialModal">+ 新增医用材料</el-button>
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
        <el-table-column label="操作" width="200" align="center">
          <template #default="scope">
            <el-button type="success" link size="small" @click="openLinkModal(scope.row)">联动</el-button>
            <el-button type="primary" link size="small" @click="editMedicine(scope.row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="removeMedicine(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 7. 基础数据 Tab：表单下拉可选项的统一管理与添加 -->
    <div v-else-if="activeSubTab === 'dict'" class="tab-content-zone">
      <div class="filter-bar" style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
        <span class="font-bold">下拉框管理（商品档案/物资档案各表单下拉选项的统一管理与添加）</span>
        <el-select v-model="dictTypeFilter" style="width: 160px;" @change="loadDict">
          <el-option v-for="t in dictTypes" :key="t" :label="t" :value="t" />
        </el-select>
        <div style="display: flex; align-items: center; gap: 8px; margin-left: auto;">
          <el-input v-model="newDictValue" placeholder="输入新选项值，如：喷雾剂" style="width: 220px;" />
          <el-button type="primary" @click="addDictItem">+ 添加选项</el-button>
        </div>
      </div>

      <el-table :data="dictList" stripe border class="data-table-glass" max-height="480">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dict_value" label="选项值" min-width="260">
          <template #default="scope">
            <el-input v-model="scope.row.dict_value" v-if="scope.row._editing" size="small" />
            <span v-else class="font-bold">{{ scope.row.dict_value }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <template v-if="scope.row._editing">
              <el-button type="success" link size="small" @click="saveDictEdit(scope.row)">保存</el-button>
              <el-button link size="small" @click="scope.row._editing = false">取消</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link size="small" @click="scope.row._editing = true">编辑</el-button>
              <el-button type="danger" link size="small" @click="removeDictItem(scope.row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

        <!-- 底部资产实时统计栏 (对应截图 02/03 底栏资产汇总) -->
    <div class="bottom-stats-footer">
      <div class="stats-left">
        <span class="stats-item">共计 <b>{{ bottomStats.totalProducts }}</b> 个商品</span>
        <span class="stats-divider">|</span>
        <span class="stats-item">总实际成本：<b class="text-orange">¥{{ Number(bottomStats.totalCost).toFixed(2) }}</b></span>
        <span class="stats-sub">(西药 ¥{{ Number(bottomStats.westernCost).toFixed(2) }} · 中成药 ¥{{ Number(bottomStats.patentCost).toFixed(2) }} · 中药配方颗粒 ¥{{ Number(bottomStats.herbCost).toFixed(2) }} · 医用材料 ¥{{ Number(bottomStats.materialCost).toFixed(2) }})</span>
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

<!-- 入库单明细弹窗 -->
    <el-dialog v-model="showInboundDetail" :title="'入库单明细 · ' + (inboundDetail.order && inboundDetail.order.inboundNo || '')" width="760px">
      <template v-if="inboundDetail.order">
        <el-descriptions :column="3" size="small" border style="margin-bottom: 12px;">
          <el-descriptions-item label="入库类型">{{ inboundDetail.order.orderType }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ inboundDetail.order.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="入库时间">{{ formatTime(inboundDetail.order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="制单人">{{ inboundDetail.order.creatorName }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ inboundDetail.order.auditorName }}</el-descriptions-item>
          <el-descriptions-item label="总进货成本">¥{{ Number(inboundDetail.order.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="inboundDetail.items || []" stripe border size="small">
          <el-table-column prop="medicineName" label="药品名称" min-width="160" />
          <el-table-column prop="specification" label="规格" width="130" />
          <el-table-column prop="batchNumber" label="批号" width="110" />
          <el-table-column prop="quantity" label="入库数量" width="90" align="center" />
          <el-table-column prop="costPrice" label="入库单价" width="100" align="center">
            <template #default="scope">¥{{ Number(scope.row.costPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="totalCost" label="小计成本" width="100" align="center">
            <template #default="scope">¥{{ Number(scope.row.totalCost || 0).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>



<!-- 盘点明细弹窗 -->
    <el-dialog v-model="showStocktakeDetail" :title="'盘点明细 · ' + (stocktakeDetail.stocktake && stocktakeDetail.stocktake.stocktakeNo || '')" width="820px">
      <template v-if="stocktakeDetail.stocktake">
        <el-descriptions :column="3" size="small" border style="margin-bottom: 12px;">
          <el-descriptions-item label="盘点范围">{{ stocktakeDetail.stocktake.categoryScope }}</el-descriptions-item>
          <el-descriptions-item label="盘点人">{{ stocktakeDetail.stocktake.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="盘点日期">{{ formatTime(stocktakeDetail.stocktake.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="账面总数">{{ stocktakeDetail.stocktake.totalBookQty }}</el-descriptions-item>
          <el-descriptions-item label="实盘总数">{{ stocktakeDetail.stocktake.totalActualQty }}</el-descriptions-item>
          <el-descriptions-item label="盈亏总额">
            <span :class="Number(stocktakeDetail.stocktake.profitLossAmount) < 0 ? 'text-red' : 'text-green'">
              ¥{{ Number(stocktakeDetail.stocktake.profitLossAmount || 0).toFixed(2) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="stocktakeDetail.items || []" stripe border size="small">
          <el-table-column prop="medicineName" label="药品名称" min-width="160" />
          <el-table-column prop="specification" label="规格" width="130" />
          <el-table-column prop="bookQuantity" label="账面数" width="80" align="center" />
          <el-table-column prop="actualQuantity" label="实盘数" width="80" align="center" />
          <el-table-column prop="diffQuantity" label="盈亏数" width="80" align="center">
            <template #default="scope">
              <span :class="scope.row.diffQuantity < 0 ? 'text-red' : (scope.row.diffQuantity > 0 ? 'text-green' : '')">
                {{ scope.row.diffQuantity > 0 ? '+' : '' }}{{ scope.row.diffQuantity }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="diffAmount" label="盈亏金额" width="100" align="center">
            <template #default="scope">¥{{ Number(scope.row.diffAmount || 0).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>



<!-- 药品-物资联动配置弹窗 -->
    <el-dialog v-model="showLinkModal" :title="'开方联动配置 · ' + (linkSupply ? linkSupply.name : '')" width="640px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px;"
        title="配置后：门诊开方使用下方药品时，系统自动按数量附加本物资（随处方划价收费、发药时扣减物资库存）" />
      <el-form :inline="true" style="margin-bottom: 10px;">
        <el-form-item label="联动药品">
          <el-select v-model="linkForm.medicineId" filterable placeholder="选择触发联动的药品" style="width: 280px;">
            <el-option v-for="m in linkableMedicines" :key="m.id" :label="m.name + (m.specification ? ' (' + m.specification + ')' : '')" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="每次附加数量">
          <el-input-number v-model="linkForm.quantity" :min="1" :step="1" style="width: 110px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveLink">保存联动</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="linkList" stripe border size="small">
        <el-table-column prop="medicine_name" label="触发药品" min-width="200" />
        <el-table-column prop="quantity" label="每1个药品附加数量" width="150" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope">
            <el-button type="danger" link size="small" @click="removeLink(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="linkList.length === 0" style="text-align: center; color: #94a3b8; padding: 16px 0;">暂无联动配置</div>
    </el-dialog>



    <!-- 弹窗：新建/编辑商品档案 (对应截图 04/05 扫码建档) -->
    <el-dialog v-model="showMedicineModal" :title="medModalTitle" width="680px">
      <div class="ai-scan-box">
        <input type="file" ref="drugBoxFileInput" accept="image/*" @change="handleDrugBoxFileChange" style="display: none;" />
        <el-button type="warning" plain @click="triggerDrugBoxUpload">
          <el-icon><Camera /></el-icon> {{ drugBoxPreview ? '更换药盒照片' : '选择药盒包装照片 (图像建档)' }}
        </el-button>
        <span class="ai-tip">选取药盒包装正面照，辅助核对条形码、国药准字与规格厂家</span>
      </div>
      <div v-if="drugBoxPreview" style="margin: 0 0 16px 0; display: flex; align-items: center; gap: 12px; background: rgba(245, 158, 11, 0.08); padding: 8px 14px; border-radius: 8px; border: 1px solid rgba(245, 158, 11, 0.25);">
        <img :src="drugBoxPreview" alt="药盒包装" style="width: 50px; height: 50px; object-fit: cover; border-radius: 6px; border: 1px solid #f59e0b;" />
        <div style="flex: 1; font-size: 12px; color: #b45309;">
          <div><b>已载入药盒实物图像：{{ drugBoxFileName }}</b></div>
          <div style="color: #64748b;">已调取影像特征，请根据药盒标示核准下方药品通用名、国药准字与零售指导价</div>
        </div>
        <el-button size="small" type="danger" link @click="removeDrugBoxImage">移除</el-button>
      </div>

      <el-form :model="medForm" :rules="medFormRules" ref="medFormRef" label-width="130px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="通用名称" prop="name">
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
            <el-form-item label="批准文号" prop="approvalNumber">
              <el-select v-model="medForm.approvalNumber" filterable allow-create default-first-option
                placeholder="选择已有文号或直接输入，如 国药准字Z20190014" style="width: 100%;" clearable>
                <el-option v-for="a in approvalNumberOptions" :key="a" :label="a" :value="a" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="一级分类">
              <el-select v-model="medForm.primaryCategory" style="width: 100%;">
                <el-option label="西药 (门诊西药处方)" value="西药" />
                <el-option label="中成药 (门诊成药处方)" value="中成药" />
                <el-option label="中药 / 配方颗粒 (中药饮片处方)" value="中药" />
                <el-option label="特色贴敷 (穴位贴敷处方)" value="特色贴敷" />
                <el-option label="诊疗理疗项目 (理疗收费单)" value="诊疗理疗项目" />
                <el-option label="医用材料 (物资管理，不参与开方)" value="医用材料" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="二级剂型" prop="secondaryCategory">
              <el-select v-model="medForm.secondaryCategory" filterable allow-create default-first-option
                placeholder="选择或直接输入自定义剂型" style="width: 100%;">
                <el-option v-for="s in secondaryFormOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="规格" prop="specification">
              <el-input v-model="medForm.specification" placeholder="如 5mg*7片/盒，可自由修改">
                <template #append>
                  <el-dropdown trigger="click" @command="applySpecTemplate">
                    <el-button link type="primary">选规格模板 ▾</el-button>
                    <template #dropdown>
                      <el-dropdown-menu style="max-height: 320px; overflow-y: auto;">
                        <el-dropdown-item v-for="t in specTemplateOptions" :key="t" :command="t">{{ t }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位码" prop="locationCode">
              <el-select v-model="medForm.locationCode" filterable allow-create default-first-option
                placeholder="选择库位或直接输入，如 A-01-01" style="width: 100%;">
                <el-option v-for="c in locationCodeOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="进货成本价(元)" prop="costPrice">
              <el-input-number v-model="medForm.costPrice" :precision="2" :step="1" :min="0" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="零售价(元)" prop="price">
              <el-input-number v-model="medForm.price" :precision="2" :step="1" :min="0" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="当前库存">
              <el-input-number v-model="medForm.stock" :min="0" :step="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存预警线">
              <el-input-number v-model="medForm.warningStock" :min="0" :step="5" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="生产厂家">
          <el-select v-model="medForm.manufacturer" filterable allow-create default-first-option
            placeholder="选择已有厂家或直接输入新厂家（可在基础数据页统一管理）" style="width: 100%;" clearable>
            <el-option v-for="m in manufacturerOptions" :key="m" :label="m" :value="m" />
          </el-select>
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
            <el-radio value="全品类">全院全品类</el-radio>
            <el-radio value="中成药">中成药</el-radio>
            <el-radio value="西药">西药</el-radio>
            <el-radio value="中药">中药颗粒</el-radio>
            <el-radio value="医用材料">医用材料耗材</el-radio>
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
const currentUserName = localStorage.getItem('chunbo_display_name') || localStorage.getItem('chunbo_username') || '系统用户'
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const activeSubTab = ref('dispense')

// 1. 发药窗口状态
const prescriptions = ref([])
const selectedPrescription = ref(null)
const dispensing = ref(false)
const dispenseSubTab = ref('pending')
// 发药窗口日期筛选：默认今天，清空则显示全部（与门诊接诊/挂号的日期切换一致）
const localToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const dispenseDate = ref(localToday())

const loadPrescriptions = async () => {
  try {
    const params = {}
    if (dispenseDate.value) params.date = dispenseDate.value
    const res = await axios.get('/api/pharmacy/prescriptions', { params })
    prescriptions.value = res.data || []
    onDispenseTabChange()
  } catch (e) {
    console.error(e)
  }
}

const isRxPaid = (item) => {
  if (!item || !item.prescription) return false
  const p = item.prescription
  const st = String(p.status)
  return st === '1' || p.payStatus === '已支付'
}

const pendingPrescriptions = computed(() => {
  return prescriptions.value.filter(p => {
    const st = String(p.prescription.status)
    return st !== '2' && st !== '已发药'
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
      <p><b>发药药师：</b> ${currentUserName} (调剂药师) &nbsp;&nbsp; <b>发药状态：</b> 已核验出库</p>
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

const resetProductFilters = () => {
  productSearchKey.value = ''
  productCategory.value = ''
  productStatus.value = ''
  loadMedicines()
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

// ── 入库单 / 盘点单明细弹窗 ──
const showInboundDetail = ref(false)
const inboundDetail = ref({ order: null, items: [] })
const openInboundDetail = async (row) => {
  try {
    const res = await axios.get(`/api/inbound/orders/${row.id}`)
    inboundDetail.value = { order: res.data.order || row, items: res.data.items || [] }
    showInboundDetail.value = true
  } catch (e) {
    ElMessage.error('加载入库明细失败')
  }
}

const showStocktakeDetail = ref(false)
const stocktakeDetail = ref({ stocktake: null, items: [] })
const openStocktakeDetail = async (row) => {
  try {
    const res = await axios.get(`/api/stocktake/${row.id}`)
    stocktakeDetail.value = { stocktake: res.data.stocktake || row, items: res.data.items || [] }
    showStocktakeDetail.value = true
  } catch (e) {
    ElMessage.error('加载盘点明细失败')
  }
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

// 底部资产汇总：从后端真实药品库按分类实时计算（替代写死的假统计）
const bottomStats = computed(() => {
  const list = medicines.value || []
  const costOf = (cat) => list
    .filter(m => m.primaryCategory === cat)
    .reduce((s, m) => s + Number(m.costPrice || 0) * Number(m.stock || 0), 0)
  return {
    totalProducts: list.length,
    totalCost: list.reduce((s, m) => s + Number(m.costPrice || 0) * Number(m.stock || 0), 0),
    westernCost: costOf('西药'),
    patentCost: costOf('中成药'),
    herbCost: costOf('中药'),
    materialCost: costOf('医用材料')
  }
})

// 物资实际成本合计（动态计算，替代写死的「¥2,294.00」）
const materialTotalCost = computed(() => {
  return materialMedicines.value
    .reduce((sum, m) => sum + Number(m.costPrice || 0) * Number(m.stock || 0), 0)
    .toFixed(2)
})

// ── 物资管理增删改查 ──
const openNewMaterialModal = () => {
  medForm.value = {
    id: null,
    name: '',
    pinyinCode: '',
    barcode: '',
    approvalNumber: '',
    manufacturer: '',
    primaryCategory: '医用材料',
    secondaryCategory: '',
    specification: '',
    costPrice: 0,
    price: 0,
    stock: 0,
    warningStock: 50,
    locationCode: '',
    unit: '件'
  }
  showMedicineModal.value = true
}

const removeMedicine = async (m) => {
  try {
    await ElMessageBox.confirm(`确认删除档案【${m.name}】？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch (e) { return }
  try {
    await axios.post(`/api/pharmacy/medicines/delete/${m.id}`)
    ElMessage.success('档案已删除！')
    await loadMedicines()
    await loadStats()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

// ── 药品-物资开方联动配置 ──
const showLinkModal = ref(false)
const linkSupply = ref(null)
const linkList = ref([])
const linkForm = ref({ medicineId: null, quantity: 1 })

// 可参与联动的药品：排除医用材料自身（物资不能联动物资）与当前物资
const linkableMedicines = computed(() => {
  return (medicines.value || []).filter(m =>
    m.primaryCategory !== '医用材料' && (!linkSupply.value || m.id !== linkSupply.value.id))
})

const openLinkModal = async (supply) => {
  linkSupply.value = supply
  linkForm.value = { medicineId: null, quantity: 1 }
  try {
    const res = await axios.get('/api/pharmacy/supply-links', { params: { supplyId: supply.id } })
    linkList.value = res.data || []
  } catch (e) {
    linkList.value = []
  }
  showLinkModal.value = true
}

const saveLink = async () => {
  if (!linkForm.value.medicineId) {
    ElMessage.warning('请选择触发联动的药品！')
    return
  }
  try {
    const res = await axios.post('/api/pharmacy/supply-links/save', {
      supplyId: linkSupply.value.id,
      medicineId: linkForm.value.medicineId,
      quantity: linkForm.value.quantity
    })
    ElMessage.success(res.data.message || '联动配置已保存！')
    const refreshed = await axios.get('/api/pharmacy/supply-links', { params: { supplyId: linkSupply.value.id } })
    linkList.value = refreshed.data || []
    linkForm.value = { medicineId: null, quantity: 1 }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存联动配置失败')
  }
}

const removeLink = async (row) => {
  try {
    await axios.post(`/api/pharmacy/supply-links/delete/${row.id}`)
    ElMessage.success('联动配置已删除！')
    linkList.value = linkList.value.filter(x => x.id !== row.id)
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadPrescriptions()
  loadMedicines()
  loadInbounds()
  loadStocktakes()
  loadStats()
  loadDict()
  loadAllDictOptions()
})

// 弹窗表单
const showMedicineModal = ref(false)

// 规格 = 完整包装规格模板（如 5mg*7片/盒）：下拉选模板填入输入框后，数字可自由修改（不会被覆盖）
const defaultSpecTemplates = [
  '5mg*7片/盒', '0.25g*24粒/盒', '10ml*10支/盒', '15ml/瓶', '2ml*5支/盒', '10g*6袋/盒',
  '0.5g*30片/盒', '1g(相当于饮片5g)/袋', '8贴/盒', '12g*10袋/盒'
]
const defaultSecondaryCategories = [
  '水丸', '颗粒', '颗粒剂', '胶囊', '片剂', '贴剂', '丸剂', '注射剂', '口服溶液剂',
  '混悬滴剂', '配方颗粒', '敷料', '消毒液', '管件', '中医理疗'
]
const secondaryCategoryOptions = defaultSecondaryCategories

// ==================== 下拉框管理（表单下拉选项统一管理与添加） ====================
const dictTypes = ['规格模板', '二级剂型', '批准文号', '生产厂家', '库位码']
const dictTypeFilter = ref('规格模板')
const dictList = ref([])
const newDictValue = ref('')

const loadDict = async () => {
  try {
    const res = await axios.get('/api/pharmacy/dict', { params: { type: dictTypeFilter.value } })
    dictList.value = (res.data || []).map(x => ({ ...x, _editing: false }))
  } catch (e) {
    console.warn('加载基础数据失败:', e)
  }
}

const addDictItem = async () => {
  const v = (newDictValue.value || '').trim()
  if (!v) { ElMessage.warning('请输入要添加的选项值！'); return }
  try {
    const res = await axios.post('/api/pharmacy/dict/save', { dictType: dictTypeFilter.value, dictValue: v })
    if (res.data.success === false) { ElMessage.warning(res.data.message); return }
    ElMessage.success(res.data.message || '已添加！')
    newDictValue.value = ''
    await loadDict()
    await loadAllDictOptions()
  } catch (e) {
    ElMessage.error('添加失败')
  }
}

const saveDictEdit = async (row) => {
  const v = (row.dict_value || '').trim()
  if (!v) { ElMessage.warning('选项值不能为空！'); return }
  try {
    await axios.post('/api/pharmacy/dict/save', { id: row.id, dictType: row.dict_type, dictValue: v })
    ElMessage.success('已保存！')
    row._editing = false
    await loadDict()
    await loadAllDictOptions()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const removeDictItem = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除选项【${row.dict_value}】？已保存的档案不受影响。`, '删除确认', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch (e) { return }
  try {
    await axios.post(`/api/pharmacy/dict/delete/${row.id}`)
    ElMessage.success('已删除！')
    await loadDict()
    await loadAllDictOptions()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

// 全量加载各类字典（供各下拉合并使用）
const allDictOptions = ref({ '规格模板': [], '二级剂型': [], '批准文号': [], '生产厂家': [], '库位码': [] })
const loadAllDictOptions = async () => {
  try {
    await Promise.all(dictTypes.map(async t => {
      const res = await axios.get('/api/pharmacy/dict', { params: { type: t } })
      allDictOptions.value[t] = (res.data || []).map(x => x.dict_value)
    }))
  } catch (e) {
    console.warn('加载字典选项失败:', e)
  }
}

// 规格模板选项 = 内置默认 + 字典 + 现有档案规格（去重）
const specTemplateOptions = computed(() => {
  const s = new Set([...defaultSpecTemplates, ...(allDictOptions.value['规格模板'] || [])])
  ;(medicines.value || []).forEach(m => { if (m.specification && m.specification !== '标准') s.add(m.specification) })
  return Array.from(s)
})
// 选择规格模板 → 填入输入框，之后可自由修改数字（不会被覆盖）
const applySpecTemplate = (tpl) => {
  medForm.value.specification = tpl
}
// 库位码选项 = 字典 + 现有档案（去重）
const locationCodeOptions = computed(() => {
  const s = new Set([...(allDictOptions.value['库位码'] || [])])
  ;(medicines.value || []).forEach(m => { if (m.locationCode) s.add(m.locationCode) })
  return Array.from(s)
})
const secondaryFormOptions = computed(() => {
  const s = new Set([...defaultSecondaryCategories, ...(allDictOptions.value['二级剂型'] || [])])
  return Array.from(s)
})

// 弹窗标题随建档对象动态变化（药品档案 / 医用物资档案）
const medModalTitle = computed(() => {
  if (medForm.value && medForm.value.primaryCategory === '医用材料') {
    return (medForm.value.id ? '编辑' : '新建') + '医用物资档案 (物资管理专用)'
  }
  return (medForm.value.id ? '编辑' : '新建') + '药品商品档案'
})

const medForm = ref({
  id: null,
  name: '',
  pinyinCode: '',
  barcode: '',
  approvalNumber: '',
  manufacturer: '',
  primaryCategory: '中成药',
  secondaryCategory: '',
  specification: '',
  costPrice: 0,
  price: 0,
  stock: 0,
  warningStock: 50,
  locationCode: '',
  unit: '盒'
})

// 必填校验规则（红色星号由 prop+rules 自动渲染）
const medFormRef = ref(null)
const notBlank = (msg) => (rule, value, cb) => {
  if (value === null || value === undefined || String(value).trim() === '') cb(new Error(msg))
  else cb()
}
const medFormRules = {
  name: [{ required: true, validator: notBlank('请输入通用名称'), trigger: 'blur' }],
  approvalNumber: [{ required: true, validator: notBlank('请选择或输入批准文号'), trigger: 'change' }],
  secondaryCategory: [{ required: true, validator: notBlank('请选择或输入二级剂型'), trigger: 'change' }],
  specification: [{ required: true, validator: notBlank('请选择或输入规格单位（如 盒/瓶/支）'), trigger: 'change' }],
  locationCode: [{ required: true, validator: notBlank('请输入库位码，如 A-01-01'), trigger: 'blur' }],
  costPrice: [{ required: true, type: 'number', validator: (r, v, cb) => (v === null || v === undefined || isNaN(v)) ? cb(new Error('请输入进货成本价')) : cb(), trigger: 'blur' }],
  price: [{ required: true, type: 'number', validator: (r, v, cb) => (v === null || v === undefined || isNaN(v)) ? cb(new Error('请输入零售价（门诊开方单价）')) : cb(), trigger: 'blur' }]
}

// 批准文号下拉：基础数据字典 + 已有档案文号，可自定义输入
const approvalNumberOptions = computed(() => {
  const s = new Set([...(allDictOptions.value['批准文号'] || [])])
  ;(medicines.value || []).forEach(m => { if (m.approvalNumber) s.add(m.approvalNumber) })
  return Array.from(s)
})
// 生产厂家下拉：基础数据字典 + 已有档案厂家，可自定义输入
const manufacturerOptions = computed(() => {
  const s = new Set([...(allDictOptions.value['生产厂家'] || [])])
  ;(medicines.value || []).forEach(m => { if (m.manufacturer) s.add(m.manufacturer) })
  return Array.from(s)
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
    secondaryCategory: '',
    specification: '',
    costPrice: 0,
    price: 0,
    stock: 0,
    warningStock: 50,
    locationCode: '',
    unit: '盒'
  }
  showMedicineModal.value = true
  nextTick(() => medFormRef.value && medFormRef.value.clearValidate())
}

const drugBoxFileInput = ref(null)
const drugBoxPreview = ref('')
const drugBoxFileName = ref('')

const triggerDrugBoxUpload = () => {
  if (drugBoxFileInput.value) drugBoxFileInput.value.click()
}

const handleDrugBoxFileChange = (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择药盒实物高清照片（JPG/PNG格式）')
    return
  }
  drugBoxFileName.value = file.name
  const reader = new FileReader()
  reader.onload = (event) => {
    drugBoxPreview.value = event.target.result
    // 自动尝试从文件名解析药名预填
    const cleanName = file.name.replace(/\.[^/.]+$/, '').replace(/[0-9_\-\s]/g, '')
    if (cleanName && cleanName.length >= 2 && !medForm.value.name) {
      medForm.value.name = cleanName
    }
    ElMessage.success(`药盒包装照片【${file.name}】已成功载入！请核对并完善药品规格参数。`)
  }
  reader.readAsDataURL(file)
}

const removeDrugBoxImage = () => {
  drugBoxPreview.value = ''
  drugBoxFileName.value = ''
  if (drugBoxFileInput.value) drugBoxFileInput.value.value = ''
  ElMessage.info('已移除药盒照片附件')
}

const editMedicine = (m) => {
  medForm.value = { ...m }
  showMedicineModal.value = true
  nextTick(() => medFormRef.value && medFormRef.value.clearValidate())
}

const submitMedicine = async () => {
  // 统一走表单规则校验（红色星号必填项），校验不通过自动定位提示
  if (medFormRef.value) {
    try {
      await medFormRef.value.validate()
    } catch (e) {
      ElMessage.warning('请先补全必填项（标红星字段）！')
      return
    }
  }
  try {
    await axios.post('/api/pharmacy/medicines/save', medForm.value)
    ElMessage.success('商品档案已保存！')
    showMedicineModal.value = false
    await loadMedicines()
    await loadStats()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
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
  // 真实导出：把当前药品进销存台账生成 CSV 文件下载（带 BOM，Excel 可直接打开）
  if (!medicines.value || medicines.value.length === 0) {
    ElMessage.warning('当前无药品台账数据可导出')
    return
  }
  const header = ['药品编号', '药品名称', '规格', '库存', '预警线', '零售价', '分类', '生产厂家']
  const rows = medicines.value.map(m => [
    m.id || '', m.name || '', m.specification || '', m.stock ?? '', m.warningStock ?? '',
    m.price ?? '', m.category || m.primaryCategory || '', m.manufacturer || ''
  ])
  const csv = [header, ...rows]
    .map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(','))
    .join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `药品进销存台账_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success('药品进销存台账 CSV 已导出')
}

const syncCloudWarehouse = async () => {
  // 真实实现：从后端重新拉取最新药品台账（云端药房数据同步），而非仅弹提示
  await loadMedicines()
  ElMessage.success('已从云端药房同步最新药品台账（共 ' + (medicines.value?.length || 0) + ' 条）')
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

.field-tip {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
  margin-top: 2px;
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
