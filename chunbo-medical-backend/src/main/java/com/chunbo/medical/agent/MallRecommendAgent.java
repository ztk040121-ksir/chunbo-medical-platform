package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城·商品推荐智能体（MALL_RECOMMEND）
 * 处理「想找/推荐/挑选/去掉或更换某药品、描述症状求药」类意图
 */
@Component
public class MallRecommendAgent extends MallBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_RECOMMEND;
    }

    @Override
    protected String skillHint() {
        return "MALL_RECOMMEND";
    }
}
