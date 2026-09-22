package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·商品与进销存智能体（OA_PRODUCT）
 * 处理「商品下架/上架/调价/入库补货/商品表格批量新增」类指令
 */
@Component
public class OaProductAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_PRODUCT;
    }

    @Override
    protected String skillHint() {
        return "OA_PRODUCT";
    }
}
