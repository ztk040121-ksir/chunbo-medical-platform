package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体路由框架（参照《SpringAI》笔记多智能体协作标准实现）
 * 先由路由智能体（RouteAgent）用一次 LLM 调用判断用户意图，
 * 再通过 findAgentByType 找到对应业务智能体，委派其 processStream 流式处理。
 * 补全了参考源码 AgentServiceImpl 中「路由出意图后 return null」的半成品缺陷。
 *
 * 会话意图延续：用户发出「发第二个」「全部发货」这类指代性短消息时，
 * 单轮路由 LLM 无法从消息本身判断业务域（会掉进规则技能兜底），
 * 此时沿用该会话上一轮的路由意图，保证多轮操作（如多订单确认）不中断。
 */
@Component
public class AgentRouter {

    @Autowired
    private List<Agent> agents;

    private static final int MAX_SESSIONS = 1000;
    /** 会话最近一次路由意图（指代性短消息沿用），key=sessionId，最大容量1000按LRU淘汰防内存泄漏 */
    private final Map<String, String> lastIntentBySession = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SESSIONS;
                }
            }
    );

    /** 指代性/延续性短消息识别：指代序号、确认类，以及「补充信息类」（如按提示补账号/密码/手机号），且不含明确业务关键词 */
    private boolean isFollowUp(String question) {
        if (question == null) return false;
        String q = question.trim();
        if (q.isEmpty() || q.length() > 40) return false;
        // 明确含业务关键词时正常路由，不沿用
        if (q.contains("工资") || q.contains("工资表") || q.contains("订单号") || q.contains("库存")
                || q.contains("审批") || q.contains("贴敷") || q.contains("营收")) {
            return false;
        }
        // 补充信息类短消息（多轮引导填单场景）：含手机号/账号/密码/姓名/地址等字段词或较长数字串
        // 如「14539326819，用这个手机号」「登录账号：77，密码：123456」——单轮路由判不出意图，应沿用上一轮
        if (q.matches(".*\\d{4,}.*") || q.contains("手机号") || q.contains("电话") || q.contains("账号")
                || q.contains("密码") || q.contains("姓名") || q.contains("昵称") || q.contains("地址")) {
            return true;
        }
        // 时间/月份/短确认类：如「9月份」「9月」「本月」「2026-09」「今天」「昨天」「是的」「好的」等
        if (q.matches(".*(\\d{1,2}\\s*月(份)?|202\\d[-/.]\\d{1,2}|本月|上月|下月|今天|明天|昨天|当前月).*")) {
            return true;
        }
        // 极短消息（<=10个字符，且不包含退出/取消）：在已有上一轮会话意图时，直接视为上下文补充延续！
        if (q.length() <= 10 && !q.contains("退出") && !q.contains("取消")) {
            return true;
        }
        return q.matches(".*(第[一二三四五六七八九十百\\d]+\\s*[个条单笔号]?|全部(发货|送达|发放|确认|出库)?|都发|依次|按顺序|上一个|刚(才|刚)?那个|这个|就绪|确定|好|可以|是的?).*");
    }

    /**
     * 极速意图匹配（Fast-Path 规则）：
     * 对高频明确指令 0ms 瞬间分流，彻底杜绝先等 2-4 秒路由 LLM 造成的前端假死白屏
     */
    private boolean isGreetingOrGeneral(String s) {
        return s.matches(".*(你好|您好|在吗|在不在|早|早安|早上好|下午好|晚上好|嗨|hello|hi|在么|请问|咨询|帮助).*");
    }

    /**
     * 极速意图匹配（Fast-Path 规则）：
     * 对高频明确指令 0ms 瞬间分流，彻底杜绝先等 2-4 秒路由 LLM 造成的前端假死白屏
     */
    private String fastMatchIntent(String q) {
        if (q == null) return null;
        String s = q.trim();

        // 1. OA 门诊大盘与经营营收（优先匹配！防止「门诊运营大盘真实诊断」被「诊断」误拦截为辨证开方）
        if (s.contains("大盘") || s.contains("营收") || s.contains("营业") || s.contains("门诊量") || s.contains("收入") || s.contains("挂号费") || s.contains("经营") || s.contains("运营")) {
            return "OA_ANALYTICS";
        }

        // 2. 门诊辨证开方与临床常见主诉/症状（排除大盘、经营、运营、营收）
        if ((s.contains("辨证") || s.contains("开方") || s.contains("处方") || s.contains("主诉")
                || s.contains("开药") || s.contains("拟定") || s.contains("电子病历") || s.contains("诊断")
                || s.contains("发热") || s.contains("发烧") || s.contains("咳嗽") || s.contains("头痛")
                || s.contains("感冒") || s.contains("咽痛") || s.contains("腹痛") || s.contains("腹泻")
                || s.contains("胃痛") || s.contains("头晕") || s.contains("高血压") || s.contains("过敏")
                || s.contains("湿疹") || s.contains("皮疹") || s.contains("调理") || s.contains("怎么治")
                || s.contains("用药方案") || s.contains("用药建议"))
                && !s.contains("大盘") && !s.contains("经营") && !s.contains("运营") && !s.contains("营收")) {
            return "MED_DIAGNOSE";
        }

        // 药房库存与价格
        if (s.contains("药房") || s.contains("库房") || s.contains("缺药") || s.contains("在库")
                || (s.contains("库存") && !s.contains("商城") && !s.contains("进销存"))) {
            return "MED_STOCK";
        }

        // OA 审批/考勤/请假
        if (s.contains("请假") || s.contains("审批") || s.contains("批假") || s.contains("考勤") || s.contains("待办")) {
            return "OA_APPROVAL";
        }

        // OA 工资薪资
        if (s.contains("工资") || s.contains("薪资") || s.contains("薪酬") || s.contains("薪水") || s.contains("实发") || s.contains("应发") || s.contains("工资条") || s.contains("提成") || s.contains("发薪")) {
            return "OA_SALARY";
        }

        // OA/商城 履约与发货
        if (s.contains("发货") || s.contains("出库") || s.contains("送达") || s.contains("签收") || s.contains("待发货")) {
            return "OA_ORDER";
        }

        // OA 商品进销存与补货调价研判
        if (s.contains("上架") || s.contains("下架") || s.contains("调价") || s.contains("改价") || s.contains("进销存")
                || s.contains("补货") || s.contains("研判") || s.contains("库存预警")
                || (s.contains("商品") && (s.contains("库存") || s.contains("预警") || s.contains("列表") || s.contains("新增")))) {
            return "OA_PRODUCT";
        }

        // OA 商城用户管理
        if (s.contains("注册用户") || s.contains("商城用户") || s.contains("注册商城") || s.contains("会员") || s.contains("体验金") || s.contains("购药金")) {
            return "OA_MALL_USER";
        }

        // OA 特色贴敷理疗
        if (s.contains("贴敷") || s.contains("理疗") || s.contains("穴位") || s.contains("通络贴") || s.contains("三伏贴") || s.contains("外治")) {
            return "OA_PLASTER";
        }

        // OA 排班值班
        if (s.contains("排班") || s.contains("值班") || s.contains("坐诊") || s.contains("班表")) {
            return "OA_SCHEDULE";
        }

        // 商城发货/配送/运费
        if (s.contains("运费") || s.contains("快递") || s.contains("配送") || s.contains("包邮") || s.contains("几天到") || s.contains("物流") || s.contains("单号") || s.contains("到哪了")) {
            return "MALL_SHIPPING";
        }

        // 商城推荐/对症选药
        if (s.contains("推荐") || s.contains("吃什么药") || s.contains("用什么药") || s.contains("有什么药") || s.contains("买药") || s.contains("在售") || s.contains("配伍") || s.contains("禁忌") || s.contains("一起吃") || s.contains("价格表")) {
            return "MALL_RECOMMEND";
        }

        return null;
    }

    /**
     * 路由：RouteAgent 判意图 → 业务智能体 processStream；意图未命中或路由失败时回退到默认业务智能体
     *
     * @param routeAgent   路由智能体
     * @param defaultAgent 默认兜底业务智能体
     */
    public Flux<ChatEventVO> route(Agent routeAgent, Agent defaultAgent,
                                   String question, String sessionId, String userId) {
        Agent target = null;
        String intent = fastMatchIntent(question);
        if (intent != null) {
            target = findAgentByType(AgentTypeEnum.agentNameOf(intent));
        }
        // 指代性短消息沿用上一轮意图（如多订单确认的「发第二个」「全部发货」）
        if (target == null) {
            String cached = sessionId != null ? lastIntentBySession.get(sessionId) : null;
            if (cached != null && isFollowUp(question)) {
                intent = cached;
                target = findAgentByType(AgentTypeEnum.agentNameOf(intent));
            }
        }
        if (target == null) {
            // 对短问句或日常问候快速回退到 defaultAgent，杜绝等待 2-4 秒的同步 LLM 路由阻塞首包
            if (question != null && (question.trim().length() <= 8 || isGreetingOrGeneral(question.trim()))) {
                target = defaultAgent;
            } else {
                try {
                    intent = routeAgent.process(question, sessionId, userId);
                    AgentTypeEnum type = AgentTypeEnum.agentNameOf(intent);
                    if (type != null && !type.getAgentName().endsWith("_ROUTE")) {
                        target = findAgentByType(type);
                    }
                } catch (Exception ignored) {
                }
                if (target == null) {
                    target = defaultAgent;
                }
            }
        }
        if (sessionId != null && intent != null && !intent.endsWith("_ROUTE")) {
            lastIntentBySession.put(sessionId, intent);
        }
        // 把路由判出的意图作为提示传给业务智能体（如 MALL_SHIPPING 直接走配送政策技能）
        return target.processStream(question, sessionId, userId, intent);
    }

    /**
     * 路由（携带业务上下文）：同上，额外把 patientId/emrContext 等上下文透传给业务智能体
     */
    public Flux<ChatEventVO> route(Agent routeAgent, Agent defaultAgent,
                                   String question, String sessionId, String userId, java.util.Map<String, Object> context) {
        Agent target = null;
        String intent = fastMatchIntent(question);
        if (intent != null) {
            target = findAgentByType(AgentTypeEnum.agentNameOf(intent));
        }
        if (target == null) {
            String cached = sessionId != null ? lastIntentBySession.get(sessionId) : null;
            if (cached != null && isFollowUp(question)) {
                intent = cached;
                target = findAgentByType(AgentTypeEnum.agentNameOf(intent));
            }
        }
        if (target == null) {
            // 对短问句或日常问候快速回退到 defaultAgent，杜绝等待 2-4 秒的同步 LLM 路由阻塞首包
            if (question != null && (question.trim().length() <= 8 || isGreetingOrGeneral(question.trim()))) {
                target = defaultAgent;
            } else {
                try {
                    intent = routeAgent.process(question, sessionId, userId);
                    AgentTypeEnum type = AgentTypeEnum.agentNameOf(intent);
                    if (type != null && !type.getAgentName().endsWith("_ROUTE")) {
                        target = findAgentByType(type);
                    }
                } catch (Exception ignored) {
                }
                if (target == null) {
                    target = defaultAgent;
                }
            }
        }
        if (sessionId != null && intent != null && !intent.endsWith("_ROUTE")) {
            lastIntentBySession.put(sessionId, intent);
        }
        return target.processStream(question, sessionId, userId, intent, context);
    }

    /** 从 Spring 容器按类型查找业务智能体 */
    private Agent findAgentByType(AgentTypeEnum type) {
        if (type == null) return null;
        for (Agent agent : agents) {
            if (agent.getAgentType() == type) {
                return agent;
            }
        }
        return null;
    }
}
